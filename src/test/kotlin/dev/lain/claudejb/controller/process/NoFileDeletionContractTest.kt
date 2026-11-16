package dev.lain.claudejb.controller.process

import dev.lain.claudejb.controller.process.credentials.CredentialsVault
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NoFileDeletionContractTest {

    private val recursive = listOf(
        "deleteRecursively",
        "FileUtil.delete(",
        "FileUtils.deleteDirectory",
        "FileUtils.forceDelete",
        "walkFileTree",
    )

    private val single = listOf(
        ".delete()",
        "deleteIfExists",
        "Files.delete(",
        "deleteOnExit",
    )

    private companion object {
        val ALLOWED = setOf(
            "CredentialsVault.kt",
            "LaunchFiles.kt",
            "LegacyProjectSettings.kt",
            "LegacySessionHistory.kt",
            "SettingsStore.kt",
            "SharedPluginFiles.kt",
            "SocketHome.kt",
        )
    }

    @Test
    fun `SocketHome only deletes inside the private directory it created`() {
        val home = ktFiles().first { it.name == "SocketHome.kt" }
        val allowed = Regex("""^(import |file\.deleteIfExists\(\)|runCatching \{ dir\.)""")
        val bad = hits(home, single).filterNot { (_, line) -> allowed.containsMatchIn(line) }
        assertTrue(bad.isEmpty()) {
            "SocketHome.kt may only remove its own token file and the entries of the socket directory it " +
                "created under the temp dir.\n" + bad.joinToString("\n") { "${home.name}:${it.first}: ${it.second}" }
        }
    }

    @Test
    fun `LaunchFiles only deletes the files it wrote and the directory it created`() {
        val launch = ktFiles().first { it.name == "LaunchFiles.kt" }
        val allowed = setOf(
            "written.forEach { file -> runCatching { Files.deleteIfExists(file) } }",
            "dir?.let { own -> runCatching { Files.deleteIfExists(own) } }",
        )
        val bad = hits(launch, single).filterNot { (_, line) -> line in allowed }
        assertTrue(bad.isEmpty()) {
            "LaunchFiles.kt may only remove the files it wrote itself, one by one, and the directory it created " +
                "for them under the temp dir.\n" + bad.joinToString("\n") { "${launch.name}:${it.first}: ${it.second}" }
        }
        val text = launch.readText()
        assertTrue(text.contains("write(home.resolve(file.name), value).also { written.add(it) }")) {
            "LaunchFiles.kt no longer records exactly the files it writes inside its own directory — this contract " +
                "is checking the wrong thing."
        }
        assertTrue(text.contains("Files.createTempDirectory(base, DIR_PREFIX")) {
            "LaunchFiles.kt no longer creates the directory it deletes."
        }
    }

    @Test
    fun `no source file deletes recursively`() {
        val offenders = ktFiles().flatMap { file ->
            hits(file, recursive).map { "${file.name}:${it.first}: ${it.second}" }
        }
        assertTrue(offenders.isEmpty()) {
            "Recursive deletion is banned in this codebase — it emptied a user's whole ~/.claude once, " +
                "through symlinks (see this test's KDoc). Remove it; do not \"fix\" it.\n" +
                offenders.joinToString("\n")
        }
    }

    @Test
    fun `only CredentialsVault deletes a file`() {
        val offenders = ktFiles().filterNot { it.name in ALLOWED }.flatMap { file ->
            hits(file, single).map { "${file.name}:${it.first}: ${it.second}" }
        }
        assertTrue(offenders.isEmpty()) {
            "Only ${ALLOWED.joinToString()} may delete a file, each for the one purpose documented there. " +
                "Everything else on the user's disk — conversations above all — is theirs.\n" +
                offenders.joinToString("\n")
        }
    }

    @Test
    fun `the one permitted deletion targets the credentials file and nothing else`() {
        val vault = ktFiles().first { it.name == "CredentialsVault.kt" }
        val bad = hits(vault, single).filterNot { (_, line) -> Regex("""\bfile\.delete\(\)""").containsMatchIn(line) }
        assertTrue(bad.isEmpty()) {
            "$ALLOWED may only delete the harvested credentials file (`file.delete()`, where `file` is " +
                "credentialsFile()).\n" + bad.joinToString("\n") { "${vault.name}:${it.first}: ${it.second}" }
        }
        assertTrue(vault.readText().contains("fun credentialsFile()")) {
            "$ALLOWED no longer resolves credentialsFile() — this contract is checking the wrong thing."
        }
    }

    private fun hits(file: File, needles: List<String>): List<Pair<Int, String>> =
        file.readLines().mapIndexedNotNull { index, raw ->
            val line = raw.trim()
            if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) return@mapIndexedNotNull null
            if (needles.any { it in line }) index + 1 to line else null
        }

    private fun ktFiles(): List<File> =
        sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun sourceRoot(): File =
        sequenceOf(File("src/main/kotlin"), File("../src/main/kotlin"))
            .firstOrNull { it.isDirectory }
            ?: error("could not locate src/main/kotlin from ${File("").absolutePath}")
}
