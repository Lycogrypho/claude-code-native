package dev.lain.claudejb.controller.process

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryFlag
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

internal class LaunchFiles private constructor(
    val args: List<String>,
    private val dir: Path?,
    private val written: List<Path>,
) {

    fun discard() {
        written.forEach { file -> runCatching { Files.deleteIfExists(file) } }
        dir?.let { own -> runCatching { Files.deleteIfExists(own) } }
    }

    companion object {

        private const val MCP_CONFIG = "--mcp-config"
        private const val PROMPT = "--append-system-prompt"
        private const val PROMPT_FILE = "--append-system-prompt-file"
        private const val DIR_PREFIX = "claude-launch"
        private val POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
        private val OWNER_DIR = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        private val OWNER_FILE = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))

        fun externalise(
            args: List<String>,
            promptToFile: Boolean,
            base: Path = Path.of(System.getProperty("java.io.tmpdir")),
        ): LaunchFiles {
            val out = ArrayList<String>(args.size)
            val written = ArrayList<Path>()
            var dir: Path? = null
            var at = 0
            while (at < args.size) {
                val flag = args[at]
                val value = args.getOrNull(at + 1)
                val file = if (value == null) null else fileFor(flag, promptToFile)
                if (file == null || value == null) {
                    out += flag
                    at++
                    continue
                }
                val home = dir ?: privateDirectory(base).also { dir = it }
                out += file.flag
                out += write(home.resolve(file.name), value).also { written.add(it) }.toString()
                at += 2
            }
            return LaunchFiles(out, dir, written)
        }

        private class Target(val flag: String, val name: String)

        private fun fileFor(flag: String, promptToFile: Boolean): Target? = when {
            flag == MCP_CONFIG -> Target(MCP_CONFIG, "mcp.json")
            flag == PROMPT && promptToFile -> Target(PROMPT_FILE, "prompt.txt")
            else -> null
        }

        private fun privateDirectory(base: Path): Path {
            if (POSIX) return Files.createTempDirectory(base, DIR_PREFIX, OWNER_DIR)
            return Files.createTempDirectory(base, DIR_PREFIX).also(::grantToOwnerAlone)
        }

        private fun write(file: Path, text: String): Path {
            if (POSIX) Files.createFile(file, OWNER_FILE)
            return Files.writeString(file, text)
        }

        private fun grantToOwnerAlone(dir: Path) {
            val view = Files.getFileAttributeView(dir, AclFileAttributeView::class.java) ?: return
            val owner = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(view.owner)
                .setPermissions(AclEntryPermission.entries.toSet())
                .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
                .build()
            view.acl = listOf(owner)
        }
    }
}
