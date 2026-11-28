package com.vorthas.yusu.ui.screen.texteditor

/**
 * Language-aware token source shared by the native editor and the chunked viewer.
 * MT-style colouring is kept intentionally lightweight: keywords, strings, variables,
 * comments and numbers are enough to read source files without a full parser.
 */
object SourceSyntax {
    enum class Lang { SHELL, C_LIKE, XML, PLAIN }

    private const val DEFAULT_TEXT = 0xFFC8C8C8.toInt()

    private val colors = mapOf(
        ShellToken.KEY to 0xFF79B8FF.toInt(),
        ShellToken.TAG to 0xFFE8C07D.toInt(),
        ShellToken.VALUE to 0xFF98C379.toInt(),
        ShellToken.ASSIGN to DEFAULT_TEXT,
        ShellToken.COMMENT to 0xFF7A7A7A.toInt(),
        ShellToken.TIMESTAMP to 0xFF7A7A7A.toInt(),
        ShellToken.ERROR to 0xFFFF8A80.toInt(),
        ShellToken.WARNING to 0xFFFFD180.toInt(),
    )

    fun color(kind: ShellToken): Int = colors[kind] ?: DEFAULT_TEXT

    fun langFor(path: String): Lang {
        val name = path.substringAfterLast('/').lowercase()
        if (listOf("sh", "bash", "zsh", "ksh", "fish").any { name.endsWith(".$it") }) return Lang.SHELL
        if (listOf("xml", "xsd", "xsl", "svg", "axml").any { name.endsWith(".$it") }) return Lang.XML
        if (listOf(
                "c", "cc", "cpp", "cxx", "h", "hpp", "hh", "java", "kt", "kts", "js", "jsx",
                "ts", "tsx", "go", "rs", "cs", "php", "swift", "m", "mm",
            ).any { name.endsWith(".$it") }) return Lang.C_LIKE
        return Lang.PLAIN
    }

    fun tokenize(source: String, lang: Lang): List<ShellSyntax.Token> = when (lang) {
        Lang.SHELL -> ShellSyntax.tokenize(source)
        Lang.C_LIKE -> tokenizeCLike(source)
        Lang.XML -> tokenizeXml(source)
        Lang.PLAIN -> emptyList()
    }

    /** Lightweight XML colouring: tags blue, attribute names amber, values green. */
    private fun tokenizeXml(source: String): List<ShellSyntax.Token> {
        val result = ArrayList<ShellSyntax.Token>()
        var offset = 0
        for (raw in source.split('\n')) {
            fun add(a: Int, b: Int, kind: ShellToken) {
                if (b > a) result += ShellSyntax.Token(offset + a, offset + b, kind)
            }
            var i = 0
            var inTag = false
            while (i < raw.length) {
                when {
                    raw.startsWith("<!--", i) -> {
                        add(i, raw.length, ShellToken.COMMENT)
                        break
                    }
                    raw.startsWith("<![CDATA[", i) -> {
                        add(i, raw.length, ShellToken.COMMENT)
                        break
                    }
                    raw[i] == '<' -> {
                        var j = i + 1
                        if (j < raw.length && (raw[j] == '/' || raw[j] == '?' || raw[j] == '!')) j++
                        while (j < raw.length && (isXmlNameChar(raw[j]) || raw[j] == ':')) j++
                        add(i, j, ShellToken.KEY)
                        inTag = true
                        i = j
                    }
                    raw[i] == '>' -> {
                        inTag = false
                        i++
                    }
                    inTag && (raw[i] == '"' || raw[i] == '\'') -> {
                        val quote = raw[i]
                        var j = i + 1
                        while (j < raw.length && raw[j] != quote) j++
                        if (j < raw.length) j++
                        add(i, j, ShellToken.VALUE)
                        i = j
                    }
                    inTag && isXmlNameChar(raw[i]) -> {
                        var j = i + 1
                        while (j < raw.length && (isXmlNameChar(raw[j]) || raw[j] == ':')) j++
                        var k = j
                        while (k < raw.length && raw[k] == ' ') k++
                        if (k < raw.length && raw[k] == '=') add(i, j, ShellToken.TAG)
                        i = j
                    }
                    raw[i] == '&' -> {
                        var j = i + 1
                        while (j < raw.length && raw[j] != ';') j++
                        if (j < raw.length) j++
                        add(i, j, ShellToken.TAG)
                        i = j
                    }
                    else -> i++
                }
            }
            offset += raw.length + 1
        }
        return result
    }

    private fun isXmlNameChar(ch: Char): Boolean =
        ch == '_' || ch == '-' || ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'

    private val cKeywords = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "return",
        "goto", "struct", "class", "enum", "union", "typedef", "namespace", "template",
        "public", "private", "protected", "static", "const", "constexpr", "inline", "virtual",
        "override", "final", "new", "delete", "this", "super", "try", "catch", "throw",
        "int", "long", "short", "char", "float", "double", "void", "bool", "unsigned", "signed",
        "auto", "var", "val", "fun", "object", "interface", "package", "import", "include",
        "define", "sizeof", "nullptr", "null", "true", "false", "async", "await", "function",
    )

    private fun isWordChar(ch: Char): Boolean =
        ch == '_' || ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'

    private fun tokenizeCLike(source: String): List<ShellSyntax.Token> {
        val result = ArrayList<ShellSyntax.Token>()
        var offset = 0
        for (raw in source.split('\n')) {
            fun add(a: Int, b: Int, kind: ShellToken) {
                if (b > a) result += ShellSyntax.Token(offset + a, offset + b, kind)
            }
            var line = raw
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#")) {
                val start = line.indexOfFirst { !it.isWhitespace() }
                var end = start + 1
                while (end < line.length && isWordChar(line[end])) end++
                add(start, end, ShellToken.KEY)
                offset += raw.length + 1
                continue
            }
            var commentAt = line.length
            var inSingle = false
            var inDouble = false
            var escaped = false
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                if (escaped) { escaped = false; i++; continue }
                when {
                    ch == '\\' && (inSingle || inDouble) -> escaped = true
                    ch == '\'' && !inDouble -> inSingle = !inSingle
                    ch == '"' && !inSingle -> inDouble = !inDouble
                    !inSingle && !inDouble && ch == '/' && i + 1 < line.length &&
                        (line[i + 1] == '/' || line[i + 1] == '*') -> { commentAt = i; break }
                }
                i++
            }
            line = line.substring(0, commentAt)
            if (commentAt < raw.length) add(commentAt, raw.length, ShellToken.COMMENT)

            i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' || ch == '\'' -> {
                        val quote = ch
                        var j = i + 1
                        while (j < line.length) {
                            if (line[j] == '\\') { j += 2; continue }
                            if (line[j] == quote) { j++; break }
                            j++
                        }
                        add(i, j.coerceAtMost(line.length), ShellToken.VALUE)
                        i = j
                    }
                    ch in '0'..'9' -> {
                        var j = i + 1
                        while (j < line.length && (isWordChar(line[j]) || line[j] == '.')) j++
                        add(i, j, ShellToken.TAG)
                        i = j
                    }
                    isWordChar(ch) -> {
                        var j = i + 1
                        while (j < line.length && isWordChar(line[j])) j++
                        if (line.substring(i, j) in cKeywords) add(i, j, ShellToken.KEY)
                        i = j
                    }
                    else -> i++
                }
            }
            offset += raw.length + 1
        }
        return result
    }
}
