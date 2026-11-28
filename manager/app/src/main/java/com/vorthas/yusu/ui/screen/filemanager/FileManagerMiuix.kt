package com.vorthas.yusu.ui.screen.filemanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Menu
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
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            // Same Miuix TopAppBar component other pages use, so title size/position match.
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                title = stringResource(if (state.pickMode) R.string.pick_file_title else R.string.file_manager),
                navigationIcon = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单", tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
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
                // Let the shared open() handle APKs (install dialog) and archives.
                actions.onOpenEditor(path)
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
