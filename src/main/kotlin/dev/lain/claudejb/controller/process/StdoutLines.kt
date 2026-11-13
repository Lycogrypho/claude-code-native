package dev.lain.claudejb.controller.process

internal class StdoutLines(private val maxLineLength: Int) {

    sealed interface Out {
        data class Line(val text: String) : Out

        data class Dropped(val length: Int, val controlRequestId: String?) : Out
    }

    private val buffer = StringBuilder()

    private var discarding = false

    fun feed(text: String): List<Out> = synchronized(buffer) {
        val out = ArrayList<Out>()
        val rest = if (discarding) {
            val newline = text.indexOf('\n')
            if (newline < 0) return out
            discarding = false
            text.substring(newline + 1)
        } else {
            text
        }
        buffer.append(rest)
        var start = 0
        var newline = buffer.indexOf("\n", start)
        while (newline >= 0) {
            out += Out.Line(buffer.substring(start, newline))
            start = newline + 1
            newline = buffer.indexOf("\n", start)
        }
        if (start > 0) buffer.delete(0, start)
        if (buffer.length > maxLineLength) {
            out += Out.Dropped(buffer.length, controlRequestId(buffer))
            buffer.setLength(0)
            discarding = true
        }
        out
    }

    fun finish(): String? = synchronized(buffer) {
        val last = buffer.toString().takeIf { it.isNotBlank() }
        buffer.setLength(0)
        last
    }

    private companion object {
        const val HEAD = 4096

        val CONTROL_REQUEST = Regex(""""type"\s*:\s*"control_request"""")
        val REQUEST_ID = Regex(""""request_id"\s*:\s*"([^"\\]+)"""")

        fun controlRequestId(line: CharSequence): String? {
            val head = line.subSequence(0, minOf(line.length, HEAD))
            if (!CONTROL_REQUEST.containsMatchIn(head)) return null
            return REQUEST_ID.find(head)?.groupValues?.get(1)
        }
    }
}
