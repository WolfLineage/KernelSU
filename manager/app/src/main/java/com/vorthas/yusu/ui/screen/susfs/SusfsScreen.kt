package com.vorthas.yusu.ui.screen.susfs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vorthas.yusu.R
import com.vorthas.yusu.data.susfs.SusfsStatus
import com.vorthas.yusu.ui.LocalUiMode
import com.vorthas.yusu.ui.UiMode
import com.vorthas.yusu.ui.navigation3.LocalNavigator
import com.vorthas.yusu.ui.viewmodel.SusfsViewModel

@Composable
fun SusfsScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SusfsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val state = SusfsScreenState(
        status = uiState.status,
        configs = uiState.configs,
        scripts = uiState.scripts,
        rulesExpanded = uiState.rulesExpanded,
        scriptsExpanded = uiState.scriptsExpanded,
        busy = uiState.busy,
        message = uiState.message,
    )
    val actions = SusfsScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onRefresh = viewModel::refresh,
        onApply = viewModel::apply,
        onLoadLkm = viewModel::loadLkm,
        onToggle = viewModel::setToggle,
        onConfigValueChange = viewModel::setConfigValue,
        onSaveConfig = viewModel::saveConfig,
        onScriptStage = viewModel::setScriptStage,
        onToggleRules = viewModel::toggleRules,
        onToggleScripts = viewModel::toggleScripts,
        onResetConfigs = viewModel::resetConfigs,
        onDismissMessage = { viewModel.setMessage(null) },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> SusfsScreenMiuix(state, actions)
        UiMode.Material -> SusfsScreenMaterial(state, actions)
    }
}

@Composable
fun susfsStatusHeadline(status: SusfsStatus?): String = when {
    status == null -> stringResource(R.string.susfs_status_loading)
    !status.integrated -> stringResource(R.string.susfs_status_not_integrated)
    !status.installed -> stringResource(R.string.susfs_status_not_installed)
    status.active -> stringResource(R.string.susfs_status_active)
    !status.builtInKernel && status.integrated -> stringResource(R.string.susfs_status_pending_load)
    else -> stringResource(R.string.susfs_status_inactive)
}

@Composable
fun susfsStatusSummary(status: SusfsStatus?): String {
    if (status == null) return stringResource(R.string.susfs_status_loading)
    val kmi = status.kmi.ifBlank { "-" }
    val kernel = if (status.builtInKernel) stringResource(R.string.susfs_builtin_kernel) else stringResource(R.string.susfs_lkm_mode)
    val version = status.version ?: "-"
    val variant = status.variant ?: "-"
    return stringResource(
        R.string.susfs_status_summary,
        kernel,
        kmi,
        version,
        variant,
        status.featureCount,
    )
}

@Composable
fun susfsConfigLabel(name: String): String = when (name) {
    "config.txt" -> stringResource(R.string.susfs_cfg_core)
    "uname.txt" -> stringResource(R.string.susfs_cfg_uname)
    "sus_paths.txt" -> stringResource(R.string.susfs_cfg_paths)
    "sus_paths_loop.txt" -> stringResource(R.string.susfs_cfg_paths_loop)
    "sus_maps.txt" -> stringResource(R.string.susfs_cfg_maps)
    "kstat_paths.txt" -> stringResource(R.string.susfs_cfg_kstat)
    "open_redirect.txt" -> stringResource(R.string.susfs_cfg_redirect)
    "cmdline_or_bootconfig.txt" -> stringResource(R.string.susfs_cfg_cmdline)
    else -> name.substringBeforeLast('.')
}

@Composable
fun susfsScriptLabel(name: String): Pair<String, String> = when (name) {
    "ReSuSFS_apply-cmdline-bootconfig.sh" -> stringResource(R.string.susfs_script_cmdline) to stringResource(R.string.susfs_script_cmdline_desc)
    "ReSuSFS_apply-kstat-add.sh" -> stringResource(R.string.susfs_script_kstat) to stringResource(R.string.susfs_script_kstat_desc)
    "ReSuSFS_apply-ksu-settings.sh" -> stringResource(R.string.susfs_script_ksu) to stringResource(R.string.susfs_script_ksu_desc)
    "ReSuSFS_apply-uname.sh" -> stringResource(R.string.susfs_script_uname) to stringResource(R.string.susfs_script_uname_desc)
    "ReSuSFS_apply-mount-hiding.sh" -> stringResource(R.string.susfs_script_mount) to stringResource(R.string.susfs_script_mount_desc)
    "ReSuSFS_apply-props.sh" -> stringResource(R.string.susfs_script_props) to stringResource(R.string.susfs_script_props_desc)
    "ReSuSFS_apply-settings.sh" -> stringResource(R.string.susfs_script_settings) to stringResource(R.string.susfs_script_settings_desc)
    "ReSuSFS_apply-sus-maps.sh" -> stringResource(R.string.susfs_script_maps) to stringResource(R.string.susfs_script_maps_desc)
    "ReSuSFS_apply-sus-paths-loop.sh" -> stringResource(R.string.susfs_script_tools) to stringResource(R.string.susfs_script_tools_desc)
    "ReSuSFS_apply-sus-paths.sh" -> stringResource(R.string.susfs_script_rom) to stringResource(R.string.susfs_script_rom_desc)
    "ReSuSFS_cleanup-markers.sh" -> stringResource(R.string.susfs_script_markers) to stringResource(R.string.susfs_script_markers_desc)
    else -> name.substringBeforeLast('.') to name.substringBeforeLast('.')
}
