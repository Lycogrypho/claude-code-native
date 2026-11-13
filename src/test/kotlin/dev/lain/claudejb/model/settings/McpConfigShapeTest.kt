package dev.lain.claudejb.model.settings

import dev.lain.claudejb.model.session.launch.McpConfigBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpConfigShapeTest {

    private fun serverNames(text: String): Set<String> = McpConfigBuilder.customMcpServersObject(text)!!.keys

    @Test
    fun `Claude's own config loses its mcpServers wrapper`() {
        val unwrapped = McpConfigShape.unwrap(WRAPPED)
        assertEquals(setOf("leonardo", "files"), serverNames(unwrapped))
        assertNull(McpConfigShape.problem(unwrapped))
    }

    @Test
    fun `a config that is already the inner object is left exactly as typed`() {
        val typed = "{\n  \"docs\":   { \"type\": \"http\", \"url\": \"https://example.com/mcp\" }\n}"
        assertEquals(typed, McpConfigShape.unwrap(typed))
        assertNull(McpConfigShape.problem(typed))
        assertEquals("{not json", McpConfigShape.unwrap("{not json"))
        assertEquals("", McpConfigShape.unwrap(""))
    }

    @Test
    fun `a server that happens to be named mcpServers is a server`() {
        val named = """{"mcpServers":{"type":"sse","url":"http://localhost:1/sse"}}"""
        assertEquals(named, McpConfigShape.unwrap(named))
        assertNull(McpConfigShape.problem(named))
    }

    @Test
    fun `a wrapper beside other servers is refused, naming the fix`() {
        val mixed = """{"mcpServers":{"a":{"command":"x"}},"b":{"command":"y"}}"""
        assertEquals(mixed, McpConfigShape.unwrap(mixed))
        val problem = McpConfigShape.problem(mixed)!!
        assertTrue(problem.contains("mcpServers") && problem.contains("inside")) { problem }
    }

    @Test
    fun `anything but an object of config objects is refused, naming the entry`() {
        assertNull(McpConfigShape.problem(""))
        assertNull(McpConfigShape.problem("{}"))
        assertTrue(McpConfigShape.problem("[]")!!.contains("JSON object"))
        assertTrue(McpConfigShape.problem("{not json")!!.contains("JSON object"))
        val stray = McpConfigShape.problem("""{"docs":"https://example.com/mcp"}""")!!
        assertTrue(stray.contains("\"docs\"")) { stray }
    }

    private companion object {
        const val WRAPPED =
            """{ "mcpServers": { "leonardo": { "type": "http", "url": "https://example.com/mcp" }, "files": { "command": "npx" } } }"""
    }
}
