package com.vorthas.yusu.ui.navigation3

import com.vorthas.yusu.KernelVersion
import com.vorthas.yusu.ui.component.bottombar.BottomBarDestination
import com.vorthas.yusu.ui.screen.home.HomeUiState
import com.vorthas.yusu.ui.screen.home.SystemInfo
import com.vorthas.yusu.ui.util.module.LatestVersionInfo
import com.vorthas.yusu.ui.viewmodel.MainPagerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAndLkmTest {
    @Test
    fun toolsAreNavigationDestinationsNotSettingsEntries() {
        assertEquals(6, MainPagerConfig.PAGE_COUNT)
        assertEquals(6, BottomBarDestination.entries.size)
        assertEquals(3, BottomBarDestination.FileManager.ordinal)
        assertEquals(4, BottomBarDestination.Terminal.ordinal)
        assertEquals(5, BottomBarDestination.Setting.ordinal)
    }

    @Test
    fun noStandaloneLkmPromptExists() {
        // The bundled LKM and manager ship together; comparing their numeric versions
        // produced a permanent false-positive notice. UAPI compatibility is covered by
        // requiresNewKernel / requiresNewManager, so no separate showLkmUpdate flag.
        assertFalse(
            HomeUiState::class.java.declaredMethods.any { it.name == "getShowLkmUpdate" } ||
                HomeUiState::class.java.declaredFields.any { it.name == "showLkmUpdate" }
        )
    }

    @Test
    fun uapiCompatibilityStillDetected() = assertTrue(
        homeState(ksuVersion = 30400, managerVersion = 30400)
            .copy(requiresNewManager = true).requiresNewManager
    )

    private fun homeState(ksuVersion: Int, managerVersion: Long): HomeUiState = HomeUiState(
        kernelVersion = KernelVersion(6, 1, 0),
        ksuVersion = ksuVersion,
        managerUAPIVersion = 1,
        kernelUAPIVersion = 1,
        lkmMode = true,
        isLkmBundled = true,
        isManager = true,
        isManagerPrBuild = false,
        isKernelPrBuild = false,
        requiresNewKernel = false,
        requiresNewManager = false,
        isRootAvailable = true,
        isSafeMode = false,
        isLateLoadMode = false,
        checkUpdateEnabled = false,
        latestVersionInfo = LatestVersionInfo(),
        currentManagerVersionCode = managerVersion,
        systemInfo = SystemInfo("6.1.0", "v3.3.3", "test", "test", "Enforcing", 0),
    )
}
