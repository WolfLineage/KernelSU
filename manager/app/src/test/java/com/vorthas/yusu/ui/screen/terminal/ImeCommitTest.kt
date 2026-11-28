package com.vorthas.yusu.ui.screen.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeCommitTest {
    @Test
    fun candidateCommittedAgainWithNewlineIsNotDuplicated() {
        // Some IMEs commit "1\n" after the composing text "1" was already forwarded.
        val delta = imeTextDelta("1", "1")
        assertEquals(0, delta.deleteCount)
        assertEquals("", delta.insertText)
    }

    @Test
    fun pastedTextForwardsEveryNewCharacter() {
        val delta = imeTextDelta("", "abc")
        assertEquals(0, delta.deleteCount)
        assertEquals("abc", delta.insertText)
    }

    @Test
    fun composingReplacementSendsBackspacesThenInsertion() {
        val delta = imeTextDelta("ab", "ac")
        assertEquals(1, delta.deleteCount)
        assertEquals("c", delta.insertText)
    }

    @Test
    fun deletionSendsBackspace() {
        val delta = imeTextDelta("ab", "a")
        assertEquals(1, delta.deleteCount)
        assertEquals("", delta.insertText)
    }
}
