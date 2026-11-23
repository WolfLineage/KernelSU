package com.vorthas.yusu.ui.screen.susfs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vorthas.yusu.R
import com.vorthas.yusu.ui.theme.LocalEnableBlur
import com.vorthas.yusu.ui.util.BlurredBar
import com.vorthas.yusu.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SusfsScreenMiuix(state: SusfsScreenState, actions: SusfsScreenActions) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(LocalEnableBlur.current)
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val snackbar = remember { SnackbarHostState() }
    val status = state.status

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onDismissMessage()
        }
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.susfs_title),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            val layoutDirection = LocalLayoutDirection.current
                            Icon(
                                modifier = Modifier.graphicsLayer {
                                    if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                                },
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
        popupHost = { },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = padding,
                overscrollEffect = null,
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text(
                            text = susfsStatusHeadline(status),
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                        )
                        Text(
                            text = susfsStatusSummary(status),
                            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp, end = 16.dp),
                        )
                        BasicComponent(
                            title = stringResource(R.string.susfs_refresh),
                            enabled = !state.busy,
                            startAction = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = colorScheme.onBackground) },
                            onClick = actions.onRefresh,
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        BasicComponent(
                            title = stringResource(R.string.susfs_apply_now),
                            enabled = !state.busy && status?.integrated == true,
                            startAction = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = colorScheme.onBackground) },
                            onClick = actions.onApply,
                        )
                        if (status?.installed == true && status.active == false && status.builtInKernel == false) {
                            BasicComponent(
                                title = stringResource(R.string.susfs_load_lkm),
                                enabled = !state.busy,
                                startAction = {
                                    Icon(Icons.Filled.LayersClear, contentDescription = null, tint = colorScheme.onBackground)
                                },
                                onClick = actions.onLoadLkm,
                            )
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        SwitchPreference(
                            title = stringResource(R.string.susfs_hide_mounts),
                            checked = status?.hideMounts == true,
                            enabled = !state.busy && status?.integrated == true,
                            startAction = { Icon(Icons.Filled.LayersClear, contentDescription = null, tint = colorScheme.onBackground) },
                            onCheckedChange = { actions.onToggle("HIDE_SUS_MNTS_NON_SU", it) },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.susfs_logging),
                            checked = status?.logging == true,
                            enabled = !state.busy && status?.integrated == true,
                            startAction = { Icon(Icons.Filled.Description, contentDescription = null, tint = colorScheme.onBackground) },
                            onCheckedChange = { actions.onToggle("ENABLE_LOG", it) },
                        )
                        SwitchPreference(
                            title = stringResource(R.string.susfs_avc_spoof),
                            checked = status?.avcSpoof == true,
                            enabled = !state.busy && status?.integrated == true,
                            startAction = { Icon(Icons.Filled.Security, contentDescription = null, tint = colorScheme.onBackground) },
                            onCheckedChange = { actions.onToggle("ENABLE_AVC_LOG_SPOOFING", it) },
                        )
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        BasicComponent(
                            title = stringResource(R.string.susfs_section_rules),
                            summary = stringResource(R.string.susfs_section_rules_summary, state.configs.size),
                            startAction = { Icon(Icons.Filled.Description, contentDescription = null, tint = colorScheme.onBackground) },
                            endActions = {
                                Icon(
                                    Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.graphicsLayer { rotationZ = if (state.rulesExpanded) 180f else 0f },
                                )
                            },
                            onClick = actions.onToggleRules,
                        )
                        if (state.rulesExpanded) {
                            state.configs.forEach { cfg ->
                                MiuixConfigEditor(cfg.name, susfsConfigLabel(cfg.name), state, actions)
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        BasicComponent(
                            title = stringResource(R.string.susfs_section_scripts),
                            summary = stringResource(
                                R.string.susfs_section_scripts_summary,
                                state.scripts.count { it.postFsData || it.bootCompleted },
                                state.scripts.size,
                            ),
                            startAction = { Icon(Icons.Filled.LayersClear, contentDescription = null, tint = colorScheme.onBackground) },
                            endActions = {
                                Icon(
                                    Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                    modifier = Modifier.graphicsLayer { rotationZ = if (state.scriptsExpanded) 180f else 0f },
                                )
                            },
                            onClick = actions.onToggleScripts,
                        )
                        if (state.scriptsExpanded) {
                            state.scripts.forEach { script ->
                                val label = susfsScriptLabel(script.name)
                                SwitchPreference(
                                    title = label.first,
                                    summary = "${stringResource(R.string.susfs_script_postfs)} · ${label.second}",
                                    checked = script.postFsData,
                                    enabled = !state.busy,
                                    onCheckedChange = { actions.onScriptStage(script.name, "postfs", it) },
                                )
                                SwitchPreference(
                                    title = label.first,
                                    summary = "${stringResource(R.string.susfs_script_boot)} · ${label.second}",
                                    checked = script.bootCompleted,
                                    enabled = !state.busy,
                                    onCheckedChange = { actions.onScriptStage(script.name, "bootcompleted", it) },
                                )
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        BasicComponent(
                            title = stringResource(R.string.susfs_reset_configs),
                            enabled = !state.busy && status?.integrated == true,
                            startAction = { Icon(Icons.Filled.Restore, contentDescription = null, tint = colorScheme.onBackground) },
                            onClick = actions.onResetConfigs,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixConfigEditor(
    name: String,
    label: String,
    state: SusfsScreenState,
    actions: SusfsScreenActions,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        TextField(
            value = state.config(name),
            onValueChange = { actions.onConfigValueChange(name, it) },
            label = label,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                text = stringResource(R.string.susfs_save_apply),
                enabled = !state.busy,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = { actions.onSaveConfig(name) },
            )
        }
    }
}
