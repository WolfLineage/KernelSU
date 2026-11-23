package com.vorthas.yusu.ui.screen.filemanager

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.navigation3.LocalNavigator
import com.vorthas.yusu.ui.navigation3.Route

@Composable
fun FileManagerScreen(
    bottomPadding: Dp = Dp.Unspecified,
    onRunScript: (String) -> Unit = {},
) {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val context = LocalContext.current
    val viewModel = viewModel<FileManagerViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.panes.getOrNull(state.activePane)?.selected?.isNotEmpty() == true) {
        viewModel.clearSelection(state.activePane)
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    val actions = FileManagerActions(
        onBack = { navigator.pop() },
        onRefresh = { viewModel.refresh(state.activePane) },
        onNavigate = { pane, path -> viewModel.navigate(pane, path) },
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
        onOpenEditor = { path -> navigator.push(Route.TextEditor(path)) },
        onOpenEditorFirst = {
            state.panes.getOrNull(state.activePane)?.selected?.firstOrNull()?.let {
                navigator.push(Route.TextEditor(it))
            }
        },
        onOpenTerminal = {
            navigator.push(Route.Terminal(state.panes.getOrNull(state.activePane)?.path))
        },
        onInstallApk = { path -> viewModel.installApk(path) },
        onMessageShown = viewModel::messageShown,
    )

    when (uiMode) {
        UiMode.Miuix -> FileManagerScreenMiuix(state, actions, bottomPadding, onRunScript)
        UiMode.Material -> FileManagerScreenMaterial(state, actions, bottomPadding, onRunScript)
    }
}
