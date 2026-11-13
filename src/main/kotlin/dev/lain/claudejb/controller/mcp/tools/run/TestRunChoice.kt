package dev.lain.claudejb.controller.mcp.tools.run

internal object TestRunChoice {

    fun <T> pick(offered: List<T>, isGradle: (T) -> Boolean, tasks: (T) -> List<String>): T? =
        offered.firstOrNull { !isGradle(it) }
            ?: offered.firstOrNull { tasks(it).any(::isTestTask) }
            ?: offered.firstOrNull()

    private fun isTestTask(name: String): Boolean = name.substringAfterLast(':') == TEST_TASK

    private const val TEST_TASK = "test"
}
