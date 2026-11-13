package dev.lain.claudejb.controller.session.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EarlyLinesTest {

    @Test
    fun `lines that arrive before their tool is known are handed over once`() {
        val early = EarlyLines(maxTools = 4)
        early.add("t1", "a")
        early.add("t1", "b")
        assertEquals("a\nb", early.take("t1")?.render())
        assertNull(early.take("t1"))
    }

    @Test
    fun `another chat's tool ids are bounded, the oldest evicted first`() {
        val early = EarlyLines(maxTools = 4)
        (1..10).forEach { early.add("foreign-$it", "x") }
        assertEquals(4, early.size)
        assertNull(early.take("foreign-6"))
        assertNotNull(early.take("foreign-7"))
        assertNotNull(early.take("foreign-10"))
    }
}
