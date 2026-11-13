package dev.lain.claudejb.controller.mcp.tools.code

internal object ReadWindow {

    fun end(lines: List<String>, from: Int, limit: Int, budget: Int): Int {
        val last = minOf(from.toLong() + limit - 1, lines.size.toLong()).toInt()
        var to = from - 1
        var used = 0
        while (to < last) {
            val cost = cost(lines[to])
            if (to >= from && used + cost > budget) break
            used += cost
            to++
        }
        return maxOf(to, minOf(from - 1, lines.size))
    }

    fun cost(line: String): Int = encodedLength(line) + JOIN_COST

    private fun encodedLength(line: String): Int = line.sumOf { c ->
        when {
            c == '\\' || c == '"' || c == '\r' || c == '\t' -> 2
            c < ' ' -> UNICODE_ESCAPE
            else -> 1
        }
    }

    private const val JOIN_COST = 2
    private const val UNICODE_ESCAPE = 6
}
