package com.vorthas.yusu.ui.screen.terminal

/**
 * Pipe execution: the target path is read from the terminal stdin after echo has
 * been disabled, so it appears neither in argv nor on screen.
 *
 * Scripts are sourced from a separate file descriptor (fd 3) while stdin stays
 * attached to the PTY. This keeps interactive scripts usable and gives the script a
 * readable $0 (fd 4) for self-extracting archives that read their own payload.
 * ELF binaries run directly with the PTY attached. The launcher exits with the
 * target so the terminal can offer Enter-to-close for finished sessions.
 */
internal data class PipeCommand(val path: String, val root: Boolean, val binary: Boolean = false)

internal fun parsePipeCommand(command: String?): PipeCommand? {
    if (command == null) return null
    when {
        command.startsWith(PIPE_EXEC_BIN_ROOT_PREFIX) ->
            return PipeCommand(command.removePrefix(PIPE_EXEC_BIN_ROOT_PREFIX), root = true, binary = true)
        command.startsWith(PIPE_EXEC_BIN_PREFIX) ->
            return PipeCommand(command.removePrefix(PIPE_EXEC_BIN_PREFIX), root = false, binary = true)
        command.startsWith(PIPE_EXEC_ROOT_PREFIX) ->
            return PipeCommand(command.removePrefix(PIPE_EXEC_ROOT_PREFIX), root = true)
        command.startsWith(PIPE_EXEC_PREFIX) ->
            return PipeCommand(command.removePrefix(PIPE_EXEC_PREFIX), root = false)
    }
    return null
}

internal fun pipeLaunchScript(binary: Boolean, root: Boolean): String {
    // Bare `$` in PS1 avoids the extra ":" that mksh renders for "\$" when PS1
    // is imported from the environment.
    val prompt = if (root) "#" else "$"
    val invoke = if (binary) {
        "chmod 755 \"\$__yusu_pipe_path\" 2>/dev/null; \"\$__yusu_pipe_path\""
    } else {
        // fd 3 feeds the sourced script; fd 4 gives the script a readable $0.
        "sh -c '(. /dev/fd/3)' /proc/self/fd/4 3< \"\$__yusu_pipe_path\" 4< \"\$__yusu_pipe_path\""
    }
    // No trailing exec here: execution sessions end with the target so the
    // terminal can show the "press Enter to close" hint (MT Manager behaviour).
    return "stty -echo 2>/dev/null; read -r __yusu_pipe_path; " +
        "cd \"\$(dirname \"\$__yusu_pipe_path\")\"; " +
        "stty echo 2>/dev/null; export PS1='\$PWD $prompt '; " +
        invoke
}
