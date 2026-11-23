package com.vorthas.yusu.ui.screen.texteditor

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vorthas.yusu.R
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextEditorScreenMaterial(state: TextEditorUiState, actions: TextEditorActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.path.substringAfterLast('/')) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
            )
        }
    ) { padding -> TextEditorContent(state, actions, Modifier.padding(padding)) }
}

@Composable
internal fun TextEditorScreenMiuix(state: TextEditorUiState, actions: TextEditorActions) {
    MiuixScaffold(
        topBar = { MiuixTopAppBar(title = state.path.substringAfterLast('/')) },
        popupHost = {},
    ) { padding -> TextEditorContent(state, actions, Modifier.padding(padding)) }
}

@Composable
internal fun TextEditorContent(state: TextEditorUiState, actions: TextEditorActions, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }
        if (state.truncated) {
            // Huge files load a read-only preview: a multi-MB string cannot be laid out
            // (the reported crash) and saving a truncated buffer would destroy the file.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "文件过大（${com.vorthas.yusu.data.filemanager.FileManagerRepository.formatSize(state.fileSize)}），" +
                        "已载入前 4 MB，此为只读预览",
                    color = androidx.compose.ui.graphics.Color(0xFFFFB74D),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "编辑并保存此预览不会写回原文件",
                    color = androidx.compose.ui.graphics.Color(0xFFFFB74D),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
        OutlinedTextField(
            value = state.content,
            onValueChange = { if (!state.truncated) actions.onContentChange(it) },
            readOnly = state.truncated,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (state.dirty) R.string.fm_unsaved else R.string.fm_no_changes),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = actions.onSave, enabled = state.dirty && !state.truncated) {
                Text(stringResource(R.string.fm_save))
            }
        }
    }
}

@Composable
fun TextEditorPage(path: String) {
    val navigator = LocalNavigator.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiMode = LocalUiMode.current
    val viewModel = viewModel<TextEditorViewModel>(key = "text-editor-$path")
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(path) { viewModel.load(path) }
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
    )

    when (uiMode) {
        UiMode.Miuix -> TextEditorScreenMiuix(state, actions)
        UiMode.Material -> TextEditorScreenMaterial(state, actions)
    }
}
