package com.vorthas.yusu.ui.screen.filemanager

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.vorthas.yusu.R
import com.vorthas.yusu.data.filemanager.FileManagerRepository
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TopTool(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp).padding(end = 2.dp),
        contentPadding = PaddingValues(0.dp),
    ) { Text(symbol, fontSize = 15.sp) }
}

@Composable
internal fun FileManagerScreenMiuix(
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
            Row(
                modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.file_manager),
                    style = MaterialTheme.typography.titleMedium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        popupHost = {},
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(bottom = if (bottomPadding == Dp.Unspecified) 0.dp else bottomPadding)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopTool("←", state.canBack.getOrNull(state.activePane) == true) {
                    actions.onPaneBack(state.activePane)
                }
                TopTool("→", state.canForward.getOrNull(state.activePane) == true) {
                    actions.onPaneForward(state.activePane)
                }
                TopTool("⇄", true, actions.onSwap)
                val chosen = state.panes.getOrNull(state.activePane)?.selected?.size ?: 0
                if (chosen > 0) {
                    Text(
                        "已选 $chosen",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val compact = maxWidth < 600.dp
                Row(modifier = Modifier.fillMaxSize()) {
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
                            Text("新建文件（关闭则新建文件夹）", fontSize = 12.sp)
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
