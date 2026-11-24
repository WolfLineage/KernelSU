package com.vorthas.yusu.ui.component

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vorthas.yusu.R
import com.vorthas.yusu.ui.component.dialog.rememberConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gate that asks for "Manage all files" access on first launch. The manager needs
 * this to open files from other apps and reveal their real download path without
 * a slow root-shell lookup or an internal cache copy.
 */
@Composable
fun AllFilesAccessGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val context = LocalContext.current
    val checked = remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Re-check when returning from Settings.
        checked.value = Environment.isExternalStorageManager()
    }
    val dialog = rememberConfirmDialog(
        onConfirm = {
            val intent = runCatching {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.getOrNull() ?: Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { launcher.launch(intent) }
        },
        onDismiss = { checked.value = true }, // "later": don't nag again this session
    )

    LaunchedEffect(Unit) {
        // Remove cache copies created by the old UriPathResolver — with all-files
        // access we reveal real paths and never need them.
        withContext(Dispatchers.IO) {
            runCatching { File(context.cacheDir, "opened").deleteRecursively() }
        }
        if (!Environment.isExternalStorageManager() && !checked.value) {
            dialog.showConfirm(
                title = context.getString(R.string.all_files_access_title),
                content = context.getString(R.string.all_files_access_message),
                confirm = context.getString(R.string.all_files_access_grant),
                dismiss = context.getString(R.string.all_files_access_later),
            )
        }
    }
}
