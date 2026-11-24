package com.vorthas.yusu.ui.screen.susfs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vorthas.yusu.R
import com.vorthas.yusu.ui.component.material.ExpressiveScaffold
import com.vorthas.yusu.ui.component.material.SegmentedColumn
import com.vorthas.yusu.ui.component.material.SegmentedListItem
import com.vorthas.yusu.ui.component.material.SegmentedSwitchItem
import com.vorthas.yusu.ui.component.material.SnackBarHost
import com.vorthas.yusu.ui.component.material.TopBarBackButton
import com.vorthas.yusu.ui.component.material.expressiveTopAppBarColors

@Composable
fun SusfsScreenMaterial(state: SusfsScreenState, actions: SusfsScreenActions) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbar = remember { SnackbarHostState() }
    val status = state.status

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onDismissMessage()
        }
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.susfs_title)) },
                navigationIcon = { TopBarBackButton(actions.onBack) },
                actions = {
                    IconButton(onClick = actions.onRefresh, enabled = !state.busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.susfs_refresh))
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(top = 16.dp, bottom = 13.dp),
                content = listOf {
                    SegmentedListItem(
                        headlineContent = { Text(susfsStatusHeadline(status)) },
                        supportingContent = { Text(susfsStatusSummary(status)) },
                        leadingContent = { Icon(Icons.Filled.Security, null) },
                    )
                },
            )

            SegmentedColumn(
                modifier = Modifier.padding(bottom = 13.dp),
                title = stringResource(R.string.susfs_section_actions),
                content = buildList {
                    add {
                        SegmentedListItem(
                            onClick = actions.onApply,
                            enabled = !state.busy && status?.active == true,
                            headlineContent = { Text(stringResource(R.string.susfs_apply_now)) },
                            leadingContent = { Icon(Icons.Filled.Refresh, null) },
                        )
                    }
                    if (status?.installed == true && status.active == false && status.builtInKernel == false) {
                        add {
                            SegmentedListItem(
                                onClick = actions.onLoadLkm,
                                enabled = !state.busy,
                                headlineContent = { Text(stringResource(R.string.susfs_load_lkm)) },
                                leadingContent = { Icon(Icons.Filled.LayersClear, null) },
                            )
                        }
                    }
                },
            )

            SegmentedColumn(
                modifier = Modifier.padding(bottom = 13.dp),
                title = stringResource(R.string.susfs_section_toggles),
                content = listOf(
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.LayersClear,
                            title = stringResource(R.string.susfs_hide_mounts),
                            checked = status?.hideMounts == true,
                            enabled = !state.busy && status?.active == true,
                            onCheckedChange = { actions.onToggle("HIDE_SUS_MNTS_NON_SU", it) },
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Description,
                            title = stringResource(R.string.susfs_logging),
                            checked = status?.logging == true,
                            enabled = !state.busy && status?.active == true,
                            onCheckedChange = { actions.onToggle("ENABLE_LOG", it) },
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.Security,
                            title = stringResource(R.string.susfs_avc_spoof),
                            checked = status?.avcSpoof == true,
                            enabled = !state.busy && status?.active == true,
                            onCheckedChange = { actions.onToggle("ENABLE_AVC_LOG_SPOOFING", it) },
                        )
                    },
                ),
            )

            val rulesRotation by animateFloatAsState(if (state.rulesExpanded) 180f else 0f)
            SegmentedColumn(
                modifier = Modifier.padding(bottom = 13.dp),
                content = buildList {
                    add {
                        SegmentedListItem(
                            onClick = actions.onToggleRules,
                            headlineContent = { Text(stringResource(R.string.susfs_section_rules)) },
                            supportingContent = { Text(stringResource(R.string.susfs_section_rules_summary, state.configs.size)) },
                            leadingContent = { Icon(Icons.Filled.Description, null) },
                            trailingContent = {
                                Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.graphicsLayer { rotationZ = rulesRotation })
                            },
                        )
                    }
                    if (state.rulesExpanded) {
                        state.configs.forEach { cfg ->
                            add {
                                ConfigEditor(
                                    name = cfg.name,
                                    label = susfsConfigLabel(cfg.name),
                                    state = state,
                                    actions = actions,
                                )
                            }
                        }
                    }
                },
            )

            val scriptsRotation by animateFloatAsState(if (state.scriptsExpanded) 180f else 0f)
            SegmentedColumn(
                modifier = Modifier.padding(bottom = 13.dp),
                content = buildList {
                    add {
                        SegmentedListItem(
                            onClick = actions.onToggleScripts,
                            headlineContent = { Text(stringResource(R.string.susfs_section_scripts)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.susfs_section_scripts_summary,
                                        state.scripts.count { it.postFsData || it.bootCompleted },
                                        state.scripts.size,
                                    )
                                )
                            },
                            leadingContent = { Icon(Icons.Filled.LayersClear, null) },
                            trailingContent = {
                                Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.graphicsLayer { rotationZ = scriptsRotation })
                            },
                        )
                    }
                    if (state.scriptsExpanded) {
                        state.scripts.forEach { script ->
                            val label = susfsScriptLabel(script.name)
                            add {
                                SegmentedSwitchItem(
                                    title = label.first,
                                    summary = "${stringResource(R.string.susfs_script_postfs)} · ${label.second}",
                                    checked = script.postFsData,
                                    enabled = !state.busy,
                                    onCheckedChange = { actions.onScriptStage(script.name, "postfs", it) },
                                )
                            }
                            add {
                                SegmentedSwitchItem(
                                    title = label.first,
                                    summary = "${stringResource(R.string.susfs_script_boot)} · ${label.second}",
                                    checked = script.bootCompleted,
                                    enabled = !state.busy,
                                    onCheckedChange = { actions.onScriptStage(script.name, "bootcompleted", it) },
                                )
                            }
                        }
                    }
                },
            )

            SegmentedColumn(
                modifier = Modifier.padding(bottom = 13.dp),
                content = listOf {
                    SegmentedListItem(
                        onClick = actions.onResetConfigs,
                        enabled = !state.busy && status?.active == true,
                        headlineContent = { Text(stringResource(R.string.susfs_reset_configs)) },
                        leadingContent = { Icon(Icons.Filled.Restore, null) },
                    )
                },
            )

            Text(
                text = stringResource(R.string.susfs_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfigEditor(
    name: String,
    label: String,
    state: SusfsScreenState,
    actions: SusfsScreenActions,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = state.config(name),
            onValueChange = { actions.onConfigValueChange(name, it) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
            enabled = !state.busy,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { actions.onSaveConfig(name) },
            enabled = !state.busy,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.susfs_save_apply))
        }
    }
}
