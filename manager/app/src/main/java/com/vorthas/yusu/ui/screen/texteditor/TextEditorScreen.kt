package com.vorthas.yusu.ui.screen.texteditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vorthas.yusu.R
import com.vorthas.yusu.data.filemanager.FileKind
import com.vorthas.yusu.data.filemanager.FileManagerRepository
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.navigation3.LocalNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix mode has no MaterialTheme, so Material3 Text must be given an explicit color. */
@Composable
private fun viewerTextColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onBackground
    UiMode.Material -> MaterialTheme.colorScheme.onSurface
}

/** Editor background follows the app's current theme (material / miuix, light / dark). */
@Composable
private fun editorBackground(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.background
    UiMode.Material -> MaterialTheme.colorScheme.surface
}

/**
 * MT-style shell: one shared top bar + content + bottom symbol bar. The same frame is used
 * for the editor, hex viewer and archive browser so every file type feels identical.
 */
@Composable
internal fun TextEditorContent(state: TextEditorUiState, actions: TextEditorActions, modifier: Modifier) {
    var editorRef by remember { mutableStateOf<SyntaxEditText?>(null) }
    var pinned by remember(state.path) { mutableStateOf(false) }
    val contentActions = actions.copy(onSymbolInsert = { symbol -> editorRef?.insertAtCursor(symbol) })
    val title = state.displayName.ifEmpty { state.path.substringAfterLast('/') }
    Column(modifier = modifier
        .fillMaxSize()
        .background(editorBackground())
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)) {
        if (state.mode == FileViewMode.ARCHIVE) {
            MtArchiveTopBar(
                title = title,
                info = "文件夹: ${state.archiveItems.count { it.directory }}  文件: ${state.archiveItems.count { !it.directory }}",
                onBack = actions.onBack,
                onMenu = actions.onOpenWith,
            )
        } else {
            MtEditorTopBar(
                title = title,
                statusLine = when (state.mode) {
                    FileViewMode.IMAGE -> "图片"
                    FileViewMode.VIDEO -> "视频"
                    FileViewMode.CONTINUOUS -> if (state.isHex) "十六进制" else "文本"
                    else -> "1:1  UTF-8"
                },
                onBack = actions.onBack,
                onSave = actions.onSave,
                canSave = state.dirty && !state.readOnly && !state.inArchive,
                onMenu = actions.onOpenWith,
                onUndo = { editorRef?.undoEdit() },
                onRedo = { editorRef?.redoEdit() },
                onToggleEdit = actions.onToggleEdit,
                editing = !state.readOnly,
                pinned = pinned,
                onTogglePin = { pinned = !pinned },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.loading && state.lines.isEmpty() && state.content.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (state.mode) {
                    FileViewMode.EDIT -> EditableText(state, contentActions, onEditorReady = { editorRef = it })
                    FileViewMode.CONTINUOUS -> ContinuousViewer(state, contentActions)
                    FileViewMode.ARCHIVE -> ArchiveViewer(state, contentActions)
                    FileViewMode.IMAGE -> ImageViewer(state)
                    FileViewMode.VIDEO -> VideoViewer(state)
                    FileViewMode.SQLITE -> SqliteViewer(state)
                    FileViewMode.PDF -> PdfViewer(state)
                }
            }
        }
        when (state.mode) {
            FileViewMode.ARCHIVE -> MtArchiveBottomBar(onUp = actions.onArchiveUp, onBack = actions.onBack)
            FileViewMode.IMAGE, FileViewMode.VIDEO, FileViewMode.PDF, FileViewMode.SQLITE -> { /* viewer-specific bars */ }
            else -> MtSymbolBar(onInsert = { contentActions.onSymbolInsert(it) })
        }
    }
}

@Composable
private fun EditableText(
    state: TextEditorUiState,
    actions: TextEditorActions,
    onEditorReady: (SyntaxEditText) -> Unit = {},
) {
    // One editor for every editable text file: the line-numbered native view. The
    // former OutlinedTextField branch had no gutter, no pinch zoom and wrapped long
    // lines, so the two implementations never behaved the same way.
    AndroidView(
        modifier = Modifier.fillMaxSize().padding(8.dp),
            factory = { context ->
            SyntaxEditText(context).apply {
                setLanguage(state.path)
                onDocumentChange = { actions.onContentChange(it) }
                onEditorReady(this)
            }
        },
        update = { view ->
            view.setLanguage(state.path)
            view.setDocument(state.content)
            view.isEnabled = !state.readOnly
        },
    )
}

/**
 * MT-style continuous viewer: the file's raw lines are displayed from byte 0 in one
 * scrollable list. More data is appended automatically near the end; no page controls.
 */
@Composable
private fun ContinuousViewer(state: TextEditorUiState, actions: TextEditorActions) {
    val lines = state.lines
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val number = state.lineNumbers.getOrNull(index) ?: (index + 1)
                val showNumber = !state.isHex &&
                    (index == 0 || state.lineNumbers.getOrNull(index - 1) != number)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = if (showNumber) number.toString() else "",
                        color = Color(0xFF6E7681),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp),
                        modifier = Modifier.width(40.dp),
                    )
                    Text(
                        text = state.lineTexts.getOrNull(index) ?: AnnotatedString(line),
                        color = viewerTextColor(),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                if (state.hasMore) {
                    LaunchedEffect(state.loadedBytes) { actions.onLoadMore() }
                    Text(
                        "继续载入…",
                        color = Color(0xFF64B5F6),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else if (lines.isEmpty()) {
                    Text("（空文件）", color = viewerTextColor(), modifier = Modifier.padding(12.dp))
                }
            }
        }
        // Isolated so per-frame scroll state only recomposes this small bar, not the rows.
        Box(Modifier.align(Alignment.TopEnd)) { ContinuousScrollbar(listState) }
    }
}

@Composable
private fun ContinuousScrollbar(listState: LazyListState) {
    val layoutInfo = listState.layoutInfo
    val total = layoutInfo.totalItemsCount
    if (total <= 0) return
    val first = listState.firstVisibleItemIndex.toFloat() +
        listState.firstVisibleItemScrollOffset.toFloat() /
        (layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() ?: 1f)
    val thumb = (layoutInfo.visibleItemsInfo.size.toFloat() / total).coerceIn(0.04f, 1f)
    val progress = (first / total).coerceIn(0f, 1f - thumb)
    Box(
        Modifier
            .fillMaxHeight()
            .width(5.dp)
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFF8A94A6).copy(alpha = 0.55f),
                    topLeft = androidx.compose.ui.geometry.Offset(1f, progress * size.height),
                    size = androidx.compose.ui.geometry.Size(3f, thumb * size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f),
                )
            },
    )
}

@Composable
private fun ArchiveViewer(state: TextEditorUiState, actions: TextEditorActions) {
    Column(Modifier.fillMaxSize()) {
        // MT-style path bar: tap to go up, directories are browsable like normal folders.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "/${state.archiveDir}".trimEnd('/'),
                color = Color(0xFFFFB74D),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = state.archiveDir.isNotEmpty()) { actions.onArchiveUp() },
            )
            if (state.inArchive) {
                Button(onClick = actions.onArchiveBack) { Text("返回条目预览") }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            // MT always shows a ".." parent entry at the top of each directory.
            if (state.archiveDir.isNotEmpty()) {
                item {
                    MtArchiveRow(name = "..", isDirectory = true, info = "", onClick = actions.onArchiveUp)
                }
            }
            items(state.archiveItems) { item ->
                MtArchiveRow(
                    name = item.name,
                    isDirectory = item.directory,
                    info = if (item.directory) "" else FileManagerRepository.formatSize(item.size),
                    onClick = {
                        if (item.directory) actions.onArchiveDir(item.name)
                        else actions.onArchiveEntry(item.name)
                    },
                )
            }
        }
    }
}

@Composable
private fun ImageViewer(state: TextEditorUiState) {
    var bitmap by remember(state.path) { mutableStateOf<Bitmap?>(null) }
    var message by remember(state.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.path) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                FileManagerRepository.openStream(state.path).use { BitmapFactory.decodeStream(it, null, bounds) }
                val scale = maxOf(1, kotlin.math.ceil(maxOf(bounds.outWidth, bounds.outHeight) / 2048f).toInt())
                val options = BitmapFactory.Options().apply { inSampleSize = scale }
                FileManagerRepository.openStream(state.path).use { BitmapFactory.decodeStream(it, null, options) }
            }
        }
        result.fold(
            onSuccess = { bitmap = it; message = if (it == null) "无法解码该图片" else null },
            onFailure = { message = "无法读取图片：${it.message}" },
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                factory = { context -> ZoomableImageView(context) },
                update = { view -> view.setZoomBitmap(image) },
            )
        } else {
            Text(message ?: "正在解码图片…", color = viewerTextColor())
        }
    }
}

@Composable
private fun VideoViewer(state: TextEditorUiState) {
    val file = state.videoFile
    AndroidView(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        factory = { context ->
            val view = ControlledVideoView(context)
            val controller = MediaController(context)
            view.playerController = controller
            view.setMediaController(controller)
            controller.setAnchorView(view)
            view
        },
        update = { view ->
            if (file != null && view.tag != file.path) {
                view.tag = file.path
                view.setVideoURI(Uri.fromFile(file))
                view.setOnPreparedListener { player ->
                    player.start()
                    view.playerController?.show()
                }
            }
        },
    )
}

@Composable
private fun SqliteViewer(state: TextEditorUiState) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.sqliteTables) { table ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("表：${table.name}", color = Color(0xFF42A5F5), style = MaterialTheme.typography.titleSmall)
                Text(table.columns.joinToString(" | "), color = viewerTextColor(), style = MaterialTheme.typography.labelSmall)
                table.rows.forEach { row ->
                    Text(
                        text = row.joinToString(" | ") { it ?: "NULL" },
                        color = viewerTextColor(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfViewer(state: TextEditorUiState) {
    val file = state.pdfFile
    var page by remember(file) { mutableIntStateOf(0) }
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var message by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file, page) {
        if (file == null) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val total = renderer.pageCount
                        if (total == 0) error("PDF 没有可显示页面")
                        val index = page.coerceIn(0, total - 1)
                        renderer.openPage(index).use { pdfPage ->
                            val scale = minOf(1f, 1600f / maxOf(pdfPage.width, pdfPage.height))
                            val rendered = Bitmap.createBitmap(
                                (pdfPage.width * scale).toInt().coerceAtLeast(1),
                                (pdfPage.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            pdfPage.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            Pair(total, rendered)
                        }
                    }
                }
            }
        }
        result.fold(
            onSuccess = { pageCount = it.first; bitmap = it.second; message = null },
            onFailure = { message = "无法渲染 PDF：${it.message}" },
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text("上一页") }
            Text("第 ${page + 1} / ${pageCount} 页", color = viewerTextColor(), modifier = Modifier.weight(1f))
            Button(
                onClick = { page = (page + 1).coerceAtMost(pageCount - 1) },
                enabled = pageCount > 0 && page < pageCount - 1,
            ) { Text("下一页") }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image != null) {
                Image(bitmap = image.asImageBitmap(), contentDescription = "PDF 第 ${page + 1} 页")
            } else {
                Text(message ?: "正在渲染 PDF…", color = viewerTextColor())
            }
        }
    }
}

@Composable
fun TextEditorPage(path: String, openAs: String? = null, displayName: String? = null) {
    val navigator = LocalNavigator.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiMode = LocalUiMode.current
    val viewModel = viewModel<TextEditorViewModel>(key = "text-editor-$path")
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The file manager always passes an explicit openAs from type sniffing, so a tap opens
    // exactly one viewer. The "Open with" panel is opt-in from the editor's top menu only.
    var showOpenWith by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path, openAs) {
        viewModel.setDisplayName(displayName)
        if (openAs != null) viewModel.openAs(openAs, path) else viewModel.load(path)
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    val actions = TextEditorActions(
        onBack = { navigator.pop() },
        onContentChange = viewModel::setContent,
        onSave = viewModel::save,
        onMessageShown = viewModel::messageShown,
        onLoadMore = viewModel::loadMore,
        onArchiveEntry = viewModel::openArchiveEntry,
        onArchiveDir = viewModel::openArchiveDir,
        onArchiveUp = viewModel::archiveUp,
        onArchiveBack = viewModel::backToArchive,
        onOpenWith = { showOpenWith = true },
        onToggleEdit = viewModel::toggleEdit,
    )

    when (uiMode) {
        UiMode.Miuix -> TextEditorContent(state, actions, Modifier.fillMaxSize())
        UiMode.Material -> TextEditorContent(state, actions, Modifier.fillMaxSize())
    }

    if (showOpenWith) {
        MtOpenWithDialog(
            fileName = path.substringAfterLast('/'),
            onDismiss = { showOpenWith = false },
            onAction = { choice ->
                showOpenWith = false
                viewModel.openAs(choice, path)
            },
        )
    }
}
