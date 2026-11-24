package com.vorthas.yusu.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vorthas.yusu.Natives
import com.vorthas.yusu.ui.LocalMainPagerState
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NavigationRailMiuix(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    val fullFeatured = Natives.isFullFeatured()
    if (!fullFeatured) return

    val mainState = LocalMainPagerState.current

    val items = remember {
        BottomBarDestination.entries.map { destination ->
            Pair(destination.label, destination.icon)
        }
    }
    // On tablets the rail's expand/collapse morph animation (an internal per-frame
    // animateFloatAsState + custom Layout over every item) caused visible jank.
    // Keep the rail permanently expanded via the static-Boolean overload, which has
    // no toggle and no morph animation. Labels come from resources inside the row.
    NavigationRail(
        modifier = modifier,
        expanded = true,
        color = MiuixTheme.colorScheme.surface,
    ) {
        items.forEachIndexed { index, (labelRes, icon) ->
            val label = androidx.compose.ui.res.stringResource(labelRes)
            NavigationRailItem(
                selected = mainState.selectedPage == index,
                onClick = { mainState.animateToPage(index) },
                icon = icon,
                label = label,
                badge = navigationBadgeFor(index, navigationBadge),
            )
        }
    }
}
