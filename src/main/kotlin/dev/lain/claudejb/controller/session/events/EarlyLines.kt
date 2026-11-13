package dev.lain.claudejb.controller.session.events

import dev.lain.claudejb.model.session.transcript.LiveLines

internal class EarlyLines(private val maxTools: Int) {

    private val byTool = object : LinkedHashMap<String, LiveLines>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LiveLines>): Boolean = size > maxTools
    }

    val size: Int get() = byTool.size

    fun add(toolUseId: String, line: String) {
        byTool.getOrPut(toolUseId) { LiveLines() }.add(line)
    }

    fun take(toolUseId: String): LiveLines? = byTool.remove(toolUseId)
}
