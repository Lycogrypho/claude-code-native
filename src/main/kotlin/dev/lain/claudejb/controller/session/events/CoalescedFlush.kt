package dev.lain.claudejb.controller.session.events

import javax.swing.Timer

internal class CoalescedFlush(delayMillis: Int, flush: () -> Unit) {

    private val timer = Timer(delayMillis) { flush() }.apply { isRepeats = false }

    fun request() {
        if (!timer.isRunning) timer.start()
    }
}
