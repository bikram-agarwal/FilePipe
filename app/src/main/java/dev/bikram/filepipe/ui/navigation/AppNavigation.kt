package dev.bikram.filepipe.ui.navigation

import android.app.Activity
import android.os.Build
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneExpansionStateKey
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeFloatingActionButton
import dev.bikram.filepipe.ui.components.FilePipeIconButton
import dev.bikram.filepipe.ui.components.FilePipeTextButton
import dev.bikram.filepipe.ui.components.ThemeColoredEmptyHistoryIllustration
import dev.bikram.filepipe.ui.components.ThemeColoredEmptyTrashIllustration
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import dev.bikram.filepipe.ui.screens.devoptions.DevOptionsScreen
import dev.bikram.filepipe.ui.screens.help.FaqScreen
import dev.bikram.filepipe.ui.screens.history.HistoryScreen
import dev.bikram.filepipe.ui.screens.history.HistorySection
import dev.bikram.filepipe.ui.screens.history.HistoryViewModel
import dev.bikram.filepipe.ui.screens.historydetail.HistoryDetailScreen
import dev.bikram.filepipe.ui.screens.onboarding.OnboardingPermissionsScreen
import dev.bikram.filepipe.ui.screens.onboarding.OnboardingRuleWizardScreen
import dev.bikram.filepipe.ui.screens.onboarding.OnboardingTitleScreen
import dev.bikram.filepipe.ui.screens.ruledetail.RuleDetailScreen
import dev.bikram.filepipe.ui.screens.rules.RulesScreen
import dev.bikram.filepipe.ui.screens.rules.RulesViewModel
import dev.bikram.filepipe.ui.screens.settings.SettingsScreen
import dev.bikram.filepipe.ui.screens.settings.SettingsSectionKey
import dev.bikram.filepipe.ui.screens.settings.SettingsSectionListPane
import dev.bikram.filepipe.ui.screens.settings.SettingsViewModel
import dev.bikram.filepipe.ui.screens.settings.launchAppShareChooser
import dev.bikram.filepipe.ui.screens.settings.settingsSectionKeyForHighlight
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurEnabled
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalReducedMotion
import dev.bikram.filepipe.ui.theme.LocalSnackbarHostState
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.ProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.RoundedPolygonShape
import dev.bikram.filepipe.ui.theme.pillShape
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import dev.bikram.filepipe.update.PlayInAppUpdateBannerUiState
import dev.bikram.filepipe.update.notificationDedupeKey
import kotlinx.coroutines.launch

private data class BottomNavItem(
    val screen: Screen,
    val label: Int,
    val symbolName: String,
)

private val bottomNavItems =
    listOf(
        BottomNavItem(
            screen = Screen.Rules,
            label = R.string.nav_rules,
            symbolName = "tune",
        ),
        BottomNavItem(
            screen = Screen.History,
            label = R.string.nav_history,
            symbolName = "history",
        ),
        BottomNavItem(
            screen = Screen.Settings,
            label = R.string.nav_settings,
            symbolName = "settings",
        ),
    )

private val mainTabRouteOrdinals: Map<String, Int> =
    bottomNavItems.mapIndexed { index, item -> item.screen.route to index }.toMap()

private sealed interface UpdateChromeState {
    data object Hidden : UpdateChromeState

    data object Available : UpdateChromeState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
        val indeterminateProgress: Boolean,
    ) : UpdateChromeState

    data object ReadyToInstall : UpdateChromeState
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun AppNavigation(
    hasSeenIntro: Boolean = true,
    introSeenAtLaunch: Boolean = hasSeenIntro,
    preferences: AppPreferences = AppPreferences(),
    pendingShortcutRepository: PendingShortcutRepository,
) {
    val playTap = rememberPlayTapSound()
    val navController = rememberNavController()
    val pendingOpenHistory by pendingShortcutRepository.pendingOpenHistory.collectAsStateWithLifecycle()
    val pendingHistoryId by pendingShortcutRepository.pendingHistoryDetailId.collectAsStateWithLifecycle()
    val pendingOpenSettingsUpdates by pendingShortcutRepository.pendingOpenSettingsForUpdates.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar =
        bottomNavItems.any {
            currentDestination?.hierarchy?.any { destination -> destination.route == it.screen.route } == true
        }
    val navigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val listDetailPaneNavigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val canUseListDetailPanes = navigationSuiteType != NavigationSuiteType.NavigationBar
    val useListDetailPanes =
        canUseListDetailPanes &&
            listDetailPaneNavigator.scaffoldDirective.maxHorizontalPartitions > 1
    val useNavigationSuiteScaffold = useListDetailPanes
    val showFloatingBottomBar = showBottomBar && !useNavigationSuiteScaffold

    val isRuleDetailRoute =
        currentDestination?.hierarchy?.any { destination ->
            destination.route == Screen.RuleDetail.route
        } == true
    val isHistoryDetailRoute =
        currentDestination?.hierarchy?.any { destination ->
            destination.route == Screen.HistoryDetail.route
        } == true
    val isFaqRoute =
        currentDestination?.hierarchy?.any { destination ->
            destination.route == Screen.Faq.route
        } == true
    val isDevOptionsRoute =
        currentDestination?.hierarchy?.any { destination ->
            destination.route == Screen.DevOptions.route
        } == true

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingBarHeight = 64.dp

    // Activity-scoped VMs for nav bar FAB actions
    val historyVm: HistoryViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val hasAnyHistory by historyVm.hasAnyHistory.collectAsStateWithLifecycle()
    val historySection by historyVm.section.collectAsStateWithLifecycle()
    val trashedRules by historyVm.trashedRules.collectAsStateWithLifecycle()
    val hasAnyTrashedRules = trashedRules.isNotEmpty()
    val updateInfo by settingsVm.updateInfo.collectAsStateWithLifecycle()
    val openUpdateSheetFromNotification by settingsVm.openUpdateSheetFromNotification.collectAsStateWithLifecycle()
    val playBannerState by settingsVm.playInAppUpdateBannerUiState.collectAsStateWithLifecycle()
    var dismissedUpdateBarKey by remember { mutableStateOf<String?>(null) }
    var settingsHighlightSection by remember { mutableStateOf<String?>(null) }
    var openNewRuleInPane by remember { mutableStateOf<(() -> Unit)?>(null) }

    val updateKey = updateInfo?.notificationDedupeKey()
    val updateAvailable = BuildConfig.SHOW_UPDATES && updateInfo != null && showFloatingBottomBar
    val updateFabState =
        if (!showFloatingBottomBar) {
            UpdateChromeState.Hidden
        } else {
            when (val currentPlayState = playBannerState) {
                is PlayInAppUpdateBannerUiState.Downloading -> {
                    UpdateChromeState.Downloading(
                        bytesDownloaded = currentPlayState.bytesDownloaded,
                        totalBytesToDownload = currentPlayState.totalBytesToDownload,
                        indeterminateProgress = currentPlayState.indeterminateProgress,
                    )
                }

                PlayInAppUpdateBannerUiState.ReadyToInstall -> {
                    UpdateChromeState.ReadyToInstall
                }

                PlayInAppUpdateBannerUiState.Hidden -> {
                    if (updateAvailable) {
                        UpdateChromeState.Available
                    } else {
                        UpdateChromeState.Hidden
                    }
                }
            }
        }
    val updateBarState =
        if (!showFloatingBottomBar) {
            UpdateChromeState.Hidden
        } else {
            when (val currentPlayState = playBannerState) {
                is PlayInAppUpdateBannerUiState.Downloading -> {
                    UpdateChromeState.Downloading(
                        bytesDownloaded = currentPlayState.bytesDownloaded,
                        totalBytesToDownload = currentPlayState.totalBytesToDownload,
                        indeterminateProgress = currentPlayState.indeterminateProgress,
                    )
                }

                PlayInAppUpdateBannerUiState.ReadyToInstall -> {
                    UpdateChromeState.ReadyToInstall
                }

                PlayInAppUpdateBannerUiState.Hidden -> {
                    if (updateAvailable && updateKey != dismissedUpdateBarKey) {
                        UpdateChromeState.Available
                    } else {
                        UpdateChromeState.Hidden
                    }
                }
            }
        }
    val floatingUpdateBarExtraHeight = if (updateBarState != UpdateChromeState.Hidden) 72.dp else 0.dp
    val scrimHeight = navBarInset + floatingBarHeight + 24.dp + floatingUpdateBarExtraHeight

    /** Extra top blur under History filter chips (must match [isHistoryFilterRoute] detection). */
    val historyFilterChipsBand = 96.dp
    val isHistoryFilterRoute =
        currentDestination?.hierarchy?.any { destination ->
            destination.route == Screen.History.route ||
                (destination.route?.startsWith("history_for_rule") == true)
        } == true
    val fullScreenBottomBlurShort = navBarInset + 48.dp
    val fullScreenBottomBlurRuleEdit = navBarInset + 88.dp
    val bottomBlurHeightDp =
        when {
            isFaqRoute -> 0.dp
            showFloatingBottomBar -> scrimHeight
            showBottomBar -> navBarInset + 96.dp
            isRuleDetailRoute -> fullScreenBottomBlurRuleEdit
            else -> fullScreenBottomBlurShort
        }
    val primaryTabContentPadding =
        PaddingValues(bottom = if (showFloatingBottomBar) scrimHeight else navBarInset + 96.dp)
    val density = LocalDensity.current
    val bottomBlurHeightPx = with(density) { bottomBlurHeightDp.toPx() }

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current
    val navSpatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val navFadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val navFadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val devOptionsFadeSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())

    val openSettingsRoot: () -> Unit = {
        if (navController.currentDestination?.route != Screen.Settings.route) {
            val poppedToSettings = navController.popBackStack(Screen.Settings.route, inclusive = false)
            if (!poppedToSettings) {
                navController.navigate(Screen.Settings.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
            }
        }
    }

    // Lock from disk-backed snapshot (MainActivity) so the first frame does not use
    // AppPreferences.DEFAULT.hasSeenIntro (false) and force onboarding on every cold start.
    // Still stable for the activity so live hasSeenIntro updates do not change startDestination.
    val lockedNavStartDestination =
        remember(introSeenAtLaunch) {
            if (introSeenAtLaunch) Screen.Rules.route else Screen.OnboardingTitle.route
        }

    LaunchedEffect(hasSeenIntro, preferences.updateCheckSchedule) {
        if (hasSeenIntro &&
            BuildConfig.SHOW_UPDATES &&
            preferences.updateCheckSchedule == UpdateCheckSchedule.AT_APP_START
        ) {
            settingsVm.checkForUpdate(silent = true)
        }
    }

    LaunchedEffect(hasSeenIntro, pendingOpenSettingsUpdates, navController) {
        if (!pendingOpenSettingsUpdates || !hasSeenIntro) return@LaunchedEffect
        openSettingsRoot()
        settingsVm.flagOpenUpdateSheetFromNotification()
        pendingShortcutRepository.clearPendingOpenSettingsForUpdates()
    }

    LaunchedEffect(hasSeenIntro, openUpdateSheetFromNotification, navController, currentDestination?.route) {
        if (!openUpdateSheetFromNotification || !hasSeenIntro) return@LaunchedEffect
        openSettingsRoot()
    }

    LaunchedEffect(hasSeenIntro, pendingOpenHistory, navController) {
        if (!pendingOpenHistory || !hasSeenIntro) return@LaunchedEffect
        navController.navigate(Screen.History.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        pendingShortcutRepository.clearPendingOpenHistory()
    }

    LaunchedEffect(hasSeenIntro, pendingHistoryId, navController) {
        val historyId = pendingHistoryId ?: return@LaunchedEffect
        if (!hasSeenIntro) return@LaunchedEffect
        navController.navigate(Screen.HistoryDetail.createRoute(historyId)) {
            launchSingleTop = true
        }
        pendingShortcutRepository.clearPendingHistoryDetail()
    }

    val navRoute = navBackStackEntry?.destination?.route
    val primaryTabRoute =
        navRoute != null &&
            (
                navRoute == Screen.Rules.route ||
                    navRoute == Screen.History.route ||
                    navRoute == Screen.Settings.route
            )
    val topBlurSmallChrome = statusBarInset + 56.dp
    val topBlurHeightDp =
        if (navRoute == Screen.Rules.route || navRoute == Screen.Settings.route) {
            0.dp
        } else if (isDevOptionsRoute) {
            topBlurSmallChrome + 48.dp
        } else if (isHistoryFilterRoute) {
            topBlurSmallChrome + historyFilterChipsBand
        } else {
            topBlurSmallChrome
        }
    val topBlurHeightPx = with(density) { topBlurHeightDp.toPx() }
    val progressiveBlurStyle: ProgressiveBlurStyle? =
        run {
            val blurRadius =
                if (preferences.progressiveBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when {
                        isRuleDetailRoute || isFaqRoute || isDevOptionsRoute -> 56f
                        isHistoryFilterRoute -> 54f
                        else -> 44f
                    }
                } else {
                    0f
                }
            val overlayAlpha =
                when {
                    isRuleDetailRoute -> 0.38f
                    isFaqRoute -> 0.38f
                    isDevOptionsRoute -> 0.42f
                    isHistoryDetailRoute -> 0.30f
                    isHistoryFilterRoute -> 0.52f
                    navRoute == Screen.Rules.route -> 0.46f
                    navRoute == Screen.Settings.route -> 0.40f
                    else -> 0.34f
                }
            val primaryTabBottomBlurBoost =
                showFloatingBottomBar &&
                    (
                        navRoute == Screen.Rules.route ||
                            navRoute == Screen.History.route ||
                            navRoute == Screen.Settings.route
                    )
            val overlayAlphaBottom =
                if (primaryTabBottomBlurBoost) {
                    (overlayAlpha + 0.14f).coerceAtMost(0.58f)
                } else {
                    overlayAlpha
                }

            val finalOverlayAlpha =
                if (!preferences.progressiveBlurEnabled) {
                    (overlayAlpha + 0.15f).coerceAtMost(0.65f)
                } else {
                    overlayAlpha
                }
            val finalOverlayAlphaBottom =
                if (!preferences.progressiveBlurEnabled) {
                    (overlayAlphaBottom + 0.15f).coerceAtMost(0.65f)
                } else {
                    overlayAlphaBottom
                }

            ProgressiveBlurStyle(
                topHeightPx = topBlurHeightPx,
                bottomHeightPx = bottomBlurHeightPx,
                blurRadius = blurRadius,
                overlayAlpha = finalOverlayAlpha,
                overlayAlphaBottom = finalOverlayAlphaBottom,
            )
        }

    val currentTab =
        bottomNavItems
            .find { item ->
                currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
            }?.screen

    if (showClearHistoryDialog && hasAnyHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                FilePipeTextButton(onClick = {
                    showClearHistoryDialog = false
                    historyVm.clearAllHistory()
                }) { Text(stringResource(R.string.history_clear)) }
            },
            dismissButton = {
                FilePipeTextButton(onClick = {
                    showClearHistoryDialog = false
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showEmptyTrashDialog && hasAnyTrashedRules) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text(stringResource(R.string.history_trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.history_trash_empty_confirm_message)) },
            confirmButton = {
                FilePipeTextButton(onClick = {
                    showEmptyTrashDialog = false
                    historyVm.emptyTrashForever()
                }) {
                    Text(
                        text = stringResource(R.string.delete_forever),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                FilePipeTextButton(onClick = {
                    showEmptyTrashDialog = false
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val effectiveUseGradientBackground =
        preferences.useGradientBackground && preferences.themeMode != AppThemeMode.BLACK
    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalUseGradientBackground provides effectiveUseGradientBackground,
        LocalProgressiveBlurEnabled provides preferences.progressiveBlurEnabled,
        LocalProgressiveBlurStyle provides progressiveBlurStyle,
    ) {
        val navigationContent: @Composable () -> Unit = {
            val hostContext = LocalContext.current
            val openUpdateSheetFromChrome = {
                settingsVm.flagOpenUpdateSheetFromRulesPromo()
                navController.navigate(Screen.Settings.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            val installReadyUpdate = {
                settingsVm.completePlayFlexibleUpdateIfReady(hostContext as? Activity)
            }
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    SharedTransitionLayout(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    ) {
                        NavHost(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent),
                            navController = navController,
                            startDestination = lockedNavStartDestination,
                            enterTransition = {
                                primaryTabEnterTransition(
                                    reducedMotion = reducedMotion,
                                    verticalMotion = useNavigationSuiteScaffold,
                                    spatialSpec = navSpatialSpec,
                                    fadeInSpec = navFadeInSpec,
                                )
                                    ?: if (reducedMotion) {
                                        EnterTransition.None
                                    } else {
                                        slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                            fadeIn(animationSpec = navFadeInSpec)
                                    }
                            },
                            exitTransition = {
                                primaryTabExitTransition(
                                    reducedMotion = reducedMotion,
                                    verticalMotion = useNavigationSuiteScaffold,
                                    spatialSpec = navSpatialSpec,
                                    fadeOutSpec = navFadeOutSpec,
                                )
                                    ?: if (reducedMotion) {
                                        ExitTransition.None
                                    } else {
                                        slideOutHorizontally(animationSpec = navSpatialSpec) { -it / 3 } +
                                            fadeOut(animationSpec = navFadeOutSpec)
                                    }
                            },
                            popEnterTransition = {
                                primaryTabEnterTransition(
                                    reducedMotion = reducedMotion,
                                    verticalMotion = useNavigationSuiteScaffold,
                                    spatialSpec = navSpatialSpec,
                                    fadeInSpec = navFadeInSpec,
                                )
                                    ?: if (reducedMotion) {
                                        EnterTransition.None
                                    } else {
                                        slideInHorizontally(animationSpec = navSpatialSpec) { -it } +
                                            fadeIn(animationSpec = navFadeInSpec)
                                    }
                            },
                            popExitTransition = {
                                primaryTabExitTransition(
                                    reducedMotion = reducedMotion,
                                    verticalMotion = useNavigationSuiteScaffold,
                                    spatialSpec = navSpatialSpec,
                                    fadeOutSpec = navFadeOutSpec,
                                )
                                    ?: if (reducedMotion) {
                                        ExitTransition.None
                                    } else {
                                        slideOutHorizontally(animationSpec = navSpatialSpec) { it } +
                                            fadeOut(animationSpec = navFadeOutSpec)
                                    }
                            },
                        ) {
                            val sharedTransitionScope = this@SharedTransitionLayout
                            composable(
                                route = Screen.OnboardingTitle.route,
                            ) {
                                OnboardingTitleScreen(
                                    onLetsBegan = {
                                        navController.navigate(Screen.OnboardingPermissions.route)
                                    },
                                )
                            }

                            composable(Screen.OnboardingPermissions.route) {
                                OnboardingPermissionsScreen(
                                    onContinue = {
                                        navController.navigate(Screen.OnboardingRuleWizard.route) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenStorageAccessFaq = {
                                        navController.navigate(Screen.Faq.createRoute(Screen.Faq.FOCUS_STORAGE_ACCESS))
                                    },
                                )
                            }

                            composable(Screen.OnboardingRuleWizard.route) {
                                OnboardingRuleWizardScreen(
                                    onBackToPermissions = {
                                        navController.popBackStack()
                                    },
                                    onUseTemplate = { templateIndex ->
                                        navController.navigate(Screen.Rules.route) {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                        }
                                        navController.navigate(
                                            Screen.RuleDetail.createRoute(
                                                templateIndex = templateIndex,
                                                skipTemplatePicker = true,
                                            ),
                                        )
                                        settingsVm.markIntroSeen()
                                    },
                                    onStartBlank = {
                                        navController.navigate(Screen.Rules.route) {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                        }
                                        navController.navigate(
                                            Screen.RuleDetail.createRoute(skipTemplatePicker = true),
                                        )
                                        settingsVm.markIntroSeen()
                                    },
                                    onSkip = {
                                        navController.navigate(Screen.Rules.route) {
                                            popUpTo(navController.graph.id) { inclusive = true }
                                        }
                                        settingsVm.markIntroSeen()
                                    },
                                )
                            }

                            composable(Screen.Rules.route) {
                                if (useListDetailPanes) {
                                    RulesTwoPaneRoute(
                                        contentPadding = primaryTabContentPadding,
                                        onOpenRuleDetail = { ruleId ->
                                            navController.navigate(Screen.RuleDetail.createRoute(ruleId))
                                        },
                                        onOpenNewRule = {
                                            navController.navigate(Screen.RuleDetail.createRoute())
                                        },
                                        onOpenFaq = { navController.navigate(Screen.Faq.createRoute()) },
                                        onNavigateToHistoryDetail = { historyId ->
                                            navController.navigate(Screen.HistoryDetail.createRoute(historyId)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onNavigateToHistoryList = {
                                            navController.navigate(Screen.History.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onNavigateToRuleHistory = { ruleId ->
                                            navController.navigate(Screen.HistoryForRule.createRoute(ruleId))
                                        },
                                        onRegisterOpenNewRuleInPane = { openNewRuleInPane = it },
                                        settingsViewModel = settingsVm,
                                    )
                                } else {
                                    RulesScreen(
                                        contentPadding = primaryTabContentPadding,
                                        onEditRule = { ruleId -> navController.navigate(Screen.RuleDetail.createRoute(ruleId)) },
                                        onNavigateToHistoryDetail = { historyId ->
                                            navController.navigate(Screen.HistoryDetail.createRoute(historyId)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onNavigateToHistoryList = {
                                            navController.navigate(Screen.History.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onNavigateToRuleHistory = { ruleId ->
                                            navController.navigate(Screen.HistoryForRule.createRoute(ruleId))
                                        },
                                    )
                                }
                            }
                            composable(
                                route = Screen.RuleDetail.route,
                                arguments =
                                    listOf(
                                        navArgument(Screen.RuleDetail.ARG_RULE_ID) {
                                            type = NavType.LongType
                                        },
                                        navArgument(Screen.RuleDetail.ARG_TEMPLATE_INDEX) {
                                            type = NavType.IntType
                                            defaultValue = -1
                                        },
                                        navArgument(Screen.RuleDetail.ARG_SKIP_TEMPLATE_PICKER) {
                                            type = NavType.BoolType
                                            defaultValue = false
                                        },
                                    ),
                            ) {
                                RuleDetailScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onOpenFaq = { navController.navigate(Screen.Faq.createRoute()) },
                                )
                            }
                            composable(Screen.History.route) {
                                if (useListDetailPanes) {
                                    HistoryTwoPaneRoute(
                                        contentPadding = primaryTabContentPadding,
                                        onOpenHistoryDetail = { historyId ->
                                            navController.navigate(Screen.HistoryDetail.createRoute(historyId))
                                        },
                                        onNavigateBack = null,
                                        paneFabContent = {
                                            MainNavFabSlot(
                                                currentTab = Screen.History,
                                                hasAnyHistory = hasAnyHistory,
                                                historySection = historySection,
                                                hasAnyTrashedRules = hasAnyTrashedRules,
                                                onAddRule = {},
                                                onClearHistory = { showClearHistoryDialog = true },
                                                onEmptyTrash = { showEmptyTrashDialog = true },
                                                onShareApp = {},
                                            )
                                        },
                                        viewModel = historyVm,
                                    )
                                } else {
                                    HistoryScreen(
                                        contentPadding = primaryTabContentPadding,
                                        onHistoryClick = { historyId ->
                                            navController.navigate(Screen.HistoryDetail.createRoute(historyId))
                                        },
                                        viewModel = historyVm,
                                    )
                                }
                            }
                            composable(
                                route = Screen.Settings.route,
                                enterTransition = {
                                    if (initialState.destination.route == Screen.DevOptions.route) {
                                        if (reducedMotion) {
                                            EnterTransition.None
                                        } else {
                                            fadeIn(animationSpec = devOptionsFadeSpec)
                                        }
                                    } else {
                                        null
                                    }
                                },
                                exitTransition = {
                                    if (targetState.destination.route == Screen.DevOptions.route) {
                                        if (reducedMotion) {
                                            ExitTransition.None
                                        } else {
                                            fadeOut(animationSpec = devOptionsFadeSpec)
                                        }
                                    } else {
                                        null
                                    }
                                },
                                popEnterTransition = {
                                    if (initialState.destination.route == Screen.DevOptions.route) {
                                        if (reducedMotion) {
                                            EnterTransition.None
                                        } else {
                                            fadeIn(animationSpec = devOptionsFadeSpec)
                                        }
                                    } else {
                                        null
                                    }
                                },
                                popExitTransition = {
                                    if (targetState.destination.route == Screen.DevOptions.route) {
                                        if (reducedMotion) {
                                            ExitTransition.None
                                        } else {
                                            fadeOut(animationSpec = devOptionsFadeSpec)
                                        }
                                    } else {
                                        null
                                    }
                                },
                            ) {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides sharedTransitionScope,
                                    LocalNavAnimatedVisibilityScope provides this,
                                ) {
                                    if (useListDetailPanes) {
                                        SettingsTwoPaneRoute(
                                            contentPadding = primaryTabContentPadding,
                                            onOpenIntro = {
                                                navController.navigate(Screen.OnboardingTitle.route)
                                            },
                                            onOpenFaqStorageSection = {
                                                navController.navigate(Screen.Faq.createRoute(Screen.Faq.FOCUS_STORAGE_ACCESS))
                                            },
                                            onOpenHelp = {
                                                navController.navigate(Screen.Faq.createRoute())
                                            },
                                            onOpenDevOptions = {
                                                navController.navigate(Screen.DevOptions.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            paneFabContent = {
                                                MainNavFabSlot(
                                                    currentTab = Screen.Settings,
                                                    hasAnyHistory = hasAnyHistory,
                                                    historySection = historySection,
                                                    hasAnyTrashedRules = hasAnyTrashedRules,
                                                    onAddRule = {},
                                                    onClearHistory = {},
                                                    onEmptyTrash = {},
                                                    onShareApp = { launchAppShareChooser(hostContext) },
                                                )
                                            },
                                            viewModel = settingsVm,
                                            highlightSectionKey = settingsHighlightSection,
                                            onHighlightHandled = { settingsHighlightSection = null },
                                        )
                                    } else {
                                        SettingsScreen(
                                            contentPadding = primaryTabContentPadding,
                                            onOpenIntro = {
                                                navController.navigate(Screen.OnboardingTitle.route)
                                            },
                                            onOpenFaqStorageSection = {
                                                navController.navigate(Screen.Faq.createRoute(Screen.Faq.FOCUS_STORAGE_ACCESS))
                                            },
                                            onOpenHelp = {
                                                navController.navigate(Screen.Faq.createRoute())
                                            },
                                            onOpenDevOptions = {
                                                navController.navigate(Screen.DevOptions.route) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            viewModel = settingsVm,
                                            highlightSectionKey = settingsHighlightSection,
                                            onHighlightHandled = { settingsHighlightSection = null },
                                        )
                                    }
                                }
                            }
                            composable(
                                route = Screen.DevOptions.route,
                                enterTransition = {
                                    if (reducedMotion) {
                                        EnterTransition.None
                                    } else {
                                        fadeIn(animationSpec = devOptionsFadeSpec)
                                    }
                                },
                                exitTransition = {
                                    if (reducedMotion) {
                                        ExitTransition.None
                                    } else {
                                        fadeOut(animationSpec = devOptionsFadeSpec)
                                    }
                                },
                                popEnterTransition = {
                                    if (reducedMotion) {
                                        EnterTransition.None
                                    } else {
                                        fadeIn(animationSpec = devOptionsFadeSpec)
                                    }
                                },
                                popExitTransition = {
                                    if (reducedMotion) {
                                        ExitTransition.None
                                    } else {
                                        fadeOut(animationSpec = devOptionsFadeSpec)
                                    }
                                },
                            ) {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides sharedTransitionScope,
                                    LocalNavAnimatedVisibilityScope provides this,
                                ) {
                                    DevOptionsScreen(
                                        contentPadding = primaryTabContentPadding,
                                        onNavigateBack = { navController.popBackStack() },
                                        settingsViewModel = settingsVm,
                                    )
                                }
                            }
                            composable(
                                route = Screen.Faq.route,
                                arguments =
                                    listOf(
                                        navArgument(Screen.Faq.ARG_FOCUS_SECTION) {
                                            type = NavType.StringType
                                            defaultValue = ""
                                        },
                                    ),
                            ) { backStackEntry ->
                                val focusSection =
                                    backStackEntry.arguments?.getString(Screen.Faq.ARG_FOCUS_SECTION).orEmpty()
                                val goToSettingsFromFaq: () -> Unit = {
                                    val poppedToExistingSettings =
                                        navController.popBackStack(Screen.Settings.route, inclusive = false)
                                    if (!poppedToExistingSettings) {
                                        navController.popBackStack()
                                        navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                                    }
                                }
                                val openFolderAccessInSettings: () -> Unit = {
                                    settingsHighlightSection = "folder_access"
                                    goToSettingsFromFaq()
                                }
                                FaqScreen(
                                    initialFocusSectionId = focusSection,
                                    onNavigateBack = { navController.popBackStack() },
                                    onOpenFolderAccessInSettings = openFolderAccessInSettings,
                                    onOpenSettingsNotifications = {
                                        settingsHighlightSection = "notifications"
                                        goToSettingsFromFaq()
                                    },
                                    onOpenAppNotificationSettings = {
                                        settingsVm.openAppNotificationSettings()
                                    },
                                )
                            }
                            composable(
                                route = Screen.HistoryDetail.route,
                                arguments =
                                    listOf(
                                        navArgument(Screen.HistoryDetail.ARG_HISTORY_ID) {
                                            type = NavType.LongType
                                        },
                                    ),
                            ) {
                                HistoryDetailScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable(
                                route = Screen.HistoryForRule.route,
                                arguments =
                                    listOf(
                                        navArgument(Screen.HistoryForRule.ARG_RULE_ID) {
                                            type = NavType.LongType
                                        },
                                    ),
                            ) {
                                if (useListDetailPanes) {
                                    HistoryTwoPaneRoute(
                                        contentPadding = PaddingValues(),
                                        onOpenHistoryDetail = { historyId ->
                                            navController.navigate(Screen.HistoryDetail.createRoute(historyId))
                                        },
                                        onNavigateBack = { navController.popBackStack() },
                                    )
                                } else {
                                    HistoryScreen(
                                        onHistoryClick = { historyId ->
                                            navController.navigate(Screen.HistoryDetail.createRoute(historyId))
                                        },
                                        onNavigateBack = { navController.popBackStack() },
                                    )
                                }
                            }
                        }
                    }
                }

                UpdateFloatingBar(
                    state = updateBarState,
                    onCheckClick = openUpdateSheetFromChrome,
                    onDismissAvailable = { dismissedUpdateBarKey = updateKey },
                    onInstallClick = installReadyUpdate,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = floatingBarHeight + 28.dp),
                )

                if (showFloatingBottomBar) {
                    FloatingNavBar(
                        items = bottomNavItems,
                        currentDestination = currentDestination,
                        onItemClick = { item ->
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        leadingFab =
                            if (updateFabState == UpdateChromeState.Hidden) {
                                null
                            } else {
                                {
                                    UpdateFloatingFab(
                                        state = updateFabState,
                                        onClick = {
                                            if (updateFabState == UpdateChromeState.ReadyToInstall) {
                                                installReadyUpdate()
                                            } else {
                                                openUpdateSheetFromChrome()
                                            }
                                        },
                                    )
                                }
                            },
                        fabContent = {
                            MainNavFabSlot(
                                currentTab = currentTab,
                                hasAnyHistory = hasAnyHistory,
                                historySection = historySection,
                                hasAnyTrashedRules = hasAnyTrashedRules,
                                onAddRule = {
                                    openNewRuleInPane?.invoke() ?: navController.navigate(Screen.RuleDetail.createRoute())
                                },
                                onClearHistory = { showClearHistoryDialog = true },
                                onEmptyTrash = { showEmptyTrashDialog = true },
                                onShareApp = { launchAppShareChooser(hostContext) },
                            )
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 80.dp),
                )
            }
        }
        if (useNavigationSuiteScaffold) {
            NavigationSuiteScaffold(
                layoutType = navigationSuiteType,
                containerColor = Color.Transparent,
                navigationSuiteItems = {
                    bottomNavItems.forEach { navItem ->
                        val selected =
                            currentDestination
                                ?.hierarchy
                                ?.any { it.route == navItem.screen.route } == true
                        item(
                            selected = selected,
                            onClick = {
                                playTap()
                                navController.navigate(navItem.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                FilePipeMaterialRoundedSymbol(
                                    name = navItem.symbolName,
                                    contentDescription = null,
                                    filled = selected,
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(navItem.label),
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                        )
                    }
                },
            ) {
                navigationContent()
            }
        } else {
            navigationContent()
        }
    }
}

@Composable
private fun TwoPaneListPaneWithFab(
    fabContent: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val centeredContentModifier =
            if (maxWidth > 720.dp) {
                Modifier
                    .width(720.dp)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }
        Box(centeredContentModifier) {
            content()
            if (fabContent != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 24.dp, bottom = 24.dp),
                ) {
                    fabContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RulesTwoPaneRoute(
    contentPadding: PaddingValues,
    onOpenRuleDetail: (Long) -> Unit,
    onOpenNewRule: () -> Unit,
    onOpenFaq: () -> Unit,
    onNavigateToHistoryDetail: (Long) -> Unit,
    onNavigateToHistoryList: () -> Unit,
    onNavigateToRuleHistory: (Long) -> Unit,
    onRegisterOpenNewRuleInPane: ((() -> Unit)?) -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val isMultiPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1

    if (!isMultiPane) {
        DisposableEffect(onRegisterOpenNewRuleInPane) {
            onRegisterOpenNewRuleInPane(null)
            onDispose {
                onRegisterOpenNewRuleInPane(null)
            }
        }
        Box(Modifier.fillMaxSize()) {
            RulesScreen(
                contentPadding = contentPadding,
                onEditRule = onOpenRuleDetail,
                onNavigateToHistoryDetail = onNavigateToHistoryDetail,
                onNavigateToHistoryList = onNavigateToHistoryList,
                onNavigateToRuleHistory = onNavigateToRuleHistory,
                viewModel = viewModel,
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(end = 24.dp, bottom = 24.dp),
            ) {
                SimpleNavFab(
                    icon = { tint ->
                        FilePipeMaterialRoundedSymbol(
                            name = "add",
                            contentDescription = null,
                            tint = tint,
                        )
                    },
                    description = stringResource(R.string.rules_add_rule),
                    enabled = true,
                    onClick = onOpenNewRule,
                )
            }
        }
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var activeRuleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingSavedRuleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previousRuleIdBeforeNew by rememberSaveable { mutableStateOf<Long?>(null) }

    fun showRuleInDetailPane(ruleId: Long) {
        if (ruleId == Screen.RuleDetail.NEW_RULE_ID && activeRuleId != Screen.RuleDetail.NEW_RULE_ID) {
            previousRuleIdBeforeNew = activeRuleId
        } else if (ruleId != Screen.RuleDetail.NEW_RULE_ID) {
            previousRuleIdBeforeNew = null
        }
        activeRuleId = ruleId
        pendingSavedRuleId = null
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, ruleId)
        }
    }

    fun closePendingNewRule() {
        val ruleIds = uiState.rules.map { rule -> rule.id }
        val fallbackRuleId =
            when {
                previousRuleIdBeforeNew != null && previousRuleIdBeforeNew in ruleIds -> previousRuleIdBeforeNew
                ruleIds.isNotEmpty() -> ruleIds.first()
                else -> null
            }
        previousRuleIdBeforeNew = null
        activeRuleId = fallbackRuleId
        scope.launch {
            if (fallbackRuleId != null) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, fallbackRuleId)
            } else {
                navigator.navigateBack()
            }
        }
    }

    val openNewRuleInPane: () -> Unit =
        remember {
            {
                showRuleInDetailPane(Screen.RuleDetail.NEW_RULE_ID)
            }
        }

    DisposableEffect(onRegisterOpenNewRuleInPane, openNewRuleInPane) {
        onRegisterOpenNewRuleInPane(openNewRuleInPane)
        onDispose {
            onRegisterOpenNewRuleInPane(null)
        }
    }

    LaunchedEffect(uiState.rules, activeRuleId, pendingSavedRuleId, isMultiPane) {
        val ruleIds = uiState.rules.map { rule -> rule.id }
        val currentRuleId = activeRuleId
        if (pendingSavedRuleId != null && pendingSavedRuleId in ruleIds) {
            pendingSavedRuleId = null
            previousRuleIdBeforeNew = null
        }
        val targetRuleId =
            when {
                currentRuleId == Screen.RuleDetail.NEW_RULE_ID -> currentRuleId
                currentRuleId != null && currentRuleId == pendingSavedRuleId -> currentRuleId
                currentRuleId != null && currentRuleId in ruleIds -> currentRuleId
                ruleIds.isNotEmpty() -> ruleIds.first()
                else -> null
            }
        if (targetRuleId != currentRuleId) {
            activeRuleId = targetRuleId
            targetRuleId?.let { ruleId ->
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, ruleId)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val balancedPaneExpansionState =
            rememberFlatScreenBalancedPaneExpansionState(
                directive = navigator.scaffoldDirective,
            )
        val showDetailNavigateBack = navigator.scaffoldDirective.maxHorizontalPartitions <= 1
        val showPaneSelectionState = !showDetailNavigateBack
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            scaffoldState = navigator.scaffoldState,
            listPane = {
                AnimatedPane {
                    TwoPaneListPaneWithFab(
                        fabContent = {
                            SimpleNavFab(
                                icon = { tint ->
                                    FilePipeMaterialRoundedSymbol(
                                        name = "add",
                                        contentDescription = null,
                                        tint = tint,
                                    )
                                },
                                description = stringResource(R.string.rules_add_rule),
                                enabled = true,
                                onClick = openNewRuleInPane,
                            )
                        },
                    ) {
                        RulesScreen(
                            contentPadding = contentPadding,
                            onEditRule = { ruleId -> showRuleInDetailPane(ruleId) },
                            onNavigateToHistoryDetail = onNavigateToHistoryDetail,
                            onNavigateToHistoryList = onNavigateToHistoryList,
                            onNavigateToRuleHistory = onNavigateToRuleHistory,
                            activeRuleId = activeRuleId.takeIf { showPaneSelectionState },
                            onActivateRuleInDetailPane =
                                if (showPaneSelectionState) {
                                    { ruleId -> showRuleInDetailPane(ruleId) }
                                } else {
                                    null
                                },
                            showPendingNewRuleInDetailPane =
                                showPaneSelectionState && activeRuleId == Screen.RuleDetail.NEW_RULE_ID,
                            viewModel = viewModel,
                        )
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    val selectedRuleId = activeRuleId
                    if (selectedRuleId == null) {
                        SettingsScreen(
                            contentPadding = contentPadding,
                            onOpenHelp = onOpenFaq,
                            viewModel = settingsViewModel,
                            selectedSectionKey = SettingsSectionKey.About,
                            showTopBar = false,
                            showSectionHeaders = false,
                        )
                    } else {
                        RuleDetailPaneHost(
                            ruleId = selectedRuleId,
                            onNavigateBack = {
                                if (showPaneSelectionState && selectedRuleId == Screen.RuleDetail.NEW_RULE_ID) {
                                    closePendingNewRule()
                                } else {
                                    scope.launch {
                                        navigator.navigateBack()
                                    }
                                }
                            },
                            onOpenFaq = onOpenFaq,
                            onSavedRule = { savedRuleId ->
                                activeRuleId = savedRuleId
                                pendingSavedRuleId = savedRuleId
                                previousRuleIdBeforeNew = null
                                if (!showPaneSelectionState) {
                                    scope.launch {
                                        navigator.navigateBack()
                                    }
                                }
                            },
                            showNavigateBack = showDetailNavigateBack,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            paneExpansionState = balancedPaneExpansionState,
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTwoPaneRoute(
    contentPadding: PaddingValues,
    onOpenHistoryDetail: (Long) -> Unit,
    onNavigateBack: (() -> Unit)?,
    paneFabContent: (@Composable () -> Unit)? = null,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val isMultiPane = navigator.scaffoldDirective.maxHorizontalPartitions > 1

    if (!isMultiPane) {
        Box(Modifier.fillMaxSize()) {
            HistoryScreen(
                contentPadding = contentPadding,
                onHistoryClick = onOpenHistoryDetail,
                onNavigateBack = onNavigateBack,
                viewModel = viewModel,
            )
            if (paneFabContent != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 24.dp, bottom = 24.dp),
                ) {
                    paneFabContent()
                }
            }
        }
        return
    }

    val visibleRunIds by viewModel.visibleRunIds.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var activeHistoryId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun showHistoryInDetailPane(historyId: Long) {
        activeHistoryId = historyId
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, historyId)
        }
    }

    LaunchedEffect(visibleRunIds, activeHistoryId) {
        val currentHistoryId = activeHistoryId
        val targetHistoryId =
            when {
                currentHistoryId != null && currentHistoryId in visibleRunIds -> currentHistoryId
                visibleRunIds.isNotEmpty() -> visibleRunIds.first()
                else -> null
            }
        if (targetHistoryId != currentHistoryId) {
            activeHistoryId = targetHistoryId
            targetHistoryId?.let { historyId ->
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, historyId)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val balancedPaneExpansionState =
            rememberFlatScreenBalancedPaneExpansionState(
                directive = navigator.scaffoldDirective,
            )
        val showDetailNavigateBack = navigator.scaffoldDirective.maxHorizontalPartitions <= 1
        val showPaneSelectionState = !showDetailNavigateBack
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            scaffoldState = navigator.scaffoldState,
            listPane = {
                AnimatedPane {
                    TwoPaneListPaneWithFab(fabContent = paneFabContent) {
                        HistoryScreen(
                            contentPadding = contentPadding,
                            onHistoryClick = { historyId -> showHistoryInDetailPane(historyId) },
                            onNavigateBack = onNavigateBack,
                            activeHistoryId = activeHistoryId.takeIf { showPaneSelectionState },
                            viewModel = viewModel,
                        )
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    val selectedHistoryId = activeHistoryId
                    if (section == HistorySection.TRASH) {
                        TwoPaneEmptyDetail(
                            illustration = { ThemeColoredEmptyTrashIllustration() },
                            title = stringResource(R.string.history_two_pane_trash_title),
                            message = stringResource(R.string.history_two_pane_trash_message),
                        )
                    } else if (selectedHistoryId == null) {
                        TwoPaneEmptyDetail(
                            illustration = { ThemeColoredEmptyHistoryIllustration(Modifier.size(120.dp)) },
                            title = stringResource(R.string.history_empty_title),
                            message = stringResource(R.string.history_empty_subtitle),
                        )
                    } else {
                        HistoryDetailPaneHost(
                            historyId = selectedHistoryId,
                            onNavigateBack = {
                                scope.launch {
                                    navigator.navigateBack()
                                }
                            },
                            showNavigateBack = showDetailNavigateBack,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            paneExpansionState = balancedPaneExpansionState,
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTwoPaneRoute(
    contentPadding: PaddingValues,
    onOpenIntro: () -> Unit,
    onOpenFaqStorageSection: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenDevOptions: () -> Unit,
    paneFabContent: (@Composable () -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    highlightSectionKey: String? = null,
    onHighlightHandled: () -> Unit = {},
) {
    val developerOptionsEnabled by viewModel.developerOptionsEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val paneContentPadding =
        PaddingValues(
            top = statusBarPadding,
            bottom = contentPadding.calculateBottomPadding(),
        )
    val scope = rememberCoroutineScope()
    var selectedSectionKey by rememberSaveable { mutableStateOf(SettingsSectionKey.Appearance) }
    val showDetailNavigateBack = navigator.scaffoldDirective.maxHorizontalPartitions <= 1
    val showPaneSelectionState = !showDetailNavigateBack

    fun showSettingsSection(sectionKey: SettingsSectionKey) {
        selectedSectionKey = sectionKey
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, sectionKey.routeKey)
        }
    }

    LaunchedEffect(highlightSectionKey, showPaneSelectionState) {
        if (!showPaneSelectionState) return@LaunchedEffect
        val highlightedSectionKey = settingsSectionKeyForHighlight(highlightSectionKey) ?: return@LaunchedEffect
        showSettingsSection(highlightedSectionKey)
    }

    if (!showPaneSelectionState) {
        Box(Modifier.fillMaxSize()) {
            SettingsScreen(
                contentPadding = contentPadding,
                onOpenIntro = onOpenIntro,
                onOpenFaqStorageSection = onOpenFaqStorageSection,
                onOpenHelp = onOpenHelp,
                onOpenDevOptions = onOpenDevOptions,
                viewModel = viewModel,
                highlightSectionKey = highlightSectionKey,
                onHighlightHandled = onHighlightHandled,
            )
            if (paneFabContent != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(end = 24.dp, bottom = 24.dp),
                ) {
                    paneFabContent()
                }
            }
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val balancedPaneExpansionState =
            rememberFlatScreenBalancedPaneExpansionState(
                directive = navigator.scaffoldDirective,
            )
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            scaffoldState = navigator.scaffoldState,
            listPane = {
                AnimatedPane {
                    TwoPaneListPaneWithFab(fabContent = paneFabContent) {
                        SettingsSectionListPane(
                            contentPadding = paneContentPadding,
                            selectedSectionKey = selectedSectionKey,
                            developerOptionsEnabled = developerOptionsEnabled,
                            onSectionSelected = ::showSettingsSection,
                            showSelectedState = showPaneSelectionState,
                            extraTopPadding = 52.dp,
                        )
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    if (selectedSectionKey == SettingsSectionKey.DeveloperOptions) {
                        DevOptionsScreen(
                            contentPadding = paneContentPadding,
                            onNavigateBack = { showSettingsSection(SettingsSectionKey.About) },
                            settingsViewModel = viewModel,
                            showNavigateBack = showDetailNavigateBack,
                        )
                    } else {
                        SettingsScreen(
                            contentPadding = paneContentPadding,
                            onOpenIntro = onOpenIntro,
                            onOpenFaqStorageSection = onOpenFaqStorageSection,
                            onOpenHelp = onOpenHelp,
                            onOpenDevOptions = { showSettingsSection(SettingsSectionKey.DeveloperOptions) },
                            viewModel = viewModel,
                            highlightSectionKey = highlightSectionKey,
                            onHighlightHandled = onHighlightHandled,
                            selectedSectionKey = selectedSectionKey,
                            showTopBar = false,
                            showSectionHeaders = false,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            paneExpansionState = balancedPaneExpansionState,
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberFlatScreenBalancedPaneExpansionState(
    directive: PaneScaffoldDirective,
): PaneExpansionState {
    val targetFirstPaneProportion: Float? =
        if (directive.excludedBounds.isEmpty() && directive.maxHorizontalPartitions > 1) {
            0.4f
        } else {
            null
        }
    val paneExpansionAnchors =
        remember(targetFirstPaneProportion) {
            if (targetFirstPaneProportion == null) {
                emptyList()
            } else {
                listOf(PaneExpansionAnchor.Proportion(targetFirstPaneProportion))
            }
        }
    val paneExpansionState =
        rememberPaneExpansionState(
            key = PaneExpansionStateKey.Default,
            anchors = paneExpansionAnchors,
        )
    SideEffect {
        if (targetFirstPaneProportion == null) {
            paneExpansionState.clear()
        } else {
            paneExpansionState.setFirstPaneProportion(targetFirstPaneProportion)
        }
    }
    return paneExpansionState
}

@Composable
private fun RuleDetailPaneHost(
    ruleId: Long,
    onNavigateBack: () -> Unit,
    onOpenFaq: () -> Unit,
    onSavedRule: (Long) -> Unit,
    showNavigateBack: Boolean,
) {
    val detailRoute = Screen.RuleDetail.createRoute(ruleId)
    key(detailRoute) {
        val detailNavController = rememberNavController()
        NavHost(
            navController = detailNavController,
            startDestination = detailRoute,
        ) {
            composable(
                route = Screen.RuleDetail.route,
                arguments =
                    listOf(
                        navArgument(Screen.RuleDetail.ARG_RULE_ID) {
                            type = NavType.LongType
                        },
                        navArgument(Screen.RuleDetail.ARG_TEMPLATE_INDEX) {
                            type = NavType.IntType
                            defaultValue = -1
                        },
                        navArgument(Screen.RuleDetail.ARG_SKIP_TEMPLATE_PICKER) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
            ) {
                RuleDetailScreen(
                    onNavigateBack = onNavigateBack,
                    onOpenFaq = onOpenFaq,
                    onSavedRule = onSavedRule,
                    showNavigateBack = showNavigateBack,
                )
            }
        }
    }
}

@Composable
private fun HistoryDetailPaneHost(
    historyId: Long,
    onNavigateBack: () -> Unit,
    showNavigateBack: Boolean,
) {
    val detailRoute = Screen.HistoryDetail.createRoute(historyId)
    key(detailRoute) {
        val detailNavController = rememberNavController()
        NavHost(
            navController = detailNavController,
            startDestination = detailRoute,
        ) {
            composable(
                route = Screen.HistoryDetail.route,
                arguments =
                    listOf(
                        navArgument(Screen.HistoryDetail.ARG_HISTORY_ID) {
                            type = NavType.LongType
                        },
                    ),
            ) {
                HistoryDetailScreen(
                    onNavigateBack = onNavigateBack,
                    showNavigateBack = showNavigateBack,
                )
            }
        }
    }
}

@Composable
private fun TwoPaneEmptyDetail(
    illustration: @Composable () -> Unit,
    title: String,
    message: String,
    actionLabel: String? = null,
    actionIconName: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            illustration()
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(8.dp))
                FilePipeButton(
                    onClick = onAction,
                    shape = pillShape,
                    modifier = Modifier.fillMaxWidth(0.72f),
                ) {
                    if (actionIconName != null) {
                        FilePipeMaterialRoundedSymbol(
                            name = actionIconName,
                            contentDescription = null,
                            size = 20.dp,
                            opticalCenterYOffset = (-2).dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun UpdateFloatingFab(
    state: UpdateChromeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == UpdateChromeState.Hidden) return

    val label = stringResource(R.string.update_fab_label)
    val shape = remember { RoundedPolygonShape(MaterialShapes.Cookie9Sided) }
    FilePipeFloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shape = shape,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        tooltipLabel = label,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = if (state == UpdateChromeState.ReadyToInstall) "download_done" else "download",
            contentDescription = null,
            size = 28.dp,
            weight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UpdateFloatingBar(
    state: UpdateChromeState,
    onCheckClick: () -> Unit,
    onDismissAvailable: () -> Unit,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == UpdateChromeState.Hidden) return

    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val iconShape = remember { RoundedPolygonShape(MaterialShapes.Cookie9Sided) }
    val (title, body) =
        when (state) {
            UpdateChromeState.Hidden -> {
                return
            }

            UpdateChromeState.Available -> {
                Pair(
                    stringResource(R.string.update_bar_available_title),
                    null,
                )
            }

            is UpdateChromeState.Downloading -> {
                val progressLabel =
                    if (state.indeterminateProgress || state.totalBytesToDownload <= 0L) {
                        stringResource(R.string.play_update_bar_downloading)
                    } else {
                        val downloaded = Formatter.formatFileSize(context, state.bytesDownloaded)
                        val total = Formatter.formatFileSize(context, state.totalBytesToDownload)
                        stringResource(R.string.play_update_bar_downloading_bytes, downloaded, total)
                    }
                Pair(
                    stringResource(R.string.play_update_bar_downloading_title),
                    progressLabel,
                )
            }

            UpdateChromeState.ReadyToInstall -> {
                Pair(
                    stringResource(R.string.play_update_bar_install_title),
                    stringResource(R.string.play_update_bar_install_subtitle),
                )
            }
        }

    Surface(
        modifier = modifier,
        shape = pillShape,
        color = scheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = iconShape,
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FilePipeMaterialRoundedSymbol(
                            name = if (state == UpdateChromeState.ReadyToInstall) "download_done" else "download",
                            contentDescription = null,
                            size = 24.dp,
                            weight = FontWeight.Medium,
                            tint = scheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (body != null) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                when (state) {
                    UpdateChromeState.Available -> {
                        FilePipeButton(
                            onClick = onCheckClick,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ),
                        ) {
                            Text(stringResource(R.string.update_bar_available_action))
                        }
                        val closeLabel = stringResource(R.string.close)
                        FilePipeIconButton(
                            onClick = onDismissAvailable,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .semantics { contentDescription = closeLabel },
                        ) {
                            FilePipeMaterialRoundedSymbol(
                                name = "close",
                                contentDescription = null,
                                size = 20.dp,
                                weight = FontWeight.Medium,
                            )
                        }
                    }

                    is UpdateChromeState.Downloading -> {}

                    UpdateChromeState.ReadyToInstall -> {
                        FilePipeButton(
                            onClick = onInstallClick,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ),
                        ) {
                            Text(
                                text = stringResource(R.string.play_update_bar_install_action),
                                maxLines = 1,
                            )
                        }
                    }

                    UpdateChromeState.Hidden -> {}
                }
            }

            if (state is UpdateChromeState.Downloading) {
                if (state.indeterminateProgress || state.totalBytesToDownload <= 0L) {
                    LinearWavyProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        color = scheme.primary,
                        trackColor = scheme.primaryContainer.copy(alpha = 0.28f),
                    )
                } else {
                    val fraction =
                        (state.bytesDownloaded.toFloat() / state.totalBytesToDownload.toFloat())
                            .coerceIn(0f, 1f)
                    LinearWavyProgressIndicator(
                        progress = { fraction },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        color = scheme.primary,
                        trackColor = scheme.primaryContainer.copy(alpha = 0.28f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNavBar(
    items: List<BottomNavItem>,
    currentDestination: NavDestination?,
    onItemClick: (BottomNavItem) -> Unit,
    fabContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingFab: (@Composable () -> Unit)? = null,
) {
    CenteredPillWithSideFab(
        pill = {
            FilePipeFloatingNavPill(
                items = items,
                currentDestination = currentDestination,
                onItemClick = onItemClick,
            )
        },
        leadingFab = leadingFab,
        fab = fabContent,
        fabGap = 12.dp,
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp, start = 24.dp, end = 24.dp),
    )
}

/**
 * Pill-only floating nav. The FAB is rendered as a sibling by [CenteredPillWithSideFab]
 * so the nav pill itself stays centered on screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilePipeFloatingNavPill(
    items: List<BottomNavItem>,
    currentDestination: NavDestination?,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors =
            FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.primary,
                toolbarContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        items.forEachIndexed { index, item ->
            val selected =
                currentDestination
                    ?.hierarchy
                    ?.any { it.route == item.screen.route } == true

            val labelWidth by animateDpAsState(
                targetValue = if (selected) 72.dp else 0.dp,
                animationSpec =
                    reducedMotionAwareSpec(
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                    ),
                label = "nav_label_$index",
            )
            val labelString = stringResource(item.label)

            FilePipeIconButton(
                onClick = { onItemClick(item) },
                modifier =
                    Modifier
                        .height(48.dp)
                        .width(48.dp + labelWidth)
                        .semantics { contentDescription = labelString },
                colors =
                    if (selected) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = if (selected) 6.dp else 0.dp),
                ) {
                    FilePipeMaterialRoundedSymbol(
                        name = item.symbolName,
                        contentDescription = null,
                        size = 24.dp,
                        filled = selected,
                    )
                    if (labelWidth > 4.dp) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = labelString,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom-strip layout: pill horizontally centered against the strip's full width, FAB
 * placed just to the right of the pill or clamped to the right edge.
 */
@Composable
private fun CenteredPillWithSideFab(
    pill: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    fabGap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    leadingFab: (@Composable () -> Unit)? = null,
    fabCoreSize: androidx.compose.ui.unit.Dp = 56.dp,
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = {
            Box { pill() }
            Box { fab() }
            if (leadingFab != null) {
                Box { leadingFab() }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val pillPlaceable = measurables[0].measure(loose)
        val fabPlaceable = measurables[1].measure(loose)
        val leadingFabPlaceable = measurables.getOrNull(2)?.measure(loose)
        val gapPx = fabGap.roundToPx()
        val fabCorePx = fabCoreSize.roundToPx()
        val width =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                pillPlaceable.width + gapPx + fabPlaceable.width
            }
        val stripHeight = maxOf(pillPlaceable.height, fabCorePx)

        layout(width, stripHeight) {
            val pillX = (width - pillPlaceable.width) / 2
            val pillY = (stripHeight - pillPlaceable.height) / 2
            pillPlaceable.place(pillX, pillY)

            val desiredFabRight = pillX + pillPlaceable.width + gapPx + fabCorePx
            val fabRight = desiredFabRight.coerceAtMost(width)
            val fabX = (fabRight - fabPlaceable.width).coerceAtLeast(0)
            val fabY = (stripHeight - fabPlaceable.height) / 2
            fabPlaceable.place(fabX, fabY)

            leadingFabPlaceable?.let { leadingPlaceable ->
                val leadingX = (pillX - gapPx - fabCorePx).coerceAtLeast(0)
                val leadingY = (stripHeight - leadingPlaceable.height) / 2
                leadingPlaceable.place(leadingX, leadingY)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainNavFabSlot(
    currentTab: Screen?,
    hasAnyHistory: Boolean,
    historySection: HistorySection,
    hasAnyTrashedRules: Boolean,
    onAddRule: () -> Unit,
    onClearHistory: () -> Unit,
    onEmptyTrash: () -> Unit,
    onShareApp: () -> Unit,
) {
    when (currentTab) {
        Screen.Rules -> {
            SimpleNavFab(
                icon = { tint ->
                    FilePipeMaterialRoundedSymbol(
                        name = "add",
                        contentDescription = null,
                        tint = tint,
                    )
                },
                description = stringResource(R.string.rules_add_rule),
                enabled = true,
                onClick = onAddRule,
            )
        }

        Screen.History -> {
            val inTrash = historySection == HistorySection.TRASH
            val description =
                stringResource(
                    if (inTrash) {
                        R.string.history_trash_empty_action
                    } else {
                        R.string.history_clear
                    },
                )
            SimpleNavFab(
                icon = { tint ->
                    FilePipeMaterialRoundedSymbol(
                        name = "delete_forever",
                        contentDescription = null,
                        tint = tint,
                    )
                },
                description = description,
                enabled = if (inTrash) hasAnyTrashedRules else hasAnyHistory,
                onClick = if (inTrash) onEmptyTrash else onClearHistory,
            )
        }

        Screen.Settings -> {
            SimpleNavFab(
                icon = { tint ->
                    FilePipeMaterialRoundedSymbol(
                        name = "share",
                        contentDescription = null,
                        tint = tint,
                    )
                },
                description = stringResource(R.string.settings_share_app),
                enabled = true,
                onClick = onShareApp,
            )
        }

        else -> {}
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SimpleNavFab(
    icon: @Composable (Color) -> Unit,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        }
    val fabShape = remember { RoundedPolygonShape(MaterialShapes.Cookie9Sided) }

    FilePipeFloatingActionButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .then(
                    if (enabled) {
                        Modifier
                    } else {
                        Modifier
                            .alpha(0.58f)
                            .semantics { disabled() }
                    },
                ),
        shape = fabShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
        tooltipLabel = description,
    ) {
        Box(modifier = Modifier.semantics { contentDescription = description }) {
            icon(contentColor)
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryTabEnterTransition(
    reducedMotion: Boolean,
    verticalMotion: Boolean,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    fadeInSpec: FiniteAnimationSpec<Float>,
): EnterTransition? {
    val initialOrdinal = mainTabRouteOrdinals[initialState.destination.route] ?: return null
    val targetOrdinal = mainTabRouteOrdinals[targetState.destination.route] ?: return null
    return if (reducedMotion) {
        EnterTransition.None
    } else if (targetOrdinal > initialOrdinal) {
        if (verticalMotion) {
            slideInVertically(animationSpec = spatialSpec) { it } + fadeIn(animationSpec = fadeInSpec)
        } else {
            slideInHorizontally(animationSpec = spatialSpec) { it } + fadeIn(animationSpec = fadeInSpec)
        }
    } else {
        if (verticalMotion) {
            slideInVertically(animationSpec = spatialSpec) { -it } + fadeIn(animationSpec = fadeInSpec)
        } else {
            slideInHorizontally(animationSpec = spatialSpec) { -it } + fadeIn(animationSpec = fadeInSpec)
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryTabExitTransition(
    reducedMotion: Boolean,
    verticalMotion: Boolean,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    fadeOutSpec: FiniteAnimationSpec<Float>,
): ExitTransition? {
    val initialOrdinal = mainTabRouteOrdinals[initialState.destination.route] ?: return null
    val targetOrdinal = mainTabRouteOrdinals[targetState.destination.route] ?: return null
    return if (reducedMotion) {
        ExitTransition.None
    } else if (targetOrdinal > initialOrdinal) {
        if (verticalMotion) {
            slideOutVertically(animationSpec = spatialSpec) { -it / 3 } + fadeOut(animationSpec = fadeOutSpec)
        } else {
            slideOutHorizontally(animationSpec = spatialSpec) { -it / 3 } + fadeOut(animationSpec = fadeOutSpec)
        }
    } else {
        if (verticalMotion) {
            slideOutVertically(animationSpec = spatialSpec) { it } + fadeOut(animationSpec = fadeOutSpec)
        } else {
            slideOutHorizontally(animationSpec = spatialSpec) { it } + fadeOut(animationSpec = fadeOutSpec)
        }
    }
}
