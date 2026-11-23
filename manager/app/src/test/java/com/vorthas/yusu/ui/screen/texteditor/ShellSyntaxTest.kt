package com.vorthas.yusu.ui.screen.texteditor

import org.junit.Assert.assertTrue
import org.junit.Test

class ShellSyntaxTest {
    @Test
    fun highlightsShellTokensLikeTheReference() {
        val script = "#!/system/bin/sh\n" +
            "MODDIR=/data/adb/modules/demo\n" +
            "if [ -d \"\$MODDIR\" ]; then\n" +
            "  echo \"found \$MODDIR\" # note\n" +
            "fi"
        val tokens = ShellSyntax.tokenize(script)
        assertTrue("shebang", tokens.any { it.kind == ShellToken.TIMESTAMP })
        assertTrue("assignment/variable", tokens.any { it.kind == ShellToken.TAG })
        assertTrue("keyword", tokens.any { it.kind == ShellToken.KEY && script.substring(it.start, it.end) == "if" })
        assertTrue("string", tokens.any { it.kind == ShellToken.VALUE })
        assertTrue("trailing comment", tokens.any { it.kind == ShellToken.COMMENT })
    }

    @Test
    fun hashInsideStringIsNotAComment() {
        val script = "echo \"value#inside\" # real comment"
        val tokens = ShellSyntax.tokenize(script)
        val comments = tokens.filter { it.kind == ShellToken.COMMENT }
        assertTrue(comments.isNotEmpty())
        assertTrue(comments.all { script[it.start] == '#' && it.start > script.indexOf("value#inside") })
    }

    @Test
    fun tokensStayInsideSource() {
        val script = "for i in 1 2 3; do echo \"\$i\" # note\n done"
        ShellSyntax.tokenize(script).forEach {
            assertTrue(it.start in script.indices)
            assertTrue(it.end <= script.length)
            assertTrue(it.start < it.end)
        }
    }
}
