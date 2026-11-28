package com.vorthas.yusu.ui.screen.texteditor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorthas.yusu.data.filemanager.FileKind
import com.vorthas.yusu.data.filemanager.FileManagerRepository
import com.vorthas.yusu.data.filemanager.FileViewerRepository
import com.vorthas.yusu.ksuApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** MT-style viewer modes. CONTINUOUS streams text or hex with no pagination controls. */
enum class FileViewMode { EDIT, CONTINUOUS, ARCHIVE, IMAGE, VIDEO, SQLITE, PDF }

data class TextEditorUiState(
    val path: String = "",
    val kind: FileKind = FileKind.TEXT,
    val mode: FileViewMode = FileViewMode.CONTINUOUS,
    val content: String = "",
    val original: String = "",
    /** Accumulated display lines for continuous text/hex browsing. */
    val lines: List<String> = emptyList(),
    /** Logical line number for each displayed row; continuation rows reuse their parent. */
    val lineNumbers: List<Int> = emptyList(),
    /** Fully styled rows built on IO so the main thread only measures them. */
    val lineTexts: List<AnnotatedString> = emptyList(),
    val isHex: Boolean = false,
    val loadedBytes: Long = 0L,
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val message: String? = null,
    val fileSize: Long = 0L,
    val archiveItems: List<FileViewerRepository.ArchiveItem> = emptyList(),
    /** Current directory inside the archive; empty string is the archive root. */
    val archiveDir: String = "",
    val sqliteTables: List<FileViewerRepository.SqliteTable> = emptyList(),
    val previewFile: java.io.File? = null,
    val pdfFile: java.io.File? = null,
    val videoFile: java.io.File? = null,
    val previewTitle: String = "",
    val displayName: String = "",
    val inArchive: Boolean = false,
    val userReadOnly: Boolean = false,
) {
    val dirty: Boolean get() = mode == FileViewMode.EDIT && content != original
    val readOnly: Boolean get() = mode != FileViewMode.EDIT || userReadOnly
}

data class TextEditorActions(
    val onBack: () -> Unit,
    val onContentChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onMessageShown: () -> Unit,
    val onLoadMore: () -> Unit,
    val onArchiveEntry: (String) -> Unit,
    val onArchiveDir: (String) -> Unit,
    val onArchiveUp: () -> Unit,
    val onArchiveBack: () -> Unit,
    val onSymbolInsert: (String) -> Unit = {},
    val onOpenWith: () -> Unit = {},
    val onToggleEdit: () -> Unit = {},
)

/**
 * Viewer state for files opened from the file manager.
 *
 * Large files are presented as one continuous, lazily appended list of lines (MT Manager
 * style): initial read is bounded and more data is appended as the user scrolls. There are
 * no page buttons, and a binary file is shown as raw hex from the first byte onward.
 */
class TextEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(TextEditorUiState())
    val state: StateFlow<TextEditorUiState> = _state.asStateFlow()

    companion object {
        private const val CHUNK_BYTES = 128 * 1024
        // Composable text measurement is O(n^2) in line length/width; a single
        // OutlinedTextField with hundreds of KB freezes the main thread. Only small
        // files may enter editable mode; everything else uses the chunked viewer.
        private const val MAX_EDIT_BYTES = 256 * 1024
        private const val MAX_WRAP_CHARS = 400
    }

    /** Trailing incomplete line from the previous chunk; stitched into the next read. */
    private var pendingLine = ""
    private var loadingMore = false
    // Only an explicit "hex" open-as request uses hex; every other large file is text.
    private var forceHex = false
    private var logicalLine = 1
    private var archiveEntriesPath: String? = null
    private var allArchiveItems: List<FileViewerRepository.ArchiveItem> = emptyList()

    /** Show immediate children of [dir], with directories first (MT-style ordering). */
    private fun childrenOf(dir: String): List<FileViewerRepository.ArchiveItem> {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        return allArchiveItems.mapNotNull { item ->
            val rest = item.name.removePrefix(prefix)
            if (!item.name.startsWith(prefix) || rest.isEmpty()) return@mapNotNull null
            val slash = rest.indexOf('/')
            if (slash < 0) {
                item.copy(name = rest)
            } else {
                FileViewerRepository.ArchiveItem(rest.substring(0, slash), -1L, true)
            }
        }.distinctBy { it.name }.sortedWith(
            compareByDescending<FileViewerRepository.ArchiveItem> { it.directory }.thenBy { it.name }
        )
    }

    fun load(path: String) = viewModelScope.launch(Dispatchers.IO) {
        val size = runCatching { FileManagerRepository.fileSize(path) }.getOrDefault(0L)
        val kind = runCatching { FileManagerRepository.sniff(path) }.getOrDefault(FileKind.BINARY)
        val mode = when {
            kind == FileKind.ARCHIVE -> FileViewMode.ARCHIVE
            kind == FileKind.IMAGE -> FileViewMode.IMAGE
            kind == FileKind.VIDEO -> FileViewMode.VIDEO
            kind == FileKind.SQLITE -> FileViewMode.SQLITE
            kind == FileKind.PDF -> FileViewMode.PDF
            kind == FileKind.TEXT && size <= MAX_EDIT_BYTES -> FileViewMode.EDIT
            else -> FileViewMode.CONTINUOUS
        }
        pendingLine = ""
        loadingMore = false
        forceHex = false
        logicalLine = 1
        _state.update {
            it.copy(
                path = path, kind = kind, mode = mode, fileSize = size, loading = true, message = null,
                lines = emptyList(), lineNumbers = emptyList(), lineTexts = emptyList(),
                loadedBytes = 0L, hasMore = false,
                content = "", original = "", archiveItems = emptyList(), sqliteTables = emptyList(),
                previewFile = null, pdfFile = null, videoFile = null, previewTitle = "", inArchive = false,
                // MT treats large text-ish files (including self-extracting scripts and
                // ELF payloads) as text in the unified editor. Hex is opt-in only.
                isHex = false,
            )
        }
        when (mode) {
            FileViewMode.EDIT -> {
                val result = runCatching { FileManagerRepository.readText(path) }
                result.fold(
                    onSuccess = { text ->
                        // Even a small file with one enormous line freezes Compose text
                        // measurement. Fall back to the chunked viewer in that case.
                        if (text.lineSequence().any { it.length > MAX_WRAP_CHARS * 4 }) {
                            logicalLine = 1
                            _state.update {
                                it.copy(mode = FileViewMode.CONTINUOUS, lines = emptyList(),
                                    lineNumbers = emptyList(), lineTexts = emptyList(), loadedBytes = 0L)
                            }
                            readChunk(path, kind, size, reset = true)
                        } else {
                            _state.update { it.copy(content = text, original = text, loading = false) }
                        }
                    },
                    onFailure = { error -> fail(path, size, error) },
                )
            }
            FileViewMode.CONTINUOUS -> readChunk(path, kind, size, reset = true)
            FileViewMode.ARCHIVE -> {
                archiveEntriesPath = path
                val result = runCatching { FileViewerRepository.listArchive(path, ksuApp.cacheDir) }
                result.fold(
                    onSuccess = { items ->
                        allArchiveItems = items
                        _state.update {
                            it.copy(archiveDir = "", archiveItems = childrenOf(""), loading = false)
                        }
                    },
                    onFailure = { error -> fail(path, size, error) },
                )
            }
            FileViewMode.IMAGE -> _state.update { it.copy(loading = false) }
            FileViewMode.VIDEO -> {
                val result = runCatching { FileViewerRepository.copyToCache(path, ksuApp.cacheDir, "video",
                    extension = path.substringAfterLast('.', "mp4").takeIf { it.length <= 5 } ?: "mp4") }
                result.fold(
                    onSuccess = { file -> _state.update { it.copy(videoFile = file, loading = false) } },
                    onFailure = { error -> fail(path, size, error) },
                )
            }
            FileViewMode.SQLITE -> {
                val result = runCatching { FileViewerRepository.sqlitePreview(path, ksuApp.cacheDir) }
                result.fold(
                    onSuccess = { tables -> _state.update { it.copy(sqliteTables = tables, loading = false) } },
                    onFailure = { error -> fail(path, size, error) },
                )
            }
            FileViewMode.PDF -> {
                val result = runCatching { FileViewerRepository.copyToCache(path, ksuApp.cacheDir, "pdf") }
                result.fold(
                    onSuccess = { file -> _state.update { it.copy(pdfFile = file, loading = false) } },
                    onFailure = { error -> fail(path, size, error) },
                )
            }
        }
    }

    /** Append the next bounded chunk; called automatically when the list is scrolled near its end. */
    fun loadMore() = viewModelScope.launch(Dispatchers.IO) {
        val current = _state.value
        if (current.mode != FileViewMode.CONTINUOUS || loadingMore || !current.hasMore) return@launch
        readChunk(current.path, if (forceHex) FileKind.BINARY else FileKind.TEXT,
            current.fileSize, reset = false)
    }

    private suspend fun readChunk(path: String, kind: FileKind, size: Long, reset: Boolean) {
        loadingMore = true
        val start = if (reset) 0L else _state.value.loadedBytes
        val result = runCatching {
            FileManagerRepository.readPage(path, start, CHUNK_BYTES, kind)
        }
        result.fold(
            onSuccess = { page ->
                val isHex = forceHex
                val language = SourceSyntax.langFor(path)
                // Build the final styled rows here on IO. Building AnnotatedString in
                // composition made fast scrolling through XML-heavy directories janky.
                fun row(text: String, number: Int): Triple<String, Int, AnnotatedString> {
                    val styled = buildAnnotatedString {
                        append(text)
                        if (!isHex && language != SourceSyntax.Lang.PLAIN) {
                            SourceSyntax.tokenize(text, language).forEach { token ->
                                if (token.start < token.end && token.end <= text.length) {
                                    addStyle(
                                        SpanStyle(color = Color(SourceSyntax.color(token.kind))),
                                        token.start,
                                        token.end,
                                    )
                                }
                            }
                        }
                    }
                    return Triple(text, number, styled)
                }
                // Each wrapped row keeps its logical line number. MT shows a long line on
                // several visual rows but never counts those rows as extra lines.
                val newRows: List<Triple<String, Int, AnnotatedString>> = if (isHex) {
                    page.hexDump().lineSequence().map { row(it, 0) }.toList()
                } else {
                    val combined = pendingLine + page.text
                    val parts = combined.split('\n')
                    // Keep only a bounded tail as pending: a huge single line must never
                    // accumulate without limit while more chunks are appended.
                    pendingLine = parts.lastOrNull().orEmpty().takeLast(MAX_WRAP_CHARS)
                    parts.dropLast(1).flatMap { line ->
                        val number = logicalLine++
                        wrapLongLine(line).map { row(it, number) }
                    }
                }
                val reachedEnd = start + page.bytes.size >= size
                val tailRows: List<Triple<String, Int, AnnotatedString>> =
                    if (!isHex && reachedEnd && pendingLine.isNotEmpty()) {
                        val number = logicalLine++
                        wrapLongLine(pendingLine).map { row(it, number) }.also { pendingLine = "" }
                    } else emptyList()
                val rows = newRows + tailRows
                _state.update { state ->
                    state.copy(
                        loading = false,
                        isHex = isHex,
                        lines = if (reset) rows.map { it.first } else state.lines + rows.map { it.first },
                        lineNumbers = if (reset) rows.map { it.second }
                            else state.lineNumbers + rows.map { it.second },
                        lineTexts = if (reset) rows.map { it.third }
                            else state.lineTexts + rows.map { it.third },
                        loadedBytes = start + page.bytes.size,
                        hasMore = !reachedEnd && page.bytes.isNotEmpty(),
                        message = null,
                    )
                }
            },
            onFailure = { error -> fail(path, size, error) },
        )
        loadingMore = false
    }

    /**
     * Split an arbitrarily long physical line into display-sized pieces. Rendering one
     * Text composable with hundreds of thousands of characters blocks the main thread
     * during measurement, which is the real cause of "large file" freezes/crashes.
     */
    private fun wrapLongLine(line: String): List<String> {
        if (line.length <= MAX_WRAP_CHARS) return listOf(line)
        val result = ArrayList<String>((line.length + MAX_WRAP_CHARS - 1) / MAX_WRAP_CHARS)
        var i = 0
        while (i < line.length) {
            val end = minOf(i + MAX_WRAP_CHARS, line.length)
            result += line.substring(i, end)
            i = end
        }
        return result
    }

    /** Enter a directory inside the archive (MT-style browsing). */
    fun openArchiveDir(name: String) {
        val current = _state.value.archiveDir
        val next = if (current.isEmpty()) name else "$current/$name"
        _state.update { it.copy(archiveDir = next, archiveItems = childrenOf(next)) }
    }

    fun archiveUp() {
        val current = _state.value.archiveDir
        val parent = current.substringBeforeLast('/', "")
        _state.update { it.copy(archiveDir = parent, archiveItems = childrenOf(parent)) }
    }

    fun openArchiveEntry(name: String) = viewModelScope.launch(Dispatchers.IO) {
        val dir = _state.value.archiveDir
        val fullName = if (dir.isEmpty()) name else "$dir/$name"
        val archive = archiveEntriesPath ?: _state.value.path
        archiveEntriesPath = archive
        _state.update { it.copy(loading = true, message = null) }
        val result = runCatching { FileViewerRepository.extractEntry(archive, fullName, ksuApp.cacheDir) }
        result.fold(
            onSuccess = { file ->
                _state.update { it.copy(previewFile = file, previewTitle = name, inArchive = true) }
                load(file.path)
            },
            onFailure = { error ->
                _state.update { it.copy(loading = false, message = "无法预览条目：$name（${error.message}）") }
            },
        )
    }

    fun backToArchive() = viewModelScope.launch(Dispatchers.IO) {
        val parent = archiveEntriesPath ?: return@launch
        _state.update { it.copy(previewFile = null, previewTitle = "", inArchive = false) }
        load(parent)
    }

    private fun fail(path: String, size: Long, error: Throwable) {
        FileManagerRepository.logFailure("read $path", error)
        _state.update {
            it.copy(path = path, loading = false, fileSize = size, message = "无法打开文件：${error.message ?: "未知错误"}")
        }
    }

    fun setContent(content: String) = _state.update { it.copy(content = content) }

    fun save() = viewModelScope.launch(Dispatchers.IO) {
        val state = _state.value
        if (state.path.isBlank() || !state.dirty) return@launch
        if (state.inArchive) {
            _state.update { it.copy(message = "归档内条目为只读预览，无法保存回原压缩包") }
            return@launch
        }
        val error = runCatching { FileManagerRepository.writeText(state.path, state.content) }.exceptionOrNull()
        if (error == null) {
            _state.update { it.copy(original = it.content, message = "Saved") }
        } else {
            FileManagerRepository.logFailure("save ${state.path}", error)
            _state.update { it.copy(message = "Save failed: ${error.message ?: "unknown error"}") }
        }
    }

    /** MT-style "open as": override the sniffed mode with the user's explicit choice. */
    fun openAs(choice: String, explicitPath: String? = null) {
        val path = explicitPath ?: _state.value.path
        if (path.isBlank()) return
        loadAs(path, choice)
    }

    private fun loadAs(path: String, choice: String) = viewModelScope.launch(Dispatchers.IO) {
        val size = runCatching { FileManagerRepository.fileSize(path) }.getOrDefault(0L)
        val mode = when (choice) {
            "archive" -> FileViewMode.ARCHIVE
            "image" -> FileViewMode.IMAGE
            "video" -> FileViewMode.VIDEO
            "hex" -> FileViewMode.CONTINUOUS
            else -> if (size <= MAX_EDIT_BYTES) FileViewMode.EDIT else FileViewMode.CONTINUOUS
        }
        pendingLine = ""
        loadingMore = false
        forceHex = choice == "hex"
        logicalLine = 1
        _state.update {
            it.copy(
                path = path, mode = mode, fileSize = size, loading = true, message = null,
                lines = emptyList(), lineNumbers = emptyList(), lineTexts = emptyList(),
                loadedBytes = 0L, hasMore = false,
                isHex = forceHex,
                archiveItems = emptyList(), archiveDir = "", content = "", original = "",
                previewFile = null, videoFile = null, inArchive = false,
            )
        }
        when (mode) {
            FileViewMode.CONTINUOUS -> readChunk(path,
                if (forceHex) FileKind.BINARY else FileKind.TEXT, size, reset = true)
            FileViewMode.ARCHIVE -> {
                archiveEntriesPath = path
                val result = runCatching { FileViewerRepository.listArchive(path, ksuApp.cacheDir) }
                result.fold(
                    onSuccess = { items ->
                        allArchiveItems = items
                        _state.update { it.copy(archiveDir = "", archiveItems = childrenOf(""), loading = false) }
                    },
                    onFailure = { error -> fail(path, size, error) },
                )
            }
            FileViewMode.EDIT -> {
                val result = runCatching { FileManagerRepository.readText(path) }
                result.fold(
                    onSuccess = { text -> _state.update { it.copy(content = text, original = text, loading = false) } },
                    onFailure = { error -> fail(path, size, error) },
                )
            }
            FileViewMode.IMAGE -> _state.update { it.copy(loading = false) }
            FileViewMode.VIDEO -> {
                val result = runCatching { FileViewerRepository.copyToCache(path, ksuApp.cacheDir, "video",
                    extension = path.substringAfterLast('.', "mp4").takeIf { it.length <= 5 } ?: "mp4") }
                result.fold(
                    onSuccess = { file -> _state.update { it.copy(videoFile = file, loading = false) } },
                    onFailure = { error -> fail(path, size, error) },
                )
            }
            else -> _state.update { it.copy(loading = false) }
        }
    }

    fun toggleEdit() = _state.update { it.copy(userReadOnly = !it.userReadOnly) }

    fun setDisplayName(name: String?) = _state.update { it.copy(displayName = name.orEmpty()) }

    fun messageShown() = _state.update { it.copy(message = null) }
}
