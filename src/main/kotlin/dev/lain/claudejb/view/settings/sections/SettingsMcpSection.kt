package dev.lain.claudejb.view.settings.sections

import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.McpConfigShape
import dev.lain.claudejb.view.settings.SettingsSection

internal class SettingsMcpSection : SettingsSection {

    private val customMcpArea = JBTextArea(CUSTOM_MCP_ROWS, 0).apply {
        emptyText.text = "JSON object of name → server config; add as many as you like (stdio / http / sse)"
    }
    private val strictMcpCheck = JBCheckBox("Strict MCP config (only use servers from --mcp-config)")

    override fun addTo(panel: Panel) {
        panel.collapsibleGroup("Custom MCP Servers") {
            row("Custom servers:") { scrollCell(customMcpArea).align(AlignX.FILL) }
                .rowComment(CUSTOM_MCP_NOTE, MAX_LINE_LENGTH_WORD_WRAP)
            row { cell(strictMcpCheck) }
        }
    }

    override fun reset(s: ClaudeSettings.State) {
        customMcpArea.text = s.customMcpServers
        strictMcpCheck.isSelected = s.strictMcpConfig
    }

    private fun servers(): String = McpConfigShape.unwrap(customMcpArea.text.trim())

    override fun validate() {
        McpConfigShape.problem(servers())?.let { throw ConfigurationException(it) }
    }

    override fun apply(s: ClaudeSettings.State) {
        s.customMcpServers = servers()
        s.strictMcpConfig = strictMcpCheck.isSelected
        customMcpArea.text = s.customMcpServers
    }

    override fun changedFields(s: ClaudeSettings.State): List<Boolean> = listOf(
        servers() != s.customMcpServers,
        strictMcpCheck.isSelected != s.strictMcpConfig,
    )

    private companion object {
        const val CUSTOM_MCP_ROWS = 7

        const val CUSTOM_MCP_NOTE =
            "A JSON object of name → server config. A local server: <code>{ \"files\": " + McpConfigShape.STDIO_EXAMPLE +
                " }</code>. A remote one: <code>{ \"docs\": " + McpConfigShape.HTTP_EXAMPLE + " }</code>. " +
                "<code>type</code> is <code>stdio</code> (the default, with <code>command</code> and <code>args</code>), " +
                "<code>http</code> or <code>sse</code> (with <code>url</code>). Claude's own " +
                "<code>{ \"mcpServers\": { … } }</code> can be pasted as it is: the wrapper is removed on Apply. " +
                "⚠ Third-party servers run with your privileges and can read what you share with them."
    }
}
