package dev.lain.claudejb

internal object CommentScan {

    private val PRAGMA = Regex("""noinspection|MAP:GENERATED|shellcheck\s|noqa|eslint-disable|@ts-|prettier-ignore""")
    private val MARKER = Regex("""^<!--[A-Z][A-Z_:-]*-->$""")
    private val MARKUP_COMMENT = Regex("""<!--[\s\S]*?-->""")
    private val HEREDOC = Regex("""^<<(-?)\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\2""")
    private val SCRIPT_OPENERS = listOf("//", "/*", "* ", "*/")
    private const val TRIPLE = "\"\"\""
    private val TRIPLES = listOf(TRIPLE, "'''")

    private class Lines(text: String) {
        val all = text.lines()
        val hits = sortedSetOf<Int>()

        fun hit(row: Int) {
            if (!PRAGMA.containsMatchIn(all[row])) hits += row + 1
        }
    }

    fun kotlinLike(text: String): List<Int> = KotlinScan(text).run()

    private class KotlinScan(private val text: String) {
        private val lines = Lines(text)
        private var state = CODE
        private var row = 0
        private var i = 0

        fun run(): List<Int> {
            while (i < text.length) {
                step(text[i])
                i++
            }
            return lines.hits.toList()
        }

        private fun at(token: String) = text.startsWith(token, i)

        private fun step(c: Char) = when {
            c == '\n' -> newline()
            state == CODE -> code(c)
            state == BLOCK -> if (at("*/")) leave(1) else Unit
            state == RAW -> if (at(TRIPLE)) leaveRaw() else Unit
            state == STRING -> quoted(c, '"')
            state == CHAR -> quoted(c, '\'')
            else -> Unit
        }

        private fun newline() {
            row++
            if (state == BLOCK) {
                lines.hit(row)
            } else if (state != RAW) {
                state = CODE
            }
        }

        private fun code(c: Char) = when {
            at("//") -> comment(LINE)
            at("/*") -> comment(BLOCK)
            at(TRIPLE) -> enter(RAW, 2)
            c == '"' -> enter(STRING, 0)
            c == '\'' -> enter(CHAR, 0)
            else -> Unit
        }

        private fun comment(next: Int) {
            lines.hit(row)
            enter(next, 1)
        }

        private fun enter(next: Int, skip: Int) {
            state = next
            i += skip
        }

        private fun leave(skip: Int) = enter(CODE, skip)

        private fun leaveRaw() {
            leave(2)
            while (text.getOrNull(i + 1) == '"') i++
        }

        private fun quoted(c: Char, closing: Char) {
            if (c == '\\') {
                i++
            } else if (c == closing) {
                state = CODE
            }
        }
    }

    private const val CODE = 0
    private const val LINE = 1
    private const val BLOCK = 2
    private const val RAW = 3
    private const val STRING = 4
    private const val CHAR = 5

    fun markup(text: String): List<Int> =
        MARKUP_COMMENT.findAll(text)
            .filterNot { MARKER.matches(it.value) }
            .map { text.substring(0, it.range.first).count { c -> c == '\n' } + 1 }
            .toList()

    fun stylesheet(text: String): List<Int> =
        text.lines().mapIndexedNotNull { row, line -> if ("/*" in line) row + 1 else null }

    fun scriptLike(text: String): List<Int> {
        val lines = Lines(text)
        var inTemplate = false
        lines.all.forEachIndexed { row, line ->
            val trimmed = line.trimStart()
            if (!inTemplate && SCRIPT_OPENERS.any(trimmed::startsWith)) lines.hit(row)
            if (line.replace("\\`", "").count { it == '`' } % 2 == 1) inTemplate = !inTemplate
        }
        return lines.hits.toList()
    }

    fun properties(text: String): List<Int> =
        text.lines().mapIndexedNotNull { row, line -> if (line.trimStart().firstOrNull() in setOf('#', '!')) row + 1 else null }

    fun shell(text: String): List<Int> = ShellScan(text).run()

    private class ShellScan(text: String) {
        private val lines = Lines(text)
        private var quote: Char? = null
        private var heredoc: Pair<Boolean, String>? = null

        fun run(): List<Int> {
            lines.all.forEachIndexed(::line)
            return lines.hits.toList()
        }

        private fun line(row: Int, line: String) {
            val open = heredoc
            when {
                open != null -> if (dedent(line, open.first) == open.second) heredoc = null
                row == 0 && line.startsWith("#!") -> Unit
                else -> scan(row, line)
            }
        }

        private fun dedent(line: String, tabs: Boolean) = if (tabs) line.trimStart('\t') else line

        private fun scan(row: Int, line: String) {
            var i = 0
            while (i < line.length) {
                val skip = step(line, i)
                if (skip < 0) return lines.hit(row)
                i += skip + 1
            }
        }

        private fun step(line: String, i: Int): Int {
            val c = line[i]
            return when (quote) {
                '\'' -> closeOn(c == '\'')
                '"' -> if (c == '\\') 1 else closeOn(c == '"')
                else -> unquoted(line, i, c)
            }
        }

        private fun closeOn(closes: Boolean): Int {
            if (closes) quote = null
            return 0
        }

        private fun unquoted(line: String, i: Int, c: Char): Int = when {
            c == '\\' -> 1
            c == '\'' || c == '"' -> 0.also { quote = c }
            c == '#' && startsWord(line, i) -> -1
            c == '<' -> hereDocument(line, i)
            else -> 0
        }

        private fun startsWord(line: String, i: Int) = i == 0 || line[i - 1].isWhitespace()

        private fun hereDocument(line: String, i: Int): Int {
            if (line.startsWith("<<<", i) || line.getOrNull(i - 1) == '<') return 0
            val match = HEREDOC.find(line.substring(i)) ?: return 0
            heredoc = (match.groupValues[1] == "-") to match.groupValues[3]
            return match.value.length - 1
        }
    }

    fun python(text: String): List<Int> = PythonScan(text).run()

    private class PythonScan(text: String) {
        private val lines = Lines(text)
        private var quote: String? = null
        private var previous: String? = null

        fun run(): List<Int> {
            lines.all.forEachIndexed(::line)
            return lines.hits.toList()
        }

        private fun line(row: Int, line: String) {
            val trimmed = line.trim()
            if (quote == null && opensString(trimmed) && opensBody()) lines.hit(row)
            var i = 0
            while (i < line.length) i += step(row, line, i) + 1
            if (quote !in TRIPLES) quote = null
            if (trimmed.isNotEmpty()) previous = trimmed
        }

        private fun opensString(trimmed: String) = TRIPLES.any { trimmed.removePrefix("r").startsWith(it) }

        private fun opensBody(): Boolean {
            val before = previous ?: return true
            return before.startsWith("#!") || before.endsWith(":")
        }

        private fun step(row: Int, line: String, i: Int): Int {
            val open = quote
            return when {
                open == null -> unquoted(row, line, i)
                line.startsWith(open, i) -> (open.length - 1).also { quote = null }
                line[i] == '\\' -> 1
                else -> 0
            }
        }

        private fun unquoted(row: Int, line: String, i: Int): Int = when (line[i]) {
            '#' -> {
                if (row > 0 || !line.startsWith("#!")) lines.hit(row)
                line.length
            }

            '"', '\'' -> {
                val opened = TRIPLES.firstOrNull { line.startsWith(it, i) } ?: line[i].toString()
                quote = opened
                opened.length - 1
            }

            else -> 0
        }
    }
}
