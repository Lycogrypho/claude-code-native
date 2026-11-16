package dev.lain.claudejb.controller.mcp.tools.code

internal class NodeBudget(private val limit: Int) {

    private var used = 0

    var exhausted = false
        private set

    fun fresh(): NodeBudget {
        used = 0
        exhausted = false
        return this
    }

    fun take(): Boolean {
        if (used >= limit) {
            exhausted = true
            return false
        }
        used++
        return true
    }
}
