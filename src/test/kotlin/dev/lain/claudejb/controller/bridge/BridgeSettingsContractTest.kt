package dev.lain.claudejb.controller.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BridgeSettingsContractTest {

    @Test
    fun `a settings toggle applies the menu once, inside update`() {
        val body = bodyOf("fun write(")
        val applies = body.filter { APPLY in it }
        assertEquals(1, applies.size) {
            "BridgeSettings.write names $APPLY ${applies.size} times. update already applies its block to the live " +
                "state and to the store, so a second call runs guard-mode side effects such as " +
                "SecuritySuspensions.guardOff once more on the live state."
        }
        assertTrue(applies.single().contains(UPDATE)) { "$APPLY must run inside `$UPDATE`." }
    }

    private fun bodyOf(signature: String): List<String> {
        val lines = SOURCE.readLines()
        val from = lines.indexOfFirst { it.trimStart().removePrefix("private ").startsWith(signature) }
        assertTrue(from >= 0) { "no `$signature` in ${SOURCE.path}" }
        val length = lines.drop(from).indexOfFirst { it == "    }" }
        assertTrue(length > 0) { "`$signature` in ${SOURCE.path} has no closing brace at member level" }
        return lines.subList(from, from + length + 1)
    }

    private companion object {
        const val APPLY = "JcefSettingsMenu.apply("
        const val UPDATE = "settings.update"

        val SOURCE: File = run {
            val path = "src/main/kotlin/dev/lain/claudejb/controller/bridge/BridgeSettings.kt"
            sequenceOf(File(path), File("../$path")).firstOrNull { it.isFile }
                ?: error("could not locate $path from ${File("").absolutePath}")
        }
    }
}
