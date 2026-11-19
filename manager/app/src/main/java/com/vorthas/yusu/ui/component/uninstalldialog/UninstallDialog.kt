package com.vorthas.yusu.ui.component.uninstalldialog

import androidx.compose.runtime.Composable
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode

@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> UninstallDialogMiuix(show, onDismissRequest)
        UiMode.Material -> UninstallDialogMaterial(show, onDismissRequest)
    }
}
