package io.github.barqdb.chat.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wire protocol shared by the Ktor server (JVM) and the browser client (JS).
 *
 * One `.kt` file, compiled to both targets, so the two ends can never disagree about the
 * shape of a message. Everything is a `@Serializable` sealed hierarchy encoded as JSON with
 * a `t` discriminator field, e.g. `{"t":"say","text":"hi"}`.
 */

// ─────────────────────────── shared value types ───────────────────────────

@Serializable
data class RoomInfo(
    val id: String,
    val icon: String,
    val name: String,
    val topic: String,
    val online: Long,   // people actually connected to this room right now
    val mps: Int,        // messages/second actually written to this room, last sample
    val stored: Long,    // messages actually stored for this room
)

@Serializable
data class ChatMessage(
    val id: String,
    val room: String,
    val user: String,
    val color: String,
    val flag: String,
    val text: String,
    val seq: Long,
    /** Server-measured write latency for this message, in milliseconds. Real, not faked. */
    val latencyMs: Int,
)

@Serializable
data class Presence(
    val name: String,
    val color: String,
    /** "#37c257" (active) or "#f5a623" (idle). */
    val dot: String,
)

/** Headline numbers, every one computed from the live database — nothing seeded. */
@Serializable
data class Stats(
    val stored: Long,      // total messages actually stored (all rooms)
    val users: Long,       // user sessions actually served since launch
    val rooms: Int,        // number of rooms
    val latencyMs: Int,    // most recent real write latency
)

// ─────────────────────────── client → server ───────────────────────────

@Serializable
sealed interface ClientMsg

/** "I'm here, this is who I am." Sent right after the socket opens. */
@Serializable
@SerialName("hello")
data class Hello(val user: String, val color: String, val status: String) : ClientMsg

/** "Subscribe me to this room." The server answers with history, presence, then live pushes. */
@Serializable
@SerialName("join")
data class Join(val room: String) : ClientMsg

/** "Unsubscribe me from the current room." */
@Serializable
@SerialName("leave")
data object Leave : ClientMsg

/** "Store this message." The server writes it to BarqDB; the subscription fans it back out. */
@Serializable
@SerialName("say")
data class Say(val text: String) : ClientMsg

/** "I'm typing." Purely cosmetic; not persisted. */
@Serializable
@SerialName("typing")
data object Typing : ClientMsg

// ─────────────────────────── server → client ───────────────────────────

@Serializable
sealed interface ServerMsg

/** Sent once on connect: the room catalogue plus the opening stats line. */
@Serializable
@SerialName("welcome")
data class Welcome(val rooms: List<RoomInfo>, val stats: Stats) : ServerMsg

/** The last N messages of a room, sent as the FIRST emission of the Barq subscription. */
@Serializable
@SerialName("history")
data class History(val room: String, val messages: List<ChatMessage>) : ServerMsg

/**
 * One or more brand-new messages, pushed the instant BarqDB reports them as insertions.
 * This is the whole point of the demo: nobody polled for these — the database woke us up.
 */
@Serializable
@SerialName("new")
data class NewMessages(val room: String, val messages: List<ChatMessage>) : ServerMsg

/** Who's actually connected to the current room, plus its real stored-message count. */
@Serializable
@SerialName("presence")
data class Presences(
    val room: String,
    val online: Long,
    val stored: Long,
    val users: List<Presence>,
) : ServerMsg

/** Refreshed room catalogue with live online / msg-s / stored numbers, pushed on the heartbeat. */
@Serializable
@SerialName("rooms")
data class RoomList(val rooms: List<RoomInfo>) : ServerMsg

/** Someone in the room is typing. */
@Serializable
@SerialName("typing")
data class TypingSignal(val user: String, val color: String) : ServerMsg

/** Fresh headline numbers. */
@Serializable
@SerialName("stats")
data class StatsUpdate(val stats: Stats) : ServerMsg
