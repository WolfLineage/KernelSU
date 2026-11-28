package com.vorthas.yusu.ui.screen.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import com.vorthas.yusu.ui.component.material.expressiveTopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.vorthas.yusu.R
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

/**
 * Bottom inset for the terminal: when the IME is up it covers the floating bottom
 * bar, so use max(bar space, keyboard) instead of adding them — otherwise a large
 * empty band appears between the toolbar and the keyboard.
 */
@Composable
private fun terminalBottomPadding(bottomPadding: Dp): Dp {
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val barBottom = if (bottomPadding == Dp.Unspecified) 0.dp else bottomPadding
    return maxOf(barBottom, imeBottom)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalScreenMaterial(
    state: TerminalUiState,
    actions: TerminalActions,
    onResize: (Int, Int) -> Unit,
    bottomPadding: Dp = Dp.Unspecified,
    isVisible: Boolean = true,
) {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.terminal)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = actions.onClear) { Icon(Icons.Filled.Delete, null) }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        }
    ) { padding ->
        TerminalContent(
            state,
            actions,
            onResize,
            Modifier
                .padding(padding)
                .padding(bottom = terminalBottomPadding(bottomPadding)),
            isVisible,
        )
    }
}

@Composable
internal fun TerminalScreenMiuix(
    state: TerminalUiState,
    actions: TerminalActions,
    onResize: (Int, Int) -> Unit,
    bottomPadding: Dp = Dp.Unspecified,
    isVisible: Boolean = true,
) {
    MiuixScaffold(
        topBar = {
            // Same Miuix TopAppBar component other pages use, so title size/position match.
            MiuixTopAppBar(
                color = MiuixTheme.colorScheme.surface,
                title = stringResource(R.string.terminal),
                actions = {
                    IconButton(onClick = actions.onClear) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空", tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
            )
        },
        popupHost = {},
    ) { padding ->
        TerminalContent(
            state,
            actions,
            onResize,
            Modifier
                .padding(padding)
                .padding(bottom = terminalBottomPadding(bottomPadding)),
            isVisible,
        )
    }
}

@Composable
private fun TerminalContent(
    state: TerminalUiState,
    actions: TerminalActions,
    onResize: (Int, Int) -> Unit,
    modifier: Modifier,
    isVisible: Boolean = true,
) {
    val listState = rememberLazyListState()
    // Measured from the actual output viewport, not from the whole page: the session
    // chips and the toolbar also occupy height, so deriving rows from the page height
    // left the last PTY line behind the IME.
    var outputHeightPx by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val rootView = LocalView.current
    val imeManager = LocalContext.current.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val showIme: () -> Unit = {
        focusRequester.requestFocus()
        keyboard?.show()
        runCatching { imeManager.showSoftInput(rootView, InputMethodManager.SHOW_FORCED) }
    }
    var confirmClose by remember { mutableStateOf<Long?>(null) }
    // A blinking block at the emulator's cursor position makes the current input
    // point visible instead of relying on the shell prompt alone.
    var cursorOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorOn = !cursorOn
        }
    }
    val activeId = state.activeSessionId
    // The hidden field keeps a local draft of what has already been forwarded, so
    // candidate/composition snapshots are not re-sent. Hardware keys are consumed by
    // onKeyEvent and never reach the field; IME commits arrive via onValueChange.
    val drafts = remember { mutableStateMapOf<Long, String>() }
    val draft = activeId?.let { drafts[it] }.orEmpty()
    val setDraft: (String) -> Unit = { value -> activeId?.let { drafts[it] = value } }
    // One Enter can be reported by an IME as commitText("…\n"), KEYCODE_ENTER and
    // KeyboardActions.onSend. Only the IME-driven paths are debounced; the toolbar
    // button remains immediate.
    var lastImeEnterAt by remember { mutableLongStateOf(0L) }
    // commitText("...\n") can arrive together with KeyboardActions.onSend; debounce
    // only that path. Explicit IME Enter keys must always work (e.g. closing a
    // finished execution session).
    val enterFromCommit: () -> Unit = {
        val now = SystemClock.elapsedRealtime()
        if (now - lastImeEnterAt >= 200L) {
            lastImeEnterAt = now
            actions.onEnter()
        }
        setDraft("")
    }
    val enterDirect: () -> Unit = {
        actions.onEnter()
        setDraft("")
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) keyboard?.hide()
    }
    LaunchedEffect(Unit) {
        TerminalCommandBus.focusRequests.collect { /* intentionally no auto IME */ }
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Convert sp to real pixels; using raw 18f/7.4f made the PTY believe it had a
        // ~170x150 screen, so full-screen apps such as top could not lay out correctly.
        val cellWidth = with(density) { 7.2.sp.toPx() }
        val cellHeight = with(density) { 18.sp.toPx() }
        val columns = with(density) { (maxWidth.toPx() / cellWidth).toInt() }.coerceIn(20, 240)
        // Size the PTY from the measured output viewport. The viewport itself already
        // shrinks when the IME opens (via terminalBottomPadding), so subtracting the
        // IME here as well used to make the PTY disagree with what was on screen.
        val rows = (if (outputHeightPx > 0) (outputHeightPx / cellHeight).toInt()
            else with(density) { (maxHeight.toPx() / cellHeight).toInt() }).coerceIn(4, 120)
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        LaunchedEffect(columns, rows, state.activeSessionId) { onResize(columns, rows) }

        // Jump straight to the newest line; an animated scroll can still be mid-flight
        // when the IME finishes its own animation, leaving a sliver of the last line
        // hidden behind the keyboard.
        LaunchedEffect(rows, state.frame.rows.size, imeBottom) {
            if (state.frame.rows.isNotEmpty()) listState.scrollToItem(state.frame.rows.lastIndex)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.sessions.forEach { id ->
                    FilterChip(
                        selected = id == state.activeSessionId,
                        onClick = { actions.onSwitchSession(id) },
                        label = { Text("会话 $id", fontSize = 11.sp) },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                TextButton(onClick = actions.onNewSession, modifier = Modifier.height(32.dp)) {
                    Text("+", fontSize = 15.sp)
                }
            }
            // Invisible IME bridge: the terminal canvas itself handles taps, while this field
            // receives hardware/IME keystrokes. There is no separate visible input box.
            BasicTextField(
                value = draft,
                onValueChange = { new ->
                    // Forward only the delta from the local draft. An IME clipboard paste
                    // commits the whole clip at once, so all inserted characters must be
                    // sent; a candidate committed together with "\n" must NOT be sent
                    // twice (it was already forwarded while composing).
                    // Some IMEs commit CR, some LF, some both; treat either as Enter.
                    val hasEnter = new.any { it == '\n' || it == '\r' }
                    val committed = new.filter { it != '\n' && it != '\r' }
                    val delta = imeTextDelta(draft, committed)
                    repeat(delta.deleteCount) { actions.onType("\u007F") }
                    if (delta.insertText.isNotEmpty()) actions.onType(delta.insertText)
                    if (hasEnter) enterFromCommit() else setDraft(new)
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        when (native.keyCode) {
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                enterDirect(); true
                            }
                            android.view.KeyEvent.KEYCODE_DEL -> { actions.onType("\u007F"); true }
                            android.view.KeyEvent.KEYCODE_TAB -> { actions.onType("\t"); true }
                            android.view.KeyEvent.KEYCODE_ESCAPE -> { actions.onType("\u001B"); true }
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> { actions.onType("\u001B[A"); true }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { actions.onType("\u001B[B"); true }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { actions.onType("\u001B[D"); true }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { actions.onType("\u001B[C"); true }
                            else -> {
                                val unicode = native.unicodeChar
                                if (unicode != 0 && native.metaState and
                                    (android.view.KeyEvent.META_CTRL_ON or
                                        android.view.KeyEvent.META_ALT_ON or
                                        android.view.KeyEvent.META_META_ON) == 0
                                ) {
                                    val ch = unicode.toChar()
                                    actions.onType(ch.toString())
                                    // Mirroring the char into the draft makes the
                                    // subsequent TextField onValueChange a no-op diff.
                                    setDraft(draft + ch)
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    },
                singleLine = false,
                textStyle = TextStyle(color = Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                    // Default gives IMEs a normal Enter/newline key. Send/Done is
                    // greyed out by some IMEs (e.g. WeType) when the draft is empty.
                    imeAction = ImeAction.Default,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { enterDirect() },
                    onDone = { enterDirect() },
                    onGo = { enterDirect() },
                    onSearch = { enterDirect() },
                ),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { outputHeightPx = it.height }
                    .background(Color(0xFF102238))
                    .pointerInput(Unit) {
                        detectTapGestures { showIme() }
                    }
                    .padding(horizontal = 4.dp),
            ) {
                itemsIndexed(state.frame.rows) { rowIndex, cells ->
                    Text(
                        text = renderCells(
                            cells = cells,
                            defaultFg = Color(0xFFDCEBFA),
                            cursorCol = if (rowIndex == state.frame.cursorRow) state.frame.cursorCol else null,
                            cursorOn = cursorOn,
                        ),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 2.dp),
            ) {
                state.activeSessionId?.let { active ->
                    TextButton(onClick = { confirmClose = active }, modifier = Modifier.height(34.dp)) {
                        Text("×", fontSize = 14.sp)
                    }
                }
                TextButton(
                    onClick = showIme,
                    modifier = Modifier.height(34.dp).padding(end = 2.dp),
                ) { Text("键盘", fontSize = 12.sp) }
                TextButton(
                    onClick = { actions.onEnter(); setDraft("") },
                    modifier = Modifier.height(34.dp).padding(end = 2.dp),
                ) { Text(stringResource(R.string.term_enter), fontSize = 12.sp) }
                TextButton(
                    onClick = actions.onCopyOutput,
                    modifier = Modifier.height(34.dp).padding(end = 2.dp),
                ) { Text("复制", fontSize = 12.sp) }
                TextButton(
                    onClick = actions.onPaste,
                    modifier = Modifier.height(34.dp).padding(end = 2.dp),
                ) { Text("粘贴", fontSize = 12.sp) }
                listOf(
                    stringResource(R.string.term_ctrl_c) to "\u0003",
                    "^D" to "\u0004",
                    stringResource(R.string.term_esc) to "\u001B",
                    stringResource(R.string.term_tab) to "\t",
                    "Home" to "\u001B[H",
                    "End" to "\u001B[F",
                    "↑" to "\u001B[A",
                    "↓" to "\u001B[B",
                    "←" to "\u001B[D",
                    "→" to "\u001B[C",
                ).forEach { (label, sequence) ->
                    TextButton(
                        onClick = { actions.onControl(sequence) },
                        modifier = Modifier.height(34.dp).padding(end = 2.dp),
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
            confirmClose?.let { id ->
                AlertDialog(
                    onDismissRequest = { confirmClose = null },
                    containerColor = if (LocalUiMode.current == UiMode.Miuix) Color(0xFF1C1C1E)
                        else MaterialTheme.colorScheme.surface,
                    titleContentColor = toolTextColor(),
                    textContentColor = toolTextColor(),
                    title = { Text("关闭会话") },
                    text = { Text("将终止此会话及其子进程。") },
                    confirmButton = {
                        TextButton(onClick = {
                            actions.onCloseSession(id)
                            confirmClose = null
                        }) { Text("关闭") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClose = null }) { Text("取消") }
                    },
                )
            }
        }
    }
}

@Composable
private fun toolMutedColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    UiMode.Material -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun toolTextColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onBackground
    UiMode.Material -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun renderCells(
    cells: List<TerminalCell>,
    defaultFg: Color,
    cursorCol: Int? = null,
    cursorOn: Boolean = true,
): AnnotatedString = buildAnnotatedString {
    cells.forEachIndexed { index, cell ->
        val isCursor = cursorOn && index == cursorCol
        val baseFg = cell.fg?.let { Color(it) } ?: defaultFg
        val fg = if (isCursor) Color(0xFF102238)
            else if (cell.reverse) (cell.bg?.let { Color(it) } ?: Color.Black) else baseFg
        val bg = if (isCursor) Color(0xFFDCEBFA)
            else if (cell.reverse) baseFg else (cell.bg?.let { Color(it) } ?: Color.Transparent)
        withStyle(
            SpanStyle(
                color = fg,
                background = bg,
                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
            )
        ) { append(if (isCursor) ' ' else cell.char) }
    }
    if (cursorOn && cursorCol != null && cursorCol >= cells.size) {
        withStyle(SpanStyle(color = Color(0xFF102238), background = Color(0xFFDCEBFA))) { append('█') }
    }
}

@Composable
fun TerminalPage(
    startPath: String? = null,
    bottomPadding: Dp = Dp.Unspecified,
    isVisible: Boolean = true,
) {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val context = LocalContext.current
    val viewModel = viewModel<TerminalViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.connect(startPath) }
    LaunchedEffect(Unit) {
        TerminalCommandBus.commands.collect { command ->
            viewModel.runCommand(command)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    val actions = TerminalActions(
        onBack = { navigator.pop() },
        onType = viewModel::type,
        onEnter = viewModel::enter,
        onControl = viewModel::control,
        onClear = viewModel::clear,
        onNewSession = viewModel::newSession,
        onCloseSession = viewModel::closeSession,
        onSwitchSession = viewModel::switchSession,
        onCopyOutput = {
            val text = state.frame.rows.joinToString("\n") { row ->
                row.joinToString("") { cell -> cell.char.toString() }
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text.takeLast(200000)))
            Toast.makeText(context, "已复制终端输出", Toast.LENGTH_SHORT).show()
        },
        onPaste = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.take(131072)?.let { viewModel.type(it) }
        },
        onMessageShown = viewModel::messageShown,
    )

    when (uiMode) {
        UiMode.Miuix -> TerminalScreenMiuix(state, actions, viewModel::resize, bottomPadding, isVisible)
        UiMode.Material -> TerminalScreenMaterial(state, actions, viewModel::resize, bottomPadding, isVisible)
    }
}
