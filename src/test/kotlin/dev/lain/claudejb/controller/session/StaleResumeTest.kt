package dev.lain.claudejb.controller.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StaleResumeTest {

    private fun startsFresh(
        exitCode: Int = 1,
        resumed: Boolean = true,
        initialized: Boolean = false,
        transcriptExists: Boolean = false,
    ): Boolean = StaleResume.startsFresh(exitCode, resumed, initialized) { transcriptExists }

    @Test
    fun `a resume whose transcript is gone starts a new conversation`() {
        assertTrue(startsFresh())
    }

    @Test
    fun `an early failure of a resume whose transcript still exists keeps the conversation`() {
        assertFalse(startsFresh(transcriptExists = true))
    }

    @Test
    fun `a clean exit, a fresh launch or an initialized session never starts over`() {
        assertFalse(startsFresh(exitCode = 0))
        assertFalse(startsFresh(resumed = false))
        assertFalse(startsFresh(initialized = true))
    }

    @Test
    fun `the transcript is looked up only when the launch failed early on a resume`() {
        var looked = 0
        StaleResume.startsFresh(0, resumed = true, initialized = false) { looked++; false }
        StaleResume.startsFresh(1, resumed = false, initialized = false) { looked++; false }
        StaleResume.startsFresh(1, resumed = true, initialized = true) { looked++; false }
        assertTrue(looked == 0)
    }
}
