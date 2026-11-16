package dev.lain.claudejb.controller.mcp.tools.code

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalysisBudgetTest {

    @Test
    fun `one file may wait for the whole budget`() {
        assertEquals(AnalysisBudget.TOTAL_MILLIS, AnalysisBudget(1, clockMillis = { 0 }).nextShareMillis())
    }

    @Test
    fun `a batch shares the budget, and a file analysed quickly leaves its time to the rest`() {
        var now = 0L
        val budget = AnalysisBudget(4, totalMillis = 100_000, clockMillis = { now })
        assertEquals(25_000, budget.nextShareMillis())
        now = 1_000
        assertEquals(33_000, budget.nextShareMillis())
        now = 34_000
        assertEquals(33_000, budget.nextShareMillis())
        now = 67_000
        assertEquals(33_000, budget.nextShareMillis())
    }

    @Test
    fun `the shares of a batch never add up to more than the budget, so the tool answers before its own timeout`() {
        var now = 0L
        val budget = AnalysisBudget(6, totalMillis = 100_000, clockMillis = { now })
        repeat(6) { now += budget.nextShareMillis() }
        assertTrue(now <= 100_000) { "waited $now ms" }
        assertEquals(0, budget.nextShareMillis())
    }
}
