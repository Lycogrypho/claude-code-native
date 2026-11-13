package dev.lain.claudejb.controller.mcp.tools.code

import dev.lain.claudejb.model.mcp.toon.Toon
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadWindowTest {

    private val lines = (1..100).map { "x".repeat(9) }

    @Test
    fun `the window ends at the limit when the budget allows it`() {
        assertEquals(30, ReadWindow.end(lines, from = 1, limit = 30, budget = 10_000))
        assertEquals(100, ReadWindow.end(lines, from = 50, limit = 400, budget = 10_000))
    }

    @Test
    fun `the window stops at the budget, counting each line as TOON encodes it`() {
        assertEquals(10, ReadWindow.end(lines, from = 1, limit = 400, budget = 110))
        assertEquals(19, ReadWindow.end(lines, from = 10, limit = 400, budget = 110))
        val quoted = listOf("\"\"\"\"", "\"\"\"\"", "\"\"\"\"")
        assertEquals(2, ReadWindow.end(quoted, from = 1, limit = 400, budget = 20))
    }

    @Test
    fun `one line is always returned, however long, so a read never comes back empty`() {
        assertEquals(1, ReadWindow.end(listOf("y".repeat(50_000), "z"), from = 1, limit = 400, budget = 100))
    }

    @Test
    fun `the cost of a line is what TOON writes for it, so the window and the encoder cannot drift apart`() {
        val sample = listOf("plain", "quote \" and \\ backslash", "tab\there", "bell \u0007 and cr \r", "")
        val encoded = Toon.encode(buildJsonObject { put("text", sample.joinToString("\n")) })
        assertEquals(encoded.length - "text: ".length, sample.sumOf(ReadWindow::cost))
    }

    @Test
    fun `a start past the end returns nothing`() {
        assertEquals(100, ReadWindow.end(lines, from = 101, limit = 400, budget = 100))
    }
}
