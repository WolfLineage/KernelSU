package com.vorthas.yusu.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults.flingBehavior
import androidx.compose.foundation.pager.PagerDefaults.pageNestedScrollConnection
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vorthas.yusu.Natives
import com.vorthas.yusu.R
import com.vorthas.yusu.data.repository.applyUsbDebuggingPreference
import com.vorthas.yusu.ui.component.bottombar.BottomBar
import com.vorthas.yusu.ui.component.bottombar.MainPagerState
import com.vorthas.yusu.ui.component.bottombar.NavigationBadgeState
import com.vorthas.yusu.ui.component.bottombar.SideRail
import com.vorthas.yusu.ui.component.bottombar.rememberMainPagerState
import com.vorthas.yusu.ui.component.bottombar.useNavigationRail
import com.vorthas.yusu.ui.navigation3.IntentDispatcher
import com.vorthas.yusu.ui.navigation3.LocalNavigator
import com.vorthas.yusu.ui.navigation3.Navigator
import com.vorthas.yusu.ui.navigation3.Route
import com.vorthas.yusu.ui.navigation3.rememberNavigator
import com.vorthas.yusu.ui.screen.about.AboutScreen
import com.vorthas.yusu.ui.screen.appprofile.AppProfileScreen
import com.vorthas.yusu.ui.screen.colorpalette.ColorPaletteScreen
import com.vorthas.yusu.ui.screen.executemoduleaction.ExecuteModuleActionScreen
import com.vorthas.yusu.ui.screen.filemanager.FileManagerScreen
import com.vorthas.yusu.ui.screen.flash.FlashScreen
import com.vorthas.yusu.ui.screen.home.HomePager
import com.vorthas.yusu.ui.screen.install.InstallScreen
import com.vorthas.yusu.ui.screen.module.ModulePager
import com.vorthas.yusu.ui.screen.modulerepo.ModuleRepoDetailScreen
import com.vorthas.yusu.ui.screen.modulerepo.ModuleRepoScreen
import com.vorthas.yusu.ui.screen.scriptrun.ScriptRunScreen
import com.vorthas.yusu.ui.screen.settings.SettingPager
import com.vorthas.yusu.ui.screen.sulog.SulogScreen
import com.vorthas.yusu.ui.screen.susfs.SusfsScreen
import com.vorthas.yusu.ui.screen.superuser.SuperUserPager
import com.vorthas.yusu.ui.screen.terminal.TerminalPage
import com.vorthas.yusu.ui.screen.template.AppProfileTemplateScreen
import com.vorthas.yusu.ui.screen.templateeditor.TemplateEditorScreen
import com.vorthas.yusu.ui.screen.texteditor.TextEditorPage
import com.vorthas.yusu.ui.theme.KernelSUTheme
import com.vorthas.yusu.ui.theme.LocalColorMode
import com.vorthas.yusu.ui.theme.LocalEnableBlur
import com.vorthas.yusu.ui.theme.LocalEnableFloatingBottomBar
import com.vorthas.yusu.ui.theme.LocalEnableFloatingBottomBarBlur
import com.vorthas.yusu.ui.theme.LocalEnableNavigationBadge
import com.vorthas.yusu.ui.theme.LocalRailAnimating
import com.vorthas.yusu.ui.theme.LocalModuleDescriptionMaxLines
import com.vorthas.yusu.ui.util.getSuperuserCount
import com.vorthas.yusu.ui.util.install
import com.vorthas.yusu.ui.util.rememberBlurBackdrop
import com.vorthas.yusu.ui.util.rememberContentReady
import com.vorthas.yusu.ui.viewmodel.MainActivityViewModel
import com.vorthas.yusu.ui.viewmodel.MainPagerConfig
import com.vorthas.yusu.ui.viewmodel.ModuleViewModel
import com.vorthas.yusu.ui.viewmodel.SuperUserViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

class MainActivity : ComponentActivity() {

    private val intentChannel = Channel<Intent>(capacity = Channel.BUFFERED)
    private var contentReady = false
    /** Set while composed; hands a picked file Uri back to the calling app. */
    private var pickResultSender: ((String?) -> Unit)? = null
    private var splashStartedAt = 0L
    // External file opens skip the decorative splash delay so the jump feels instant.
    private var splashAnimationDurationMs = 500L


    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashStartedAt = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        // Opening a file from another app should land immediately, not wait out the splash.
        if (intent?.action == Intent.ACTION_VIEW ||
            intent?.action == Intent.ACTION_INSTALL_PACKAGE ||
            intent?.action == Intent.ACTION_GET_CONTENT
        ) {
            splashAnimationDurationMs = 0L
        }
        // The switch defaults to off; enforce it once the manager is opened so the
        // default also applies on devices where the boot receiver is disabled.
        lifecycleScope.launch(Dispatchers.IO) { applyUsbDebuggingPreference() }
        splashScreen.setKeepOnScreenCondition {
            !contentReady || SystemClock.uptimeMillis() - splashStartedAt < splashAnimationDurationMs
        }

        val isManager = Natives.isManager
        if (isManager && Natives.kernelUAPIVersion == Natives.managerUAPIVersion) install()

        if (savedInstanceState == null) intent?.let { intentChannel.trySend(it) }

        setContent {
            val viewModel = viewModel<MainActivityViewModel>()
            // Upload picker mode: hand the selected file back to the calling app.
            // StartPick is dispatched through IntentDispatcher (covers cold + warm start).
            pickResultSender = { path ->
                if (path == null) {
                    // User backed out of the picker.
                    setResult(android.app.Activity.RESULT_CANCELED)
                    viewModel.endPick()
                    finish()
                } else {
                    // Stage into our cache so the caller can read the content Uri.
                    // Keep the ORIGINAL file name (a fixed "picked" name loses the
                    // source identity and shows up as "picked.1" in WeChat/QQ).
                    val staged = runCatching {
                        val source = java.io.File(path)
                        val dir = java.io.File(cacheDir, "pick").apply { mkdirs() }
                        dir.listFiles()?.forEach { it.delete() }
                        val target = java.io.File(dir, source.name)
                        com.vorthas.yusu.data.filemanager.FileManagerRepository.openStream(path).use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        target
                    }.getOrNull()
                    val uri = staged?.let {
                        runCatching {
                            androidx.core.content.FileProvider.getUriForFile(
                                this@MainActivity, "${packageName}.fileprovider", it
                            )
                        }.getOrNull()
                    }
                    if (uri != null) {
                        setResult(
                            android.app.Activity.RESULT_OK,
                            Intent().setDataAndType(
                                uri,
                                contentResolver.getType(uri) ?: "application/octet-stream",
                            )
                            .putExtra("yusu_source_path", path)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        )
                        viewModel.endPick()
                        finish()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.open_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val uiMode = uiState.uiMode
            val darkMode = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val navigator = rememberNavigator(Route.Main)
            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) {
                        try {
                            uriHandler.openUri(uri)
                        } catch (_: IllegalArgumentException) {
                            Toast.makeText(context, R.string.external_link_open_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                LocalNavigator provides navigator,
                LocalActivityViewModel provides viewModel,
                LocalPickResult provides { path -> pickResultSender?.invoke(path) },
                LocalDensity provides density,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableBlur provides uiState.enableBlur,
                LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
                LocalEnableNavigationBadge provides uiState.enableNavigationBadge,
                LocalModuleDescriptionMaxLines provides uiState.moduleDescriptionMaxLines,
                LocalUiMode provides uiMode,
            ) {
                KernelSUTheme(appSettings = appSettings, uiMode = uiMode) {
                    com.vorthas.yusu.ui.component.AllFilesAccessGate()
                    IntentDispatcher(intentChannel = intentChannel)
                    val swipeDismiss = if (uiState.enableSwipeDismiss) {
                        if (LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                            NavSwipeDirection.RightToLeft
                        } else {
                            NavSwipeDirection.LeftToRight
                        }
                    } else {
                        NavSwipeDirection.None
                    }
                    val flashRoute = navigator.current() as? Route.Flash
                    var flashBackEnabled by remember(flashRoute) { mutableStateOf(false) }
                    val scriptRunRoute = navigator.current() as? Route.ScriptRun
                    var scriptRunBackEnabled by remember(scriptRunRoute) { mutableStateOf(true) }
                    // Read here, not inside the NavDisplay builder: NavDisplay only rebuilds its entries
                    // (and their swipeDismiss) when the builder lambda changes, so it must capture the value.
                    val scriptRunSwipeDismiss = if (scriptRunBackEnabled) swipeDismiss else NavSwipeDirection.None
                    val mainScreenEntry = @Composable {
                        MainScreen(
                            initialPage = selectedMainPage,
                            pagerInterceptionMode = uiState.pagerInterceptionMode,
                            onPageChanged = viewModel::setSelectedMainPage,
                        )
                    }

                    val navDisplay = @Composable {
                        NavDisplay(
                            backStack = navigator.backStack,
                            effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()),
                            onBack = {
                                when (val top = navigator.current()) {
                                    is Route.TemplateEditor -> {
                                        if (!top.readOnly) {
                                            navigator.setResult("template_edit", true)
                                        } else {
                                            navigator.pop()
                                        }
                                    }

                                    else -> navigator.pop()
                                }
                            }) {
                            entry<Route.Main>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.About>(swipeDismiss = swipeDismiss) { AboutScreen() }
                            entry<Route.Sulog>(swipeDismiss = swipeDismiss) { SulogScreen() }
                            entry<Route.Susfs>(swipeDismiss = swipeDismiss) { SusfsScreen() }
                            entry<Route.ColorPalette>(swipeDismiss = swipeDismiss) { ColorPaletteScreen() }
                            entry<Route.AppProfileTemplate>(swipeDismiss = swipeDismiss) { AppProfileTemplateScreen() }
                            entry<Route.TemplateEditor>(swipeDismiss = swipeDismiss) { key -> TemplateEditorScreen(key.template, key.readOnly) }
                            entry<Route.AppProfile>(swipeDismiss = swipeDismiss) { key -> AppProfileScreen(key.uid) }
                            entry<Route.ModuleRepo>(swipeDismiss = swipeDismiss) { ModuleRepoScreen() }
                            entry<Route.ModuleRepoDetail>(swipeDismiss = swipeDismiss) { key -> ModuleRepoDetailScreen(key.module) }
                            entry<Route.Install>(swipeDismiss = swipeDismiss) { InstallScreen() }
                            entry<Route.Flash>(swipeDismiss = if (flashBackEnabled) swipeDismiss else NavSwipeDirection.None) { key ->
                                FlashScreen(
                                    key.flashIt,
                                    onBackEnabledChange = { enabled ->
                                        if (navigator.current() == key) flashBackEnabled = enabled
                                    },
                                )
                            }
                            entry<Route.ExecuteModuleAction>(swipeDismiss = swipeDismiss) { key ->
                                ExecuteModuleActionScreen(
                                    key.moduleId,
                                    key.fromShortcut
                                )
                            }
                            entry<Route.ScriptRun>(swipeDismiss = scriptRunSwipeDismiss) { key ->
                                ScriptRunScreen(
                                    key.arg,
                                    onBackEnabledChange = { enabled ->
                                        if (navigator.current() == key) scriptRunBackEnabled = enabled
                                    },
                                )
                            }
                            entry<Route.FileManager>(swipeDismiss = swipeDismiss) { key ->
                                FileManagerScreen(initialPath = key.initialPath, requestId = key.requestId)
                            }
                            entry<Route.Terminal>(swipeDismiss = swipeDismiss) { key -> TerminalPage(key.path) }
                            entry<Route.TextEditor>(swipeDismiss = swipeDismiss) { key -> TextEditorPage(key.path, key.openAs, key.displayName) }
                            entry<Route.Home>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.SuperUser>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.Module>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                            entry<Route.Settings>(swipeDismiss = swipeDismiss) { mainScreenEntry() }
                        }
                    }

                    when (uiMode) {
                        UiMode.Material -> androidx.compose.material3.Scaffold(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) { navDisplay() }

                        UiMode.Miuix -> Scaffold { navDisplay() }
                    }
                    SideEffect { contentReady = true }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentChannel.trySend(intent)
    }
}

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> { error("LocalMainPagerState not provided") }

/** The activity-scoped ViewModel, shared across NavDisplay entries for external file opens. */
val LocalActivityViewModel = staticCompositionLocalOf<com.vorthas.yusu.ui.viewmodel.MainActivityViewModel> {
    error("LocalActivityViewModel not provided")
}

/** Hands a picked file path back to the app that launched the upload picker. */
val LocalPickResult = staticCompositionLocalOf<(String?) -> Unit> { {} }

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    initialPage: Int = 0,
    pagerInterceptionMode: Int = PagerInterceptionMode.CrossAxisInterceptor.ordinal,
    onPageChanged: (Int) -> Unit = {},
) {
    val navController = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val useNavigationRail = useNavigationRail(enableFloatingBottomBar)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MainPagerConfig.PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(
        pagerState = pagerState,
        animatePageChanges = !useNavigationRail,
    )
    // External "Open with": jump to the file-manager tab even if the main screen
    // was already composed on another tab. Key on the request seq so repeated
    // opens of the same file still switch tabs.
    val revealActivityVm = LocalActivityViewModel.current
    val revealSeq by revealActivityVm.revealRequestSeq.collectAsStateWithLifecycle()
    val pickMode by revealActivityVm.pickMode.collectAsStateWithLifecycle()
    val onPick = LocalPickResult.current
    LaunchedEffect(revealSeq) {
        if (revealSeq > 0) {
            // Snap instead of spring-animating: on cold external opens the animated
            // transition gets cancelled by recomposition and leaves the pager on page 0.
            mainPagerState.snapToPage(com.vorthas.yusu.ui.viewmodel.MainActivityViewModel.FILE_MANAGER_PAGE)
        }
    }
    val isFullFeatured = Natives.isFullFeatured()
    val pagerMode = PagerInterceptionMode.Native
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor
    var userScrollEnabled by remember(isFullFeatured) { mutableStateOf(isFullFeatured) }

    val enableNavigationBadge = LocalEnableNavigationBadge.current
    val badgeEnabled = enableNavigationBadge && isFullFeatured
    val moduleViewModel = viewModel<ModuleViewModel>()
    val moduleUiState by moduleViewModel.uiState.collectAsStateWithLifecycle()

    val superUserViewModel = viewModel<SuperUserViewModel>()
    val grantedUidCount by remember(superUserViewModel) {
        superUserViewModel.uiState
            .map { state -> state.groupedApps.count { it.anyAllowSu } }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(0)

    var startupPreloadStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isFullFeatured) {
        if (!isFullFeatured || startupPreloadStarted) {
            return@LaunchedEffect
        }

        moduleViewModel.initializePreferences()
        val moduleState = moduleViewModel.uiState.value
        if (!moduleState.hasLoaded) {
            if (!moduleState.isRefreshing) moduleViewModel.fetchModuleList()
            moduleViewModel.uiState.first { it.hasLoaded }
        }
        moduleViewModel.syncModuleUpdateInfo(moduleViewModel.uiState.value.modules)

        val superUserState = superUserViewModel.uiState.value
        if (!superUserState.hasLoaded) {
            superUserViewModel.initializePreferences()
            if (superUserState.isRefreshing) {
                superUserViewModel.uiState.first { it.hasLoaded }
            } else {
                superUserViewModel.loadAppList().join()
            }
        }

        startupPreloadStarted = true
    }

    // Loading the app list just for a badge is too expensive; read the kernel allowlist instead.
    var superuserCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(badgeEnabled, grantedUidCount) {
        superuserCount = if (badgeEnabled) withContext(Dispatchers.IO) { getSuperuserCount() } else 0
    }

    val navigationBadge = if (badgeEnabled) {
        NavigationBadgeState(
            superuserCount = superuserCount,
            moduleEnabledCount = moduleUiState.modules.count { it.enabled },
            moduleUpdatableCount = moduleUiState.updateInfo.count { it.value.downloadUrl.isNotBlank() },
        )
    } else {
        NavigationBadgeState()
    }
    val uiMode = LocalUiMode.current
    val surfaceColor = when (uiMode) {
        UiMode.Material -> MaterialTheme.colorScheme.surface // Blur is not used in Material, this is just a placeholder
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    val blurBackdrop = rememberBlurBackdrop(enableBlur)

    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage) {
        onPageChanged(settledPage)
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    MainScreenBackHandler(mainPagerState, navController)

    CompositionLocalProvider(
        LocalMainPagerState provides mainPagerState
    ) {
        val contentReady = rememberContentReady()
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
                HorizontalPager(
                    modifier = Modifier
                        .pagerGestureOverride(
                            pagerState = mainPagerState.pagerState,
                            mode = pagerMode,
                            enabled = userScrollEnabled,
                        )
                        .then(if (enableFloatingBottomBar && enableFloatingBottomBarBlur) Modifier.layerBackdrop(backdrop) else Modifier),
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = if (contentReady) 3 else 0,
                    overscrollEffect = null,
                    userScrollEnabled = userScrollEnabled && !interceptPagerGestures,
                    pageNestedScrollConnection = if (interceptPagerGestures) {
                        PagerGestureNestedScrollConnection
                    } else {
                        pageNestedScrollConnection(
                            state = mainPagerState.pagerState,
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        )
                    },
                    flingBehavior = flingBehavior(
                        state = mainPagerState.pagerState,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { page ->
                    val isCurrentPage = page == settledPage
                    when (page) {
                        0 -> if (contentReady || isCurrentPage) HomePager(navController, bottomInnerPadding, isCurrentPage)
                        1 -> if (contentReady || isCurrentPage) SuperUserPager(navController, bottomInnerPadding, isCurrentPage)
                        2 -> if (contentReady || isCurrentPage) ModulePager(bottomInnerPadding, isCurrentPage)
                        3 -> if (contentReady || isCurrentPage) FileManagerScreen(
                            bottomPadding = bottomInnerPadding,
                            usePendingReveal = true,
                            pickMode = pickMode,
                            onPick = onPick,
                            onRunScript = { command ->
                                mainPagerState.animateToPage(4)
                                com.vorthas.yusu.ui.screen.terminal.TerminalCommandBus.send(command)
                            },
                        )
                        4 -> if (contentReady || isCurrentPage) TerminalPage(
                            bottomPadding = bottomInnerPadding,
                            isVisible = isCurrentPage,
                        )
                        5 -> if (contentReady || isCurrentPage) SettingPager(navController, bottomInnerPadding, isCurrentPage)
                    }
                }
            }
        }

        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
            // Shared with page-level BlurredBar: skip the costly blur shader while the
            // rail morphs, without disabling the rail's expand/collapse itself.
            val railAnimating = remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalRailAnimating provides railAnimating) {
                when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row {
                        SideRail(navigationBadge)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Miuix -> Scaffold { _ ->
                    Row {
                        SideRail(navigationBadge)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }
            }
            } // CompositionLocalProvider(LocalRailAnimating)
        } else {
            val bottomBar = @Composable {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        navigationBadge = navigationBadge,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(
                    bottomBar = bottomBar,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }

                UiMode.Miuix -> Scaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }
            }
        }
    }
}


@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main && navController.backStackSize() == 1 && mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        }
    )
}
