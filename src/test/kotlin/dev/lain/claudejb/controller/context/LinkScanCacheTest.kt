package dev.lain.claudejb.controller.context

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LinkScanCacheTest {

    @TempDir
    lateinit var tmp: Path

    private var now = 0L

    @AfterEach
    fun restoreClock() {
        LinkResolver.scanClockMillis = System::currentTimeMillis
    }

    private fun touch(rel: String) = File(tmp.toFile(), rel).apply {
        parentFile.mkdirs()
        writeText("x")
    }

    private fun found(name: String): List<String> =
        LinkResolver.scanForNames(tmp.toFile().path, listOf(name to null)).map { it.path }

    @Test
    fun `the tree is walked once for requests that follow each other, and again once the scan has aged`() {
        LinkResolver.scanClockMillis = { now }
        touch("build/a/first.zip")
        assertEquals(listOf("build/a/first.zip"), found("first.zip"))
        touch("build/a/later.zip")
        assertTrue(found("later.zip").isEmpty()) { "the second request walked the disk again" }
        assertEquals(listOf("build/a/first.zip"), found("first.zip"))
        now = LinkResolver.SCAN_TTL_MILLIS + 1
        assertEquals(listOf("build/a/later.zip"), found("later.zip"))
    }

    @Test
    fun `a scan of one root is never served for another`() {
        LinkResolver.scanClockMillis = { now }
        touch("one/build/x.zip")
        touch("two/build/y.zip")
        val one = File(tmp.toFile(), "one").path
        val two = File(tmp.toFile(), "two").path
        assertEquals(1, LinkResolver.scanForNames(one, listOf("x.zip" to null)).size)
        assertEquals(1, LinkResolver.scanForNames(two, listOf("y.zip" to null)).size)
        assertTrue(LinkResolver.scanForNames(two, listOf("x.zip" to null)).isEmpty())
    }
}
