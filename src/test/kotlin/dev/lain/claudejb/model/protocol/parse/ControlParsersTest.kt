package dev.lain.claudejb.model.protocol.parse

import dev.lain.claudejb.model.protocol.ClaudeEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ControlParsersTest {

    @Test
    fun `a control request without a request object is still answered`() {
        val events = ProtocolParser.parse("""{"type":"control_request","request_id":"r1"}""")
        assertEquals(listOf(ClaudeEvent.UnsupportedControlRequest("r1", null)), events)
    }

    @Test
    fun `a control request whose request is not an object is still answered`() {
        val events = ProtocolParser.parse("""{"type":"control_request","request_id":"r2","request":"x"}""")
        assertEquals(listOf(ClaudeEvent.UnsupportedControlRequest("r2", null)), events)
    }
}
