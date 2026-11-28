package com.vorthas.yusu.ui.screen.filemanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorthas.yusu.data.filemanager.BookmarkRepository
import com.vorthas.yusu.data.filemanager.FileEntry
import com.vorthas.yusu.data.filemanager.FileManagerRepository
import com.vorthas.yusu.data.filemanager.FileViewerRepository
import com.vorthas.yusu.data.filemanager.HomePathRepository
import com.vorthas.yusu.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Minimum time the refresh indicator stays visible so its animation can play. */
private const val MIN_LOADING_MS = 320L

class FileManagerViewModel : ViewModel() {
    private val _state = MutableStateFlow(FileManagerUiState())
    val state: StateFlow<FileManagerUiState> = _state.asStateFlow()

    private val backStacks = listOf(ArrayDeque<String>(), ArrayDeque<String>())
    private val forwardStacks = listOf(ArrayDeque<String>(), ArrayDeque<String>())
    // File to select once a pane's refresh finishes (navigate() clears selection).
    private val pendingFocus = arrayOfNulls<String>(2)
    private val bookmarks = BookmarkRepository(ksuApp)
    private val homePaths = HomePathRepository(ksuApp)

    init {
        // Default to the configured home directory for each pane independently.
        val initialLeft = homePaths.get(0) ?: FileManagerRepository.defaultLeft()
        val initialRight = homePaths.get(1) ?: FileManagerRepository.defaultRight()
        _state.value = _state.value.copy(
            panes = listOf(
                _state.value.panes[0].copy(path = initialLeft),
                _state.value.panes[1].copy(path = initialRight),
            )
        )
        refresh(0)
        refresh(1)
    }

    /** Set the given pane's home directory; only that pane's preference is touched. */
    fun setHome(pane: Int, path: String) {
        if (pane !in 0..1) return
        homePaths.set(pane, path)
        message("窗口 ${pane + 1} 首页已设为 $path")
    }

    /** MT-style bookmarks: toggle, list and navigate. */
    fun toggleBookmark(path: String) {
        bookmarks.toggle(path)
        _state.update { it.copy(bookmarks = bookmarks.all()) }
    }

    fun isBookmarked(path: String): Boolean = bookmarks.all().any { it.path == path }

    fun showBookmarks() = _state.update { it.copy(bookmarks = bookmarks.all(), bookmarksOpen = true) }

    fun hideBookmarks() = _state.update { it.copy(bookmarksOpen = false) }

    fun updateBookmark(path: String, note: String, group: String) {
        bookmarks.update(path, note, group)
        _state.update { it.copy(bookmarks = bookmarks.all()) }
    }

    fun moveBookmark(from: Int, to: Int) {
        bookmarks.move(from, to)
        _state.update { it.copy(bookmarks = bookmarks.all()) }
    }

    fun removeBookmark(path: String) {
        bookmarks.remove(path)
        _state.update { it.copy(bookmarks = bookmarks.all()) }
    }

    fun refresh(pane: Int) = viewModelScope.launch(Dispatchers.IO) {
        val path = _state.value.panes.getOrNull(pane)?.path ?: return@launch
        // Surface feedback immediately: the pane title flips to "加载中" and the
        // progress bar under it appears while the listing runs.
        val startedAt = System.currentTimeMillis()
        _state.update { state -> state.withPane(pane) {
            it.copy(loading = true, refreshTick = it.refreshTick + 1)
        } }
        val entries = runCatching {
            val archive = FileViewerRepository.parseArchiveVirtual(path)
            if (archive != null) {
                listArchivePane(archive.first, archive.second)
            } else {
                FileManagerRepository.list(path)
            }
        }
            .onFailure { FileManagerRepository.logFailure("list $path", it) }
            .getOrDefault(emptyList())
        val sort = _state.value.panes.getOrNull(pane)?.sort ?: FileSortMode.NAME
        val sorted = when (sort) {
            FileSortMode.NAME -> entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            FileSortMode.SIZE -> entries.sortedWith(compareBy({ !it.isDirectory }, { -it.size }))
            FileSortMode.DATE -> entries.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified }))
        }
        // Local directory listings finish in a few milliseconds. Without a minimum
        // visible window the loading animation never actually renders — the user only
        // sees a flicker. Hold the loading state for at least MIN_LOADING_MS so the
        // progress bar can fade in/out smoothly, like MT Manager's refresh feedback.
        val elapsed = System.currentTimeMillis() - startedAt
        if (elapsed < MIN_LOADING_MS) delay(MIN_LOADING_MS - elapsed)
        _state.update { state -> state.withPane(pane) { paneState ->
            val pending = synchronized(pendingFocus) { pendingFocus[pane] }
            if (pending != null && sorted.any { it.path == pending }) {
                synchronized(pendingFocus) { pendingFocus[pane] = null }
                paneState.copy(
                    entries = sorted, loading = false,
                    selected = setOf(pending), focusPath = pending,
                    focusTick = paneState.focusTick + 1,
                )
            } else {
                paneState.copy(entries = sorted, loading = false)
            }
        } }
    }

    /**
     * Reveal a file opened from another app: navigate its parent pane to the
     * containing directory and select the file once the listing is ready.
     */
    fun reveal(pane: Int, filePath: String) {
        if (pane !in 0..1) return
        val parent = FileManagerRepository.parent(filePath)
        synchronized(pendingFocus) { pendingFocus[pane] = filePath }
        navigate(pane, parent)
    }

    /**
     * List immediate children of a directory inside an archive, returned as synthetic
     * FileEntry objects so the panes, selection, sorting and swipe gestures all keep working.
     */
    private fun listArchivePane(archive: String, dir: String): List<FileEntry> {
        val all = archiveListingCache[archive] ?: runCatching {
            FileViewerRepository.listArchive(archive, ksuApp.cacheDir).also { archiveListingCache[archive] = it }
        }.getOrDefault(emptyList())
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val childrenNames = HashSet<String>()
        val children = all.mapNotNull { item ->
            val rest = item.name.removePrefix(prefix)
            if (!item.name.startsWith(prefix) || rest.isEmpty()) return@mapNotNull null
            val slash = rest.indexOf('/')
            val name: String
            val isDir: Boolean
            val size: Long
            val entryPath: String
            if (slash < 0) {
                name = rest
                isDir = item.directory
                size = item.size
                entryPath = FileViewerRepository.archiveVirtualPath(archive, item.name.trimEnd('/'))
            } else {
                name = rest.substring(0, slash)
                isDir = true
                size = -1L
                entryPath = FileViewerRepository.archiveVirtualPath(archive, prefix + name)
            }
            if (isDir && childrenNames.contains(name)) return@mapNotNull null
            childrenNames += name
            FileEntry(
                path = entryPath,
                name = name,
                isDirectory = isDir,
                size = size,
                lastModified = -1L,
                mode = if (isDir) "drwxr-xr-x" else "-rw-r--r--",
            )
        }
        return children
    }

    private val archiveListingCache = mutableMapOf<String, List<FileViewerRepository.ArchiveItem>>()

    fun navigate(pane: Int, path: String, fromHistory: Boolean = false) {
        if (path.isBlank()) return
        if (pane == 0) homePaths.setLastDir(path)
        val current = _state.value.panes.getOrNull(pane)?.path.orEmpty()
        if (!fromHistory && path != current) {
            backStacks[pane].addLast(current)
            if (backStacks[pane].size > 100) backStacks[pane].removeFirst()
            forwardStacks[pane].clear()
        }
        publishPath(pane, path)
        refresh(pane)
    }

    fun back(pane: Int) {
        val dest = backStacks[pane].removeLastOrNull() ?: return
        val current = _state.value.panes.getOrNull(pane)?.path.orEmpty()
        forwardStacks[pane].addLast(current)
        publishPath(pane, dest)
        refresh(pane)
    }

    fun forward(pane: Int) {
        val dest = forwardStacks[pane].removeLastOrNull() ?: return
        val current = _state.value.panes.getOrNull(pane)?.path.orEmpty()
        backStacks[pane].addLast(current)
        publishPath(pane, dest)
        refresh(pane)
    }

    private fun publishPath(pane: Int, path: String) {
        _state.update { state ->
            state.withPane(pane) { it.copy(path = path, selected = emptySet(), loading = true) }
                .copy(
                    canBack = backStacks.mapIndexed { i, stack -> if (i == pane) stack.isNotEmpty() else state.canBack[i] },
                    canForward = forwardStacks.mapIndexed { i, stack -> if (i == pane) stack.isNotEmpty() else state.canForward[i] },
                )
        }
    }

    fun swap() = _state.update { state ->
        val b0 = backStacks[0].toList()
        val b1 = backStacks[1].toList()
        backStacks[0].clear(); backStacks[0].addAll(b1)
        backStacks[1].clear(); backStacks[1].addAll(b0)
        val f0 = forwardStacks[0].toList()
        val f1 = forwardStacks[1].toList()
        forwardStacks[0].clear(); forwardStacks[0].addAll(f1)
        forwardStacks[1].clear(); forwardStacks[1].addAll(f0)
        state.copy(
            panes = listOf(state.panes[1], state.panes[0]),
            activePane = 1 - state.activePane,
            canBack = listOf(state.canBack[1], state.canBack[0]),
            canForward = listOf(state.canForward[1], state.canForward[0]),
        )
    }

    fun up(pane: Int) {
        val path = _state.value.panes.getOrNull(pane)?.path ?: return
        val virtual = FileViewerRepository.parseArchiveVirtual(path)
        if (virtual != null) {
            val (archive, dir) = virtual
            if (dir.isEmpty()) {
                // Leaving the archive: land on the archive file's real parent directory.
                archiveListingCache.remove(archive)
                navigate(pane, FileManagerRepository.parent(archive))
            } else {
                val parent = dir.substringBeforeLast('/', "")
                navigate(pane, FileViewerRepository.archiveVirtualPath(archive, parent))
            }
            return
        }
        navigate(pane, FileManagerRepository.parent(path))
    }

    fun active(pane: Int) = _state.update { it.copy(activePane = pane) }

    fun toggle(pane: Int, path: String) = _state.update { state ->
        state.withPane(pane) { paneState ->
            val selected = if (path in paneState.selected) paneState.selected - path else paneState.selected + path
            paneState.copy(selected = selected)
        }
    }

    fun selectOnly(pane: Int, path: String) = _state.update { state ->
        state.withPane(pane) { it.copy(selected = setOf(path)) }
    }

    fun selectRange(pane: Int, from: Int, to: Int) = _state.update { state ->
        state.withPane(pane) { paneState ->
            if (paneState.entries.isEmpty()) return@withPane paneState
            val start = minOf(from, to).coerceIn(0, paneState.entries.lastIndex)
            val end = maxOf(from, to).coerceIn(0, paneState.entries.lastIndex)
            paneState.copy(selected = paneState.entries.subList(start, end + 1).map { it.path }.toSet())
        }
    }

    fun clearSelection(pane: Int) = _state.update { state -> state.withPane(pane) { it.copy(selected = emptySet()) } }

    fun selectAll() = _state.update { state ->
        state.withPane(state.activePane) { pane ->
            pane.copy(selected = pane.entries.map { it.path }.toSet())
        }
    }

    fun invertSelection() = _state.update { state ->
        state.withPane(state.activePane) { pane ->
            pane.copy(selected = pane.entries.map { it.path }.toSet() - pane.selected)
        }
    }

    fun cycleSort() {
        val pane = _state.value.activePane
        _state.update { state ->
            state.withPane(pane) { current ->
                val next = when (current.sort) {
                    FileSortMode.NAME -> FileSortMode.SIZE
                    FileSortMode.SIZE -> FileSortMode.DATE
                    FileSortMode.DATE -> FileSortMode.NAME
                }
                current.copy(sort = next)
            }
        }
        refresh(pane)
    }

    fun copy() = stageClipboard(move = false)

    fun move() = stageClipboard(move = true)

    private fun stageClipboard(move: Boolean) = _state.update { state ->
        val pane = state.activePane
        val paths = state.panes.getOrNull(pane)?.selected.orEmpty().toList()
        if (paths.isEmpty()) {
            state.copy(message = "No file selected")
        } else {
            state.copy(clipboard = FileClipboard(paths, move, pane))
        }
    }

    fun paste() = viewModelScope.launch(Dispatchers.IO) {
        val state = _state.value
        val clipboard = state.clipboard ?: return@launch
        val targetDir = state.panes.getOrNull(state.activePane)?.path ?: return@launch
        val firstSource = clipboard.paths.firstOrNull() ?: return@launch
        if (FileManagerRepository.parent(firstSource).trimEnd('/') == targetDir.trimEnd('/')) {
            message("Already in source directory")
            return@launch
        }
        var failed = 0
        for (source in clipboard.paths) {
            val target = targetDir.trimEnd('/') + "/" + File(source).name
            val error = if (clipboard.move) {
                FileManagerRepository.move(source, target)
            } else {
                FileManagerRepository.copy(source, target)
            }
            if (error != null) failed++
        }
        message(if (failed == 0) "Done" else "$failed operation(s) failed")
        val fromPane = clipboard.sourcePane
        clearSelection(fromPane)
        _state.update { it.copy(clipboard = null) }
        refresh(fromPane)
        refresh(state.activePane)
    }

    fun delete() = viewModelScope.launch(Dispatchers.IO) {
        val state = _state.value
        val pane = state.activePane
        val paths = state.panes.getOrNull(pane)?.selected.orEmpty().toList()
        if (paths.isEmpty()) {
            message("No file selected")
            return@launch
        }
        val failed = paths.count { !FileManagerRepository.delete(it) }
        message(if (failed == 0) "Deleted" else "$failed deletion(s) failed")
        clearSelection(pane)
        refresh(pane)
    }

    /** MT-style direct transfer from the focused pane to the other pane. */
    fun transfer(move: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val state = _state.value
        val pane = state.activePane
        val dest = state.panes.getOrNull(1 - pane)?.path ?: return@launch
        val paths = state.panes.getOrNull(pane)?.selected.orEmpty().toList()
        if (paths.isEmpty()) {
            message("No file selected")
            return@launch
        }
        val errors = paths.mapNotNull { path ->
            val target = FileManagerRepository.joinPath(dest, path.substringAfterLast('/'))
            runCatching {
                if (move) FileManagerRepository.move(path, target) else FileManagerRepository.copy(path, target)
            }.getOrElse { it.message }
        }
        message(
            if (errors.isEmpty()) "${if (move) "Moved" else "Copied"} ${paths.size} item(s) → $dest"
            else errors.first()
        )
        clearSelection(pane)
        refresh(pane)
        refresh(1 - pane)
    }

    fun chmod(mode: String, recursive: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val state = _state.value
        val pane = state.activePane
        val paths = state.panes.getOrNull(pane)?.selected.orEmpty().toList()
        if (paths.isEmpty()) {
            message("No file selected")
            return@launch
        }
        val errors = paths.mapNotNull { FileManagerRepository.chmod(it, mode, recursive) }
        message(if (errors.isEmpty()) "Permissions updated" else errors.first())
        refresh(pane)
    }

    /** Apply a mode to one explicit path from the properties dialog, independent of selection/focus. */
    fun chmodPath(path: String, mode: String, recursive: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val pane = _state.value.panes.indexOfFirst { path in it.selected || it.entries.any { e -> e.path == path } }
            .let { if (it >= 0) it else _state.value.activePane }
        val error = FileManagerRepository.chmod(path, mode, recursive)
        message(if (error == null) "权限已更新" else error)
        refresh(pane)
    }

    fun extract() = viewModelScope.launch(Dispatchers.IO) {
        val state = _state.value
        val pane = state.activePane
        val dir = state.panes.getOrNull(1 - pane)?.path ?: state.panes.getOrNull(pane)?.path ?: return@launch
        val suffixes = listOf("zip", "jar", "apk", "apks", "war", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar")
        val archives = state.panes.getOrNull(pane)?.selected.orEmpty()
            .filter { path -> suffixes.any { path.lowercase().endsWith(".$it") } }
            .toList()
        if (archives.isEmpty()) {
            message("No archive selected")
            return@launch
        }
        var files = 0
        var failed = 0
        for (archive in archives) {
            val result = runCatching { FileManagerRepository.extract(archive, dir) }
                .onFailure { FileManagerRepository.logFailure("extract $archive", it) }
                .getOrNull()
            if (result == null) failed++ else files += result.files
        }
        message("Extracted $files file(s)" + if (failed > 0) ", $failed failed" else "")
        clearSelection(pane)
        refresh(pane)
    }

    fun newFolder(pane: Int, name: String) = viewModelScope.launch(Dispatchers.IO) {
        if (name.isBlank() || name.contains('/')) {
            message("Invalid folder name")
            return@launch
        }
        val dir = _state.value.panes.getOrNull(pane)?.path ?: return@launch
        val path = dir.trimEnd('/') + "/" + name
        message(if (FileManagerRepository.mkdir(path)) "Created" else "Failed to create folder")
        refresh(pane)
    }

    fun newFile(pane: Int, name: String) = viewModelScope.launch(Dispatchers.IO) {
        if (name.isBlank() || name.contains('/')) {
            message("Invalid file name")
            return@launch
        }
        val dir = _state.value.panes.getOrNull(pane)?.path ?: return@launch
        val path = dir.trimEnd('/') + "/" + name
        message(if (FileManagerRepository.createFile(path)) "Created" else "Failed to create file")
        refresh(pane)
    }

    fun rename(from: String, newName: String) = viewModelScope.launch(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/')) {
            message("Invalid name")
            return@launch
        }
        val target = FileManagerRepository.parent(from).trimEnd('/') + "/" + newName
        val error = FileManagerRepository.rename(from, target)
        message(error ?: "Renamed")
        val pane = _state.value.activePane
        clearSelection(pane)
        refresh(pane)
    }

    /** Root package-manager install of an APK; result is surfaced via [message]. */
    fun installApk(
        path: String,
        allowDowngrade: Boolean = false,
        forceStop: Boolean = false,
    ): kotlinx.coroutines.Job = viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            _state.update { it.copy(busy = true) }
            message(ksuApp.getString(com.vorthas.yusu.R.string.installing_apk))
        }
        val outcome = FileManagerRepository.installApk(path, allowDowngrade, forceStop)
        var retry: (() -> Unit)? = null
        val msg: String = when (outcome) {
            is FileManagerRepository.InstallResult.Success ->
                ksuApp.getString(com.vorthas.yusu.R.string.install_success)

            is FileManagerRepository.InstallResult.Downgrade ->
                if (!allowDowngrade) {
                    // Auto-retry with -d once: the user explicitly tapped install.
                    retry = { installApk(path, allowDowngrade = true, forceStop = forceStop) }
                    ksuApp.getString(com.vorthas.yusu.R.string.installing_apk)
                } else "降级安装失败：${outcome.detail}"

            is FileManagerRepository.InstallResult.AppRunning ->
                if (!forceStop) {
                    retry = { installApk(path, allowDowngrade = allowDowngrade, forceStop = true) }
                    ksuApp.getString(com.vorthas.yusu.R.string.installing_apk)
                } else "应用正在运行且无法停止：${outcome.detail}"

            is FileManagerRepository.InstallResult.Unreadable ->
                "无法读取 APK（特权目录）：${outcome.detail}"
            is FileManagerRepository.InstallResult.SignatureMismatch ->
                "签名冲突，请先卸载旧版：${outcome.detail}"
            is FileManagerRepository.InstallResult.InsufficientStorage ->
                "存储空间不足：${outcome.detail}"
            is FileManagerRepository.InstallResult.ParseFailed ->
                "APK 解析失败：${outcome.detail}"
            is FileManagerRepository.InstallResult.Failed ->
                "${ksuApp.getString(com.vorthas.yusu.R.string.install_failed)}: " +
                    (outcome.code?.let { "$it " } ?: "") + outcome.detail
        }
        withContext(Dispatchers.Main) {
            _state.update { it.copy(busy = false) }
            message(msg)
            retry?.invoke()
        }
    }

    fun setPickMode(enabled: Boolean) = _state.update { it.copy(pickMode = enabled) }

    fun message(text: String) = _state.update { it.copy(message = text, busy = false) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun FileManagerUiState.withPane(
        pane: Int,
        block: (PaneUiState) -> PaneUiState,
    ): FileManagerUiState = copy(
        panes = panes.mapIndexed { index, value -> if (index == pane) block(value) else value }
    )
}
