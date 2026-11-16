package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommentScanTest {

    private val slashes = "/" + "/"
    private val open = "/" + "*"
    private val close = "*" + "/"
    private val triple = "\"".repeat(3)

    @Test
    fun `Kotlin comments are found on their line, in any position, and not inside strings`() {
        val source = listOf(
            "val url = \"https:$slashes example.com\"",
            "val a = 1 $slashes trailing",
            "$slashes whole line",
            "val raw = $triple",
            "    $slashes inside a raw string",
            "    $open also $close",
            triple,
            "$open block",
            "   continues",
            "$close val b = 2",
            "val c = '\"' $slashes after a quote character",
            "@Suppress(\"x\") $slashes noinspection Something",
            "val quoted = ${triple}ends with a quote\"$triple",
            "val shell = \"python3 -c 'print(a${slashes}b)'\"",
            "val apostrophe = \"it's\"",
            "val division = \"a${slashes}b\"",
        ).joinToString("\n")
        assertEquals(listOf(2, 3, 8, 9, 10, 11), CommentScan.kotlinLike(source))
    }

    @Test
    fun `markup comments are found unless they are the markers the page assembly replaces`() {
        val html = "<head>\n<!--CSP-->\n<!-- a reason -->\n<p>x</p> <!--\n spanning\n-->\n<!--THEME_VARS-->"
        assertEquals(listOf(3, 4), CommentScan.markup(html))
    }

    @Test
    fun `a stylesheet comment is found anywhere on a line`() {
        assertEquals(listOf(2), CommentScan.stylesheet(".a { color: red; }\n.b { $open why $close color: blue; }"))
    }

    @Test
    fun `script comments are found at the start of a line, and fixture text in a template literal is not one`() {
        val script = listOf(
            "const a = 1;",
            "$slashes a reason",
            "const text = `",
            "$slashes fixture text",
            "`;",
            "$open block $close",
            "$slashes eslint-disable-next-line no-undef",
        ).joinToString("\n")
        assertEquals(listOf(2, 6), CommentScan.scriptLike(script))
    }

    @Test
    fun `shell comments are found outside quotes and here-documents, and the shebang is not one`() {
        val script = listOf(
            "#!/usr/bin/env bash",
            "# a reason",
            "echo \"a # not a comment\" # but this is",
            "echo \${#array[@]} \$#",
            "cat <<'EOF'",
            "# data, not a comment",
            "EOF",
            "awk '",
            "# awk text inside a quoted program",
            "'",
            "# shellcheck disable=SC2086",
            "true",
        ).joinToString("\n")
        assertEquals(listOf(2, 3), CommentScan.shell(script))
    }

    @Test
    fun `Python comments and docstrings are found, and string data is not`() {
        val script = listOf(
            "#!/usr/bin/env python3",
            "${triple}A module docstring.$triple",
            "import os  # trailing",
            "TEXT = $triple",
            "# data inside a string",
            triple,
            "def run():",
            "    ${triple}A function docstring.",
            "    $triple",
            "    return '#'  # noqa: E501",
        ).joinToString("\n")
        assertEquals(listOf(2, 3, 8), CommentScan.python(script))
    }

    @Test
    fun `a properties comment is a line that starts with a hash or a bang`() {
        assertEquals(listOf(1, 3), CommentScan.properties("# why\nkey=value # part of the value\n! also"))
    }
}
