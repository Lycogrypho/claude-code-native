package dev.lain.claudejb.controller.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SessionSendContractTest {

    @Test
    fun `a prompt sent while the session cannot start is queued, not dropped`() {
        val body = bodyOf("fun send(text: String, attachments")
        assertFalse(body.any { DROP.containsMatchIn(it) }) {
            "ClaudeSession.send returns when start() fails. The tray is already emptied by then, so the prompt and " +
                "its attachments are lost silently; queue it, and it goes once the session starts."
        }
        assertTrue(body.any { ENQUEUE in it }) { "ClaudeSession.send no longer queues the prompt." }
    }

    @Test
    fun `a side question asked with no running session is never sent as a normal prompt`() {
        val body = bodyOf("fun sendSideQuestion(")
        assertFalse(body.any { ENQUEUE in it }) {
            "ClaudeSession.sendSideQuestion queues the question as a normal prompt, so a /btw aside becomes a turn " +
                "of the conversation."
        }
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
        const val ENQUEUE = "prompts.enqueue("
        val DROP = Regex("""start\(\)\)\s*return""")

        val SOURCE: File = run {
            val path = "src/main/kotlin/dev/lain/claudejb/controller/session/ClaudeSession.kt"
            sequenceOf(File(path), File("../$path")).firstOrNull { it.isFile }
                ?: error("could not locate $path from ${File("").absolutePath}")
        }
    }
}
