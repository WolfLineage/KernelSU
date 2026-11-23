package com.vorthas.yusu.ui.screen.filemanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Bookmark
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            // Same Miuix TopAppBar component other pages use, so title size/position match.
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                title = stringResource(R.string.file_manager),
                actions = {
                    IconButton(onClick = actions.onShowBookmarks) {
                        Icon(Icons.Filled.Bookmark, contentDescription = "书签", tint = MiuixTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
            )
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
                    // Full-height 1dp rule with low alpha: separates panes without
                    // the tiny-dot artifact of a narrowed HorizontalDivider.
                    Box(
                        Modifier.width(1.dp).fillMaxHeight()
                            .background(MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    )
                    PaneMaterial(state, actions, 1, Modifier.weight(1f), compact, onRunScript)
                }
            }
            ActionBarMaterial(state, actions, showDialog = { dialog = it }, onSearch = { searchOpen = true })
        }
    }

    if (state.bookmarksOpen) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = dialogSurface(),
            titleContentColor = dialogText(),
            textContentColor = dialogText(),
            title = { Text("书签") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (state.bookmarkPaths.isEmpty()) {
                        Text("暂无书签。长按目录或文件可添加。", color = dialogText())
                    } else {
                        state.bookmarkPaths.forEach { path ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    actions.onNavigateBookmark(path)
                                    actions.onHideBookmarks()
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Bookmark, null,
                                    tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(path, color = dialogText(), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = actions.onHideBookmarks) { Text("关闭") } },
        )
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
