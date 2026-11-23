package com.vorthas.yusu.ui.screen.susfs

import androidx.compose.runtime.Immutable
import com.vorthas.yusu.data.susfs.SusfsScriptState
import com.vorthas.yusu.data.susfs.SusfsStatus

@Immutable
data class SusfsConfigUi(val name: String, val value: String)

@Immutable
data class SusfsScreenState(
    val status: SusfsStatus?,
    val configs: List<SusfsConfigUi>,
    val scripts: List<SusfsScriptState>,
    val rulesExpanded: Boolean,
    val scriptsExpanded: Boolean,
    val busy: Boolean,
    val message: String?,
) {
    fun config(name: String): String = configs.firstOrNull { it.name == name }?.value.orEmpty()
}

@Immutable
data class SusfsScreenActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onApply: () -> Unit,
    val onLoadLkm: () -> Unit,
    val onToggle: (String, Boolean) -> Unit,
    val onConfigValueChange: (String, String) -> Unit,
    val onSaveConfig: (String) -> Unit,
    val onScriptStage: (String, String, Boolean) -> Unit,
    val onToggleRules: () -> Unit,
    val onToggleScripts: () -> Unit,
    val onResetConfigs: () -> Unit,
    val onDismissMessage: () -> Unit,
)
