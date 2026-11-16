package dev.lain.claudejb.controller.session.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CoalescedFlushTest {

    @Test
    fun `a burst of requests is flushed once, shortly after the first`() {
        val flushes = AtomicInteger()
        val flushed = CountDownLatch(1)
        val flush = CoalescedFlush(DELAY_MS) {
            flushes.incrementAndGet()
            flushed.countDown()
        }
        repeat(BURST) { flush.request() }
        assertTrue(flushed.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "nothing was flushed without another event arriving" }
        Thread.sleep(SETTLE_MS)
        assertEquals(1, flushes.get())
    }

    @Test
    fun `text that arrives after a flush is flushed again`() {
        val flushed = CountDownLatch(2)
        val flush = CoalescedFlush(DELAY_MS) { flushed.countDown() }
        flush.request()
        Thread.sleep(SETTLE_MS)
        flush.request()
        assertTrue(flushed.await(WAIT_MS, TimeUnit.MILLISECONDS))
    }

    private companion object {
        const val DELAY_MS = 20
        const val BURST = 50
        const val WAIT_MS = 5_000L
        const val SETTLE_MS = 200L
    }
}
