package dev.lain.claudejb.headless

import com.intellij.openapi.options.ConfigurationException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.model.session.launch.McpConfigBuilder
import dev.lain.claudejb.model.settings.ClaudeSettings
import dev.lain.claudejb.model.settings.env.hasRiskyExecConfig
import dev.lain.claudejb.view.settings.sections.SettingsMcpSection

class SettingsMcpSectionHeadlessTest : BasePlatformTestCase() {

    private fun sectionShowing(text: String): SettingsMcpSection =
        SettingsMcpSection().apply { reset(ClaudeSettings.State().apply { customMcpServers = text }) }

    fun `test a pasted Claude config is stored without its wrapper, and is not a change once stored`() {
        val section = sectionShowing(WRAPPED_STDIO)
        val state = ClaudeSettings.State()
        section.validate()
        section.apply(state)
        assertEquals(setOf("files"), McpConfigBuilder.customMcpServersObject(state.customMcpServers)!!.keys)
        assertEquals(listOf(false, false), section.changedFields(state))
    }

    fun `test the stored form is the one the execution trust prompt reads`() {
        val section = sectionShowing(WRAPPED_STDIO)
        val settings = ClaudeSettings.getInstance(project)
        val before = settings.state.customMcpServers
        try {
            section.apply(settings.state)
            assertTrue("a pasted stdio command must still ask for execution trust", settings.hasRiskyExecConfig())
        } finally {
            settings.state.customMcpServers = before
        }
    }

    fun `test a wrapper beside other servers cannot be saved`() {
        val section = sectionShowing("""{"mcpServers":{"a":{"command":"x"}},"b":{"command":"y"}}""")
        val refused = runCatching { section.validate() }.exceptionOrNull()
        assertTrue(refused is ConfigurationException)
        assertTrue(refused!!.message!!.contains("mcpServers"))
    }

    private companion object {
        const val WRAPPED_STDIO = """{ "mcpServers": { "files": { "command": "npx", "args": ["-y", "server"] } } }"""
    }
}
