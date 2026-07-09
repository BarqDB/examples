package io.github.barqdb.chat.client

import kotlin.random.Random

/** Random 2010-flavored screen names, ported from the original design. */
object Names {
    private val W1 = listOf(
        "Shadow", "Neon", "Pixel", "Cyber", "Frost", "Blaze", "Luna", "Star", "Ghost",
        "Rebel", "Storm", "Panda", "Dragon", "Ryder", "Angel", "Vortex", "Havoc", "Sk8r",
    )
    private val W2 = listOf(
        "Ninja", "Wolf", "Angel", "Kid", "Grrl", "Boi", "Master", "Lord",
        "Fairy", "Wizard", "Pirate", "Dreamz", "Vibes", "Legend",
    )

    fun random(): String {
        val a = W1.random()
        val b = W2.random()
        val n = Random.nextInt(0, 100)
        return when (Random.nextInt(0, 5)) {
            0 -> "xX_${a}${b}_Xx"
            1 -> "$a$b$n"
            2 -> "${a.lowercase()}_${b.lowercase()}"
            3 -> "ii$a$b"
            else -> "${a}${b}4life"
        }
    }
}

private val EMOTICONS = listOf(
    ":)" to "🙂", ":D" to "😄", ":P" to "😛", ";)" to "😉",
    "<3" to "❤️", ":(" to "😢", "8)" to "😎", "xD" to "😂",
)

fun emoticons(): List<Pair<String, String>> = EMOTICONS

/** Compact formatting for whatever the real counters happen to be: 1,240 → "1,240", 3,400,000 → "3.4M". */
fun fmtBig(n: Long): String = when {
    n >= 1_000_000_000L -> ((n / 1_000_000_00L) / 10.0).toString().ensureDecimals(2) + "B"
    n >= 1_000_000L -> ((n / 100_000L) / 10.0).toString().ensureDecimals(1) + "M"
    n >= 1_000L -> groupThousands(n)
    else -> n.toString()
}

/** 1,284,502 → "1.28M", 9,120 → "9.1K". */
fun fmtCount(n: Long): String = when {
    n >= 1_000_000L -> ((n / 10_000L) / 100.0).toString().ensureDecimals(2) + "M"
    n >= 1_000L -> ((n / 100L) / 10.0).toString().ensureDecimals(1) + "K"
    else -> n.toString()
}

fun groupThousands(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString()
}

/** Kotlin/JS `Double.toString()` drops trailing zeros ("8.4" not "8.40"); pad them back. */
private fun String.ensureDecimals(places: Int): String {
    val dot = indexOf('.')
    if (dot < 0) return this + "." + "0".repeat(places)
    val decimals = length - dot - 1
    return if (decimals >= places) substring(0, dot + 1 + places) else this + "0".repeat(places - decimals)
}
