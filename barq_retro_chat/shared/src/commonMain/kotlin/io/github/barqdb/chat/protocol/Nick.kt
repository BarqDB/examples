package io.github.barqdb.chat.protocol

/** Nickname coloring, shared so the server and browser always pick the same color for a name. */
object Nick {
    val COLORS = listOf(
        "#C0392B", "#2472bd", "#1f9950", "#8E44AD", "#D35400",
        "#127a6b", "#c2185b", "#5b6b1f", "#0277bd", "#b8860b",
    )

    fun colorFor(name: String): String {
        var h = 0
        for (c in name) h = (h * 31 + c.code) and 0x7fffffff
        return COLORS[h % COLORS.size]
    }
}
