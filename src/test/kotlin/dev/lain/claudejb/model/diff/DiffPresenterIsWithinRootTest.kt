package dev.lain.claudejb.model.diff

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class DiffPresenterIsWithinRootTest {

    private companion object {
        val NUL: Char = 0.toChar()
    }

    @Test
    fun `null path returns false (fail-closed)`(@TempDir root: Path) {
        assertFalse(DiffPresenter.isWithinRoot(null, root.toFile().canonicalPath))
    }

    @Test
    fun `null projectRoot returns false (fail-closed)`(@TempDir root: Path) {
        val file = File(root.toFile(), "a.kt").apply { writeText("") }
        assertFalse(DiffPresenter.isWithinRoot(file.canonicalPath, null))
    }

    @Test
    fun `both null returns false`() {
        assertFalse(DiffPresenter.isWithinRoot(null, null))
    }

    @Test
    fun `file directly under the root is contained`(@TempDir root: Path) {
        val file = File(root.toFile(), "App.kt").apply { writeText("x") }
        assertTrue(DiffPresenter.isWithinRoot(file.canonicalPath, root.toFile().canonicalPath))
    }

    @Test
    fun `nested file under the root is contained`(@TempDir root: Path) {
        val dir = File(root.toFile(), "src/main/kotlin").apply { mkdirs() }
        val file = File(dir, "App.kt").apply { writeText("x") }
        assertTrue(DiffPresenter.isWithinRoot(file.canonicalPath, root.toFile().canonicalPath))
    }

    @Test
    fun `root itself is considered contained`(@TempDir root: Path) {
        val rootPath = root.toFile().canonicalPath
        assertTrue(DiffPresenter.isWithinRoot(rootPath, rootPath))
    }

    @Test
    fun `dotdot traversal that escapes the root is rejected`(@TempDir root: Path) {
        val escaping = File(root.toFile(), "../../etc/passwd").path
        assertFalse(DiffPresenter.isWithinRoot(escaping, root.toFile().canonicalPath))
    }

    @Test
    fun `absolute path outside the root is rejected`(@TempDir root: Path) {
        assertFalse(DiffPresenter.isWithinRoot("/etc/hosts", root.toFile().canonicalPath))
    }

    @Test
    fun `sibling whose name shares the root prefix is rejected`(@TempDir parent: Path) {
        val root = File(parent.toFile(), "proj").apply { mkdirs() }
        val sibling = File(parent.toFile(), "proj-evil").apply { mkdirs() }
        val target = File(sibling, "secret.txt").apply { writeText("x") }
        assertFalse(DiffPresenter.isWithinRoot(target.canonicalPath, root.canonicalPath))
    }

    @Test
    fun `path with spaces and unicode under the root is contained`(@TempDir root: Path) {
        val dir = File(root.toFile(), "señor dir/ünïcode").apply { mkdirs() }
        val file = File(dir, "fïle ñ.txt").apply { writeText("x") }
        assertTrue(DiffPresenter.isWithinRoot(file.canonicalPath, root.toFile().canonicalPath))
    }

    @Test
    fun `symlink pointing outside the root is rejected after canonicalization`(@TempDir tmp: Path) {
        val root = File(tmp.toFile(), "root").apply { mkdirs() }
        val outside = File(tmp.toFile(), "outside").apply { mkdirs() }
        val target = File(outside, "secret.txt").apply { writeText("nope") }
        val link = File(root, "link.txt")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }
        assumeTrue(created.isSuccess, "symlinks unsupported in this environment")
        assertFalse(
            DiffPresenter.isWithinRoot(link.canonicalPath, root.canonicalPath),
            "symlink escaping the root must not be considered contained",
        )
    }

    @Test
    fun `symlink pointing inside the root is accepted`(@TempDir root: Path) {
        val real = File(root.toFile(), "real.txt").apply { writeText("ok") }
        val link = File(root.toFile(), "link.txt")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), real.toPath()) }
        assumeTrue(created.isSuccess, "symlinks unsupported in this environment")
        assertTrue(DiffPresenter.isWithinRoot(link.canonicalPath, root.toFile().canonicalPath))
    }

    @Test
    fun `a dangling symlink inside the root is rejected, wherever it points`(@TempDir tmp: Path) {
        val root = File(tmp.toFile(), "root").apply { mkdirs() }
        val link = File(root, "dangling.txt")
        val target = File(tmp.toFile(), "outside/not-yet.txt")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }
        assumeTrue(created.isSuccess, "symlinks unsupported in this environment")
        assertFalse(DiffPresenter.isWithinRoot(link.path, root.canonicalPath))
    }

    @Test
    fun `a new file under a symlinked directory pointing outside is rejected`(@TempDir tmp: Path) {
        val root = File(tmp.toFile(), "root").apply { mkdirs() }
        val outside = File(tmp.toFile(), "outside").apply { mkdirs() }
        val link = File(root, "linked")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }
        assumeTrue(created.isSuccess, "symlinks unsupported in this environment")
        assertFalse(DiffPresenter.isWithinRoot(File(link, "new.txt").path, root.canonicalPath))
        assertFalse(DiffPresenter.isWithinRoot(File(link, "deeper/new.txt").path, root.canonicalPath))
    }

    @Test
    fun `a new file under a symlinked directory pointing inside is accepted`(@TempDir tmp: Path) {
        val root = File(tmp.toFile(), "root").apply { mkdirs() }
        val inner = File(root, "inner").apply { mkdirs() }
        val link = File(root, "linked")
        val created = runCatching { Files.createSymbolicLink(link.toPath(), inner.toPath()) }
        assumeTrue(created.isSuccess, "symlinks unsupported in this environment")
        assertTrue(DiffPresenter.isWithinRoot(File(link, "new.txt").path, root.canonicalPath))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `a Windows junction to a directory outside the root is rejected`(@TempDir tmp: Path) {
        val root = File(tmp.toFile(), "root").apply { mkdirs() }
        val outside = File(tmp.toFile(), "outside").apply { mkdirs() }
        val existing = File(outside, "secret.txt").apply { writeText("nope") }
        val junction = File(root, "junction")
        val made = ProcessBuilder("cmd", "/c", "mklink", "/J", junction.path, outside.path)
            .redirectErrorStream(true).start().waitFor()
        assumeTrue(made == 0 && junction.exists(), "mklink /J unavailable")
        assertFalse(DiffPresenter.isWithinRoot(File(junction, existing.name).path, root.canonicalPath))
        assertFalse(DiffPresenter.isWithinRoot(File(junction, "new.txt").path, root.canonicalPath))
    }

    @Test
    fun `relative root is canonicalized against the working directory and behaves consistently`(@TempDir root: Path) {
        val file = File(root.toFile(), "a.kt").apply { writeText("") }
        val relativeRoot = File(".").path
        val expected = DiffPresenter.isWithinRoot(file.canonicalPath, File(relativeRoot).canonicalPath)
        val actual = DiffPresenter.isWithinRoot(file.canonicalPath, relativeRoot)
        assertTrue(expected == actual, "relative and pre-canonicalized roots must yield the same verdict")
    }

    @Test
    fun `a path that cannot be canonicalized is rejected (fail-closed on IO failure)`(@TempDir root: Path) {
        val canonicalRoot = root.toFile().canonicalPath
        val unresolvable = canonicalRoot + File.separator + "a" + NUL + "b.kt"
        assumeTrue(
            runCatching { File(unresolvable).canonicalFile }.isFailure,
            "this platform does not reject a NUL byte in a path",
        )
        assertFalse(DiffPresenter.isWithinRoot(unresolvable, canonicalRoot))
        assertFalse(DiffPresenter.isWithinRoot(File(root.toFile(), "a.kt").path, canonicalRoot + NUL + "x"))
    }
}
