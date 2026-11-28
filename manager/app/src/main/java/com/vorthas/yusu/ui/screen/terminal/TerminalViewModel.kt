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
        val pipeSession: Boolean = false,
        var finished: Boolean = false,
    )

    private val sessions = Collections.synchronizedList(mutableListOf<Session>())
    private var pendingStartPath: String? = null

    private fun String.shellQuote(): String = "'" + replace("'", "'\\''") + "'"

    /** Keep at least one interactive session, like MT Manager's terminal. */
    fun connect(startPath: String? = null) {
        pendingStartPath = startPath ?: DEFAULT_START_PATH
        if (sessions.isEmpty()) newSession()
    }

    private fun nextFreeId(): Long {
        val used = synchronized(sessions) { sessions.map { it.id }.toSet() }
        return generateSequence(1L) { it + 1 }.first { it !in used }
    }

    @Synchronized
    fun newSession(command: String? = null) {
        if (sessions.size >= MAX_SESSIONS) {
            // MT-style: never block a new execution. Recycle the oldest slot,
            // preferring sessions whose process has already exited.
            val victim = synchronized(sessions) {
                sessions.firstOrNull { it.finished }
                    ?: sessions.firstOrNull { it.transient }
                    ?: sessions.firstOrNull()
            }
            if (victim != null) finishSession(victim.id)
        }
        val startPath = if (command == null) pendingStartPath else null
        if (command == null) pendingStartPath = null
        val id = nextFreeId()
        val emulator = TerminalEmulator(COLUMNS) { publishFrame(id) }
        val pipe = parsePipeCommand(command)
        // Reserve the session synchronously so rapid taps cannot spawn duplicates.
        // Finished sessions stay visible with a hint until Enter closes them.
        val session = Session(
            id = id,
            emulator = emulator,
            transient = command != null,
            pipeSession = pipe != null,
        )
        synchronized(sessions) { sessions.add(session) }
        publishState(id)

        viewModelScope.launch(Dispatchers.IO) {
            val interactiveSu = command?.startsWith(INTERACTIVE_SU_PREFIX) == true
            val initialInput = when {
                command == null -> null
                pipe != null -> pipe.path // read by the launcher shell after echo is off
                interactiveSu -> command.removePrefix(INTERACTIVE_SU_PREFIX)
                else -> command
            }
            val args = when {
                // The launcher reads the path from stdin, so it never enters argv.
                pipe != null -> arrayOf(
                    if (pipe.root) "/system/bin/su" else "/system/bin/sh",
                    "-c",
                    pipeLaunchScript(pipe.binary, pipe.root),
                )
                interactiveSu -> arrayOf("/system/bin/su") // real interactive root shell
                command != null -> arrayOf("/system/bin/sh")
                // Set the working directory before the interactive shell starts, so the
                // initial `cd` is not echoed as the first line of the terminal.
                !startPath.isNullOrBlank() -> arrayOf(
                    "/system/bin/sh",
                    "-c",
                    "cd ${startPath.shellQuote()} && exec /system/bin/sh",
                )
                else -> arrayOf("/system/bin/sh")
            }
            val handle = Pty.open(args, COLUMNS, ROWS)
            if (handle == 0L) {
                finishSession(id)
                _state.update { it.copy(message = "Failed to open PTY") }
                return@launch
            }
            session.handle = handle
            if (pipe == null) {
                // Show the working directory in the prompt instead of only $ / #.
                val prompt = if (interactiveSu) "PS1='\$PWD # '" else "PS1='\$PWD \\\$ '"
                Pty.send(handle, "$prompt; clear\r")
            }
            if (!initialInput.isNullOrBlank()) {
                if (pipe?.root == true || interactiveSu) delay(300) // let `su` finish opening its shell
                // The pipe launcher disables echo before reading the path. Do NOT send
                // any extra line here: interactive scripts/binaries would consume it.
                if (pipe != null) delay(200)
                Pty.send(handle, initialInput + "\r")
            }
            session.reader = launch(Dispatchers.IO) {
                while (isActive && session.handle != 0L) {
                    val bytes = Pty.nativeRead(session.handle)
                    if (bytes == null) break
                    if (bytes.isNotEmpty()) emulator.write(bytes)
                    delay(10)
                }
                markFinished(id)
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
    private fun markFinished(id: Long) {
        val session = synchronized(sessions) { sessions.firstOrNull { it.id == id } } ?: return
        if (session.finished) return
        session.finished = true
        // MT-style: every session that exits keeps its output and waits for Enter.
        session.emulator.write("\r\n[进程已结束，按回车关闭会话]\r\n".toByteArray(Charsets.UTF_8))
        publishState(id)
    }

    @Synchronized
    fun switchSession(id: Long) {
        if (synchronized(sessions) { sessions.any { it.id == id } }) publishState(id)
    }

    /** Forward IME output as keystrokes; the text field keeps its own local draft. */
    fun type(text: String) {
        if (text.isEmpty()) return
        activeSession()?.let { session ->
            if (!session.finished && session.handle != 0L) Pty.send(session.handle, text)
        }
    }

    private var lastFinishedCloseAt = 0L

    fun enter() {
        val session = activeSession() ?: return
        if (session.finished) {
            // An IME may report the same Enter twice; one close/navigation is enough.
            val now = System.currentTimeMillis()
            if (now - lastFinishedCloseAt < 400L) return
            lastFinishedCloseAt = now
            finishSession(session.id)
            if (session.transient) TerminalCommandBus.notifyExecutionClosed()
            return
        }
        if (session.handle != 0L) Pty.send(session.handle, "\r")
    }

    fun control(sequence: String) {
        activeSession()?.let { session ->
            if (!session.finished && session.handle != 0L) Pty.send(session.handle, sequence)
        }
    }

    fun clear() {
        val session = activeSession() ?: return
        // Clear locally for instant feedback, then run `clear` in the shell so the
        // prompt is redrawn at row 0. Ctrl+L (readline redraw) emits an extra newline
        // on this shell, which left a blank first line after clearing.
        session.emulator.write("\u001B[2J\u001B[H".toByteArray(Charsets.UTF_8))
        if (session.handle != 0L) Pty.send(session.handle, "clear\r")
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
        const val DEFAULT_START_PATH = "/storage/emulated/0"
        const val MAX_SESSIONS = 4
    }
}
