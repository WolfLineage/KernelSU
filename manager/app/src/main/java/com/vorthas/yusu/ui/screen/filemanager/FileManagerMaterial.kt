package com.vorthas.yusu.ui.screen.filemanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.vorthas.yusu.ui.screen.texteditor.MtColors
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.vorthas.yusu.R
import com.vorthas.yusu.data.filemanager.FileEntry
import com.vorthas.yusu.data.filemanager.FileKind
import com.vorthas.yusu.data.filemanager.FileManagerRepository
import com.vorthas.yusu.data.filemanager.Bookmark
import com.vorthas.yusu.data.filemanager.ExecutionPreferenceRepository
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.screen.terminal.INTERACTIVE_SU_PREFIX
import com.vorthas.yusu.ui.screen.terminal.PIPE_EXEC_PREFIX
import com.vorthas.yusu.ui.screen.terminal.PIPE_EXEC_ROOT_PREFIX
import com.vorthas.yusu.ui.screen.terminal.PIPE_EXEC_BIN_PREFIX
import com.vorthas.yusu.ui.screen.terminal.PIPE_EXEC_BIN_ROOT_PREFIX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

internal enum class DialogKind { NEW_FOLDER, RENAME }

/** Shell scripts that MT Manager offers to execute on a single tap. */
private fun isShellScriptEntry(entry: FileEntry): Boolean {
    if (entry.isDirectory) return false
    val name = entry.name.lowercase()
    return listOf(".sh", ".bash", ".zsh", ".ksh", ".fish").any { name.endsWith(it) }
}

/** Broad "script-like" set used only for type colouring, never for auto-execute. */
private fun isScriptEntry(entry: FileEntry): Boolean {
    val name = entry.name.lowercase()
    return !entry.isDirectory && (
        name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".py") ||
            name.endsWith(".js") || name.endsWith(".lua") || isElfFile(entry.path)
        )
}

private fun isApkEntry(entry: FileEntry): Boolean {
    if (entry.isDirectory) return false
    val name = entry.name
    if (name.endsWith(".apk", true) || name.endsWith(".apks", true)) return true
    // IM/ downloader apps append a numeric suffix to duplicates: foo.apk.1, foo.apk.2 …
    return name.contains(Regex("\\.apk\\.\\d+$", RegexOption.IGNORE_CASE))
}

/** MT Manager type-tile colours, sampled from its file list (grey folder, blue scripts, ...). */
private fun mtTileColor(entry: FileEntry): Color {
    if (entry.isDirectory) return Color(0xFF4A4A4A)
    val name = entry.name.lowercase()
    return when {
        name.endsWith(".apk") || name.endsWith(".apks") -> Color(0xFF3AA83A)
        name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".py") ||
            name.endsWith(".js") || name.endsWith(".lua") -> Color(0xFF3F72D6)
        name.endsWith(".json") || name.endsWith(".xml") -> Color(0xFF1976D2)
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".gif") || name.endsWith(".webp") -> Color(0xFF26A69A)
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
            name.endsWith(".tar") || name.endsWith(".gz") -> Color(0xFF9C6B3F)
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") -> Color(0xFFD32F2F)
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") -> Color(0xFFEF6C00)
        name.endsWith(".pdf") -> Color(0xFFC62828)
        else -> Color(0xFF607D8B)
    }
}

private fun mtTileIcon(entry: FileEntry): androidx.compose.ui.graphics.vector.ImageVector {
    val name = entry.name.lowercase()
    return when {
        entry.isDirectory -> Icons.Filled.Folder
        isApkEntry(entry) -> Icons.Filled.Android
        name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") ||
            name.endsWith(".tar") || name.endsWith(".gz") -> Icons.Filled.FolderZip
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".gif") || name.endsWith(".webp") -> Icons.Filled.Image
        name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") -> Icons.Filled.MusicNote
        name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") -> Icons.Filled.VideoLibrary
        name.endsWith(".pdf") -> Icons.Filled.PictureAsPdf
        name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".py") ||
            name.endsWith(".js") -> Icons.Filled.Terminal
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun isElfFile(path: String): Boolean = if (path.contains('\u0000')) false else runCatching {
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
        // Execution sessions must end with the script so the terminal can show
        // the MT-style Enter-to-close hint (and return to File Manager on Enter).
        interactive && root -> INTERACTIVE_SU_PREFIX + "$base; exit"
        interactive -> "$base; exit"         // stay as the app user (can read its own files)
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
 * MT-style top-left menu: a compact "本地" section with the two paths users jump to
 * most often. The "工具" section is intentionally omitted for now.
 */
@Composable
internal fun FileManagerMenuDialog(onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogSurface(),
        titleContentColor = dialogText(),
        textContentColor = dialogText(),
        title = { Text("文件管理") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "本地",
                    color = toolPrimaryColor(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                MenuPathRow(
                    icon = Icons.Filled.FolderOpen,
                    label = "根目录",
                    path = "/",
                    onClick = { onNavigate("/") },
                )
                MenuPathRow(
                    icon = Icons.Filled.Home,
                    label = "内部存储",
                    path = "/storage/emulated/0",
                    onClick = { onNavigate("/storage/emulated/0") },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun MenuPathRow(icon: ImageVector, label: String, path: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = toolPrimaryColor(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = dialogText(), fontSize = 15.sp)
            Text(path, color = toolMutedColor(), fontSize = 11.sp)
        }
    }
}

/**
 * MT-style bookmark dialog: the path is the primary content, with a note and a group
 * header, plus explicit reorder/edit/delete controls. Groups are free text so a new
 * group is created simply by typing its name in the edit dialog.
 */
@Composable
internal fun BookmarksDialog(state: FileManagerUiState, actions: FileManagerActions) {
    var editingPath by remember { mutableStateOf<String?>(null) }
    var editNote by remember { mutableStateOf("") }
    var editGroup by remember { mutableStateOf("") }
    var batchMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragPath by remember { mutableStateOf<String?>(null) }
    val currentBookmarks = rememberUpdatedState(state.bookmarks)
    val currentDragPath = rememberUpdatedState(dragPath)
    val currentBatchMode = rememberUpdatedState(batchMode)
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 76.dp.toPx() }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var topRowHeightPx by remember { mutableFloatStateOf(0f) }
    val editing = state.bookmarks.firstOrNull { it.path == editingPath }
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    AlertDialog(
        onDismissRequest = {},
        containerColor = dialogSurface(),
        titleContentColor = dialogText(),
        textContentColor = dialogText(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("书签", modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    batchMode = !batchMode
                    if (!batchMode) { selected = emptySet(); dragPath = null }
                }) { Text(if (batchMode) "完成" else "批量操作") }
            }
        },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        // This parent never moves when rows are reordered, so its
                        // pointer deltas remain the finger's real movement. A dragged
                        // row's own coordinates shift with animateItem() layer offsets
                        // and must not be used for hit testing.
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                pass = PointerEventPass.Initial,
                                requireUnconsumed = false,
                            )
                            var started = false
                            var accumulated = 0f
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) break
                                val path = currentDragPath.value ?: continue
                                if (!started) {
                                    started = true
                                    accumulated = 0f
                                }
                                accumulated += change.positionChange().y
                                change.consume()
                                val fingerY = down.position.y + accumulated
                                val items = currentBookmarks.value
                                // Deterministic slot centers: fixed row height plus
                                // measured header/top-row heights, unaffected by the
                                // in-flight swap animation.
                                val centers = ArrayList<Float>(items.size)
                                var cursor = if (currentBatchMode.value) topRowHeightPx else 0f
                                var lastGroup: String? = null
                                val header = if (headerHeightPx > 0f) headerHeightPx
                                    else with(density) { 30.dp.toPx() }
                                items.forEach { item ->
                                    val group = item.group.ifBlank { "本地" }
                                    if (group != lastGroup) {
                                        cursor += header
                                        lastGroup = group
                                    }
                                    centers += cursor + rowHeightPx / 2f
                                    cursor += rowHeightPx
                                }
                                val target = centers.indices.minByOrNull { abs(centers[it] - fingerY) }
                                    ?: continue
                                val from = items.indexOfFirst { it.path == path }
                                if (from >= 0 && target != from) actions.onMoveBookmark(from, target)
                            }
                        }
                    }
            ) {
                LazyColumn(Modifier.fillMaxWidth()) {
                if (batchMode) {
                    item(key = "batch-actions") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.onGloballyPositioned {
                                topRowHeightPx = it.size.height.toFloat()
                            },
                        ) {
                            Text("已选 ${selected.size}", color = toolMutedColor(),
                                fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                selected.forEach { actions.onRemoveBookmark(it) }
                                selected = emptySet()
                            }) { Text("删除") }
                            TextButton(onClick = {
                                val paths = state.bookmarks.filter { it.path in selected }.map { it.path }
                                clipboard.setPrimaryClip(ClipData.newPlainText("yusu-bookmarks", paths.joinToString("\n")))
                            }) { Text("复制路径") }
                        }
                    }
                }
                if (state.bookmarks.isEmpty()) {
                    item(key = "empty") {
                        Text("暂无书签。长按目录或文件可添加。", color = dialogText())
                    }
                } else {
                    state.bookmarks.map { it.group.ifBlank { "本地" } }.distinct().forEach { group ->
                        item(key = "header:$group") {
                            Text(
                                group,
                                color = toolPrimaryColor(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 6.dp)
                                    .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
                                    .animateItem(),
                            )
                        }
                        val groupItems = state.bookmarks.filter { (it.group.ifBlank { "本地" }) == group }
                        items(groupItems, key = { it.path }) { bookmark ->
                            val isSelected = bookmark.path in selected
                            BookmarkRow(
                                bookmark = bookmark,
                                itemModifier = Modifier.animateItem(),
                                batchMode = batchMode,
                                selected = isSelected,
                                dragging = dragPath == bookmark.path,
                                onNavigate = {
                                    actions.onNavigateBookmark(bookmark.path)
                                    actions.onHideBookmarks()
                                },
                                onToggleSelect = {
                                    selected = if (isSelected) selected - bookmark.path
                                        else selected + bookmark.path
                                },
                                onEnterBatch = {
                                    batchMode = true
                                    selected = setOf(bookmark.path)
                                },
                                onDragStart = { dragPath = bookmark.path },
                                onDragEnd = { dragPath = null },
                                onEdit = {
                                    editingPath = bookmark.path
                                    editNote = bookmark.note
                                    editGroup = bookmark.group.ifBlank { "本地" }
                                },
                                onDelete = { actions.onRemoveBookmark(bookmark.path) },
                            )
                        }
                    }
                }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = actions.onHideBookmarks) { Text("关闭") } },
    )

    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editingPath = null },
            containerColor = dialogSurface(),
            titleContentColor = dialogText(),
            textContentColor = dialogText(),
            title = { Text("编辑书签") },
            text = {
                Column {
                    Text(editing.path, color = toolMutedColor(), fontSize = 12.sp)
                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("备注") },
                        singleLine = true,
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editGroup,
                        onValueChange = { editGroup = it },
                        label = { Text("分组") },
                        singleLine = true,
                        colors = dialogFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.onUpdateBookmark(editing.path, editNote, editGroup)
                    editingPath = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingPath = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    itemModifier: Modifier = Modifier,
    batchMode: Boolean,
    selected: Boolean,
    dragging: Boolean,
    onNavigate: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterBatch: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Batch selection highlights the whole row, matching the file manager's selection.
    val background = when {
        dragging -> toolPrimaryColor().copy(alpha = 0.32f)
        batchMode && selected -> toolPrimaryColor().copy(alpha = 0.24f)
        else -> Color.Transparent
    }
    Row(
        itemModifier
            .zIndex(if (dragging) 1f else 0f)
            .fillMaxWidth()
            .height(76.dp)
            .background(background)
            .combinedClickable(
                onClick = if (batchMode) onToggleSelect else onNavigate,
                // Batch-mode long press is reserved for the drag handle. Toggling
                // selection here made extra rows light up when dragging began.
                onLongClick = if (batchMode) null else onEnterBatch,
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Bookmark, null, tint = toolPrimaryColor(), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bookmark.note.ifBlank { bookmark.path.substringAfterLast('/') },
                    color = dialogText(),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    bookmark.path,
                    color = toolMutedColor(),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (batchMode) {
            SmallActionIcon(Icons.Filled.Edit, "备注/分组") { onEdit() }
            SmallActionIcon(Icons.Filled.Delete, "删除") { onDelete() }
            // MT-style drag handle is the rightmost control. The dragged row is lifted
            // out and the remaining rows slide aside to make room at the drop target.
            Box(
                Modifier
                    .size(30.dp)
                    .pointerInput(bookmark.path) {
                        // Only detects the long press. The enclosing dialog tracks the
                        // movement in its own (stationary) coordinate space.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var fired = false
                            val hold = scope.launch {
                                delay(400L)
                                fired = true
                                onDragStart()
                            }
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) break
                                if (!fired) {
                                    // Movement before the long press completes is a scroll.
                                    val moved = change.position - down.position
                                    if (kotlin.math.hypot(moved.x, moved.y) > 10f) break
                                }
                            }
                            hold.cancel()
                            if (fired) onDragEnd()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Menu, "长按拖动排序", tint = toolPrimaryColor(),
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SmallActionIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(30.dp)) {
        Icon(icon, label, tint = if (enabled) toolPrimaryColor() else toolMutedColor(),
            modifier = Modifier.size(16.dp))
    }
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
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.pickMode) R.string.pick_file_title else R.string.file_manager)) },
                // MT-style hamburger: opens the quick navigation menu from the top-left.
                navigationIcon = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                // Back/forward/swap live in the bottom action bar (MT style); the top bar
                // only keeps bookmarks and search. Removing the redundant back arrow also
                // avoids a dead-looking icon on the pager page.
                actions = {
                    IconButton(onClick = actions.onShowBookmarks) {
                        Icon(Icons.Filled.Bookmark, contentDescription = "书签")
                    }
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索")
                    }
                },
                colors = expressiveTopAppBarColors(),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
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
                    // A real full-height 1dp rule, not HorizontalDivider forced narrow
                    // (which renders as a tiny dot). Low alpha keeps the two panes
                    // visually separated without cutting the screen in half.
                    Box(
                        Modifier.width(1.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )
                    PaneMaterial(state, actions, 1, Modifier.weight(1f), compact, onRunScript)
                }
            }
            ActionBarMaterial(state, actions, showDialog = { dialog = it }, onSearch = { searchOpen = true })
        }
    }

    if (menuOpen) {
        FileManagerMenuDialog(
            onDismiss = { menuOpen = false },
            onNavigate = { path ->
                actions.onNavigate(state.activePane, path)
                menuOpen = false
            },
        )
    }

    if (state.bookmarksOpen) {
        BookmarksDialog(state, actions)
    }

    if (searchOpen) {
        SearchDialog(
            rootPath = state.panes.getOrNull(state.activePane)?.path ?: "/",
            onDismiss = { searchOpen = false },
            onNavigate = { path -> actions.onNavigate(state.activePane, path) },
            onOpenFile = { path ->
                val lower = path.lowercase()
                if (lower.endsWith(".apk") || lower.endsWith(".apks") ||
                    isElfFile(path) || listOf(".sh", ".bash", ".zsh", ".ksh", ".fish").any { lower.endsWith(it) }
                ) {
                    // Jump to the parent so the file is opened through its full
                    // install/execute dialog instead of a viewer.
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
    val executionContext = LocalContext.current
    val executionPrefs = remember(executionContext) { ExecutionPreferenceRepository(executionContext) }
    var scriptRoot by remember { mutableStateOf(executionPrefs.load().root) }
    var scriptPipe by remember { mutableStateOf(executionPrefs.load().pipe) }
    var scriptSettings by remember { mutableStateOf(false) }
    var contextPath by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var infoTarget by remember { mutableStateOf<String?>(null) }
    // MT-style permission editor state: 3x3 checkbox matrix (owner/group/other x r/w/x)
    // plus the three special bits. Derived from the current octal mode when opened.
    var permDialog by remember { mutableStateOf(false) }
    var permRead by remember { mutableStateOf(booleanArrayOf(false, false, false)) }
    var permWrite by remember { mutableStateOf(booleanArrayOf(false, false, false)) }
    var permExec by remember { mutableStateOf(booleanArrayOf(false, false, false)) }
    var permSuid by remember { mutableStateOf(false) }
    var permSgid by remember { mutableStateOf(false) }
    var permSticky by remember { mutableStateOf(false) }
    var infoModeInput by remember { mutableStateOf("") }
    var infoModeRecursive by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
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
    // Scroll to a file revealed from an external "Open with" intent. The first
    // row is the __parent__ item, so file rows are offset by one.
    LaunchedEffect(pane.focusTick, pane.focusPath) {
        val target = pane.focusPath ?: return@LaunchedEffect
        val row = pane.entries.indexOfFirst { it.path == target }
        if (row >= 0) listState.animateScrollToItem(row + 1)
    }
    val refreshHolder = remember { object { var lastRefreshAt = 0L } }
    var pathInput by rememberSaveable(pane.path) { mutableStateOf(pane.path) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
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
        // MT-style smooth refresh feedback: cross-fade the status text instead of
        // snapping between "N items" and "loading", and ease the progress bar in/out.
        val statusText = if (pane.selected.isNotEmpty()) {
            stringResource(R.string.fm_pane, index + 1) + " · " +
                stringResource(R.string.fm_selected, pane.selected.size)
        } else {
            buildString {
                append(stringResource(R.string.fm_pane, index + 1))
                append(" · ")
                append(if (pane.loading) stringResource(R.string.fm_loading) else stringResource(R.string.fm_items, pane.entries.size))
                append(" · ${stringResource(when (pane.sort) { FileSortMode.NAME -> R.string.fm_sort_name; FileSortMode.SIZE -> R.string.fm_sort_size; FileSortMode.DATE -> R.string.fm_sort_date })}")
            }
        }
        val statusColor = if (state.activePane == index) toolPrimaryColor() else toolMutedColor()
        AnimatedContent(
            targetState = statusText,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "pane_status",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        // Loading feedback is drawn INSIDE a Box that overlays the list, so showing or
        // hiding it never consumes layout space (the old inline bar shifted every row
        // down by ~4 dp). The list is blurred and covered by a clearly darker scrim so
        // the busy state is unmistakable, then a centered spinner names the state.
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (pane.loading) Modifier.blur(4.dp) else Modifier),
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

            // Darker scrim + blurred listing: the busy state is obvious without a
            // spinner. Drawn on top of the list with matchParentSize so it never
            // changes the list's measured height.
            androidx.compose.animation.AnimatedVisibility(
                visible = pane.loading,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(180)),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.38f)),
                )
            }
            // Indeterminate bar pinned over the top edge — the only refresh indicator.
            androidx.compose.animation.AnimatedVisibility(
                visible = pane.loading,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(180)),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = toolPrimaryColor(),
                    trackColor = Color.Transparent,
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
                    // MT-style two-column icon menu.
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).width(340.dp)) {
                        val rows = mutableListOf(
                            MtCtx(Icons.Filled.ContentCopy, if (index == 0) "复制 ->" else "<- 复制", enabled = true) {
                                if (!wasSelected) actions.onSelectOnly(index, path)
                                actions.onTransfer(false); contextPath = null
                            },
                            MtCtx(Icons.Filled.ContentCopy, if (index == 0) "移动 ->" else "<- 移动", enabled = true) {
                                if (!wasSelected) actions.onSelectOnly(index, path)
                                actions.onTransfer(true); contextPath = null
                            },
                            MtCtx(Icons.Filled.Delete, "删除", enabled = true) {
                                if (!wasSelected) actions.onSelectOnly(index, path)
                                deleteTarget = path; contextPath = null
                            },
                            MtCtx(Icons.Filled.Edit, "重命名", enabled = true) {
                                renameTarget = path
                                renameInput = path.substringAfterLast('/')
                                contextPath = null
                            },
                            MtCtx(Icons.Filled.ZoomOutMap, "压缩", enabled = false) { contextPath = null },
                            MtCtx(Icons.Filled.Info, "属性", enabled = true) {
                                infoTarget = path
                                val octal = FileManagerRepository.symbolicToOctal(
                                    pane.entries.firstOrNull { it.path == path }?.mode.orEmpty()
                                ) ?: "0755"
                                infoModeInput = octal
                                val checks = FileManagerRepository.checksFromOctal(octal)
                                permRead = checks.read.copyOf()
                                permWrite = checks.write.copyOf()
                                permExec = checks.execute.copyOf()
                                permSuid = checks.setUid
                                permSgid = checks.setGid
                                permSticky = checks.sticky
                                infoModeRecursive = false
                                contextPath = null
                            },
                            MtCtx(Icons.Filled.Unarchive, if (index == 0) "解压 ->" else "<- 解压", enabled = true) {
                                if (!wasSelected) actions.onSelectOnly(index, path)
                                actions.onExtract(); contextPath = null
                            },
                            MtCtx(Icons.Filled.MoreVert, "打开方式...", enabled = true) {
                                actions.onOpenEditor(path); contextPath = null
                            },
                            MtCtx(Icons.Filled.Bookmark, if (state.bookmarks.any { it.path == path }) "取消书签" else "添加书签", enabled = true) {
                                actions.onToggleBookmark(path); contextPath = null
                            },
                            MtCtx(Icons.Filled.Home, "设为首页", enabled = true) {
                                val target = pane.entries.firstOrNull { it.path == path }
                                val home = if (target?.isDirectory == true) path
                                    else path.substringBeforeLast('/').ifBlank { "/" }
                                actions.onSetHome(index, home)
                                contextPath = null
                            },
                        )
                        if (FileManagerRepository.isApkPath(path)) {
                            rows.add(0, MtCtx(Icons.Filled.Android, "安装 APK", enabled = true) {
                                if (!wasSelected) actions.onSelectOnly(index, path)
                                actions.onInstallApk(path); contextPath = null
                            })
                        }
                        rows.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth()) {
                                pair.forEach { item -> MtContextItem(item, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
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

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text("删除") },
                text = { Text("确定删除「${target.substringAfterLast('/')}」吗？此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        actions.onDelete()
                        deleteTarget = null
                    }) { Text("删除", color = Color(0xFFE53935)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        infoTarget?.let { target ->
            val context = LocalContext.current
            val copyToClipboard: (String) -> Unit = { text ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("yusu-file-info", text))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
            val entry = pane.entries.firstOrNull { it.path == target }
            val kindText = when (FileManagerRepository.sniff(target)) {
                FileKind.IMAGE -> "图片"
                FileKind.VIDEO -> "视频"
                FileKind.ARCHIVE -> "压缩包"
                FileKind.ELF -> "可执行文件"
                FileKind.DEX -> "DEX"
                FileKind.PDF -> "PDF"
                FileKind.SQLITE -> "数据库"
                FileKind.TEXT -> "文本"
                else -> "文件"
            }
            AlertDialog(
                onDismissRequest = { infoTarget = null },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text("属性") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("名称", target.substringAfterLast('/')) { copyToClipboard(target.substringAfterLast('/')) }
                        InfoRow("路径", target) { copyToClipboard(target) }
                        InfoRow("类型", kindText)
                        InfoRow("大小", FileManagerRepository.formatSize(FileManagerRepository.fileSize(target)))
                        InfoRow("修改时间", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(entry?.lastModified ?: 0L)))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("权限", color = toolMutedColor(), fontSize = 13.sp, modifier = Modifier.width(56.dp))
                            Text(
                                FileManagerRepository.octalToDisplay(infoModeInput),
                                color = dialogText(), fontSize = 13.sp, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { permDialog = true }) { Text("更改") }
                        }
                        InfoRow("所有者", "root")
                        InfoRow("用户组", "root")
                    }
                },
                confirmButton = { TextButton(onClick = { infoTarget = null }) { Text("关闭") } },
            )
        }

        // MT Manager style permission editor: a 3x3 checkbox matrix plus special bits.
        if (permDialog && infoTarget != null) {
            val target = infoTarget!!
            val currentOctal = FileManagerRepository.modeFromChecks(permRead, permWrite, permExec, permSuid, permSgid, permSticky)
            AlertDialog(
                onDismissRequest = { permDialog = false },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text("权限") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(56.dp))
                            listOf("读", "写", "执行").forEach {
                                Text(it, color = dialogText(), fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.width(36.dp))
                            }
                        }
                        listOf("所有者", "用户组", "其它").forEachIndexed { row, label ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = dialogText(), fontSize = 14.sp, modifier = Modifier.width(56.dp))
                                PermCheck(permRead[row]) { permRead = permRead.copyOf().also { it[row] = !it[row] } }
                                PermCheck(permWrite[row]) { permWrite = permWrite.copyOf().also { it[row] = !it[row] } }
                                PermCheck(permExec[row]) { permExec = permExec.copyOf().also { it[row] = !it[row] } }
                            }
                        }
                        Text("---- 特殊权限 ----", color = dialogText(), fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PermCheck(permSuid) { permSuid = !permSuid }; Text("设置UID", color = dialogText(), fontSize = 12.sp)
                            PermCheck(permSgid) { permSgid = !permSgid }; Text("设置GID", color = dialogText(), fontSize = 12.sp)
                            PermCheck(permSticky) { permSticky = !permSticky }; Text("粘滞", color = dialogText(), fontSize = 12.sp)
                        }
                        Text(
                            FileManagerRepository.octalToDisplay(currentOctal),
                            color = dialogText(), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = infoModeRecursive, onCheckedChange = { infoModeRecursive = it })
                            Text("递归应用到子目录", style = MaterialTheme.typography.bodySmall, color = dialogText())
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        actions.onChmodPath(target, currentOctal, infoModeRecursive)
                        permDialog = false
                        infoTarget = null
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { permDialog = false }) { Text("取消") } },
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
            // MT Manager style: the main dialog only offers 设置 / 编辑 / 执行;
            // the environment choice lives in the 设置 sub-dialog.
            AlertDialog(
                onDismissRequest = { scriptTarget = null },
                containerColor = dialogSurface(),
                titleContentColor = dialogText(),
                textContentColor = dialogText(),
                title = { Text(path.substringAfterLast('/')) },
                text = {
                    Column {
                        Text("请选择要执行的操作")
                        if (scriptRoot) {
                            Text(
                                "您已勾选使用 ROOT 权限执行，请确保脚本中不含有危险代码！",
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { scriptSettings = true }) { Text("设置") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            actions.onOpenEditor(path)
                            scriptTarget = null
                        }) { Text("编辑") }
                        TextButton(onClick = {
                            val command = if (scriptPipe) {
                                val prefix = when {
                                    elf && scriptRoot -> PIPE_EXEC_BIN_ROOT_PREFIX
                                    elf -> PIPE_EXEC_BIN_PREFIX
                                    scriptRoot -> PIPE_EXEC_ROOT_PREFIX
                                    else -> PIPE_EXEC_PREFIX
                                }
                                prefix + path
                            } else {
                                scriptCommand(path, root = scriptRoot, elf = elf, interactive = true)
                            }
                            onRunScript(command)
                            scriptTarget = null
                            scriptSettings = false
                        }) { Text("执行") }
                    }
                },
                dismissButton = null,
            )
            if (scriptSettings) {
                AlertDialog(
                    onDismissRequest = {
                        val saved = executionPrefs.load()
                        scriptRoot = saved.root
                        scriptPipe = saved.pipe
                        scriptSettings = false
                    },
                    containerColor = dialogSurface(),
                    titleContentColor = dialogText(),
                    textContentColor = dialogText(),
                    title = { Text("执行设置") },
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = !scriptPipe, onClick = { scriptPipe = false })
                                Text("终端执行（PTY）")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = scriptPipe, onClick = { scriptPipe = true })
                                Text("管道执行（PIPESO）")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = scriptRoot, onCheckedChange = { scriptRoot = it })
                                Text("使用 ROOT 权限执行")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            executionPrefs.save(com.vorthas.yusu.data.filemanager.ExecutionPreference(scriptRoot, scriptPipe))
                            scriptSettings = false
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            val saved = executionPrefs.load()
                            scriptRoot = saved.root
                            scriptPipe = saved.pipe
                            scriptSettings = false
                        }) { Text("取消") }
                    },
                )
            }
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
        Box(
            Modifier.size(34.dp).background(Color(0xFF4A4A4A), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Folder, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
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
        else if ((isShellScriptEntry(entry) || isElfFile(entry.path)) && !entry.path.contains('\u0000')) onScript(entry.path)
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
                // Directional-lock swipe (MT style): horizontal wins only when |ΣX|
                // exceeds |ΣY| at slop time, so vertical scrolls are never stolen.
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
                            change.consume()
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
                        onContext(entry.path)
                    },
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(mtTileColor(entry), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = mtTileIcon(entry),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = toolTextColor())
                Text(
                    buildString {
                        append(entry.mode)
                        if (entry.isLink) append(" -> ${entry.linkTarget}")
                        append("  ")
                        append(if (entry.isDirectory) "<DIR>" else FileManagerRepository.formatSize(entry.size))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.isLink) Color(0xFF64B5F6) else toolMutedColor(),
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
    // MT Manager's file-manager action bar: back, forward, new, swap panes and up.
    // Height (34dp), icon size and padding match the terminal's bottom action bar.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "后退") { actions.onPaneBack(state.activePane) }
        ActionIcon(Icons.AutoMirrored.Filled.ArrowForward, "前进") { actions.onPaneForward(state.activePane) }
        ActionIcon(Icons.Filled.CreateNewFolder, "新建") { showDialog(DialogKind.NEW_FOLDER) }
        ActionIcon(Icons.Filled.SwapHoriz, "交换窗格") { actions.onSwap() }
        ActionIcon(Icons.Filled.ArrowUpward, "上级") { actions.onUp(state.activePane) }
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
@OptIn(ExperimentalFoundationApi::class)
private fun InfoRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = toolMutedColor(), fontSize = 13.sp, modifier = Modifier.width(56.dp))
        Text(
            value,
            color = dialogText(),
            fontSize = 13.sp,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onCopy != null) Modifier.combinedClickable(onLongClick = onCopy, onClick = {})
                    else Modifier
                ),
        )
    }
}

/** MT-style blue check box for the permission matrix. */
@Composable
private fun PermCheck(checked: Boolean, onChange: () -> Unit) {
    Checkbox(
        checked = checked,
        onCheckedChange = { onChange() },
        modifier = Modifier.size(36.dp),
        colors = androidx.compose.material3.CheckboxDefaults.colors(
            checkedColor = toolPrimaryColor(),
            uncheckedColor = toolMutedColor(),
        ),
    )
}

private data class MtCtx(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun MtContextItem(item: MtCtx, modifier: Modifier) {
    Row(
        modifier.clickable(enabled = item.enabled) { item.onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = if (item.enabled) toolPrimaryColor() else toolMutedColor(),
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(item.label, color = if (item.enabled) dialogText() else toolMutedColor(), fontSize = 14.sp)
    }
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

@Composable
private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, label, tint = toolPrimaryColor(), modifier = Modifier.size(20.dp))
    }
}
