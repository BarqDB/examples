package io.github.barqdb.chat.server

import io.github.barqdb.chat.protocol.ClientMsg
import io.github.barqdb.chat.protocol.Hello
import io.github.barqdb.chat.protocol.History
import io.github.barqdb.chat.protocol.Join
import io.github.barqdb.chat.protocol.Leave
import io.github.barqdb.chat.protocol.NewMessages
import io.github.barqdb.chat.protocol.Nick
import io.github.barqdb.chat.protocol.Presence
import io.github.barqdb.chat.protocol.Presences
import io.github.barqdb.chat.protocol.RoomInfo
import io.github.barqdb.chat.protocol.RoomList
import io.github.barqdb.chat.protocol.Say
import io.github.barqdb.chat.protocol.Search
import io.github.barqdb.chat.protocol.SearchResults
import io.github.barqdb.chat.protocol.ServerMsg
import io.github.barqdb.chat.protocol.StatsUpdate
import io.github.barqdb.chat.protocol.Typing
import io.github.barqdb.chat.protocol.TypingSignal
import io.github.barqdb.chat.protocol.Welcome
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** One JSON codec for the whole app. `t` is the type discriminator, e.g. `{"t":"say",...}`. */
val json = Json {
    classDiscriminator = "t"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** A single connected browser. Wraps the socket and serializes all writes to it. */
class Conn(private val session: DefaultWebSocketServerSession) {
    val id: String = UUID.randomUUID().toString()
    var user: String = ""
    var color: String = ""
    var status: String = "online"
    var room: String? = null
    var feedJob: Job? = null

    private val sendLock = Mutex()

    /** Send is guarded so the room-feed, the bots, and the heartbeat can't interleave frames. */
    suspend fun send(msg: ServerMsg) = sendLock.withLock {
        session.send(Frame.Text(json.encodeToString<ServerMsg>(msg)))
    }
}

/** Registry of live connections. Knows who is in which room and can fan messages out. */
class Hub {
    private val conns = ConcurrentHashMap<String, Conn>()

    fun add(c: Conn) { conns[c.id] = c }
    fun remove(c: Conn) { conns.remove(c.id) }

    fun membersOf(room: String): List<Conn> = conns.values.filter { it.room == room }

    /** Rooms with at least one live viewer — the only rooms the bots bother chattering in. */
    fun liveRooms(): List<String> = conns.values.mapNotNull { it.room }.distinct()

    suspend fun broadcast(room: String, msg: ServerMsg, except: Conn? = null) {
        for (c in membersOf(room)) if (c !== except) runCatching { c.send(msg) }
    }

    suspend fun broadcastAll(msg: ServerMsg) {
        for (c in conns.values) runCatching { c.send(msg) }
    }
}

/** The room catalogue with live, real numbers: who is connected, real msg/s, real stored count. */
private fun roomListOf(store: ChatStore, hub: Hub): List<RoomInfo> =
    store.catalog.map { r ->
        RoomInfo(
            id = r.id, icon = r.icon, name = r.name, topic = r.topic,
            online = hub.membersOf(r.id).size.toLong(),
            mps = store.mpsOf(r.id),
            stored = store.messagesIn(r.id),
        )
    }

/** Who is really connected to a room, plus its real stored count. No padding, no bots. */
private fun presenceFor(room: String, store: ChatStore, hub: Hub): Presences {
    val members = hub.membersOf(room)
    val users = members
        .filter { it.user.isNotBlank() }
        .map { Presence(it.user, it.color.ifBlank { Nick.colorFor(it.user) }, "#37c257") }
    return Presences(room, members.size.toLong(), store.messagesIn(room), users)
}

/** Subscribe a connection to a room's Barq feed. This is the realtime core of the demo. */
private fun CoroutineScope.startFeed(conn: Conn, room: String, store: ChatStore): Job = launch {
    store.roomFeed(room).collect { feed ->
        when (feed) {
            is RoomFeed.Snapshot -> conn.send(History(room, feed.messages))
            is RoomFeed.Added -> if (feed.messages.isNotEmpty()) conn.send(NewMessages(room, feed.messages))
        }
    }
}

fun Application.module(store: ChatStore, hub: Hub) {
    install(WebSockets)
    install(ContentNegotiation) { json(json) }

    routing {
        get("/healthz") { call.respondText("ok") }
        get("/api/stats") { call.respond(store.stats()) }

        // The whole pure-Kotlin browser app, compiled to JS and bundled into this jar.
        staticResources("/", "web")

        webSocket("/ws") {
            val conn = Conn(this)
            hub.add(conn)
            store.connectionOpened()
            conn.send(Welcome(roomListOf(store, hub), store.stats()))
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val msg = runCatching { json.decodeFromString<ClientMsg>(frame.readText()) }.getOrNull()
                        ?: continue
                    when (msg) {
                        is Hello -> { conn.user = msg.user; conn.color = msg.color; conn.status = msg.status }
                        is Join -> {
                            conn.feedJob?.cancel()
                            conn.room = msg.room
                            // The Barq subscription immediately replays history, then pushes live.
                            conn.feedJob = startFeed(conn, msg.room, store)
                            hub.broadcast(msg.room, presenceFor(msg.room, store, hub))
                        }
                        Leave -> {
                            val left = conn.room
                            conn.feedJob?.cancel(); conn.feedJob = null; conn.room = null
                            left?.let { hub.broadcast(it, presenceFor(it, store, hub)) }
                        }
                        is Say -> {
                            val room = conn.room ?: continue
                            val text = msg.text.trim()
                            if (text.isEmpty()) continue
                            // Write to BarqDB. We do NOT echo it ourselves — the subscription
                            // above delivers it back to this client (and every other one).
                            store.post(
                                room = room,
                                user = conn.user.ifBlank { "guest" },
                                color = conn.color.ifBlank { Nick.colorFor(conn.user) },
                                flag = "",
                                text = text.take(280),
                            )
                        }
                        Typing -> conn.room?.let { r ->
                            hub.broadcast(r, TypingSignal(conn.user, conn.color), except = conn)
                        }
                        is Search -> {
                            val q = msg.q.trim()
                            if (q.isEmpty()) continue
                            // Real BarqDB full-text search over the whole archive, timed on the server.
                            val r = store.search(q, 40)
                            conn.send(SearchResults(q, r.matches, r.latencyMs, store.messagesStored(), r.hits))
                        }
                    }
                }
            } finally {
                val left = conn.room
                hub.remove(conn)
                conn.feedJob?.cancel()
                left?.let { hub.broadcast(it, presenceFor(it, store, hub)) }
            }
        }
    }
}

/**
 * Optional load generator, OFF unless `BARQ_BOTS` is set. When on, a "bot" periodically writes a
 * REAL message into BarqDB for a room that has live viewers. The rows, the stored counts, and the
 * msg/s they produce are all genuine — bots are just a way to create real database activity when
 * you're testing alone. They are never counted as "online" (they aren't connected).
 */
private fun startBots(store: ChatStore, hub: Hub, scope: CoroutineScope) = scope.launch {
    while (true) {
        val rooms = hub.liveRooms()
        if (rooms.isEmpty()) { delay(600); continue }

        val room = rooms.random()
        val bot = BOT_NAMES.random()
        val color = Nick.colorFor(bot)
        val text = (ROOM_TEXTS[room].orEmpty() + BOT_TEXTS).random()

        hub.broadcast(room, TypingSignal(bot, color))
        delay(Random.nextLong(450, 1100))
        store.post(room, bot, color, FLAGS.random(), text)

        delay(Random.nextLong(700, 2000))
    }
}

/** Heartbeat: sample real msg/s, then push real stats + room list + presence to everyone. */
private fun startHeartbeat(store: ChatStore, hub: Hub, scope: CoroutineScope) = scope.launch {
    var tick = 0
    while (true) {
        delay(2000)
        store.sampleRates(2000)
        hub.broadcastAll(StatsUpdate(store.stats()))
        hub.broadcastAll(RoomList(roomListOf(store, hub)))
        for (room in hub.liveRooms()) hub.broadcast(room, presenceFor(room, store, hub))
        if (tick % 15 == 0) store.persistServed()
        tick++
    }
}

private fun botsEnabled(): Boolean {
    val v = System.getenv("BARQ_BOTS")?.trim()?.lowercase()
    return v != null && v !in setOf("", "0", "off", "false", "no")
}

fun main() {
    val port = (System.getenv("PORT") ?: System.getProperty("port"))?.toIntOrNull() ?: 8080
    // Where BarqDB keeps its files. Override with BARQ_DATA_DIR (e.g. a mounted volume in Docker).
    val dataDir = System.getenv("BARQ_DATA_DIR") ?: System.getProperty("barq.data") ?: "data"
    val store = ChatStore(File(dataDir))
    val hub = Hub()
    val engine = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val bots = botsEnabled()
    if (bots) startBots(store, hub, engine)
    startHeartbeat(store, hub, engine)

    Runtime.getRuntime().addShutdownHook(Thread {
        runCatching { store.persistServed() }
        runCatching { store.close() }
    })

    println("barqDB Messenger — http://localhost:$port  (realtime over ws://localhost:$port/ws)")
    println("  data: real (all numbers come from BarqDB); simulated bot traffic: ${if (bots) "ON" else "OFF — set BARQ_BOTS=on to demo load"}")
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module(store, hub) }.start(wait = true)
}
