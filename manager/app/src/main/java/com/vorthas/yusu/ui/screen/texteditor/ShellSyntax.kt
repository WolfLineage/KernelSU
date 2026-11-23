package com.vorthas.yusu.ui.screen.texteditor

/**
 * Token kinds mapped to the colours observed in MT Manager's text editor:
 * KEY = blue keywords, TAG = amber assignment/variable, VALUE = amber strings,
 * COMMENT = muted grey, TIMESTAMP = muted shebang.
 */
enum class ShellToken { KEY, TAG, VALUE, COMMENT, TIMESTAMP, ERROR, WARNING }

/**
 * Line-based shell highlighter ported from the reference tool's DocumentSyntax.ranges.
 *
 * Deliberately implemented with manual character scanning instead of regular expressions:
 * Android's ICU engine rejects several patterns that are valid on the JVM (a trailing '-'
 * or '?' inside a character class), which caused an ExceptionInInitializerError and crashed
 * the editor. Manual scanning behaves identically everywhere.
 */
object ShellSyntax {
    data class Token(val start: Int, val end: Int, val kind: ShellToken)

    private val keywords = setOf(
        "if", "then", "elif", "else", "fi", "case", "esac", "for", "while", "until",
        "do", "done", "in", "function", "return", "exit", "local", "export", "readonly",
        "unset", "shift", "source", "set", "trap", "exec", "eval", "true", "false",
    )

    private fun isWordChar(ch: Char): Boolean =
        ch == '_' || ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'

    /** Locate the comment start, ignoring '#' inside single/double quoted strings. */
    private fun commentStart(line: String): Int {
        var inSingle = false
        var inDouble = false
        var escaped = false
        for (i in line.indices) {
            val ch = line[i]
            if (escaped) { escaped = false; continue }
            when {
                ch == '\\' && inDouble -> escaped = true
                ch == '\'' && !inDouble -> inSingle = !inSingle
                ch == '"' && !inSingle -> inDouble = !inDouble
                ch == '#' && !inSingle && !inDouble && (i == 0 || line[i - 1].isWhitespace()) -> return i
            }
        }
        return line.length
    }

    fun tokenize(source: String): List<Token> {
        if (source.isEmpty() || source.length > 1_500_000) return emptyList()
        val result = ArrayList<Token>()
        var offset = 0
        for (line in source.split('\n')) {
            fun add(a: Int, b: Int, kind: ShellToken) {
                if (b > a) result += Token(offset + a, offset + b, kind)
            }
            when {
                line.startsWith("#!") -> add(0, line.length, ShellToken.TIMESTAMP)
                line.trimStart().startsWith("#") -> add(0, line.length, ShellToken.COMMENT)
                else -> {
                    val codeEnd = commentStart(line)
                    val slice = line.substring(0, codeEnd)
                    var i = 0
                    // Leading assignment: NAME=
                    val firstSpace = slice.indexOfFirst { !it.isWhitespace() }
                    if (firstSpace >= 0) {
                        var j = firstSpace
                        while (j < slice.length && isWordChar(slice[j])) j++
                        if (j > firstSpace && j < slice.length && slice[j] == '=') {
                            add(firstSpace, j, ShellToken.TAG)
                        }
                    }
                    // Keywords, variables, strings — single left-to-right pass.
                    while (i < slice.length) {
                        val ch = slice[i]
                        when {
                            ch == '\'' || ch == '"' -> {
                                val quote = ch
                                var j = i + 1
                                while (j < slice.length) {
                                    if (slice[j] == '\\' && quote == '"') { j += 2; continue }
                                    if (slice[j] == quote) { j++; break }
                                    j++
                                }
                                add(i, j.coerceAtMost(slice.length), ShellToken.VALUE)
                                i = j
                            }
                            ch == '$' -> {
                                var j = i + 1
                                if (j < slice.length && slice[j] == '{') {
                                    j++
                                    while (j < slice.length && isWordChar(slice[j])) j++
                                    if (j < slice.length && slice[j] == '}') j++
                                } else {
                                    while (j < slice.length && isWordChar(slice[j])) j++
                                    if (j == i + 1 && j < slice.length && slice[j] in "@*#?0123456789") j++
                                }
                                add(i, j, ShellToken.TAG)
                                i = j
                            }
                            isWordChar(ch) -> {
                                var j = i
                                while (j < slice.length && isWordChar(slice[j])) j++
                                if (slice.substring(i, j) in keywords) add(i, j, ShellToken.KEY)
                                i = j
                            }
                            else -> i++
                        }
                    }
                    if (codeEnd < line.length) add(codeEnd, line.length, ShellToken.COMMENT)
                }
            }
            offset += line.length + 1
        }
        return result
    }
}
