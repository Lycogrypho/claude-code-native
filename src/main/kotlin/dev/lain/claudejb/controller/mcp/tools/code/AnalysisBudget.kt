package dev.lain.claudejb.controller.mcp.tools.code

internal class AnalysisBudget(
    files: Int,
    totalMillis: Long = TOTAL_MILLIS,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {

    private var filesLeft = files.coerceAtLeast(1)
    private val end = clockMillis() + totalMillis

    fun nextShareMillis(): Long {
        val share = (end - clockMillis()).coerceAtLeast(0) / filesLeft
        if (filesLeft > 1) filesLeft--
        return share
    }

    companion object {
        const val TOTAL_MILLIS = 100_000L
    }
}
