package dev.lain.claudejb.controller.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StdoutLinesTest {

    @Test
    fun `lines split across chunks are joined`() {
        val lines = StdoutLines(MAX)
        assertEquals(emptyList<StdoutLines.Out>(), lines.feed("{\"a\":"))
        assertEquals(listOf(StdoutLines.Out.Line("{\"a\":1}"), StdoutLines.Out.Line("x")), lines.feed("1}\nx\ny"))
        assertEquals("y", lines.finish())
    }

    @Test
    fun `a last line without a newline is handed over on finish, once`() {
        val lines = StdoutLines(MAX)
        lines.feed("{\"type\":\"result\"}")
        assertEquals("{\"type\":\"result\"}", lines.finish())
        assertNull(lines.finish())
    }

    @Test
    fun `an oversized control request is reported with its id, and its tail is discarded`() {
        val lines = StdoutLines(MAX)
        val head = "{\"type\":\"control_request\",\"request_id\":\"req-7\",\"request\":{\"x\":\""
        val dropped = lines.feed(head + "z".repeat(MAX))
        assertEquals(listOf(StdoutLines.Out.Dropped(head.length + MAX, "req-7")), dropped)
        assertEquals(emptyList<StdoutLines.Out>(), lines.feed("z".repeat(MAX)))
        assertEquals(listOf(StdoutLines.Out.Line("{\"next\":1}")), lines.feed("zz\"}}\n{\"next\":1}\n"))
        assertNull(lines.finish())
    }

    @Test
    fun `an oversized line that is not a control request carries no id`() {
        val lines = StdoutLines(MAX)
        val dropped = lines.feed("{\"type\":\"assistant\",\"request_id\":\"no\"," + "z".repeat(MAX))
        assertNull((dropped.single() as StdoutLines.Out.Dropped).controlRequestId)
    }

    private companion object {
        const val MAX = 64
    }
}
