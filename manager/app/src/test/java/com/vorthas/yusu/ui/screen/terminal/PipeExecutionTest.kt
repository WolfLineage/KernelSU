package com.vorthas.yusu.ui.screen.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipeExecutionTest {
    @Test
    fun parsesAllPipeVariants() {
        assertEquals(PipeCommand("/sdcard/a.sh", false), parsePipeCommand(PIPE_EXEC_PREFIX + "/sdcard/a.sh"))
        assertEquals(PipeCommand("/sdcard/a.sh", true), parsePipeCommand(PIPE_EXEC_ROOT_PREFIX + "/sdcard/a.sh"))
        assertEquals(
            PipeCommand("/data/local/tmp/tool", false, true),
            parsePipeCommand(PIPE_EXEC_BIN_PREFIX + "/data/local/tmp/tool"),
        )
        assertEquals(
            PipeCommand("/data/local/tmp/tool", true, true),
            parsePipeCommand(PIPE_EXEC_BIN_ROOT_PREFIX + "/data/local/tmp/tool"),
        )
        assertNull(parsePipeCommand("sh /sdcard/a.sh"))
        assertNull(parsePipeCommand(null))
    }

    @Test
    fun scriptIsSourcedWithFdAndReadableArgZero() {
        val script = pipeLaunchScript(binary = false, root = true)
        assertTrue(script.contains("read -r __yusu_pipe_path"))
        assertTrue(script.contains(". /dev/fd/3"))
        assertTrue(script.contains("/proc/self/fd/4"))
        assertTrue(script.contains("PS1='\$PWD # '"))
        // No extra queued line: interactive scripts must own the PTY stdin.
        assertTrue(!script.contains("cat <"))
    }

    @Test
    fun binaryRunsDirectlyAndRestoresInteractiveShell() {
        val script = pipeLaunchScript(binary = true, root = false)
        assertTrue(script.contains("\"\$__yusu_pipe_path\""))
        assertTrue(!script.contains("sh -s"))
        assertTrue(!script.contains("exec /system/bin/sh"))
        assertTrue(script.contains("PS1='\$PWD $ '"))
    }
}
