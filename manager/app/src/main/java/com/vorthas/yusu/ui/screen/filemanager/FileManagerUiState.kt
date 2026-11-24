package com.vorthas.yusu.ui.screen.filemanager

import com.vorthas.yusu.data.filemanager.FileEntry
import com.vorthas.yusu.data.filemanager.FileManagerRepository

enum class FileSortMode { NAME, SIZE, DATE }

data class FileClipboard(
    val paths: List<String>,
    val move: Boolean,
    val sourcePane: Int,
)

data class PaneUiState(
    val path: String,
    val entries: List<FileEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = true,
    val sort: FileSortMode = FileSortMode.NAME,
    val refreshTick: Int = 0,
    /** File revealed from an external "Open with" intent; UI scrolls to and highlights it. */
    val focusPath: String? = null,
    val focusTick: Int = 0,
)

data class FileManagerUiState(
    val panes: List<PaneUiState> = listOf(
        PaneUiState(FileManagerRepository.defaultLeft()),
        PaneUiState(FileManagerRepository.defaultRight()),
    ),
    val activePane: Int = 0,
    val busy: Boolean = false,
    val clipboard: FileClipboard? = null,
    val canBack: List<Boolean> = listOf(false, false),
    val canForward: List<Boolean> = listOf(false, false),
    val message: String? = null,
    val bookmarkPaths: List<String> = emptyList(),
    val bookmarksOpen: Boolean = false,
)

data class FileManagerActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onNavigate: (Int, String) -> Unit,
    val onPaneBack: (Int) -> Unit,
    val onPaneForward: (Int) -> Unit,
    val onSwap: () -> Unit,
    val onUp: (Int) -> Unit,
    val onSelect: (Int, String) -> Unit,
    val onSelectOnly: (Int, String) -> Unit,
    val onSelectRange: (Int, Int, Int) -> Unit,
    val onClearSelection: (Int) -> Unit,
    val onActivePane: (Int) -> Unit,
    val onCopy: () -> Unit,
    val onMove: () -> Unit,
    val onTransfer: (Boolean) -> Unit,
    val onPaste: () -> Unit,
    val onSelectAll: () -> Unit,
    val onInvertSelection: () -> Unit,
    val onCycleSort: () -> Unit,
    val onDelete: () -> Unit,
    val onExtract: () -> Unit,
    val onNewFolder: (Int, String) -> Unit,
    val onNewFile: (Int, String) -> Unit,
    val onRename: (String, String) -> Unit,
    val onChmod: (String, Boolean) -> Unit,
    val onChmodPath: (String, String, Boolean) -> Unit,
    val onOpenEditor: (String) -> Unit,
    val onOpenEditorFirst: () -> Unit,
    val onOpenTerminal: () -> Unit,
    val onInstallApk: (String) -> Unit,
    val onMessageShown: () -> Unit,
    val onToggleBookmark: (String) -> Unit = {},
    val onSetHome: (Int, String) -> Unit = { _, _ -> },
    val onShowBookmarks: () -> Unit = {},
    val onHideBookmarks: () -> Unit = {},
    val onNavigateBookmark: (String) -> Unit = {},
    val onReveal: (Int, String) -> Unit = { _, _ -> },
)
