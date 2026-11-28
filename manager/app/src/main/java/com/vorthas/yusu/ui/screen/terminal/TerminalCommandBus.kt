package com.vorthas.yusu.ui.screen.terminal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Marks a command that must be typed into an interactive `su` shell, not passed to `sh -c`. */
const val INTERACTIVE_SU_PREFIX = "\u0001SU\u0001"

/** Pipe execution: script content is streamed to `sh -s` from the script's own directory. */
const val PIPE_EXEC_PREFIX = "\u0001PIPE\u0001"
const val PIPE_EXEC_ROOT_PREFIX = "\u0001PIPESU\u0001"
/** Same pipe-based interactive session, but the target is an ELF binary. */
const val PIPE_EXEC_BIN_PREFIX = "\u0001PIPEBIN\u0001"
const val PIPE_EXEC_BIN_ROOT_PREFIX = "\u0001PIPEBINSU\u0001"

/** Simple bridge from other pager pages (e.g. File Manager) into the active terminal session. */
object TerminalCommandBus {
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val commands = _commands.asSharedFlow()
    private val _focusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val focusRequests = _focusRequests.asSharedFlow()
    /** Emitted when an execution session is closed with Enter; main pager returns to File Manager. */
    private val _executionClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val executionClosed = _executionClosed.asSharedFlow()

    fun send(command: String) {
        _commands.tryEmit(command)
    }

    fun requestFocus() {
        _focusRequests.tryEmit(Unit)
    }

    fun notifyExecutionClosed() {
        _executionClosed.tryEmit(Unit)
    }
}
