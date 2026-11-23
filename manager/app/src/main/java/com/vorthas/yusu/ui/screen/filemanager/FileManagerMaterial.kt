package com.vorthas.yusu.ui.screen.filemanager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.vorthas.yusu.R
import com.vorthas.yusu.data.filemanager.FileEntry
import com.vorthas.yusu.data.filemanager.FileManagerRepository
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.screen.terminal.INTERACTIVE_SU_PREFIX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

internal enum class DialogKind { NEW_FOLDER, RENAME }

private fun isScriptEntry(entry: FileEntry): Boolean {
    val name = entry.name.lowercase()
    return !entry.isDirectory && (
        name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".py") ||
            name.endsWith(".js") || name.endsWith(".lua") || name.endsWith(".so") ||
            entry.mode.contains('x') || isElfFile(entry.path)
        )
}

private fun isApkEntry(entry: FileEntry): Boolean {
    if (entry.isDirectory) return false
    val name = entry.name
    if (name.endsWith(".apk", true) || name.endsWith(".apks", true)) return true
    // IM/ downloader apps append a numeric suffix to duplicates: foo.apk.1, foo.apk.2 …
    return name.contains(Regex("\\.apk\\.\\d+$", RegexOption.IGNORE_CASE))
}

private fun isElfFile(path: String): Boolean = runCatching {
    FileManagerRepository.readHeader(path).contentEquals(
        byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
    )
}.getOrDefault(false)

private fun scriptCommand(path: String, root: Boolean, elf: Boolean, interactive: Boolean): String {
    val escaped = path.replace("\\", "\\\\").replace("\"", "\\\"")
    // Run from the script's own directory: tools such as run.sh resolve companion
    // binaries with relative paths, which is why MT Manager works but a plain
    // `sh /sdcard/run.sh` from / fails.
    val invocation = if (elf) "\"$escaped\"" else "sh \"$escaped\""
    val base = "cd \"$(dirname \"$escaped\")\"; chmod 755 \"$escaped\"; $invocation"
    return when {
        interactive && root -> INTERACTIVE_SU_PREFIX + base
        interactive -> base                  // stay as the app user (can read its own files)
        root -> "su -c '$base'"
        else -> base
    }
}

@Composable
internal fun dialogSurface(): Color = when (LocalUiMode.current) {
    // Miuix mode does NOT wrap content in a MaterialTheme, so
    // MaterialTheme.colorScheme would return the default light scheme (white)
    // regardless of the app's dark setting. Use the miuix dialog convention
    // (miuix DialogDefaults.backgroundColor = Colors.background) instead.
    UiMode.Miuix -> MiuixTheme.colorScheme.background
    UiMode.Material -> MaterialTheme.colorScheme.surface
}

@Composable
internal fun dialogText(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onBackground
    UiMode.Material -> MaterialTheme.colorScheme.onSurface
}

/**
 * Themed colors for dialog text fields. Without this, Material3 fields fall back to
 * the *un-themed* default scheme (Miuix mode wraps no MaterialTheme), which rendered
 * black label/text on dark dialogs — the "部分字体是黑色" report.
 */
@Composable
internal fun dialogFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = dialogText(),
    unfocusedTextColor = dialogText(),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = toolPrimaryColor(),
    unfocusedBorderColor = dialogText().copy(alpha = 0.35f),
    focusedLabelColor = toolPrimaryColor(),
    unfocusedLabelColor = dialogText().copy(alpha = 0.6f),
    cursorColor = toolPrimaryColor(),
)

@Composable
private fun toolSurfaceColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.surface
    UiMode.Material -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun toolTextColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onBackground
    UiMode.Material -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun toolPrimaryColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.primary
    UiMode.Material -> MaterialTheme.colorScheme.primary
}

@Composable
private fun toolMutedColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    UiMode.Material -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileManagerScreenMaterial(
    state: FileManagerUiState,
    actions: FileManagerActions,
    bottomPadding: Dp = Dp.Unspecified,
    onRunScript: (String) -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<DialogKind?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var newFileMode by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.file_manager),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = actions.onBack, modifier = Modifier.width(48.dp)) {
                        Text("←", fontSize = 18.sp)
                    }
                },
                actions = {
                    TopTool("↶", state.canBack.getOrNull(state.activePane) == true) {
                        actions.onPaneBack(state.activePane)
                    }
                    TopTool("↷", state.canForward.getOrNull(state.activePane) == true) {
                        actions.onPaneForward(state.activePane)
                    }
                    TopTool("⇄", true, actions.onSwap)
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(bottom = if (bottomPadding == Dp.Unspecified) 0.dp else bottomPadding)
                .fillMaxSize(),
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val compact = maxWidth < 600.dp
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Child rows handle file swipes; consume here (after the child)
                            // so the parent pager does not flip, while header/footer still can.
                            detectHorizontalDragGestures { change, _ -> change.consume() }
                        },
                ) {
                    PaneMaterial(state, actions, 0, Modifier.weight(1f), compact, onRunScript)
                    HorizontalDivider(Modifier.width(1.dp).fillMaxHeight())
                    PaneMaterial(state, actions, 1, Modifier.weight(1f), compact, onRunScript)
                }
            }
            ActionBarMaterial(state, actions, showDialog = { dialog = it }, onSearch = { searchOpen = true })
        }
    }

    if (searchOpen) {
        SearchDialog(
            rootPath = state.panes.getOrNull(state.activePane)?.path ?: "/",
            onDismiss = { searchOpen = false },
            onNavigate = { path -> actions.onNavigate(state.activePane, path) },
            onOpenFile = { path ->
                val lower = path.lowercase()
                if (lower.endsWith(".apk") || lower.endsWith(".apks")) {
                    // Don't install straight from the result row: jump to the APK's
                    // parent folder so the user opens it through the full install dialog.
                    actions.onNavigate(state.activePane, FileManagerRepository.parent(path))
                } else {
                    actions.onOpenEditor(path)
                }
            },
        )
    }

    val currentDialog = dialog
    if (currentDialog != null) {
        AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = dialogSurface(),
            titleContentColor = dialogText(),
            textContentColor = dialogText(),
            title = {
                Text(
                    stringResource(
                        when (currentDialog) {
                            DialogKind.NEW_FOLDER -> R.string.fm_new_target
                            else -> R.string.fm_rename
                        }
                    )
                )
            },
            text = {
                Column {
                    if (currentDialog == DialogKind.NEW_FOLDER) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = newFileMode, onCheckedChange = { newFileMode = it })
                            Text("新建文件（关闭则新建文件夹）", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = {
                        Text(
                            stringResource(
                                if (currentDialog == DialogKind.NEW_FOLDER) R.string.fm_folder_name else R.string.fm_new_name
                            )
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = dialogFieldColors(),
                )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (currentDialog) {
                        DialogKind.NEW_FOLDER -> {
                            if (newFileMode) actions.onNewFile(state.activePane, input)
                            else actions.onNewFolder(state.activePane, input)
                        }
                        DialogKind.RENAME -> {
                            state.panes.getOrNull(state.activePane)?.selected?.firstOrNull()?.let {
                                actions.onRename(it, input)
                            }
                        }
                    }
                    input = ""
                    dialog = null
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
internal fun PaneMaterial(
    state: FileManagerUiState,
    actions: FileManagerActions,
    index: Int,
    modifier: Modifier,
    compact: Boolean = false,
    onRunScript: (String) -> Unit = {},
) {
    val pane = state.panes.getOrNull(index) ?: return
    var scriptTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var scriptRoot by remember { mutableStateOf(true) }
    var contextPath by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var chmodDialog by remember { mutableStateOf(false) }
    var chmodInput by remember { mutableStateOf("0755") }
    var chmodRecursive by remember { mutableStateOf(false) }
    var pathDialog by remember { mutableStateOf(false) }
    var swipeAnchor by remember { mutableIntStateOf(-1) }
    // APK install dialog (MT-style): tap or long-press an .apk to open it.
    var apkTarget by remember { mutableStateOf<String?>(null) }
    var apkInfo by remember { mutableStateOf<FileManagerRepository.ApkInfo?>(null) }
    var apkInfoLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(apkTarget) {
        val path = apkTarget
        if (path == null) {
            apkInfo = null
        } else {
            apkInfoLoading = true
            apkInfo = withContext(Dispatchers.IO) {
                FileManagerRepository.apkInfo(path, context.cacheDir, context.packageManager)
            }
            apkInfoLoading = false
        }
    }
    // Folder search (MT-style): find under the pane's current directory.
    var searchOpen by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<String>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val searchScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val refreshHolder = remember { object { var lastRefreshAt = 0L } }
    var pathInput by rememberSaveable(pane.path) { mutableStateOf(pane.path) }
    val breadcrumbs = remember(pane.path) {
        val segments = pane.path.trim('/').split('/').filter { it.isNotEmpty() }
        buildList {
            add("/" to "/")
            var current = ""
            segments.forEach { segment ->
                current += "/$segment"
                add(segment to current)
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(index) {
                // Initial pass: focus follows any touch, including vertical scrolling,
                // AND MT-style pull-to-refresh lives here too. This runs EARLIER than
                // the LazyColumn on purpose: on Android 12+ the stretch overscroll
                // effect consumes overscroll deltas before they ever reach a parent
                // nestedScroll connection, so onPostScroll-based refresh NEVER fires.
                // The refresh engages only when the list is already at the very top
                // and the finger moves down; in every other case nothing is consumed
                // and the LazyColumn scrolls freely (no gesture competition).
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    actions.onActivePane(index)
                    var pulled = 0f
                    var refreshArmed = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null && !change.isConsumed) {
                            val dy = change.positionChange().y
                            // IMPORTANT: arm on CUMULATED distance, not the per-event
                            // delta — a smooth swipe moves ~2px per event and would
                            // never cross the per-event touch slop threshold.
                            if (!refreshArmed) {
                                val atTop = listState.firstVisibleItemIndex == 0 &&
                                    listState.firstVisibleItemScrollOffset == 0
                                if (atTop && dy > 0) {
                                    pulled += dy
                                    if (pulled > viewConfiguration.touchSlop) {
                                        refreshArmed = true
                                        change.consume()
                                    }
                                } else {
                                    pulled = 0f
                                }
                            } else {
                                pulled += dy
                                change.consume()
                                if (pulled > 200f) {
                                    pulled = -1e6f // once per gesture
                                    val now = System.currentTimeMillis()
                                    if (now - refreshHolder.lastRefreshAt > 800L) {
                                        refreshHolder.lastRefreshAt = now
                                        actions.onRefresh()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = pane.path,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = toolTextColor(),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(toolSurfaceColor())
                    .clickable { pathDialog = true }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
        if (!compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            ) {
                breadcrumbs.forEach { (label, fullPath) ->
                    TextButton(
                        onClick = { actions.onNavigate(index, fullPath) },
                        modifier = Modifier.height(28.dp).padding(end = 2.dp),
                    ) { Text(label, fontSize = 11.sp, maxLines = 1) }
                }
            }
        }
        Text(
            text = if (pane.selected.isNotEmpty()) {
                stringResource(R.string.fm_pane, index + 1) + " · " +
                    stringResource(R.string.fm_selected, pane.selected.size)
            } else {
                buildString {
                    append(stringResource(R.string.fm_pane, index + 1))
                    append(" · ")
                    append(if (pane.loading) stringResource(R.string.fm_loading) else stringResource(R.string.fm_items, pane.entries.size))
                    append(" · ${stringResource(when (pane.sort) { FileSortMode.NAME -> R.string.fm_sort_name; FileSortMode.SIZE -> R.string.fm_sort_size; FileSortMode.DATE -> R.string.fm_sort_date })}")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (state.activePane == index) toolPrimaryColor() else toolMutedColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        // Visible refresh feedback: title flips to 加载中 AND this thin bar tracks.
        if (pane.loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp)
                    .height(2.dp),
                color = toolPrimaryColor(),
                trackColor = toolSurfaceColor(),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (pane.path != "/") {
                item(key = "__parent__") {
                    ParentRow(
                        index = index,
                        selectionMode = pane.selected.isNotEmpty(),
                        actions = actions,
                    )
                }
            }
            itemsIndexed(
                pane.entries,
                key = { _, e -> e.path },
                contentType = { _, _ -> "file-row" },
            ) { rowIndex, entry ->
                FileRowMaterial(
                    entry = entry,
                    rowIndex = rowIndex,
                    paneIndex = index,
                    selected = entry.path in pane.selected,
                    selectionMode = pane.selected.isNotEmpty(),
                    active = state.activePane == index,
                    actions = actions,
                    onScript = { path -> scriptTarget = path to isElfFile(path) },
                    onApk = { path -> apkTarget = path },
                    onContext = { contextPath = it },
                    onSwipe = { target ->
                        if (swipeAnchor < 0) {
                            swipeAnchor = target
                            actions.onSelectOnly(index, entry.path)
                        } else {
                            actions.onSelectRange(index, swipeAnchor, target)
                            swipeAnchor = -1
                        }
                    },
                    onClearAnchor = { swipeAnchor = -1 },
                )
            }
        }

        contextPath?.let { path ->
            // Whether the long-pressed target was already part of the current selection.
            // If not, menu actions that operate on the selection must temporarily select
            // just this target before executing — but WITHOUT highlighting it visually
            // while the menu is open.
            val wasSelected = path in pane.selected
            AlertDialog(
                onDismissRequest = { contextPath = null },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text(path.substringAfterLast('/')) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        ContextItem("执行 / 打开") {
                            val lower = path.lowercase()
                            if (lower.endsWith(".apk") || lower.endsWith(".apks")) {
                                apkTarget = path
                            } else {
                                scriptTarget = path to isElfFile(path)
                            }
                            contextPath = null
                        }
                        if (path.lowercase().endsWith(".apk") || path.lowercase().endsWith(".apks")) {
                            ContextItem("安装 APK") { apkTarget = path; contextPath = null }
                        }
                        ContextItem("修改权限") {
                            if (!wasSelected) actions.onSelectOnly(index, path)
                            chmodDialog = true; contextPath = null
                        }
                        ContextItem("重命名") {
                            renameTarget = path
                            renameInput = path.substringAfterLast('/')
                            contextPath = null
                        }
                        ContextItem("复制 → 副窗口") {
                            if (!wasSelected) actions.onSelectOnly(index, path)
                            actions.onTransfer(false); contextPath = null
                        }
                        ContextItem("移动 → 副窗口") {
                            if (!wasSelected) actions.onSelectOnly(index, path)
                            actions.onTransfer(true); contextPath = null
                        }
                        ContextItem("解压") {
                            if (!wasSelected) actions.onSelectOnly(index, path)
                            actions.onExtract(); contextPath = null
                        }
                        ContextItem("编辑") { actions.onOpenEditor(path); contextPath = null }
                        ContextItem("加入多选") { actions.onSelect(index, path); contextPath = null }
                        ContextItem("取消") { contextPath = null }
                    }
                },
                confirmButton = {},
            )
        }

        apkTarget?.let { path ->
            // MT-style APK dialog: package info + root install + extract.
            AlertDialog(
                onDismissRequest = { apkTarget = null },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text(path.substringAfterLast('/')) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (apkInfoLoading) {
                            Text("正在读取 APK 信息…", style = MaterialTheme.typography.bodySmall, color = dialogText())
                        } else {
                            val info = apkInfo
                            if (info != null) {
                                Text("应用：${info.label}", style = MaterialTheme.typography.bodyMedium, color = dialogText())
                                Text("包名：${info.packageName}", style = MaterialTheme.typography.bodySmall, color = dialogText())
                                Text(
                                    "版本：${info.versionName} (${info.versionCode})",
                                    style = MaterialTheme.typography.bodySmall, color = dialogText(),
                                )
                            } else {
                                Text(
                                    "无法解析 APK 信息，仍可尝试安装。",
                                    style = MaterialTheme.typography.bodySmall, color = dialogText(),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        actions.onInstallApk(path)
                        apkTarget = null
                    }) { Text("安装") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            if (!pane.selected.contains(path)) actions.onSelectOnly(index, path)
                            actions.onExtract()
                            apkTarget = null
                        }) { Text("解压到对面") }
                        TextButton(onClick = { apkTarget = null }) { Text(stringResource(android.R.string.cancel)) }
                    }
                },
            )
        }

        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text("重命名") },
                text = {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        colors = dialogFieldColors(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        renameTarget?.let { actions.onRename(it, renameInput) }
                        renameTarget = null
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        if (chmodDialog) {
            AlertDialog(
                onDismissRequest = { chmodDialog = false },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text("修改权限") },
                text = {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("0755", "0644", "0700", "0600", "0777").forEach {
                                TextButton(onClick = { chmodInput = it }) { Text(it, fontSize = 11.sp) }
                            }
                        }
                        OutlinedTextField(
                            value = chmodInput,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c in '0'..'7' }) chmodInput = it },
                            singleLine = true,
                            label = { Text("八进制权限 (0000-7777)") },
                            colors = dialogFieldColors(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = chmodRecursive, onCheckedChange = { chmodRecursive = it })
                            Text("递归应用到子目录", style = MaterialTheme.typography.bodySmall, color = dialogText())
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        actions.onChmod(chmodInput, chmodRecursive)
                        chmodDialog = false
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { chmodDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        if (pathDialog) {
            AlertDialog(
                onDismissRequest = { pathDialog = false },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text("转到目录") },
                text = {
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = dialogFieldColors(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        actions.onNavigate(index, pathInput)
                        pathDialog = false
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { pathDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        scriptTarget?.let { (path, elf) ->
            AlertDialog(
                onDismissRequest = { scriptTarget = null },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text(stringResource(R.string.fm_run_script)) },
                text = {
                    Column {
                        Text(
                            (if (elf) "ELF 可执行文件: " else "脚本: ") + path + "\n将自动授予 0755 权限。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = scriptRoot, onCheckedChange = { scriptRoot = it })
                            Text("Root 执行", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onRunScript(scriptCommand(path, root = scriptRoot, elf = elf, interactive = true))
                        scriptTarget = null
                    }) { Text("执行") }
                },
                dismissButton = {
                    TextButton(onClick = { scriptTarget = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParentRow(index: Int, selectionMode: Boolean, actions: FileManagerActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    actions.onActivePane(index)
                    actions.onUp(index)
                },
                onLongClick = {},
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(Icons.Filled.Folder, null, tint = Color(0xFF42A5F5))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("..", style = MaterialTheme.typography.bodyMedium, color = toolTextColor())
            Text("drwx  <DIR>", style = MaterialTheme.typography.labelSmall, color = toolMutedColor())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRowMaterial(
    entry: FileEntry,
    rowIndex: Int,
    paneIndex: Int,
    selected: Boolean,
    selectionMode: Boolean,
    active: Boolean,
    actions: FileManagerActions,
    onScript: (String) -> Unit,
    onApk: (String) -> Unit,
    onContext: (String) -> Unit,
    onSwipe: (Int) -> Unit,
    onClearAnchor: () -> Unit,
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    val openEntry = {
        actions.onActivePane(paneIndex)
        if (entry.isDirectory) actions.onNavigate(paneIndex, entry.path)
        else if (isApkEntry(entry)) onApk(entry.path)
        else if (isScriptEntry(entry)) onScript(entry.path)
        else actions.onOpenEditor(entry.path)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected || kotlin.math.abs(dragX) > 24f) toolPrimaryColor().copy(alpha = 0.20f)
                else Color.Transparent
            )
            .pointerInput(Unit) {
                // Directional-lock swipe (MT style). Compose's built-in
                // detectHorizontalDragGestures crosses touch slop on |ΣX| alone,
                // ignoring the vertical axis entirely. Real-finger vertical
                // scrolls with slight lateral drift then lock as horizontal
                // mid-scroll: they steal scroll events (the reported "jerk"),
                // accumulate dragX (phantom highlight) and can even fire
                // selectRange with a stale anchor (multi-row phantom selection).
                // Decide direction by axis dominance instead: horizontal wins
                // ONLY when |ΣX| strictly exceeds |ΣY| at slop time; otherwise
                // yield and the LazyColumn scrolls untouched.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var totalX = 0f
                    var totalY = 0f
                    var decided = false
                    var horizontal = false
                    dragX = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        if (change.isConsumed) {
                            // A parent (e.g. LazyColumn scroll) claimed the
                            // gesture while we were undecided — stand down.
                            if (!decided) break
                            continue
                        }
                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y
                        if (!decided) {
                            if (abs(totalX) >= slop || abs(totalY) >= slop) {
                                decided = true
                                horizontal = abs(totalX) > abs(totalY)
                                if (horizontal) {
                                    actions.onActivePane(paneIndex)
                                    change.consume()
                                }
                            }
                        } else if (horizontal) {
                            dragX += delta.x
                            change.consume() // keep the parent pager from switching pages
                        }
                    }
                    if (horizontal && abs(dragX) > 48f) onSwipe(rowIndex)
                    dragX = 0f
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        onClearAnchor()
                        actions.onActivePane(paneIndex)
                        if (selectionMode) actions.onSelect(paneIndex, entry.path) else openEntry()
                    },
                    onLongClick = {
                        actions.onActivePane(paneIndex)
                        // Do NOT highlight the long-pressed row — just show the context menu.
                        // Menu actions that need a selection will temporarily select the target
                        // right before executing (see context menu items below).
                        onContext(entry.path)
                    },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
            imageVector = when {
                entry.isDirectory -> Icons.Filled.Folder
                isApkEntry(entry) -> Icons.Filled.Android
                isScriptEntry(entry) -> Icons.Filled.Description
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            },
            contentDescription = null,
            tint = when {
                entry.isDirectory -> Color(0xFF42A5F5)
                isApkEntry(entry) -> Color(0xFF8BC34A)
                isScriptEntry(entry) -> Color(0xFF66BB6A)
                else -> toolMutedColor()
            },
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = toolTextColor())
            Text(
                "${entry.mode}  ${if (entry.isDirectory) "<DIR>" else FileManagerRepository.formatSize(entry.size)}",
                style = MaterialTheme.typography.labelSmall,
                color = toolMutedColor(),
            )
        }
    }
    }
}

@Composable
internal fun ActionBarMaterial(
    state: FileManagerUiState,
    actions: FileManagerActions,
    showDialog: (DialogKind) -> Unit,
    onSearch: () -> Unit = {},
) {
    val clipboard = state.clipboard
    if (clipboard != null) {
        Text(
            text = stringResource(
                if (clipboard.move) R.string.fm_clipboard_move else R.string.fm_clipboard_copy,
                clipboard.paths.size,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = toolPrimaryColor(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(Icons.Filled.ContentCopy, R.string.fm_copy) { actions.onTransfer(false) }
        ActionIcon(Icons.AutoMirrored.Filled.ArrowForward, R.string.fm_move) { actions.onTransfer(true) }
        ActionIcon(Icons.Filled.ContentPaste, R.string.fm_paste, actions.onPaste)
        ActionIcon(Icons.Filled.Delete, R.string.fm_delete, actions.onDelete)
        ActionIcon(Icons.Filled.Unarchive, R.string.fm_extract, actions.onExtract)
        ActionIcon(Icons.Filled.CreateNewFolder, R.string.fm_new_target) { showDialog(DialogKind.NEW_FOLDER) }
        ActionIcon(Icons.Filled.Edit, R.string.fm_rename) { showDialog(DialogKind.RENAME) }
        ActionIcon(Icons.Filled.ManageSearch, R.string.fm_search, onSearch)
        ActionIcon(Icons.Filled.SelectAll, R.string.fm_select_all, actions.onSelectAll)
        ActionIcon(Icons.Filled.SwapHoriz, R.string.fm_invert, actions.onInvertSelection)
        ActionIcon(Icons.Filled.SwapVert, R.string.fm_sort, actions.onCycleSort)
        ActionIcon(Icons.Filled.TextFields, R.string.fm_edit, actions.onOpenEditorFirst)
        ActionIcon(Icons.Filled.Terminal, R.string.fm_shell, actions.onOpenTerminal)
    }
}

/** MT-style folder search: recursive find under [rootPath], results tap-to-open. */
@Composable
internal fun SearchDialog(
    rootPath: String,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FileManagerRepository.SearchResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogSurface(),
        titleContentColor = dialogText(),
        textContentColor = dialogText(),
        title = { Text("搜索文件") },
        text = {
            Column {
                Text(
                    "范围：$rootPath",
                    style = MaterialTheme.typography.labelSmall,
                    color = dialogText().copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("文件名关键字") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = dialogFieldColors(),
                )
                if (searching) {
                    Text("搜索中…", style = MaterialTheme.typography.bodySmall, color = dialogText())
                }
                results?.let { list ->
                    if (list.isEmpty()) {
                        Text("无结果", style = MaterialTheme.typography.bodySmall, color = dialogText())
                    } else {
                        Column(
                            modifier = Modifier
                                .height(280.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            list.forEach { hit ->
                                // The hit's type is pre-resolved by search(), so the click
                                // handler is fully synchronous — dismissing the dialog right
                                // after the tap can no longer cancel the navigation (the
                                // coroutine-scope race behind "点击有时不跳转").
                                ContextItem(
                                    if (hit.isDir) "[文件夹] ${hit.path.substringAfterLast('/')}"
                                    else hit.path.substringAfterLast('/'),
                                ) {
                                    if (hit.isDir) onNavigate(hit.path) else onOpenFile(hit.path)
                                    onDismiss()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (input.isBlank()) return@TextButton
                searching = true
                results = null
                scope.launch {
                    val found = withContext(Dispatchers.IO) {
                        FileManagerRepository.search(rootPath, input)
                    }
                    results = found
                    searching = false
                }
            }) { Text("搜索") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ContextItem(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(40.dp),
    ) { Text(label, modifier = Modifier.fillMaxWidth(), color = dialogText()) }
}

@Composable
private fun ActionIcon(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, stringResource(labelRes), tint = toolPrimaryColor())
    }
}
