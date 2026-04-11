package dev.bikram.filepipe.ui.navigation

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import dev.bikram.filepipe.debug.AgentSessionLog
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import dev.bikram.filepipe.ui.screens.history.HistoryScreen
import dev.bikram.filepipe.ui.screens.history.HistoryViewModel
import dev.bikram.filepipe.ui.screens.historydetail.HistoryDetailScreen
import dev.bikram.filepipe.ui.screens.onboarding.OnboardingPermissionsScreen
import dev.bikram.filepipe.ui.screens.onboarding.OnboardingRuleWizardScreen
import dev.bikram.filepipe.ui.screens.onboarding.OnboardingTitleScreen
import dev.bikram.filepipe.ui.screens.ruledetail.RuleDetailScreen
import dev.bikram.filepipe.ui.screens.rules.RulesScreen
import dev.bikram.filepipe.ui.screens.settings.SettingsScreen
import dev.bikram.filepipe.ui.screens.settings.SettingsViewModel
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurEnabled
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalUseFixedCardColors
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.ProgressiveBlurStyle
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository

private data class BottomNavItem(
    val screen: Screen,
    val label: Int,
    val selectedIcon: @Composable () -> Unit,
    val unselectedIcon: @Composable () -> Unit
)

private val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Rules,
        label = R.string.nav_rules,
        selectedIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
        unselectedIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) }
    ),
    BottomNavItem(
        screen = Screen.History,
        label = R.string.nav_history,
        selectedIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
        unselectedIcon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) }
    ),
    BottomNavItem(
        screen = Screen.Settings,
        label = R.string.nav_settings,
        selectedIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
        unselectedIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) }
    )
)

@Composable
fun AppNavigation(
    hasSeenIntro: Boolean = true,
    introSeenAtLaunch: Boolean = hasSeenIntro,
    preferences: AppPreferences = AppPreferences(),
    pendingShortcutRepository: PendingShortcutRepository
) {
    val playTap = rememberPlayTapSound()
    val navController = rememberNavController()
    val appContext = LocalContext.current.applicationContext
    val pendingHistoryId by pendingShortcutRepository.pendingHistoryDetailId.collectAsStateWithLifecycle()
    val pendingOpenSettingsUpdates by pendingShortcutRepository.pendingOpenSettingsForUpdates.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any {
        currentDestination?.hierarchy?.any { destination -> destination.route == it.screen.route } == true
    }

    val isRuleDetailRoute = currentDestination?.hierarchy?.any { destination ->
        destination.route == Screen.RuleDetail.route
    } == true
    val isHistoryDetailRoute = currentDestination?.hierarchy?.any { destination ->
        destination.route == Screen.HistoryDetail.route
    } == true

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingBarHeight = 64.dp
    val scrimHeight = navBarInset + floatingBarHeight + 24.dp
    val topBlurSmallChrome = statusBarInset + 64.dp
    // Blur only under collapsed app-bar band so expanded LargeTopAppBar does not tint/blur list content below.
    val topBlurHeightDp = topBlurSmallChrome
    val fullScreenBottomBlurShort = navBarInset + 48.dp
    val fullScreenBottomBlurRuleEdit = navBarInset + 88.dp
    val bottomBlurHeightDp = when {
        showBottomBar -> scrimHeight
        isRuleDetailRoute -> fullScreenBottomBlurRuleEdit
        else -> fullScreenBottomBlurShort
    }
    val contentPaddingBottom = if (showBottomBar) scrimHeight else navBarInset
    val density = LocalDensity.current
    val bottomBlurHeightPx = with(density) { bottomBlurHeightDp.toPx() }
    val topBlurHeightPx = with(density) { topBlurHeightDp.toPx() }
    val progressiveBlurStyle: ProgressiveBlurStyle? =
        if (!preferences.progressiveBlurEnabled) {
            null
        } else {
            val blurRadius =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (isRuleDetailRoute) 56f else 44f
                } else {
                    0f
                }
            val overlayAlpha = when {
                isRuleDetailRoute -> 0.38f
                isHistoryDetailRoute -> 0.30f
                else -> 0.34f
            }
            ProgressiveBlurStyle(
                topHeightPx = topBlurHeightPx,
                bottomHeightPx = bottomBlurHeightPx,
                blurRadius = blurRadius,
                overlayAlpha = overlayAlpha
            )
        }

    // Activity-scoped VMs for nav bar FAB actions
    val historyVm: HistoryViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Lock from disk-backed snapshot (MainActivity) so the first frame does not use
    // AppPreferences.DEFAULT.hasSeenIntro (false) and force onboarding on every cold start.
    // Still stable for the activity so live hasSeenIntro updates do not change startDestination.
    val lockedNavStartDestination = remember(introSeenAtLaunch) {
        if (introSeenAtLaunch) Screen.Rules.route else Screen.OnboardingTitle.createRoute()
    }

    LaunchedEffect(hasSeenIntro, preferences.updateCheckSchedule) {
        if (hasSeenIntro && BuildConfig.SHOW_UPDATES &&
            preferences.updateCheckSchedule == UpdateCheckSchedule.AT_APP_START
        ) {
            settingsVm.checkForUpdate(silent = true)
        }
    }

    LaunchedEffect(hasSeenIntro, pendingOpenSettingsUpdates, navController) {
        if (!pendingOpenSettingsUpdates || !hasSeenIntro) return@LaunchedEffect
        navController.navigate(Screen.Settings.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        settingsVm.flagOpenUpdateSheetFromNotification()
        pendingShortcutRepository.clearPendingOpenSettingsForUpdates()
    }

    LaunchedEffect(hasSeenIntro, pendingHistoryId, navController) {
        val historyId = pendingHistoryId ?: return@LaunchedEffect
        if (!hasSeenIntro) return@LaunchedEffect
        navController.navigate(Screen.HistoryDetail.createRoute(historyId)) {
            launchSingleTop = true
        }
        pendingShortcutRepository.clearPendingHistoryDetail()
    }

    val currentTab = bottomNavItems.find { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }?.screen

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    playTap()
                    showClearHistoryDialog = false
                    historyVm.clearAllHistory()
                }) { Text(stringResource(R.string.history_clear)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    playTap()
                    showClearHistoryDialog = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    CompositionLocalProvider(
        LocalUseGradientBackground provides preferences.useGradientBackground,
        LocalUseFixedCardColors provides preferences.useFixedCardColors,
        LocalProgressiveBlurEnabled provides preferences.progressiveBlurEnabled,
        LocalProgressiveBlurStyle provides progressiveBlurStyle
    ) {
        Box(Modifier.fillMaxSize()) {
            if (preferences.useGradientBackground) {
                val scheme = MaterialTheme.colorScheme
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scheme.surface)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to scheme.primaryContainer.copy(alpha = 0.45f),
                                    0.55f to scheme.surface.copy(alpha = 0f)
                                )
                            )
                        )
                )
            }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = if (preferences.useGradientBackground) Color.Transparent else MaterialTheme.colorScheme.background,
                bottomBar = {}
            ) { _ ->
                NavHost(
                navController = navController,
                startDestination = lockedNavStartDestination,
                enterTransition = { slideInHorizontally { it } + fadeIn() },
                exitTransition = { slideOutHorizontally { -it / 3 } + fadeOut() },
                popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it } + fadeOut() }
            ) {
                composable(
                    route = Screen.OnboardingTitle.route,
                    arguments = listOf(navArgument(Screen.OnboardingTitle.ARG) {
                        type = NavType.BoolType
                        defaultValue = false
                    })
                ) { backStack ->
                    val fromSettings = backStack.arguments?.getBoolean(Screen.OnboardingTitle.ARG) ?: false
                    OnboardingTitleScreen(
                        onLetsBegan = {
                            // #region agent log
                            AgentSessionLog.append(
                                context = appContext,
                                location = "AppNavigation.kt:OnboardingTitle.onLetsBegan",
                                message = "lets_begin_tapped",
                                hypothesisId = "A",
                                data = mapOf("fromSettings" to fromSettings)
                            )
                            // #endregion
                            navController.navigate(Screen.OnboardingPermissions.route)
                        }
                    )
                }

                composable(Screen.OnboardingPermissions.route) {
                    OnboardingPermissionsScreen(
                        onContinue = {
                            // #region agent log
                            AgentSessionLog.append(
                                context = appContext,
                                location = "AppNavigation.kt:OnboardingPermissions.onContinue",
                                message = "navigate_wizard_keep_stack",
                                hypothesisId = "B",
                                data = emptyMap()
                            )
                            // #endregion
                            navController.navigate(Screen.OnboardingRuleWizard.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(Screen.OnboardingRuleWizard.route) {
                    OnboardingRuleWizardScreen(
                        onBackToPermissions = {
                            // #region agent log
                            AgentSessionLog.append(
                                context = appContext,
                                location = "AppNavigation.kt:OnboardingRuleWizard.onBack",
                                message = "pop_back_to_permissions",
                                hypothesisId = "B",
                                data = emptyMap()
                            )
                            // #endregion
                            navController.popBackStack()
                        },
                        onUseTemplate = { templateIndex ->
                            navController.navigate(Screen.Rules.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                            navController.navigate(
                                Screen.RuleDetail.createRoute(
                                    templateIndex = templateIndex,
                                    skipTemplatePicker = true
                                )
                            )
                            settingsVm.markIntroSeen()
                        },
                        onStartBlank = {
                            navController.navigate(Screen.Rules.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                            navController.navigate(
                                Screen.RuleDetail.createRoute(skipTemplatePicker = true)
                            )
                            settingsVm.markIntroSeen()
                        },
                        onSkip = {
                            navController.navigate(Screen.Rules.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                            settingsVm.markIntroSeen()
                        }
                    )
                }

                composable(Screen.Rules.route) {
                    RulesScreen(
                        contentPadding = PaddingValues(bottom = contentPaddingBottom),
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
                        }
                    )
                }
                composable(
                    route = Screen.RuleDetail.route,
                    arguments = listOf(
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
                        }
                    )
                ) {
                    RuleDetailScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(
                        contentPadding = PaddingValues(bottom = contentPaddingBottom),
                        onHistoryClick = { historyId ->
                            navController.navigate(Screen.HistoryDetail.createRoute(historyId))
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        contentPadding = PaddingValues(bottom = contentPaddingBottom),
                        onOpenIntro = {
                            navController.navigate(Screen.OnboardingTitle.createRoute(fromSettings = true))
                        },
                        viewModel = settingsVm
                    )
                }
                composable(
                    route = Screen.HistoryDetail.route,
                    arguments = listOf(navArgument(Screen.HistoryDetail.ARG_HISTORY_ID) {
                        type = NavType.LongType
                    })
                ) {
                    HistoryDetailScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(
                    route = Screen.HistoryForRule.route,
                    arguments = listOf(navArgument(Screen.HistoryForRule.ARG_RULE_ID) {
                        type = NavType.LongType
                    })
                ) {
                    HistoryScreen(
                        onHistoryClick = { historyId ->
                            navController.navigate(Screen.HistoryDetail.createRoute(historyId))
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }

        if (showBottomBar) {
            FloatingNavBar(
                items = bottomNavItems,
                currentDestination = currentDestination,
                onItemClick = { item ->
                    playTap()
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                fabContent = {
                    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    val contentColor = MaterialTheme.colorScheme.primary
                    val fabModifier = Modifier.size(64.dp)
                    when (currentTab) {
                        Screen.Rules -> FloatingActionButton(
                            onClick = {
                                playTap()
                                navController.navigate(Screen.RuleDetail.createRoute())
                            },
                            modifier = fabModifier,
                            containerColor = containerColor,
                            contentColor = contentColor
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.rules_add_rule))
                        }

                        Screen.History -> FloatingActionButton(
                            onClick = {
                                playTap()
                                showClearHistoryDialog = true
                            },
                            modifier = fabModifier,
                            containerColor = containerColor,
                            contentColor = contentColor
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.history_clear))
                        }

                        Screen.Settings -> FloatingActionButton(
                            onClick = {
                                playTap()
                                settingsVm.requestManualExportPicker()
                            },
                            modifier = fabModifier,
                            containerColor = containerColor,
                            contentColor = contentColor
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = stringResource(R.string.settings_export_now))
                        }

                        else -> {}
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
    modifier: Modifier = Modifier
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = fabContent,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp, start = 24.dp, end = 24.dp),
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.primary,
            toolbarContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        items.forEachIndexed { index, item ->
            val selected = currentDestination
                ?.hierarchy
                ?.any { it.route == item.screen.route } == true

            val labelWidth by animateDpAsState(
                targetValue = if (selected) 72.dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "nav_label_$index"
            )

            IconButton(
                onClick = { onItemClick(item) },
                modifier = Modifier
                    .height(48.dp)
                    .width(48.dp + labelWidth),
                colors = if (selected) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = if (selected) 6.dp else 0.dp)
                ) {
                    Box(modifier = Modifier.size(24.dp)) {
                        if (selected) item.selectedIcon() else item.unselectedIcon()
                    }
                    if (labelWidth > 4.dp) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(item.label),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

