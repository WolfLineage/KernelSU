package com.vorthas.yusu.ui.screen.terminal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Marks a command that must be typed into an interactive `su` shell, not passed to `sh -c`. */
const val INTERACTIVE_SU_PREFIX = "\u0001SU\u0001"

/** Simple bridge from other pager pages (e.g. File Manager) into the active terminal session. */
object TerminalCommandBus {
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val commands = _commands.asSharedFlow()
    private val _focusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val focusRequests = _focusRequests.asSharedFlow()

    fun send(command: String) {
        _commands.tryEmit(command)
    }

    fun requestFocus() {
        _focusRequests.tryEmit(Unit)
    }
}
