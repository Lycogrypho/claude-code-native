package dev.lain.claudejb.controller.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProcessSlotTest {

    @Test
    fun `a launch overtaken by a restart cannot claim the slot`() {
        val slot = ProcessSlot<String>()
        val old = slot.supersede()
        val new = slot.supersede()
        assertFalse(slot.claim(old, "old"))
        assertTrue(slot.claim(new, "new"))
        assertSame("new", slot.current)
    }

    @Test
    fun `releasing a stale launch never drops the current process`() {
        val slot = ProcessSlot<String>()
        val old = slot.supersede()
        assertTrue(slot.claim(old, "old"))
        val new = slot.supersede()
        assertTrue(slot.claim(new, "new"))
        assertTrue(slot.releaseIfStale(old, "old"))
        assertSame("new", slot.current)
    }

    @Test
    fun `a current launch is not released as stale`() {
        val slot = ProcessSlot<String>()
        val gen = slot.supersede()
        slot.claim(gen, "p")
        assertFalse(slot.releaseIfStale(gen, "p"))
        assertSame("p", slot.current)
    }

    @Test
    fun `a stale launch that still holds the slot is released`() {
        val slot = ProcessSlot<String>()
        val gen = slot.supersede()
        slot.claim(gen, "p")
        slot.supersede()
        assertTrue(slot.releaseIfStale(gen, "p"))
        assertNull(slot.current)
    }

    @Test
    fun `releasing a failed start leaves a newer process in place`() {
        val slot = ProcessSlot<String>()
        slot.claim(slot.supersede(), "failed")
        slot.claim(slot.supersede(), "new")
        slot.release("failed")
        assertSame("new", slot.current)
        slot.release("new")
        assertNull(slot.current)
    }

    @Test
    fun `take empties the slot and hands back what it held`() {
        val slot = ProcessSlot<String>()
        slot.claim(slot.supersede(), "p")
        assertEquals("p", slot.take())
        assertNull(slot.current)
        assertNull(slot.take())
    }

    @Test
    fun `events of a superseded launch are dropped`() {
        val slot = ProcessSlot<String>()
        val seen = mutableListOf<String>()
        val gen = slot.supersede()
        val deliver = slot.onlyWhileCurrent(gen) { e: String -> seen += e }
        deliver("before")
        slot.supersede()
        deliver("after")
        assertEquals(listOf("before"), seen)
        assertFalse(slot.isCurrent(gen))
    }
}
