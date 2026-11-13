package dev.lain.claudejb.controller.session

import dev.lain.claudejb.controller.mcp.IdeMcpService
import dev.lain.claudejb.controller.process.ClaudeProcess
import dev.lain.claudejb.model.protocol.ClaudeEvent
import dev.lain.claudejb.model.session.history.SessionStore
import dev.lain.claudejb.model.session.launch.IdeServer
import dev.lain.claudejb.model.session.launch.SessionLauncher
import dev.lain.claudejb.model.session.transcript.Speaker
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.util.thisLogger
import java.io.File

class SessionProcess(
    private val s: ClaudeSession,
    private val edt: (() -> Unit) -> Unit,
    private val fireState: () -> Unit,
    private val fireAttention: (AttentionReason) -> Unit,
    private val onEvent: (ClaudeEvent) -> Unit,
) {

    private val log = thisLogger()

    private val slot = ProcessSlot<ClaudeProcess>()

    val generation: Int get() = slot.generation

    @Volatile private var resumedLaunch = false

    fun isRunning(): Boolean = slot.current?.isRunning() == true

    fun write(line: String): Boolean = slot.current?.writeLine(line) ?: false

    fun supersede(): Int = slot.supersede()

    fun terminate() {
        slot.take()?.terminate()
    }

    fun spawn(launchGen: Int, binary: File, workDir: File, env: Map<String, String>, resume: Boolean): Boolean {
        if (!slot.isCurrent(launchGen)) return false
        resumedLaunch = resume
        val opts = s.launch.copy(sessionId = s.sessionId, ideSockets = ideSockets())
        val proc = ClaudeProcess(
            binary = binary,
            workDir = workDir,
            args = SessionLauncher.buildArgs(opts, resume, SessionLauncher.mcpConfigJson(opts)),
            nodeOverride = ClaudeSettings.getInstance(s.project).nodePath,
            extraEnv = env,
            onEvent = slot.onlyWhileCurrent(launchGen, onEvent),
            onTerminated = { code -> onTerminated(launchGen, code) },
        )
        if (!slot.claim(launchGen, proc)) return false
        val started = runCatching { proc.start() }
        if (started.isFailure) {
            slot.release(proc)
            log.warn("Failed to start the claude process", started.exceptionOrNull())
            s.notifier.error("Failed to start Claude Code: ${started.exceptionOrNull()?.message ?: "unknown error"}")
            return false
        }
        if (slot.releaseIfStale(launchGen, proc)) {
            proc.terminate()
            return false
        }
        return true
    }

    private fun ideSockets(): Map<IdeServer, String> {
        if (!s.launch.ideIntegration) return emptyMap()
        val service = IdeMcpService.getInstance(s.project)
        val sockets = runCatching { service.sockets() }
            .onFailure { log.warn("The IDE MCP servers could not start; the session runs without them", it) }
            .getOrDefault(emptyMap())
        service.expectConnections(sockets.size)
        return sockets
    }

    private fun onTerminated(gen: Int, exitCode: Int) {
        if (!slot.isCurrent(gen)) return
        val staleResume = StaleResume.startsFresh(exitCode, resumedLaunch, s.catalog.initialized) {
            s.sessionId?.let(SessionStore::exists) == true
        }
        s.flushDeltas()
        s.controlClient.failAll("process gone")
        edt {
            s.turn.reset()
            s.lifecycle.ready = false
            s.catalog.initialized = false
            s.prompts.dropSuggestion()
            s.cardManager.clear()
            s.taskTracker.clear()
            s.hookNarrator.clear()
            when {
                staleResume -> restartFresh(exitCode)

                exitCode != 0 -> {
                    s.transcript.add(Speaker.ERROR, "Claude Code exited (code $exitCode).")
                    s.notifier.error("Claude Code exited unexpectedly (code $exitCode).")
                    fireAttention(AttentionReason.ERROR)
                    fireState()
                }

                else -> {
                    s.systemNotice("Session ended.")
                    fireState()
                }
            }
        }
    }

    private fun restartFresh(exitCode: Int) {
        log.info("resume of session ${s.sessionId} failed (exit $exitCode) — continuing as a new conversation")
        s.sessionId = null
        resumedLaunch = false
        s.systemNotice("That conversation is no longer available — started a new one.")
        fireState()
        s.start(resume = false)
    }
}
