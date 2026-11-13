package dev.lain.claudejb.controller.mcp.tools.code

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileGlobTest {

    @Test
    fun `a glob without a directory part matches the file name anywhere`() {
        val glob = FileGlob("*.kt")
        assertTrue(glob.matches("A.kt"))
        assertTrue(glob.matches("src/main/kotlin/A.kt"))
        assertFalse(glob.matches("src/main/kotlin/A.java"))
    }

    @Test
    fun `a glob with a directory part matches the project-relative path`() {
        assertTrue(FileGlob("**/*.kt").matches("src/main/kotlin/A.kt"))
        assertFalse(FileGlob("**/*.kt").matches("src/main/kotlin/A.java"))
        assertTrue(FileGlob("src/*.kt").matches("src/A.kt"))
        assertFalse(FileGlob("src/*.kt").matches("src/main/A.kt"))
        assertFalse(FileGlob("src/*.kt").matches("other/A.kt"))
        assertTrue(FileGlob("src/**/Test?.java").matches("src/test/java/Test1.java"))
    }

    @Test
    fun `a capped list says truncated only when something was left out`() {
        assertEquals(listOf(1, 2) to false, Capped.of(listOf(1, 2), 2))
        assertEquals(listOf(1, 2) to true, Capped.of(listOf(1, 2, 3), 2))
        assertEquals(emptyList<Int>() to false, Capped.of(emptyList<Int>(), 2))
    }
}
