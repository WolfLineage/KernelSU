package com.vorthas.yusu.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vorthas.yusu.Natives
import com.vorthas.yusu.R
import com.vorthas.yusu.ui.LocalMainPagerState

@Composable
fun BottomBarMaterial(
    navigationBadge: NavigationBadgeState,
    enableFileManager: Boolean,
    enableTerminal: Boolean,
) {
    val fullFeatured = Natives.isFullFeatured()
    if (!fullFeatured) return

    val mainPagerState = LocalMainPagerState.current

    val items = visibleDestinations(enableFileManager, enableTerminal)

    ShortNavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        items.forEach { item ->
            val page = item.page
            val destination = item.destination
            val selected = mainPagerState.selectedPage == page
            ShortNavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(page)
                    }
                },
                icon = {
                    NavigationIconWithBadge(
                        icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = stringResource(destination.label),
                        badge = badgeFor(page, navigationBadge),
                    )
                },
                label = {},
            )
        }
    }
}

@Composable
internal fun NavigationIconWithBadge(
    icon: ImageVector,
    contentDescription: String?,
    badge: NavBadge?,
) {
    if (badge != null) {
        BadgedBox(
            badge = {
                when (badge.tone) {
                    BadgeTone.Alert -> Badge {
                        Text(badge.count.toString())
                    }

                    BadgeTone.Accent -> Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(badge.count.toString())
                    }
                }
            }
        ) {
            Icon(icon, contentDescription)
        }
    } else {
        Icon(icon, contentDescription)
    }
}
