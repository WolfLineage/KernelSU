package com.vorthas.yusu.ui.screen.texteditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vorthas.yusu.data.filemanager.FileManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TextEditorUiState(
    val path: String = "",
    val content: String = "",
    val original: String = "",
    val loading: Boolean = true,
    val message: String? = null,
    val truncated: Boolean = false,
    val fileSize: Long = 0L,
) {
    val dirty: Boolean get() = content != original
}

data class TextEditorActions(
    val onBack: () -> Unit,
    val onContentChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onMessageShown: () -> Unit,
)

class TextEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(TextEditorUiState())
    val state: StateFlow<TextEditorUiState> = _state.asStateFlow()

    /**
     * Cap on the bytes handed to the editor. Whole-file reads of huge files blew the
     * Java heap AND — the actual crash — Compose text layout: a multi-MB string laid
     * out by BasicTextField overflows the 16777215px Canvas size limit
     * ("Size(1104 x 18780984) is out of range"). 4MB keeps the layout well under it;
     * larger files load a read-only preview of the first chunk.
     */
    private val maxEditBytes = 4 * 1024 * 1024

    fun load(path: String) = viewModelScope.launch(Dispatchers.IO) {
        val size = FileManagerRepository.fileSize(path)
        val capped = size > maxEditBytes
        val result = runCatching {
            if (capped) FileManagerRepository.readTextCapped(path, maxEditBytes)
            else FileManagerRepository.readText(path)
        }.map { text ->
            // Compose's text layout crashes on NUL chars with
            // "IllegalArgumentException: Illegal character (U+0)" — any binary file
            // opened as text contains them, and other C0 control bytes (VT, FF, ESC…)
            // are equally unsafe in a text field. Replace them all (except \n \r \t)
            // with the standard replacement char.
            text.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "\uFFFD")
        }
        result.fold(
            onSuccess = { text ->
                _state.update {
                    TextEditorUiState(
                        path = path,
                        content = text,
                        original = text,
                        loading = false,
                        truncated = capped,
                        fileSize = size,
                        // The editor screen renders a persistent banner for truncation;
                        // a toast would be too easy to miss and add nothing.
                        message = null,
                    )
                }
            },
            onFailure = { error ->
                FileManagerRepository.logFailure("read $path", error)
                _state.update {
                    TextEditorUiState(
                        path = path,
                        loading = false,
                        fileSize = size,
                        message = "Cannot open as UTF-8 text: ${error.message ?: "unknown error"}",
                    )
                }
            },
        )
    }

    fun setContent(content: String) = _state.update { it.copy(content = content) }

    fun save() = viewModelScope.launch(Dispatchers.IO) {
        val state = _state.value
        if (state.path.isBlank() || !state.dirty) return@launch
        // A truncated preview must NEVER be written back — it would destroy the file.
        if (state.truncated) {
            _state.update { it.copy(message = "文件过大，当前为只读预览，无法保存") }
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

    fun messageShown() = _state.update { it.copy(message = null) }
}
