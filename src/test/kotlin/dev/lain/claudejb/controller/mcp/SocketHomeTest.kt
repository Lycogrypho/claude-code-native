package dev.lain.claudejb.controller.mcp

import dev.lain.claudejb.mcp.StdioBridge
import dev.lain.claudejb.model.session.launch.IdeServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.random.Random

class SocketHomeTest {

    private lateinit var tmp: Path

    @BeforeEach
    fun createShortBase() {
        val name = "sh" + Random.nextInt(SHORT_NAME_FROM, SHORT_NAME_UNTIL)
        tmp = Files.createDirectory(Path.of(System.getProperty("java.io.tmpdir"), name))
    }

    @AfterEach
    fun removeBase() {
        tmp.toFile().deleteRecursively()
    }

    private fun assumePosix() = assumeTrue(POSIX) {
        "this filesystem has no POSIX permissions to assert; SocketHome sets none here and relies on the " +
            "temporary directory's own access control"
    }

    @Test
    fun `the home is a directory under the parent whose sockets fit the platform limit`() {
        val home = SocketHome.create(listOf(tmp))
        assertTrue(home.dir.startsWith(tmp.resolve(SocketHome.PARENT)))
        IdeServer.entries.forEach { assertTrue(home.socket(it).toString().length <= SocketHome.MAX_SOCKET_PATH) }
        home.remove()
        assertFalse(Files.exists(home.dir))
    }

    @Test
    fun `the home and its parent are private to their owner`() {
        assumePosix()
        val home = SocketHome.create(listOf(tmp))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(home.dir)))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(tmp.resolve(SocketHome.PARENT))))
        home.remove()
    }

    @Test
    fun `the token file is rewritten on rotation`() {
        val home = SocketHome.create(listOf(tmp))
        home.writeToken("first")
        home.writeToken("second")
        assertEquals("second", Files.readString(home.dir.resolve(StdioBridge.TOKEN_FILE)))
        home.remove()
    }

    @Test
    fun `the token file is readable by its owner only and rewritten on rotation`() {
        assumePosix()
        val home = SocketHome.create(listOf(tmp))
        home.writeToken("first")
        home.writeToken("second")
        val file = home.dir.resolve(StdioBridge.TOKEN_FILE)
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        assertEquals("second", Files.readString(file))
        home.remove()
    }

    @Test
    fun `a parent left open by an older build is closed to other users`() {
        assumePosix()
        val parent = Files.createDirectory(tmp.resolve(SocketHome.PARENT))
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwxr-xr-x"))
        val home = SocketHome.create(listOf(tmp))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(parent)))
        home.remove()
    }

    @Test
    fun `a base too long for a socket path is skipped for the next one`() {
        val deep = tmp.resolve("x".repeat(SocketHome.MAX_SOCKET_PATH))
        val home = SocketHome.create(listOf(deep, tmp))
        assertTrue(home.dir.startsWith(tmp.resolve(SocketHome.PARENT)))
        assertFalse(Files.exists(deep))
        home.remove()
    }

    @Test
    fun `no base short enough is an error, not a silent long path`() {
        val deep = tmp.resolve("x".repeat(SocketHome.MAX_SOCKET_PATH))
        assertThrows(IllegalStateException::class.java) { SocketHome.create(listOf(deep)) }
    }

    private companion object {
        const val SHORT_NAME_FROM = 100_000
        const val SHORT_NAME_UNTIL = 1_000_000
        val POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
    }
}
