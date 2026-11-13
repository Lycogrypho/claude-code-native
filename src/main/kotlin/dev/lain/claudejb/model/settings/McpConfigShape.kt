package dev.lain.claudejb.model.settings

import dev.lain.claudejb.model.protocol.ClaudeJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object McpConfigShape {

    fun unwrap(text: String): String {
        val root = rootOf(text) ?: return text
        val inner = root[WRAPPER]?.let(::serversIn)?.takeIf { root.size == 1 } ?: return text
        return PRETTY.encodeToString(JsonObject.serializer(), inner)
    }

    fun problem(text: String): String? {
        if (text.isBlank()) return null
        val root = rootOf(text) ?: return NOT_AN_OBJECT
        if (root[WRAPPER]?.let(::serversIn) != null) return WRAPPED
        val stray = root.entries.firstOrNull { it.value !is JsonObject } ?: return null
        return "Custom MCP servers: \"${stray.key}\" must map to a config object, such as $HTTP_EXAMPLE."
    }

    private fun rootOf(text: String): JsonObject? =
        runCatching { ClaudeJson.parseToJsonElement(text) }.getOrNull() as? JsonObject

    private fun serversIn(value: JsonElement): JsonObject? =
        (value as? JsonObject)?.takeIf { servers -> servers.values.all { it is JsonObject } }

    private val PRETTY = Json { prettyPrint = true }

    private const val WRAPPER = "mcpServers"

    const val STDIO_EXAMPLE = "{ \"command\": \"npx\", \"args\": [\"-y\", \"@modelcontextprotocol/server-filesystem\", \".\"] }"

    const val HTTP_EXAMPLE = "{ \"type\": \"http\", \"url\": \"https://example.com/mcp\" }"

    private const val NOT_AN_OBJECT = "Custom MCP servers must be a JSON object mapping each server name to its config."

    private const val WRAPPED =
        "Custom MCP servers: \"$WRAPPER\" is Claude's wrapper, not a server. Paste only the object inside it, " +
            "beside your other servers."
}
