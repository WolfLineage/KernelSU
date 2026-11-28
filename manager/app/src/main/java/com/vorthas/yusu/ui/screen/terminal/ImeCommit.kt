package com.vorthas.yusu.ui.screen.terminal

/**
 * Difference between the text already forwarded to the PTY and the text the IME
 * has just committed. Some IMEs commit the composing text *again* together with
 * the newline when their Enter key is pressed, so blindly forwarding the whole
 * committed prefix would duplicate the last candidate ("1" -> "11").
 */
internal data class ImeTextDelta(val deleteCount: Int, val insertText: String)

internal fun imeTextDelta(old: String, new: String): ImeTextDelta {
    var common = 0
    val limit = minOf(old.length, new.length)
    while (common < limit && old[common] == new[common]) common++
    return ImeTextDelta(old.length - common, new.substring(common))
}
