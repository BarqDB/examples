package io.github.barqdb.chat.server

import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.annotations.FullText
import io.github.barqdb.kotlin.types.annotations.Index
import io.github.barqdb.kotlin.types.annotations.PrimaryKey

/**
 * The persisted schema. These are ordinary Kotlin classes; the Barq compiler plugin rewrites
 * them at build time into live database objects. No DAO, no SQL, no codegen to check in.
 *
 * Everything on screen is computed from these rows — there are no seeded or faked numbers.
 */

/** One chat line. Every message anyone sends is a real row here. */
class MessageEntity : BarqObject {
    @PrimaryKey
    var id: String = ""

    /** Which room this belongs to. Indexed because every subscription filters on it. */
    @Index
    var room: String = ""

    /** Monotonic ordering key. Indexed because we always sort by it. */
    @Index
    var seq: Long = 0

    var user: String = ""
    var color: String = ""
    var flag: String = ""

    /** `@FullText` would let us word-search the chat history if we wanted to. */
    @FullText
    var text: String = ""

    var createdAt: BarqInstant = BarqInstant.from(0, 0)

    /** The real server-side write latency for this row, in ms. */
    var latencyMs: Int = 0
}

/**
 * A single-row tally (id == "singleton") for the one number we can't recompute from messages:
 * how many user sessions this server has served, cumulatively, across restarts.
 */
class Counters : BarqObject {
    @PrimaryKey
    var id: String = "singleton"

    var usersServed: Long = 0
}
