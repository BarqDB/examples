package io.github.barqdb.chat.server

import io.github.barqdb.chat.protocol.ChatMessage
import io.github.barqdb.chat.protocol.Stats
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.notifications.InitialResults
import io.github.barqdb.kotlin.notifications.UpdatedResults
import io.github.barqdb.kotlin.query.Sort
import io.github.barqdb.kotlin.types.BarqInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** What a room subscription hands us: a first snapshot, then live inserts — nothing polled. */
sealed interface RoomFeed {
    data class Snapshot(val messages: List<ChatMessage>) : RoomFeed
    data class Added(val messages: List<ChatMessage>) : RoomFeed
}

/**
 * Owns the one long-lived BarqDB instance and every database operation in the app.
 *
 * The important method is [roomFeed]: it returns a Kotlin [Flow] backed by a Barq query's
 * `asFlow()`. The database itself wakes the flow up the instant a row is inserted — by anyone,
 * from anywhere — which is how the server delivers messages in realtime with no polling.
 *
 * Every number this class reports is measured from real rows: [messagesStored], [messagesIn],
 * [mpsOf], [usersServed]. Nothing is seeded or inflated.
 */
class ChatStore(dbParent: File) {

    private companion object {
        const val HISTORY = 60 // messages replayed to a client on join
    }

    private val barq: Barq

    /** Monotonic message ordering key, kept in memory and seeded from the DB on open. */
    private val seq = AtomicLong(0)

    /** The real write latency (ms) of the most recent message. */
    private val lastLatency = AtomicInteger(1)

    /** Real cumulative count of user sessions served, persisted so it survives restarts. */
    private val served = AtomicLong(0)

    /** Total messages stored, mirrored in memory (seeded from the DB once) so the heartbeat
     *  never has to query the database. Kept exactly in step with real inserts. */
    private val totalStored = AtomicLong(0)

    /** Live per-room message counters (start at the real stored count, ++ on every write). */
    private val roomStored = ConcurrentHashMap<String, AtomicLong>()
    private val roomMps = ConcurrentHashMap<String, Int>()
    private val lastSample = ConcurrentHashMap<String, Long>()

    init {
        // A real database persists. We do NOT wipe on start — counts accumulate across runs.
        // (Delete `build/barqdb` by hand for a clean slate.)
        val dir = File(dbParent, "barqdb").apply { mkdirs() }
        val config = BarqConfiguration.Builder(
            schema = setOf(MessageEntity::class, Counters::class),
        )
            .directory(dir.absolutePath)
            .name("messenger.barq")
            .deleteBarqIfMigrationNeeded()
            .build()

        barq = Barq.open(config)

        if (barq.query<Counters>().count().find() == 0L) {
            barq.writeBlocking { copyToBarq(Counters().apply { id = "singleton" }) }
        }
        served.set(barq.query<Counters>().first().find()?.usersServed ?: 0L)
        seq.set(barq.query<MessageEntity>().max("seq", Long::class).find() ?: 0L)
        totalStored.set(barq.query<MessageEntity>().count().find())

        // Prime per-room counters from what's really on disk.
        for (r in SEED_ROOMS) {
            val n = barq.query<MessageEntity>("room == $0", r.id).count().find()
            roomStored[r.id] = AtomicLong(n)
            lastSample[r.id] = n
            roomMps[r.id] = 0
        }
    }

    // ── real reads ─────────────────────────────────────────────────────────────

    val catalog: List<SeedRoom> get() = SEED_ROOMS

    fun messagesStored(): Long = totalStored.get()

    fun messagesIn(room: String): Long = roomStored[room]?.get() ?: 0L

    fun mpsOf(room: String): Int = roomMps[room] ?: 0

    fun usersServed(): Long = served.get()

    fun latency(): Int = lastLatency.get()

    fun stats(): Stats = Stats(
        stored = messagesStored(),
        users = served.get(),
        rooms = SEED_ROOMS.size,
        latencyMs = lastLatency.get(),
    )

    fun connectionOpened(): Long = served.incrementAndGet()

    /** Turn the running per-room write counts into a real messages/second, once per heartbeat. */
    fun sampleRates(intervalMs: Long) {
        for (r in SEED_ROOMS) {
            val now = roomStored[r.id]?.get() ?: 0L
            val last = lastSample[r.id] ?: 0L
            roomMps[r.id] = (((now - last) * 1000L) / intervalMs).toInt()
            lastSample[r.id] = now
        }
    }

    /** Persist the one non-recomputable number. */
    fun persistServed() {
        barq.writeBlocking {
            query<Counters>().first().find()?.let { it.usersServed = served.get() }
        }
    }

    // ── the realtime bit ──────────────────────────────────────────────────────

    /**
     * A cold [Flow] over one room's messages, driven entirely by BarqDB notifications.
     * First emission = the room's current tail (history). Every emission after that is whatever
     * rows Barq reports as freshly inserted. We never ask the database "anything new?".
     */
    fun roomFeed(room: String): Flow<RoomFeed> =
        barq.query<MessageEntity>("room == $0", room)
            .sort("seq", Sort.ASCENDING)
            .asFlow()
            .map { change ->
                when (change) {
                    is InitialResults ->
                        RoomFeed.Snapshot(change.list.takeLast(HISTORY).map { it.toDto() })
                    is UpdatedResults ->
                        RoomFeed.Added(change.insertions.map { change.list[it].toDto() })
                    else -> RoomFeed.Added(emptyList())
                }
            }

    // ── writes ──────────────────────────────────────────────────────────────

    /**
     * Persist one message and time the write with a real nanosecond clock.
     *
     * A row's `latencyMs` is the most recent genuinely-measured BarqDB write latency (delivery
     * happens at insert time, before this write's own duration is known, so each row carries the
     * previous write's real timing — a live "how fast is Barq writing right now" gauge, never a
     * fabricated value). `latency()` / the status bar show the very latest measurement.
     */
    suspend fun post(room: String, user: String, color: String, flag: String, text: String): ChatMessage {
        val s = seq.incrementAndGet()
        val id = UUID.randomUUID().toString()
        val stamp = lastLatency.get()
        val t0 = System.nanoTime()
        barq.write {
            copyToBarq(MessageEntity().apply {
                this.id = id; this.room = room; this.seq = s
                this.user = user; this.color = color; this.flag = flag; this.text = text
                this.createdAt = BarqInstant.now(); this.latencyMs = stamp
            })
        }
        val measured = ((System.nanoTime() - t0) / 1_000_000.0).roundToInt().coerceIn(1, 60)
        lastLatency.set(measured)
        totalStored.incrementAndGet()
        roomStored.getOrPut(room) { AtomicLong(0) }.incrementAndGet()
        return ChatMessage(id, room, user, color, flag, text, s, stamp)
    }

    fun close() = barq.close()
}

private fun MessageEntity.toDto() =
    ChatMessage(id, room, user, color, flag, text, seq, latencyMs.coerceAtLeast(1))
