package com.vorthas.yusu.ui.screen.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
            TopAppBar(
                title = {
                    Text(stringResource(R.string.terminal), style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = actions.onClear) { Icon(Icons.Filled.Delete, null) }
                }
            )
        }
    ) { padding ->
        TerminalContent(
            state,
            actions,
            onResize,
            Modifier
                .padding(padding)
                .padding(bottom = if (bottomPadding == Dp.Unspecified) 0.dp else bottomPadding)
                .imePadding(),
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
            Row(
                modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.terminal),
                    style = MaterialTheme.typography.titleMedium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        popupHost = {},
    ) { padding ->
        TerminalContent(
            state,
            actions,
            onResize,
            Modifier
                .padding(padding)
                .padding(bottom = if (bottomPadding == Dp.Unspecified) 0.dp else bottomPadding)
                .imePadding(),
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
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val rootView = LocalView.current
    val imeManager = LocalContext.current.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val showIme: () -> Unit = {
        focusRequester.requestFocus()
        keyboard?.show()
        runCatching { imeManager.showSoftInput(rootView, InputMethodManager.SHOW_FORCED) }
    }
    val drafts = remember { mutableStateMapOf<Long, String>() }
    var confirmClose by remember { mutableStateOf<Long?>(null) }
    val activeId = state.activeSessionId
    val draft = activeId?.let { drafts[it] }.orEmpty()
    val setDraft: (String) -> Unit = { value -> activeId?.let { drafts[it] = value } }
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
        val rows = with(density) { (maxHeight.toPx() / cellHeight).toInt() }.coerceIn(4, 120)
        LaunchedEffect(columns, rows, state.activeSessionId) { onResize(columns, rows) }

        LaunchedEffect(state.frame.rows.size) {
            if (state.frame.rows.isNotEmpty()) listState.animateScrollToItem(state.frame.rows.lastIndex)
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
                    if (new.contains('\n')) {
                        actions.onEnter()
                        setDraft("")
                    } else {
                        when {
                            new.length > draft.length ->
                                new.lastOrNull()?.let { actions.onType(it.toString()) }
                            new.length < draft.length -> actions.onType("\u007F")
                        }
                        setDraft(new)
                    }
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val modifierMask = android.view.KeyEvent.META_CTRL_ON or
                            android.view.KeyEvent.META_ALT_ON or
                            android.view.KeyEvent.META_META_ON
                        when (native.keyCode) {
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                actions.onEnter(); setDraft(""); true
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
                                if (unicode != 0 && native.metaState and modifierMask == 0) {
                                    actions.onType(unicode.toChar().toString())
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
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { actions.onEnter(); setDraft("") },
                    onDone = { actions.onEnter(); setDraft("") },
                    onGo = { actions.onEnter(); setDraft("") },
                    onSearch = { actions.onEnter(); setDraft("") },
                ),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF102238))
                    .pointerInput(Unit) {
                        detectTapGestures { showIme() }
                    }
                    .padding(horizontal = 4.dp),
            ) {
                itemsIndexed(state.frame.rows) { _, cells ->
                    Text(
                        text = renderCells(cells, Color(0xFFDCEBFA)),
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
private fun renderCells(cells: List<TerminalCell>, defaultFg: Color): AnnotatedString = buildAnnotatedString {
    cells.forEach { cell ->
        val baseFg = cell.fg?.let { Color(it) } ?: defaultFg
        val fg = if (cell.reverse) (cell.bg?.let { Color(it) } ?: Color.Black) else baseFg
        val bg = if (cell.reverse) baseFg else (cell.bg?.let { Color(it) } ?: Color.Transparent)
        withStyle(
            SpanStyle(
                color = fg,
                background = bg,
                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
            )
        ) { append(cell.char) }
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
