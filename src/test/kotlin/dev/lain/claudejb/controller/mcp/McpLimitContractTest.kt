package dev.lain.claudejb.controller.mcp

import dev.lain.claudejb.controller.mcp.tools.code.NodeBudget
import dev.lain.claudejb.model.mcp.ToolArgs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class McpLimitContractTest {

    private fun args(json: String) = ToolArgs(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `a limit is at least one and never above its ceiling`() {
        assertEquals(50, args("{}").limit("max", 50))
        assertEquals(7, args("""{"max":"7"}""").limit("max", 50))
        assertEquals(ToolArgs.MAX_LIMIT, args("""{"max":"1000000"}""").limit("max", 50))
        assertEquals(20, args("""{"max":"1000000"}""").limit("max", 50, ceiling = 20))
        assertEquals(1, args("""{"max":"0"}""").limit("max", 50))
        assertEquals(1, args("""{"max":"-3"}""").limit("max", 50))
    }

    @Test
    fun `no tool reads max without the ceiling`() {
        val offenders = sources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (UNBOUNDED.containsMatchIn(line)) "${file.name}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(emptyList<String>(), offenders) {
            "DIRECTIVES.md: anything that can enumerate carries a hard limit. Read max with ToolArgs.limit, which " +
                "clamps it; ToolArgs.int lets max=1000000 run a search unbounded inside a read action."
        }
    }

    @Test
    fun `a node budget stops at its limit, says so, and starts over when the walk is retried`() {
        val budget = NodeBudget(2)
        assertTrue(budget.take() && budget.take())
        assertFalse(budget.exhausted)
        assertFalse(budget.take())
        assertTrue(budget.exhausted)
        assertTrue(budget.fresh().take())
        assertFalse(budget.exhausted)
    }

    private fun sources(): List<File> {
        val root = sequenceOf(File(MCP), File("../$MCP")).firstOrNull { it.isDirectory }
            ?: error("could not locate $MCP from ${File("").absolutePath}")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private companion object {
        const val MCP = "src/main/kotlin/dev/lain/claudejb/controller/mcp"
        val UNBOUNDED = Regex("""\.int\(\s*"max"""")
    }
}
