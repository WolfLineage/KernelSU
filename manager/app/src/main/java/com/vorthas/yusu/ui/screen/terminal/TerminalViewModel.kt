package com.vorthas.yusu.ui.screen.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

class TerminalViewModel : ViewModel() {
    private val _state = MutableStateFlow(TerminalUiState())
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    private class Session(
        val id: Long,
        val emulator: TerminalEmulator,
        var handle: Long = 0L,
        var reader: Job? = null,
        val transient: Boolean = false,
    )

    private val sessions = Collections.synchronizedList(mutableListOf<Session>())
    private var pendingStartPath: String? = null

    /** Keep at least one interactive session, like MT Manager's terminal. */
    fun connect(startPath: String? = null) {
        pendingStartPath = startPath ?: "/sdcard"
        if (sessions.isEmpty()) newSession()
    }

    private fun nextFreeId(): Long {
        val used = synchronized(sessions) { sessions.map { it.id }.toSet() }
        return generateSequence(1L) { it + 1 }.first { it !in used }
    }

    @Synchronized
    fun newSession(command: String? = null) {
        if (sessions.size >= 4) {
            _state.update { it.copy(message = "最多 4 个会话") }
            return
        }
        val startPath = if (command == null) pendingStartPath else null
        if (command == null) pendingStartPath = null
        val id = nextFreeId()
        val emulator = TerminalEmulator(COLUMNS) { publishFrame(id) }
        // Reserve the session synchronously so rapid taps cannot spawn duplicates.
        val session = Session(id = id, emulator = emulator, transient = command != null)
        synchronized(sessions) { sessions.add(session) }
        publishState(id)

        viewModelScope.launch(Dispatchers.IO) {
            val interactiveSu = command?.startsWith(INTERACTIVE_SU_PREFIX) == true
            val initialInput = when {
                command == null -> null
                interactiveSu -> command.removePrefix(INTERACTIVE_SU_PREFIX)
                else -> command
            }
            val args = when {
                interactiveSu -> arrayOf("/system/bin/su") // real interactive root shell
                command != null -> arrayOf("/system/bin/sh")
                else -> arrayOf("/system/bin/sh")
            }
            val handle = Pty.open(args, COLUMNS, ROWS)
            if (handle == 0L) {
                finishSession(id)
                _state.update { it.copy(message = "Failed to open PTY") }
                return@launch
            }
            session.handle = handle
            // Show the working directory in the prompt instead of only $ / #.
            Pty.send(
                handle,
                if (interactiveSu) "PS1='\$PWD # '; clear\r" else "PS1='\$PWD \\\$ '; clear\r",
            )
            if (!initialInput.isNullOrBlank()) {
                if (interactiveSu) delay(300) // let `su` finish opening its shell
                Pty.send(handle, initialInput + "\r")
            }
            if (!startPath.isNullOrBlank()) {
                val quoted = "'" + startPath.replace("'", "'\\''") + "'"
                Pty.send(handle, "cd $quoted\r")
            }
            session.reader = launch(Dispatchers.IO) {
                while (isActive && session.handle != 0L) {
                    val bytes = Pty.nativeRead(session.handle) ?: break
                    if (bytes.isNotEmpty()) emulator.write(bytes)
                    delay(10)
                }
                // Command/shell has exited: keep its output briefly, then close the session.
                if (session.transient) delay(1500)
                finishSession(id)
            }
        }
    }

    /** Execute a script command in a fresh session that disappears when it exits. */
    @Synchronized
    fun runCommand(command: String) {
        val now = System.currentTimeMillis()
        // Pager may keep two TerminalPage instances alive; both collect the command bus.
        if (command == lastCommand && now - lastCommandAt < 800L) return
        lastCommand = command
        lastCommandAt = now
        newSession(command = command)
    }

    private var lastCommand = ""
    private var lastCommandAt = 0L

    @Synchronized
    fun closeSession(id: Long) = finishSession(id)

    @Synchronized
    private fun finishSession(id: Long) {
        val session = synchronized(sessions) { sessions.firstOrNull { it.id == id } } ?: return
        session.reader?.cancel()
        if (session.handle != 0L) Pty.nativeClose(session.handle)
        synchronized(sessions) { sessions.remove(session) }
        publishState()
    }

    @Synchronized
    fun switchSession(id: Long) {
        if (synchronized(sessions) { sessions.any { it.id == id } }) publishState(id)
    }

    /** Forward IME output as keystrokes; the text field keeps its own local draft. */
    fun type(text: String) {
        if (text.isEmpty()) return
        activeSession()?.let { session -> if (session.handle != 0L) Pty.send(session.handle, text) }
    }

    fun enter() {
        activeSession()?.let { session -> if (session.handle != 0L) Pty.send(session.handle, "\r") }
    }

    fun control(sequence: String) {
        activeSession()?.let { session -> if (session.handle != 0L) Pty.send(session.handle, sequence) }
    }

    fun clear() {
        activeSession()?.emulator?.write("\u001B[2J\u001B[H".toByteArray(Charsets.UTF_8))
    }

    fun resize(columns: Int, rows: Int) {
        val cols = columns.coerceIn(20, 240)
        val lineRows = rows.coerceIn(4, 120)
        sessions.forEach { it.emulator.resize(cols) }
        activeSession()?.let { session ->
            viewModelScope.launch(Dispatchers.IO) {
                Pty.nativeResize(session.handle, cols, lineRows)
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun activeSession(): Session? {
        val activeId = _state.value.activeSessionId
        return synchronized(sessions) {
            sessions.firstOrNull { it.id == activeId } ?: sessions.lastOrNull()
        }
    }

    private fun publishFrame(id: Long) {
        if (_state.value.activeSessionId == id) publishState(id)
    }

    @Synchronized
    private fun publishState(preferredId: Long? = null) {
        val snapshot = synchronized(sessions) { sessions.toList() }
        val activeId = preferredId
            ?: _state.value.activeSessionId?.takeIf { id -> snapshot.any { it.id == id } }
            ?: snapshot.lastOrNull()?.id
        val frame = snapshot.firstOrNull { it.id == activeId }?.emulator?.frame()
            ?: TerminalFrame(emptyList(), 0, 0, COLUMNS)
        _state.update {
            it.copy(
                frame = frame,
                sessions = snapshot.map { session -> session.id },
                activeSessionId = activeId,
                connected = activeId != null,
            )
        }
    }

    override fun onCleared() {
        synchronized(sessions) {
            sessions.forEach { session ->
                session.reader?.cancel()
                Pty.nativeClose(session.handle)
            }
            sessions.clear()
        }
        super.onCleared()
    }

    private companion object {
        const val COLUMNS = 100
        const val ROWS = 30
    }
}
