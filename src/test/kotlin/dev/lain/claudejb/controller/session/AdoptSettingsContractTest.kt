package dev.lain.claudejb.controller.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AdoptSettingsContractTest {

    @Test
    fun `adopt assigns every launch field before it restarts, and restarts once`() {
        val body = bodyOf("fun adopt(")
        CHANGERS.forEach { changer ->
            assertFalse(body.any { changer in it }) {
                "SessionLiveSettings.adopt calls $changer, which can restart or write to the process before the new " +
                    "launch options are assigned, so the pooled spawn reads the previous ones."
            }
        }
        val assign = body.indexOfFirst { ASSIGN in it }
        val restarts = body.withIndex().filter { RESTART in it.value }
        assertEquals(1, restarts.size) { "adopt must restart at most once, in one place." }
        assertTrue(assign in 0 until restarts.single().index) { "adopt restarts before `$ASSIGN`." }
    }

    private fun bodyOf(signature: String): List<String> {
        val lines = SOURCE.readLines()
        val from = lines.indexOfFirst { it.trimStart().startsWith(signature) }
        assertTrue(from >= 0) { "no `$signature` in ${SOURCE.path}" }
        val length = lines.drop(from).indexOfFirst { it == "    }" }
        assertTrue(length > 0) { "`$signature` in ${SOURCE.path} has no closing brace at member level" }
        return lines.subList(from, from + length + 1)
    }

    private companion object {
        val CHANGERS = listOf("changeThinkingTokens(", "changeModel(", "changePermissionMode(", "changeEffort(")
        const val ASSIGN = "session.launch = "
        const val RESTART = "session.restart("

        val SOURCE: File = run {
            val path = "src/main/kotlin/dev/lain/claudejb/controller/session/SessionLiveSettings.kt"
            sequenceOf(File(path), File("../$path")).firstOrNull { it.isFile }
                ?: error("could not locate $path from ${File("").absolutePath}")
        }
    }
}
