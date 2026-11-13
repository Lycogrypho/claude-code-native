package dev.lain.claudejb.controller.mcp.tools.code

import java.nio.file.FileSystems
import java.nio.file.Path

internal class FileGlob(pattern: String) {

    private val byPath = '/' in pattern
    private val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")

    fun matches(relativePath: String): Boolean =
        matcher.matches(Path.of(if (byPath) relativePath else relativePath.substringAfterLast('/')))
}

internal object Capped {

    fun <T> of(found: List<T>, max: Int): Pair<List<T>, Boolean> = found.take(max) to (found.size > max)
}
