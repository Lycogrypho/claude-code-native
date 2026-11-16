package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class NoCommentsEverywhereContractTest {

    private val repo: File = MainSources.root("src/main/kotlin").absoluteFile.parentFile.parentFile.parentFile

    private fun under(dir: String, vararg extensions: String): List<File> =
        File(repo, dir).walkTopDown().filter { it.isFile && it.extension in extensions }.toList().sortedBy { it.path }

    private fun inDir(dir: String, vararg extensions: String): List<File> =
        File(repo, dir).listFiles().orEmpty().filter { it.isFile && it.extension in extensions }.sortedBy { it.path }

    private fun assertClean(family: String, files: List<File>, scan: (String) -> List<Int>) {
        assertTrue(files.isNotEmpty()) { "no $family file was found, so the scan proves nothing" }
        val offenders = files.flatMap { file ->
            val lines = file.readLines()
            scan(file.readText().replace("\r\n", "\n")).map { "${file.relativeTo(repo).invariantSeparatorsPath}:$it: ${lines[it - 1].trim()}" }
        }
        assertEquals(emptyList<String>(), offenders) {
            "CLAUDE.md: no comments, in any language in this repository. The reasoning goes into a name, a test, " +
                "the commit message or docs/ (BUILD_NOTES.md, SCRIPT_NOTES.md, PAGE_NOTES.md)."
        }
    }

    @Test
    fun `no Kotlin, Java or Gradle file outside the main sources carries a comment`() = assertClean(
        "Kotlin, Java or Gradle",
        under("src/test/kotlin", "kt") + under("src/uiTest", "kt") + under("src/driftCheck", "kt") +
            under("src/main/java", "java") + inDir(".", "kts"),
        CommentScan::kotlinLike,
    )

    @Test
    fun `the page shell and the descriptors carry no comment beyond the machine-read markers`() = assertClean(
        "markup",
        inDir("src/main/resources/jcef", "html") + inDir("src/main/resources/META-INF", "xml"),
        CommentScan::markup,
    )

    @Test
    fun `no stylesheet carries a comment`() =
        assertClean("stylesheet", under("src/main/resources/jcef/css", "css"), CommentScan::stylesheet)

    @Test
    fun `no TypeScript source and no frontend test carries a comment`() = assertClean(
        "TypeScript or JavaScript",
        under("src/main/ts", "ts") + under("src/test/frontend", "js") + inDir("bin", "js"),
        CommentScan::scriptLike,
    )

    @Test
    fun `no shell script carries a comment`() = assertClean("shell", inDir("scripts", "sh"), CommentScan::shell)

    @Test
    fun `no Python script carries a comment or a docstring`() =
        assertClean("Python", inDir("scripts", "py") + File(repo, "bin/fake-claude"), CommentScan::python)

    @Test
    fun `no properties file carries a comment`() = assertClean(
        "properties",
        inDir("scripts", "properties") + inDir(".", "properties") + inDir("gradle", "properties"),
        CommentScan::properties,
    )
}
