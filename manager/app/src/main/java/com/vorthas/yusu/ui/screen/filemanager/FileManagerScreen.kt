package com.vorthas.yusu.ui.screen.filemanager

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vorthas.yusu.data.filemanager.FileKind
import com.vorthas.yusu.data.filemanager.FileManagerRepository
import com.vorthas.yusu.data.filemanager.FileViewerRepository
import com.vorthas.yusu.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vorthas.yusu.R
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.component.dialog.rememberConfirmDialog
import com.vorthas.yusu.ui.navigation3.LocalNavigator
import com.vorthas.yusu.ui.navigation3.Route

@Composable
fun FileManagerScreen(
    bottomPadding: Dp = Dp.Unspecified,
    onRunScript: (String) -> Unit = {},
    initialPath: String? = null,
    requestId: Long = 0L,
    /** True for the main-screen pager instance: consume external open requests. */
    usePendingReveal: Boolean = false,
    /** ACTION_GET_CONTENT picker: tapping a file returns its Uri to the caller. */
    pickMode: Boolean = false,
    onPick: (String?) -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val context = LocalContext.current
    val viewModel = viewModel<FileManagerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val activityViewModel = com.vorthas.yusu.ui.LocalActivityViewModel.current
    val pendingReveal by activityViewModel.pendingRevealPath.collectAsStateWithLifecycle()
    var apkTarget by remember { mutableStateOf<String?>(null) }
    var apkInfoText by remember { mutableStateOf<String?>(null) }
    val apkDialog = rememberConfirmDialog(
        onConfirm = { apkTarget?.let(viewModel::installApk); apkTarget = null },
        onDismiss = { apkTarget = null },
    )

    // External "Open with" → reveal in the active pane. Two entry points:
    //  - the main-screen pager consumes MainActivityViewModel.pendingRevealPath
    //  - a standalone Route.FileManager can still be opened with initialPath
    // A changing key re-runs the effect even for the same file opened twice.
    LaunchedEffect(requestId, initialPath, pendingReveal, usePendingReveal) {
        val path = if (usePendingReveal) pendingReveal else initialPath
        if (path != null) {
            viewModel.reveal(state.activePane, path)
            if (usePendingReveal) activityViewModel.consumeReveal()
        }
    }
    LaunchedEffect(apkTarget) {
        val path = apkTarget
        if (path == null) {
            apkInfoText = null
        } else {
            apkInfoText = withContext(Dispatchers.IO) {
                runCatching {
                    FileManagerRepository.apkInfo(path, ksuApp.cacheDir, ksuApp.packageManager)
                }.getOrNull()?.let { info ->
                    "${info.label}\n${info.packageName}\n${info.versionName} (${info.versionCode})"
                }
            }
            apkDialog.showConfirm(
                title = context.getString(R.string.install_apk_prompt),
                content = apkInfoText ?: path.substringAfterLast('/'),
            )
        }
    }

    BackHandler(enabled = state.panes.getOrNull(state.activePane)?.selected?.isNotEmpty() == true) {
        viewModel.clearSelection(state.activePane)
    }
    // Picker mode: backing out cancels the request to the calling app.
    androidx.activity.compose.BackHandler(enabled = pickMode) { onPick(null) }

    // Picker mode: start where the user last browsed (MT Manager behaviour), so
    // files in bookmarked/private folders are immediately reachable. No dialog —
    // the top bar title shows "Select file" for the whole session.
    LaunchedEffect(pickMode) {
        if (pickMode) {
            viewModel.setPickMode(true)
            com.vorthas.yusu.data.filemanager.HomePathRepository(context).getLastDir()?.let { last ->
                viewModel.navigate(state.activePane, last)
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    // Symlinks are resolved once here so every UI variant (and the search dialog)
    // navigates to the target instead of trying to list/open the link itself.
    fun resolve(path: String): String = FileManagerRepository.resolveLink(path)
    fun open(pane: Int, path: String) {
        val virtual = FileViewerRepository.parseArchiveVirtual(path)
        if (virtual != null) {
            // A file inside an archive: extract it to cache, then open the real file.
            val (archive, entry) = virtual
            val display = entry.substringAfterLast('/')
            scope.launch(Dispatchers.IO) {
                val extracted = runCatching { FileViewerRepository.extractEntry(archive, entry, ksuApp.cacheDir) }
                    .getOrNull()
                withContext(Dispatchers.Main) {
                    if (extracted != null) navigator.push(Route.TextEditor(extracted.path, displayName = display))
                    else Toast.makeText(context, "无法预览该条目", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        val target = resolve(path)
        if (pickMode && !FileManagerRepository.isDirectory(target)) {
            // Upload picker: hand the real file back to the calling app.
            onPick(target)
            return
        }
        if (FileManagerRepository.isDirectory(target)) {
            viewModel.navigate(pane, target)
        } else if (FileManagerRepository.isApkPath(target)) {
            // MT style: tapping an APK shows the install dialog, not the archive browser.
            apkTarget = target
        } else if (FileViewerRepository.archiveKind(target) != null) {
            // MT style: browse the archive in THIS pane; the other pane is untouched.
            viewModel.navigate(pane, FileViewerRepository.archiveVirtualPath(target, ""))
        } else {
            // Recognise every file type up front so a tap opens the right viewer directly,
            // never an editor-plus-chooser double act. The "Open with" panel is reachable
            // from the editor's top menu instead.
            val openAs = when (FileManagerRepository.sniff(target)) {
                FileKind.IMAGE -> "image"
                FileKind.VIDEO -> "video"
                FileKind.SQLITE -> "sqlite"
                FileKind.PDF -> "pdf"
                FileKind.ARCHIVE -> "archive"
                // MT opens large text-ish files (self-extracting scripts, ELF payloads)
                // in the unified text editor. Hex is opt-in from "打开方式" only.
                else -> "text"
            }
            navigator.push(Route.TextEditor(target, openAs))
        }
    }

    val actions = FileManagerActions(
        onBack = { navigator.pop() },
        onRefresh = { viewModel.refresh(state.activePane) },
        onNavigate = { pane, path -> viewModel.navigate(pane, resolve(path)) },
        onPaneBack = { pane -> viewModel.back(pane) },
        onPaneForward = { pane -> viewModel.forward(pane) },
        onSwap = viewModel::swap,
        onUp = { pane -> viewModel.up(pane) },
        onSelect = { pane, path -> viewModel.toggle(pane, path) },
        onSelectOnly = { pane, path -> viewModel.selectOnly(pane, path) },
        onSelectRange = { pane, from, to -> viewModel.selectRange(pane, from, to) },
        onClearSelection = { pane -> viewModel.clearSelection(pane) },
        onActivePane = { pane -> viewModel.active(pane) },
        onCopy = viewModel::copy,
        onMove = viewModel::move,
        onTransfer = { move -> viewModel.transfer(move) },
        onPaste = viewModel::paste,
        onSelectAll = viewModel::selectAll,
        onInvertSelection = viewModel::invertSelection,
        onCycleSort = viewModel::cycleSort,
        onDelete = viewModel::delete,
        onExtract = viewModel::extract,
        onNewFolder = { pane, name -> viewModel.newFolder(pane, name) },
        onNewFile = { pane, name -> viewModel.newFile(pane, name) },
        onRename = { from, to -> viewModel.rename(from, to) },
        onChmod = { mode, recursive -> viewModel.chmod(mode, recursive) },
        onChmodPath = { path, mode, recursive -> viewModel.chmodPath(path, mode, recursive) },
        onOpenEditor = { path -> open(state.activePane, path) },
        onOpenEditorFirst = {
            state.panes.getOrNull(state.activePane)?.selected?.firstOrNull()?.let {
                open(state.activePane, it)
            }
        },
        onOpenTerminal = {
            navigator.push(Route.Terminal(state.panes.getOrNull(state.activePane)?.path))
        },
        onInstallApk = { path -> viewModel.installApk(path) },
        onMessageShown = viewModel::messageShown,
        onToggleBookmark = viewModel::toggleBookmark,
        onSetHome = { pane, path -> viewModel.setHome(pane, path) },
        onShowBookmarks = viewModel::showBookmarks,
        onHideBookmarks = viewModel::hideBookmarks,
        onNavigateBookmark = { path -> viewModel.navigate(state.activePane, resolve(path)) },
        onUpdateBookmark = viewModel::updateBookmark,
        onMoveBookmark = viewModel::moveBookmark,
        onRemoveBookmark = viewModel::removeBookmark,
    )

    when (uiMode) {
        UiMode.Miuix -> FileManagerScreenMiuix(state, actions, bottomPadding, onRunScript)
        UiMode.Material -> FileManagerScreenMaterial(state, actions, bottomPadding, onRunScript)
    }
}
