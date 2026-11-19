package com.vorthas.yusu.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val enableNavigationBadge: Boolean,
    val enableSwipeDismiss: Boolean,
    val pagerInterceptionMode: Int,
    val moduleDescriptionMaxLines: Int = 4,
    val wallpaperUri: String = "",
    val uiMode: UiMode,
)
