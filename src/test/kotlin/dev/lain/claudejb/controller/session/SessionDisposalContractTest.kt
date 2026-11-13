package dev.lain.claudejb.controller.session

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SessionDisposalContractTest {

    @Test
    fun `a closed session is disposed through the Disposer, so its children go with it`() {
        val text = SOURCE.readText()
        assertFalse(DIRECT.containsMatchIn(text)) {
            "ChatSessionManager calls dispose() on a session directly. The message-bus connection ToolEvents opens with " +
                "the session as parent is disposed only by Disposer.dispose, so the session and every live tool line " +
                "it buffers stay in memory until the project closes."
        }
        assertTrue(text.contains("Disposer.dispose(")) { "ChatSessionManager no longer disposes its sessions." }
    }

    private companion object {
        val DIRECT = Regex("""\b(session|it)\.dispose\(\)""")

        val SOURCE: File = run {
            val path = "src/main/kotlin/dev/lain/claudejb/controller/session/ChatSessionManager.kt"
            sequenceOf(File(path), File("../$path")).firstOrNull { it.isFile }
                ?: error("could not locate $path from ${File("").absolutePath}")
        }
    }
}
