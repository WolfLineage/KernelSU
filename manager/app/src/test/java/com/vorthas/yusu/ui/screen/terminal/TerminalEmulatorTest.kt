package com.vorthas.yusu.ui.screen.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {
    private fun textOf(row: List<TerminalCell>): String = row.map { it.char }.joinToString("").trimEnd()

    @Test
    fun plainTextAndNewline() {
        val emulator = TerminalEmulator(columns = 20) {}
        emulator.write("hello\r\nworld".toByteArray())
        val frame = emulator.frame()
        assertEquals("hello", textOf(frame.rows[0]))
        assertEquals("world", textOf(frame.rows[1]))
    }

    @Test
    fun sgrColorsAreAppliedToFollowingCharacters() {
        val emulator = TerminalEmulator(columns = 20) {}
        emulator.write("\u001B[31mred\u001B[0mplain".toByteArray())
        val row = emulator.frame().rows[0]
        assertEquals("redplain", textOf(row))
        assertEquals(0xFFE06C75.toInt(), row[0].fg)
        assertEquals(null, row[3].fg)
    }

    @Test
    fun cursorPositionAndEraseLine() {
        val emulator = TerminalEmulator(columns = 20) {}
        emulator.write("abcdef\u001B[1;3H\u001B[K".toByteArray())
        val row = emulator.frame().rows[0]
        assertEquals("ab", textOf(row))
    }

    @Test
    fun resizePreservesContentAndClampsCursor() {
        val emulator = TerminalEmulator(columns = 40) {}
        emulator.write("0123456789".toByteArray())
        emulator.resize(24)
        val frame = emulator.frame()
        assertEquals(24, frame.columns)
        assertEquals("0123456789", textOf(frame.rows[0]))
        assertTrue(frame.cursorCol < frame.columns)
    }
}
