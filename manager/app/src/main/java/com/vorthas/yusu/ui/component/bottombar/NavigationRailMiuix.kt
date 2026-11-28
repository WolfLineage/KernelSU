package com.vorthas.yusu.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vorthas.yusu.Natives
import com.vorthas.yusu.R
import com.vorthas.yusu.data.repository.SettingsRepositoryImpl
import com.vorthas.yusu.ui.LocalMainPagerState
import com.vorthas.yusu.ui.theme.LocalRailAnimating
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NavigationRailMiuix(
    navigationBadge: NavigationBadgeState,
    enableFileManager: Boolean,
    enableTerminal: Boolean,
    modifier: Modifier = Modifier,
) {
    val fullFeatured = Natives.isFullFeatured()
    if (!fullFeatured) return

    val mainState = LocalMainPagerState.current

    val visible = visibleDestinations(enableFileManager, enableTerminal)
    val items = visible.map { item ->
        Pair(stringResource(item.destination.label), item.destination.icon)
    }
    val settingsRepo = remember { SettingsRepositoryImpl() }
    val state = rememberNavigationRailState(
        initialValue = if (settingsRepo.navigationRailExpanded) {
            NavigationRailValue.Expanded
        } else {
            NavigationRailValue.Collapsed
        },
    )
    val railAnimating = LocalRailAnimating.current
    LaunchedEffect(state.currentValue) {
        settingsRepo.navigationRailExpanded = state.isExpanded
        // The rail morph spring settles in ~0.55s. Suspend the page-wide blur for
        // that window: the per-frame width change otherwise re-records the whole
        // page's backdrop and re-runs the 25f blur shader, which janks on tablets.
        // Keying on currentValue cancels the previous delay on rapid toggles.
        railAnimating.value = true
        delay(600)
        railAnimating.value = false
    }

    NavigationRail(
        modifier = modifier,
        state = state,
        color = MiuixTheme.colorScheme.surface,
        expandContentDescription = stringResource(R.string.nav_rail_expand),
        collapseContentDescription = stringResource(R.string.nav_rail_collapse),
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val page = visible[index].page
            NavigationRailItem(
                selected = mainState.selectedPage == page,
                onClick = {
                    mainState.animateToPage(page)
                },
                icon = icon,
                label = label,
                badge = navigationBadgeFor(page, navigationBadge),
            )
        }
    }
}
