package com.vorthas.yusu.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The decoded user wallpaper, or null when no wallpaper is configured.
 * Provided once in MainActivity so every page sees the same bitmap.
 */
val LocalWallpaperBitmap = staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * Scaffolds must stop painting an opaque surface when a wallpaper is present,
 * otherwise the image is completely hidden behind them.
 */
@Composable
fun wallpaperContainerColor(fallback: Color): Color =
    if (LocalWallpaperBitmap.current != null) Color.Transparent else fallback

/**
 * Paints the wallpaper plus a theme-coloured scrim.
 *
 * The scrim is derived from the active colour scheme rather than plain black /
 * white: it pulls the photograph towards the same tonal family as the cards,
 * so cards, list items and top bars do not look like they were pasted on top of
 * an unrelated background. The gradient is slightly stronger at the top and
 * bottom where the system bars and the navigation bar live.
 */
@Composable
fun WallpaperBackdrop() {
    val bitmap = LocalWallpaperBitmap.current ?: return
    val surface = when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
        UiMode.Material -> MaterialTheme.colorScheme.surface
    }
    val dark = isInDarkTheme()
    val edge = surface.copy(alpha = if (dark) 0.90f else 0.92f)
    val center = surface.copy(alpha = if (dark) 0.66f else 0.72f)

    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(edge, center, edge)))
    )
}
