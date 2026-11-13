package dev.lain.claudejb.controller.mcp.tools.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TestRunChoiceTest {

    private data class Offer(val name: String, val gradle: Boolean, val tasks: List<String> = emptyList())

    private fun pick(vararg offers: Offer): String? =
        TestRunChoice.pick(offers.toList(), { it.gradle }, { it.tasks })?.name

    @Test
    fun `a framework's own runner wins over Gradle`() {
        assertEquals("junit", pick(Offer("drift", true, listOf(":checkDrift")), Offer("junit", false)))
    }

    @Test
    fun `among Gradle runs the one that runs the test task wins`() {
        val drift = Offer("drift", true, listOf(":checkDrift", "--tests", "\"x.Y\""))
        val test = Offer("test", true, listOf(":test", "--tests", "\"x.Y\""))
        assertEquals("test", pick(drift, test))
        assertEquals("test", pick(drift, Offer("test", true, listOf(":module:test"))))
        assertEquals("test", pick(drift, Offer("test", true, listOf("test"))))
    }

    @Test
    fun `with no test task the first Gradle run is kept, and nothing offered gives nothing`() {
        assertEquals("drift", pick(Offer("drift", true, listOf(":checkDrift")), Offer("ui", true, listOf(":uiTest"))))
        assertNull(pick())
    }
}
