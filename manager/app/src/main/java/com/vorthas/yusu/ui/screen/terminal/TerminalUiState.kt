package com.vorthas.yusu.ui.screen.terminal

data class TerminalUiState(
    val frame: TerminalFrame = TerminalFrame(emptyList(), 0, 0, 100),
    val sessions: List<Long> = emptyList(),
    val activeSessionId: Long? = null,
    val connected: Boolean = false,
    val message: String? = null,
)

data class TerminalActions(
    val onBack: () -> Unit,
    val onType: (String) -> Unit,
    val onEnter: () -> Unit,
    val onControl: (String) -> Unit,
    val onClear: () -> Unit,
    val onNewSession: () -> Unit,
    val onCloseSession: (Long) -> Unit,
    val onSwitchSession: (Long) -> Unit,
    val onCopyOutput: () -> Unit,
    val onPaste: () -> Unit,
    val onMessageShown: () -> Unit,
)
