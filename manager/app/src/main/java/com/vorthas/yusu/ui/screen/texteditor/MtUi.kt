package com.vorthas.yusu.ui.screen.texteditor

import androidx.compose.material3.MaterialTheme
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours sampled from MT Manager's dark theme. */
object MtColors {
    val background = Color(0xFF1B1B1B)
    val surface = Color(0xFF2C2C2C)
    val surfaceLight = Color(0xFF373737)
    val dialog = Color(0xFF3B3B3B)
    val text = Color(0xFFE8E8E8)
    val muted = Color(0xFF9E9E9E)
    val accent = Color(0xFF4FA3FF)
    val folder = Color(0xFF42A5F5)
    val script = Color(0xFF3F72D6)
    val image = Color(0xFF26A69A)
    val archive = Color(0xFF9C6B3F)
    val keyword = Color(0xFF79B8FF)
    val string = Color(0xFFE8C07D)
    val comment = Color(0xFF6B7785)
}

/** Theme-aware chrome colours: the MT shell adapts to the app's material/miuix theme. */
@Composable
private fun mtSurface(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.background
    UiMode.Material -> MaterialTheme.colorScheme.surfaceContainer
}

@Composable
private fun mtSurfaceLight(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.surface
    UiMode.Material -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun mtText(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onBackground
    UiMode.Material -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun mtMuted(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    UiMode.Material -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun mtAccent(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.primary
    UiMode.Material -> MaterialTheme.colorScheme.primary
}

@Composable
private fun mtDialog(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.background
    UiMode.Material -> MaterialTheme.colorScheme.surfaceContainerHigh
}

/** MT archive page top bar: hamburger, truncated archive name + counts, overflow. */
@Composable
internal fun MtArchiveTopBar(title: String, info: String, onBack: () -> Unit, onMenu: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(mtSurface())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Menu, null, tint = mtText()) }
            Column(Modifier.weight(1f)) {
                Text(title, color = mtText(), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(info, color = mtMuted(), fontSize = 11.sp)
            }
            IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, null, tint = mtText()) }
        }
    }
}

/** MT editor top bar: hamburger, pin, undo, redo, save, edit, overflow. */
@Composable
internal fun MtEditorTopBar(
    title: String,
    statusLine: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    onMenu: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleEdit: () -> Unit,
    editing: Boolean,
    pinned: Boolean,
    onTogglePin: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(mtSurface())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Menu, null, tint = mtText()) }
            IconButton(onClick = onTogglePin) {
                Icon(Icons.Default.PushPin, null, tint = if (pinned) mtAccent() else mtMuted())
            }
            IconButton(onClick = onUndo) { Icon(Icons.Default.Undo, null, tint = mtText()) }
            IconButton(onClick = onRedo) { Icon(Icons.Default.Redo, null, tint = mtText()) }
            IconButton(onClick = onSave, enabled = canSave) {
                Icon(Icons.Default.Save, null, tint = if (canSave) mtAccent() else mtMuted())
            }
            IconButton(onClick = onToggleEdit) {
                Icon(Icons.Default.Edit, null, tint = if (editing) mtAccent() else mtText())
            }
            IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, null, tint = mtText()) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
            Text(title, color = mtText(), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusLine, color = mtMuted(), fontSize = 12.sp)
        }
    }
}

/** MT editor symbol bar: glyphs sit directly on the bar with no button chrome. */
@Composable
internal fun MtSymbolBar(onInsert: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(mtSurface()).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("→", "/", "+", "-", "*", "=", "<").forEach { symbol ->
            Text(
                text = symbol,
                color = mtAccent(),
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onInsert(symbol) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

/** MT archive page bottom toolbar: back, forward, new, swap panes, up. */
@Composable
internal fun MtArchiveBottomBar(onUp: () -> Unit, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(mtSurface()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = mtAccent()) }
        IconButton(onClick = onUp) { Icon(Icons.Default.ArrowUpward, null, tint = mtAccent()) }
    }
}

/** MT archive list row: coloured type square, name and secondary info. */
@Composable
internal fun MtArchiveRow(name: String, isDirectory: Boolean, info: String, onClick: () -> Unit) {
    // MT uses the same grey tiles everywhere; only the tint signals the file category.
    val color = when {
        isDirectory -> Color(0xFF4A4A4A)
        name.endsWith(".apk", true) || name.endsWith(".apks", true) -> Color(0xFF3AA83A)
        name.endsWith(".json", true) || name.endsWith(".xml", true) -> Color(0xFF1976D2)
        name.endsWith(".sh", true) || name.endsWith(".bash", true) || name.endsWith(".py", true) -> MtColors.script
        name.endsWith(".zip", true) || name.endsWith(".apk", true) || name.endsWith(".rar", true) -> MtColors.archive
        name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".webp", true) -> MtColors.image
        else -> Color(0xFF607D8B)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(mtSurfaceLight(), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                null,
                tint = color,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(name, color = mtText(), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (info.isNotEmpty()) Text(info, color = mtMuted(), fontSize = 11.sp)
        }
    }
}

/** One MT "open with" grid action. */
data class MtOpenAction(val label: String, val symbol: String, val color: Color, val key: String)

/**
 * MT "Open with..." chooser: title + search/help icons, a scrollable 3-column grid of
 * coloured function tiles, and a bottom "类型 / 关闭" button row. Layout and palette are
 * copied from the device's MT Manager (bin.mt.plus.canary).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MtOpenWithDialog(fileName: String, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    val actions = listOf(
        MtOpenAction("编辑文本", "T", Color(0xFF3F72D6), "text"),
        MtOpenAction("浏览压缩包", "▣", Color(0xFF9C6B3F), "archive"),
        MtOpenAction("查看图片", "▧", Color(0xFF26A69A), "image"),
        MtOpenAction("十六进制", "01", Color(0xFF607D8B), "hex"),
        MtOpenAction("播放音乐", "♪", Color(0xFFD32F2F), "audio"),
        MtOpenAction("播放视频", "▶", Color(0xFFEF6C00), "video"),
        MtOpenAction("Xml功能", "<>", Color(0xFF3F72D6), "xml"),
        MtOpenAction("Dex功能", "01", Color(0xFF3C9E9E), "dex"),
        MtOpenAction("Arsc功能", "≣", Color(0xFFC77A17), "arsc"),
        MtOpenAction("Apk信息", "A", Color(0xFF3AA83A), "apk_info"),
        MtOpenAction("Apks功能", "A", Color(0xFF9C6B3F), "apks"),
        MtOpenAction("Linux脚本", ">_", Color(0xFF3F72D6), "script"),
        MtOpenAction("查看字体", "Tt", Color(0xFF7E57C2), "font"),
        MtOpenAction("导入签名", "⚿", Color(0xFF5C7C8A), "sign"),
        MtOpenAction("插件信息", "⊞", Color(0xFF2E9E7B), "plugin"),
        MtOpenAction("HTML功能", "◎", Color(0xFF2196F3), "html"),
        MtOpenAction("Svg功能", "▧", Color(0xFF26A69A), "svg"),
        MtOpenAction("点九编辑", ".9", Color(0xFF26A69A), "ninepatch"),
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(6.dp), color = mtDialog()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("打开方式...", color = mtText(), fontSize = 20.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = mtText()) }
                    TextButton(onClick = onDismiss) { Text("?", color = mtMuted(), fontSize = 18.sp) }
                }
                Column(Modifier.fillMaxWidth().height(460.dp).verticalScroll(rememberScrollState())) {
                    actions.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { action ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onAction(action.key) }.padding(4.dp),
                                ) {
                                    Box(
                                        Modifier.size(52.dp).background(action.color, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(action.symbol, color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center)
                                    }
                                    Text(action.label, color = mtText(), fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onAction("type") }) { Text("类型", color = mtAccent()) }
                    TextButton(onClick = onDismiss) { Text("关闭", color = mtAccent()) }
                }
            }
        }
    }
}
