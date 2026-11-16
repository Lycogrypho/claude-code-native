package dev.lain.claudejb.controller.process

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

class LaunchFilesTest {

    @TempDir
    lateinit var base: Path

    private val args = listOf("--print", "--append-system-prompt", PROMPT, "--mcp-config", CONFIG, "--resume", "abc")

    private fun valueAfter(flag: String, args: List<String>): String = args[args.indexOf(flag) + 1]

    @Test
    fun `the MCP config never stays on the command line`() {
        val files = LaunchFiles.externalise(args, promptToFile = false, base = base)
        assertFalse(files.args.any { "Bearer" in it })
        val config = Path.of(valueAfter("--mcp-config", files.args))
        assertEquals(CONFIG, Files.readString(config))
        assertEquals(PROMPT, valueAfter("--append-system-prompt", files.args))
        assertEquals(listOf("--resume", "abc"), files.args.takeLast(2))
        assertEquals(args.size, files.args.size)
    }

    @Test
    fun `the system prompt moves to a file only when the launch passes through a shell`() {
        val files = LaunchFiles.externalise(args, promptToFile = true, base = base)
        assertFalse("--append-system-prompt" in files.args)
        assertEquals(PROMPT, Files.readString(Path.of(valueAfter("--append-system-prompt-file", files.args))))
        assertFalse(files.args.any { '\n' in it })
    }

    @Test
    fun `a launch with nothing to move writes nothing`() {
        val plain = listOf("--print", "--append-system-prompt", PROMPT)
        val files = LaunchFiles.externalise(plain, promptToFile = false, base = base)
        assertEquals(plain, files.args)
        assertEquals(0L, Files.list(base).use { it.count() })
    }

    @Test
    fun `discarding removes the files that were written and their directory`() {
        val files = LaunchFiles.externalise(args, promptToFile = true, base = base)
        assertEquals(1L, Files.list(base).use { it.count() })
        files.discard()
        assertEquals(0L, Files.list(base).use { it.count() })
        files.discard()
    }

    @Test
    fun `discarding leaves alone anything it did not write, and the directory that holds it`() {
        val files = LaunchFiles.externalise(args, promptToFile = false, base = base)
        val dir = Path.of(valueAfter("--mcp-config", files.args)).parent
        val foreign = Files.writeString(dir.resolve("not-ours.txt"), "keep")
        files.discard()
        assertTrue(Files.exists(foreign))
        assertFalse(Files.exists(dir.resolve("mcp.json")))
    }

    @Test
    fun `the directory and its files are private to their owner where permissions are POSIX`() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) { "no POSIX permissions here" }
        val files = LaunchFiles.externalise(args, promptToFile = true, base = base)
        val config = Path.of(valueAfter("--mcp-config", files.args))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(config)))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(config.parent)))
    }

    @Test
    fun `the directory and its files grant access to their owner alone where permissions are an ACL`() {
        val files = LaunchFiles.externalise(args, promptToFile = true, base = base)
        val config = Path.of(valueAfter("--mcp-config", files.args))
        val posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
        val view = Files.getFileAttributeView(config.parent, AclFileAttributeView::class.java)
        assumeTrue(!posix && view != null) { "no ACL-only filesystem here" }
        listOf(config.parent, config).forEach { path ->
            val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
            assertTrue(acl.acl.isNotEmpty()) { "$path has an empty ACL" }
            acl.acl.forEach { entry ->
                assertEquals(AclEntryType.ALLOW, entry.type())
                assertEquals(acl.owner, entry.principal()) { "$path grants access to ${entry.principal()}" }
            }
        }
    }

    private companion object {
        const val PROMPT = "You are running inside an IDE.\nSecond line."
        const val CONFIG = """{"mcpServers":{"docs":{"type":"http","url":"x","headers":{"Authorization":"Bearer secret"}}}}"""
    }
}
