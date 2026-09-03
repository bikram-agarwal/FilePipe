@file:Suppress("ConfigurationScreenWidthHeight")

package dev.bikram.filepipe.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.data.preferences.materialSymbolName
import dev.bikram.filepipe.data.storage.safTreeUriToPath
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.usecase.BackupImportPickAction
import dev.bikram.filepipe.ui.common.AppBottomSheet
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.isLandscape
import dev.bikram.filepipe.ui.common.isSmallLandscape
import dev.bikram.filepipe.ui.components.AboutAuthorPhoto
import dev.bikram.filepipe.ui.components.AppIconImage
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeConfirmDialog
import dev.bikram.filepipe.ui.components.FilePipeDropdownMenuItem
import dev.bikram.filepipe.ui.components.FilePipeFilledTonalIconButton
import dev.bikram.filepipe.ui.components.FilePipeIconButton
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.FilePipeSwitch
import dev.bikram.filepipe.ui.components.FilePipeTextButton
import dev.bikram.filepipe.ui.components.ToggleLabelHelpDropdown
import dev.bikram.filepipe.ui.components.containers.GroupPosition
import dev.bikram.filepipe.ui.components.containers.GroupedListColumn
import dev.bikram.filepipe.ui.components.containers.GroupedListItem
import dev.bikram.filepipe.ui.components.displayPath
import dev.bikram.filepipe.ui.components.text.SimpleMarkdown
import dev.bikram.filepipe.ui.feedback.appClickable
import dev.bikram.filepipe.ui.feedback.appCombinedClickable
import dev.bikram.filepipe.ui.modifiers.progressiveBlurScrollableList
import dev.bikram.filepipe.ui.modifiers.rememberContentOverflowScrollEnabled
import dev.bikram.filepipe.ui.navigation.DEV_OPTIONS_SHARED_BOUNDS_KEY
import dev.bikram.filepipe.ui.navigation.LocalNavAnimatedVisibilityScope
import dev.bikram.filepipe.ui.navigation.LocalSharedTransitionScope
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalSnackbarHostState
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.compactControlShape
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.ui.theme.pillShape
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import dev.bikram.filepipe.ui.theme.swipeActionAccent
import dev.bikram.filepipe.update.PlayInAppUpdateBannerUiState
import dev.bikram.filepipe.update.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS = 900L

enum class SettingsSectionKey(
    val routeKey: String,
    val iconName: String,
    @StringRes val titleRes: Int,
    /**
     * Historic [routeKey] values. [routeKey] is a *stored identifier*: it is persisted in the
     * collapsed-sections preference and accepted from deep links. Renaming one would orphan that
     * stored state, so move the old value here rather than deleting it and everything keeps
     * resolving.
     *
     * This has nothing to do with [titleRes]. The displayed heading is never persisted or compared,
     * so section labels in `strings.xml` can be reworded freely with no effect anywhere else.
     */
    val legacyRouteKeys: List<String> = emptyList(),
) {
    Appearance("appearance", "palette", R.string.settings_appearance_section),
    FolderAccess("folder_access", "folder_open", R.string.settings_folder_access_section),
    Schedule("schedule", "calendar_clock", R.string.settings_schedule_section, legacyRouteKeys = listOf("notifications")),
    TouchSound("touch_sound", "vibration", R.string.settings_touch_sound_section),
    SwipeActions("swipe_actions", "swipe_left", R.string.settings_swipe_gestures_section),
    Backup("backup", "save", R.string.settings_backup_section),
    Updates("updates", "system_update", R.string.settings_updates_section),
    About("about", "info", R.string.settings_about_section),
    DeveloperOptions("developer_options", "developer_board", R.string.settings_developer_options_section),
}

/**
 * Resolves a stored or deep-linked route key - current or historic - to its section's current
 * [SettingsSectionKey.routeKey]. Returns null when the key matches no section.
 */
fun canonicalSettingsSectionRouteKey(storedKey: String): String? =
    SettingsSectionKey.entries
        .firstOrNull { section -> section.routeKey == storedKey || storedKey in section.legacyRouteKeys }
        ?.routeKey

/**
 * Index of a section's item within the settings [LazyColumn], derived from declaration order and the
 * sections actually rendered.
 *
 * These were hardcoded index constants, which silently pointed at the wrong section whenever the
 * list changed - and were always wrong in the single-section pane, where [shouldRenderSection]
 * renders only the selected section so the target is index 0. Deriving it means adding, removing or
 * hiding a section cannot leave it stale.
 *
 * About and DeveloperOptions are rendered after a non-section item, so they are not scroll targets.
 */
private fun settingsSectionScrollIndex(
    storedRouteKey: String,
    isRendered: (SettingsSectionKey) -> Boolean,
): Int? {
    val canonicalRouteKey = canonicalSettingsSectionRouteKey(storedRouteKey) ?: return null
    val target =
        SettingsSectionKey.entries.firstOrNull { section -> section.routeKey == canonicalRouteKey }
            ?: return null
    if (target == SettingsSectionKey.About || target == SettingsSectionKey.DeveloperOptions) return null
    if (!isRendered(target)) return null
    return SettingsSectionKey.entries
        .takeWhile { section -> section != target }
        .count { section -> isRendered(section) }
}

val settingsPaneSections: List<SettingsSectionKey>
    get() =
        SettingsSectionKey.entries.filter { sectionKey ->
            sectionKey != SettingsSectionKey.DeveloperOptions &&
                (sectionKey != SettingsSectionKey.Updates || BuildConfig.SHOW_UPDATES)
        }

// Resolved from the enum rather than a hand-maintained `when`, so a routeKey has exactly one home.
// settingsPaneSections already excludes DeveloperOptions and hides Updates unless SHOW_UPDATES,
// which is precisely the set this used to accept.
fun settingsSectionKeyForHighlight(highlightSectionKey: String?): SettingsSectionKey? {
    val routeKey = highlightSectionKey?.substringBefore(".") ?: return null
    return settingsPaneSections.firstOrNull { section ->
        section.routeKey == routeKey || routeKey in section.legacyRouteKeys
    }
}

private enum class BackupFolderTarget {
    Local,
    Cloud,
}

private object SettingsScreenSessionState {
    var collapsedSectionKeys: Set<String> = emptySet()
    var listFirstVisibleItemIndex: Int = 0
    var listFirstVisibleItemScrollOffset: Int = 0
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenIntro: () -> Unit = {},
    onOpenFaqStorageSection: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenDevOptions: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    updateVm: FilePipeUpdateViewModel = hiltViewModel(),
    onUpdateCheckStarted: () -> Unit = {},
    highlightSectionKey: String? = null,
    onHighlightHandled: () -> Unit = {},
    selectedSectionKey: SettingsSectionKey? = null,
    showTopBar: Boolean = true,
    showSectionHeaders: Boolean = true,
    showAboutHeader: Boolean = true,
    centerSelectedSectionContent: Boolean = false,
    suppressBlur: Boolean = false,
) {
    val preferencesOrNull by viewModel.preferencesState.collectAsStateWithLifecycle()
    val preferences =
        preferencesOrNull ?: run {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
            return
        }
    val developerOptionsEnabled by viewModel.developerOptionsEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val updateInfo by updateVm.updateInfo.collectAsStateWithLifecycle()
    val playInAppUpdateBannerUiState by updateVm.playInAppUpdateBannerUiState.collectAsStateWithLifecycle()
    val isCheckingUpdate by updateVm.isCheckingUpdate.collectAsStateWithLifecycle()
    val downloadProgress by updateVm.downloadProgress.collectAsStateWithLifecycle()
    val showUpdateSheet by updateVm.showUpdateSheet.collectAsStateWithLifecycle()
    val updateCheckFinishedWithoutResult by updateVm.updateCheckFinishedWithoutResult.collectAsStateWithLifecycle()
    val updateSheetChangelog by updateVm.updateSheetChangelog.collectAsStateWithLifecycle()
    val openSheetRequested by updateVm.openSheetRequested.collectAsStateWithLifecycle()
    val openUpdateSheetFromRulesPromo by updateVm.openUpdateSheetFromRulesPromo.collectAsStateWithLifecycle()
    val startPlayAfterRulesPromoSheet by updateVm.startPlayInAppUpdateAfterRulesPromoSheet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val density = LocalDensity.current

    fun computeNotificationsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    val alarmManager = remember(context) { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var notificationsGranted by remember { mutableStateOf(computeNotificationsEnabled()) }
    var canScheduleExactAlarms by remember { mutableStateOf(alarmManager.canScheduleExactAlarms()) }
    var allFilesAccessGranted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    var pendingFolderAccessSwitch by remember { mutableStateOf<FolderAccessMode?>(null) }
    var pendingEnableUpdateNotificationsAfterPermission by remember { mutableStateOf(false) }
    val snackbarHostState = LocalSnackbarHostState.current
    val coroutineScope = rememberCoroutineScope()
    val settingsLazyListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = SettingsScreenSessionState.listFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = SettingsScreenSessionState.listFirstVisibleItemScrollOffset,
        )
    val forceExpandedSections = selectedSectionKey != null

    fun shouldRenderSection(sectionKey: SettingsSectionKey): Boolean = selectedSectionKey == null || selectedSectionKey == sectionKey

    val topAlphaMultiplier by remember(settingsLazyListState) {
        derivedStateOf {
            if (settingsLazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val offsetPx = settingsLazyListState.firstVisibleItemScrollOffset.toFloat()
                val thresholdPx = with(density) { 24.dp.toPx() }
                (offsetPx / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }
    val scrollBlurModifier =
        if (suppressBlur || selectedSectionKey == SettingsSectionKey.About) {
            Modifier
        } else {
            LocalProgressiveBlurStyle.current?.let { blurStyle ->
                Modifier.progressiveBlurScrollableList(blurStyle, topAlphaMultiplier = topAlphaMultiplier)
            } ?: Modifier
        }

    // Derived from the enum, not a duplicate hardcoded list. When this was hand-maintained, renaming
    // a routeKey made the stored key fail this filter and updateCollapsedSettingsSectionKeys wrote
    // the filtered set back - permanently discarding that section's collapsed state.
    val settingsExpandableSectionKeys =
        remember {
            settingsPaneSections
                .filter { sectionKey -> sectionKey != SettingsSectionKey.About }
                .map { sectionKey -> sectionKey.routeKey }
                .toSet()
        }
    var collapsedSettingsSectionKeys by rememberSaveable {
        mutableStateOf<Set<String>?>(null)
    }
    // Stored keys are canonicalised first so a key written under an older routeKey still resolves.
    val currentCollapsedSectionKeys =
        collapsedSettingsSectionKeys
            ?: preferences.settingsCollapsedSectionKeys
                .mapNotNull { storedKey -> canonicalSettingsSectionRouteKey(storedKey) }
                .filter { it in settingsExpandableSectionKeys }
                .toSet()
    LaunchedEffect(preferences.settingsCollapsedSectionKeys, settingsExpandableSectionKeys) {
        collapsedSettingsSectionKeys =
            preferences.settingsCollapsedSectionKeys
                .mapNotNull { storedKey -> canonicalSettingsSectionRouteKey(storedKey) }
                .filter { sectionKey -> sectionKey in settingsExpandableSectionKeys }
                .toSet()
    }

    fun updateCollapsedSettingsSectionKeys(sectionKeys: Set<String>) {
        val filteredSectionKeys = sectionKeys.filter { sectionKey -> sectionKey in settingsExpandableSectionKeys }.toSet()
        collapsedSettingsSectionKeys = filteredSectionKeys
        viewModel.setSettingsCollapsedSectionKeys(filteredSectionKeys)
    }

    val allSettingsSectionsCollapsed =
        settingsExpandableSectionKeys.all { sectionKey ->
            sectionKey in currentCollapsedSectionKeys
        }
    var folderAccessHighlight by rememberSaveable { mutableStateOf(false) }
    var folderAccessHighlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    var notificationsHighlight by rememberSaveable { mutableStateOf(false) }
    var notificationsHighlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    val highlightSection = highlightSectionKey?.substringBefore(".")
    LaunchedEffect(highlightSectionKey) {
        val key = highlightSection ?: return@LaunchedEffect
        // Resolved through the enum's legacyRouteKeys rather than a local alias table, so "notifications"
        // (Schedule's old routeKey) keeps working without the mapping living in two places.
        val settingsSectionKey = canonicalSettingsSectionRouteKey(key) ?: key
        val wasCollapsed = settingsSectionKey in currentCollapsedSectionKeys
        updateCollapsedSettingsSectionKeys(currentCollapsedSectionKeys - settingsSectionKey)
        if (wasCollapsed) delay(SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS)
        val targetIndex =
            settingsSectionScrollIndex(settingsSectionKey) { sectionKey -> shouldRenderSection(sectionKey) }
                ?: run {
                    onHighlightHandled()
                    return@LaunchedEffect
                }
        if (settingsLazyListState.layoutInfo.totalItemsCount > targetIndex) {
            settingsLazyListState.animateScrollToItem(targetIndex)
        }
        val highlightExpiresAtMillis = SystemClock.elapsedRealtime() + SETTINGS_SECTION_HIGHLIGHT_DURATION_MS
        when (settingsSectionKey) {
            SettingsSectionKey.FolderAccess.routeKey -> {
                folderAccessHighlight = true
                folderAccessHighlightExpiresAtMillis = highlightExpiresAtMillis
            }

            SettingsSectionKey.Schedule.routeKey -> {
                notificationsHighlight = true
                notificationsHighlightExpiresAtMillis = highlightExpiresAtMillis
            }
        }
        onHighlightHandled()
    }
    LaunchedEffect(folderAccessHighlight, folderAccessHighlightExpiresAtMillis) {
        if (!folderAccessHighlight) return@LaunchedEffect
        val remainingHighlightMillis = folderAccessHighlightExpiresAtMillis - SystemClock.elapsedRealtime()
        if (remainingHighlightMillis > 0) delay(remainingHighlightMillis)
        folderAccessHighlight = false
        folderAccessHighlightExpiresAtMillis = 0L
    }
    LaunchedEffect(notificationsHighlight, notificationsHighlightExpiresAtMillis) {
        if (!notificationsHighlight) return@LaunchedEffect
        val remainingHighlightMillis = notificationsHighlightExpiresAtMillis - SystemClock.elapsedRealtime()
        if (remainingHighlightMillis > 0) delay(remainingHighlightMillis)
        notificationsHighlight = false
        notificationsHighlightExpiresAtMillis = 0L
    }
    val highlightNowMillis = SystemClock.elapsedRealtime()
    val folderAccessHighlightActive = folderAccessHighlight && folderAccessHighlightExpiresAtMillis > highlightNowMillis
    val notificationsHighlightActive = notificationsHighlight && notificationsHighlightExpiresAtMillis > highlightNowMillis
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            notificationsGranted = granted
            if (pendingEnableUpdateNotificationsAfterPermission) {
                pendingEnableUpdateNotificationsAfterPermission = false
                if (granted) {
                    coroutineScope.launch {
                        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                            snackbarHostState.showSnackbar(
                                resources.getString(R.string.settings_notify_updates_enable_notifications),
                            )
                            viewModel.openAppNotificationSettings()
                            return@launch
                        }
                        val schedule = viewModel.preferencesFlow.first().updateCheckSchedule
                        if (schedule != UpdateCheckSchedule.NEVER) {
                            viewModel.setNotifyOnNewUpdates(true)
                        }
                    }
                }
            }
        }
    var postNotificationPermissionLaunchAttempted by remember { mutableStateOf(false) }

    fun requestPostNotificationPermissionOrOpenAppSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val hostActivity = context as? ComponentActivity
        val useAppNotificationSettingsFallback =
            hostActivity != null &&
                postNotificationPermissionLaunchAttempted &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    hostActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
        if (useAppNotificationSettingsFallback) {
            viewModel.openAppNotificationSettings()
        } else {
            postNotificationPermissionLaunchAttempted = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openExactAlarmSettings() {
        val exactAlarmIntent =
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
            }
        runCatching {
            context.startActivity(exactAlarmIntent)
        }.onFailure {
            val appInfoIntent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            runCatching { context.startActivity(appInfoIntent) }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    notificationsGranted = computeNotificationsEnabled()
                    canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()
                    allFilesAccessGranted = Environment.isExternalStorageManager()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val configuration = LocalConfiguration.current
    val isLandscape = isLandscape()
    val isSmallLandscape = isSmallLandscape()
    val heightFraction = if (isLandscape) 0.95f else 0.85f
    val maxUpdateSheetHeight = (configuration.screenHeightDp * heightFraction).dp
    val settingsScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = settingsLazyListState,
            additionalScrollEnabled = false,
            ignoredBottomPadding = 24.dp,
        )

    LaunchedEffect(Unit) {
        viewModel.userMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    LaunchedEffect(Unit) {
        updateVm.userMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    DisposableEffect(lifecycleOwner, settingsLazyListState) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    SettingsScreenSessionState.collapsedSectionKeys = currentCollapsedSectionKeys
                    SettingsScreenSessionState.listFirstVisibleItemIndex = settingsLazyListState.firstVisibleItemIndex
                    SettingsScreenSessionState.listFirstVisibleItemScrollOffset = settingsLazyListState.firstVisibleItemScrollOffset
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(lifecycleOwner, snackbarHostState) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    snackbarHostState.currentSnackbarData?.dismiss()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pendingBackupFolderTarget by rememberSaveable { mutableStateOf<BackupFolderTarget?>(null) }
    val folderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            val target = pendingBackupFolderTarget
            pendingBackupFolderTarget = null
            if (uri != null) {
                when (target) {
                    BackupFolderTarget.Cloud -> viewModel.setCloudExportFolderUri(uri.toString())
                    BackupFolderTarget.Local -> viewModel.setExportFolderUri(uri.toString())
                    null -> Unit
                }
            }
        }

    val createDocumentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.completeManualExportToUri(uri)
            }
        }

    LaunchedEffect(Unit) {
        viewModel.manualExportPickerRequested.collect { suggestedFileName ->
            createDocumentLauncher.launch(suggestedFileName)
        }
    }

    var pendingBackupPickAction by remember { mutableStateOf<BackupImportPickAction?>(null) }

    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            val action = pendingBackupPickAction
            pendingBackupPickAction = null
            if (uri != null && action != null) {
                viewModel.importFromUri(uri, action)
            }
        }

    val playInAppUpdateLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_CANCELED) {
                updateVm.onPlayInAppUpdateUserCanceled()
            }
        }

    LaunchedEffect(playInAppUpdateBannerUiState, showUpdateSheet) {
        if (!showUpdateSheet || !BuildConfig.USE_PLAY_IN_APP_UPDATES) return@LaunchedEffect
        when (playInAppUpdateBannerUiState) {
            is PlayInAppUpdateBannerUiState.Downloading,
            PlayInAppUpdateBannerUiState.ReadyToInstall,
            -> {
                updateVm.closeSheetForPlayProgress()
            }

            else -> {}
        }
    }

    LaunchedEffect(openSheetRequested) {
        if (!openSheetRequested || !BuildConfig.SHOW_UPDATES) return@LaunchedEffect
        onUpdateCheckStarted()
        updateVm.markOpenSheetHandled()
        updateVm.openSheetAndCheck()
    }

    LaunchedEffect(openUpdateSheetFromRulesPromo) {
        if (!openUpdateSheetFromRulesPromo || !BuildConfig.SHOW_UPDATES) return@LaunchedEffect
        updateVm.openSheetFromRulesPromo()
        updateVm.consumeOpenUpdateSheetFromRulesPromo()
    }

    LaunchedEffect(startPlayAfterRulesPromoSheet) {
        if (!startPlayAfterRulesPromoSheet || !BuildConfig.USE_PLAY_IN_APP_UPDATES) return@LaunchedEffect
        delay(400)
        val hostActivity = context as? ComponentActivity
        updateVm.tryStartPlayInAppUpdate(hostActivity, playInAppUpdateLauncher)
        updateVm.consumeStartPlayInAppUpdateAfterRulesPromoSheet()
    }

    if (showUpdateSheet && BuildConfig.SHOW_UPDATES) {
        val updateSheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Expanded,
                confirmValueChange = { true },
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        val updateSheetContainerColors = elevatedCardColors()
        val currentOrientation = LocalConfiguration.current.orientation
        LaunchedEffect(currentOrientation) {
            updateSheetState.expand()
        }
        AppBottomSheet(
            title = "",
            onDismiss = {
                updateVm.dismissUpdateSheet()
            },
            sheetState = updateSheetState,
            showTitleBar = false,
            scrollable = false,
            contentPadding = PaddingValues(0.dp),
            containerColor = updateSheetContainerColors.containerColor,
            contentColor = updateSheetContainerColors.contentColor,
        ) {
            UpdateCheckBottomSheetContent(
                maxSheetHeight = maxUpdateSheetHeight,
                isCheckingUpdate = isCheckingUpdate,
                updateInfo = updateInfo,
                manualUpdateNoResult = updateCheckFinishedWithoutResult,
                downloadProgress = downloadProgress,
                changelogState = updateSheetChangelog,
                showGithubExtraUi = BuildConfig.FLAVOR == "github",
                useFdroidUpdates = BuildConfig.FLAVOR == "fdroid",
                usePlayInAppUpdates = BuildConfig.USE_PLAY_IN_APP_UPDATES,
                onDownloadClick = { info ->
                    if (BuildConfig.FLAVOR == "fdroid") {
                        openFdroidPackagePage(context)
                    } else if (BuildConfig.USE_PLAY_IN_APP_UPDATES && info.downloadUrl.isBlank()) {
                        val hostActivity = context as? ComponentActivity
                        updateVm.tryStartPlayInAppUpdate(hostActivity, playInAppUpdateLauncher)
                    } else {
                        updateVm.downloadAndInstall(info)
                    }
                },
                onSkipVersionClick = {
                    updateInfo?.let { info ->
                        updateVm.skipAcknowledgedGithubRelease(info)
                        updateVm.dismissUpdateSheet()
                    }
                },
            )
        }
    }

    fun applyFolderAccessMode(mode: FolderAccessMode) {
        if (preferences.folderAccessMode == FolderAccessMode.ALL_FILES_PREFERRED &&
            mode != FolderAccessMode.ALL_FILES_PREFERRED
        ) {
            pendingFolderAccessSwitch = mode
        } else {
            viewModel.setFolderAccessMode(mode)
        }
    }

    pendingFolderAccessSwitch?.let { targetMode ->
        FilePipeConfirmDialog(
            title = stringResource(R.string.settings_folder_access_switch_to_saf_title),
            text = stringResource(R.string.settings_folder_access_switch_to_saf_body),
            confirmLabel = stringResource(R.string.settings_folder_access_switch_confirm),
            onConfirm = {
                val confirmedTarget = targetMode
                pendingFolderAccessSwitch = null
                coroutineScope.launch {
                    val affectedRuleCount = viewModel.countRulesUsingFilesystemFolderPaths()
                    viewModel.setFolderAccessModeNow(confirmedTarget)
                    val message =
                        when {
                            affectedRuleCount <= 0 -> {
                                resources.getString(R.string.settings_folder_access_switched_selective_zero_rules)
                            }

                            else -> {
                                resources.getQuantityString(
                                    R.plurals.settings_folder_access_switched_selective_snackbar,
                                    affectedRuleCount,
                                    affectedRuleCount,
                                )
                            }
                        }
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Long,
                    )
                }
            },
            onDismiss = { pendingFolderAccessSwitch = null },
            // Switching modes can revoke access to folders rules depend on, so don't push it.
            destructive = true,
        )
    }

    Scaffold(
        containerColor = if (LocalUseGradientBackground.current) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) {
                Column(Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = {},
                        colors = gradientOverlayTopAppBarColors(),
                        actions = {
                            val helpOpenLabel = stringResource(R.string.settings_fab_open_help)
                            FilePipeFilledTonalIconButton(
                                onClick = onOpenHelp,
                                modifier = Modifier.semantics { contentDescription = helpOpenLabel },
                            ) {
                                Text(
                                    text = "?",
                                    style =
                                        MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = MaterialTheme.typography.titleLarge.fontSize,
                                        ),
                                )
                            }
                            val expandCollapseAllLabel =
                                stringResource(
                                    if (allSettingsSectionsCollapsed) {
                                        R.string.settings_expand_all_sections_cd
                                    } else {
                                        R.string.settings_collapse_all_sections_cd
                                    },
                                )
                            FilePipeFilledTonalIconButton(
                                onClick = {
                                    updateCollapsedSettingsSectionKeys(
                                        if (allSettingsSectionsCollapsed) {
                                            currentCollapsedSectionKeys - settingsExpandableSectionKeys
                                        } else {
                                            currentCollapsedSectionKeys + settingsExpandableSectionKeys
                                        },
                                    )
                                },
                                modifier = Modifier.semantics { contentDescription = expandCollapseAllLabel },
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name =
                                        if (allSettingsSectionsCollapsed) {
                                            "unfold_more"
                                        } else {
                                            "unfold_less"
                                        },
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = settingsLazyListState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(scrollBlurModifier),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + contentPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement =
                if (centerSelectedSectionContent && selectedSectionKey == SettingsSectionKey.About) {
                    Arrangement.Center
                } else {
                    Arrangement.spacedBy(20.dp)
                },
            userScrollEnabled = settingsScrollEnabled,
        ) {
            // Appearance
            if (shouldRenderSection(SettingsSectionKey.Appearance)) {
                item {
                    SettingsExpandableSection(
                        sectionKey = SettingsSectionKey.Appearance.routeKey,
                        iconName = SettingsSectionKey.Appearance.iconName,
                        title = stringResource(R.string.settings_appearance_section),
                        collapsedSectionKeys = currentCollapsedSectionKeys,
                        onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                        showHeader = showSectionHeaders,
                        forceExpanded = forceExpandedSections,
                    ) {
                        AppearanceSection(
                            themeMode = preferences.themeMode,
                            useBlackTheme = preferences.useBlackTheme,
                            colorSource = preferences.colorSource,
                            savedCustomSeedHexes = preferences.savedCustomSeedHexes,
                            activeCustomSeedHex = preferences.activeCustomSeedHex,
                            themePaletteStyle = preferences.themePaletteStyle,
                            useGradientBackground = preferences.useGradientBackground,
                            shadingIntensity = preferences.shadingIntensity,
                            uiScale = preferences.uiScale,
                            progressiveBlurEnabled = preferences.progressiveBlurEnabled,
                            customFontPath = preferences.customFontPath,
                            customFontName = preferences.customFontName,
                            onThemeMode = viewModel::setThemeMode,
                            onUseBlackTheme = viewModel::setUseBlackTheme,
                            onColorSource = viewModel::setColorSource,
                            onPaletteStyle = viewModel::setThemePaletteStyle,
                            onAddCustomSeedHex = viewModel::addCustomSeedHex,
                            onSelectCustomSeedHex = viewModel::selectCustomSeedHex,
                            onPreviewCustomSeedHex = viewModel::previewCustomSeedHex,
                            onRemoveCustomSeedHex = viewModel::removeCustomSeedHex,
                            onUseGradientBackground = viewModel::setUseGradientBackground,
                            onShadingIntensity = viewModel::setShadingIntensity,
                            onUiScale = viewModel::setUiScale,
                            onProgressiveBlurEnabled = viewModel::setProgressiveBlurEnabled,
                            onCustomFontImported = viewModel::importCustomFont,
                            onCustomFontClear = viewModel::clearCustomFont,
                            onBlackThemeEffectClick = {
                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        resources.getString(R.string.settings_black_theme_effect_disabled),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // Folder access
            if (shouldRenderSection(SettingsSectionKey.FolderAccess)) {
                item {
                    val folderHighlightPulse = rememberSectionHighlightPulseAlpha(folderAccessHighlightActive)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .pulsingSectionHighlightOutline(
                                    active = folderAccessHighlightActive,
                                    outlineColor =
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = folderHighlightPulse,
                                        ),
                                ),
                    ) {
                        SettingsExpandableSection(
                            sectionKey = SettingsSectionKey.FolderAccess.routeKey,
                            iconName = SettingsSectionKey.FolderAccess.iconName,
                            title = stringResource(R.string.settings_folder_access_section),
                            collapsedSectionKeys = currentCollapsedSectionKeys,
                            onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                            showHeader = showSectionHeaders,
                            forceExpanded = forceExpandedSections,
                        ) {
                            GroupedListColumn {
                                GroupedListItem(position = GroupPosition.FIRST) {
                                    ListItem(
                                        trailingContent = {
                                            RadioButton(
                                                selected = preferences.folderAccessMode == FolderAccessMode.SAF_ONLY,
                                                onClick = null,
                                            )
                                        },
                                        modifier =
                                            Modifier.appClickable {
                                                applyFolderAccessMode(FolderAccessMode.SAF_ONLY)
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        Text(
                                            stringResource(R.string.settings_folder_access_saf_only),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                                GroupedListItem(position = GroupPosition.LAST) {
                                    ListItem(
                                        trailingContent = {
                                            RadioButton(
                                                selected = preferences.folderAccessMode == FolderAccessMode.ALL_FILES_PREFERRED,
                                                onClick = null,
                                            )
                                        },
                                        modifier =
                                            Modifier.appClickable {
                                                applyFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        Text(
                                            stringResource(R.string.settings_folder_access_all_files),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val selectiveLike =
                                preferences.folderAccessMode == FolderAccessMode.SAF_ONLY ||
                                    preferences.folderAccessMode == FolderAccessMode.DEFERRED
                            val allFilesModeSelected =
                                preferences.folderAccessMode == FolderAccessMode.ALL_FILES_PREFERRED
                            val allFilesStatusLine =
                                when {
                                    selectiveLike && allFilesAccessGranted -> {
                                        stringResource(R.string.settings_folder_access_all_files_status_granted_unused)
                                    }

                                    selectiveLike && !allFilesAccessGranted -> {
                                        stringResource(R.string.settings_folder_access_all_files_status_not_granted_idle)
                                    }

                                    allFilesModeSelected && allFilesAccessGranted -> {
                                        stringResource(R.string.settings_folder_access_all_files_status_granted_used)
                                    }

                                    else -> {
                                        stringResource(R.string.settings_folder_access_all_files_status_not_granted_required)
                                    }
                                }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val statusStyle = MaterialTheme.typography.bodySmall
                                val statusColor = MaterialTheme.colorScheme.onSurfaceVariant
                                Text(
                                    text = allFilesStatusLine,
                                    style = statusStyle,
                                    color = statusColor,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_permissions_learn_more),
                                    style = statusStyle,
                                    color = statusColor,
                                    modifier =
                                        Modifier
                                            .padding(start = 8.dp)
                                            .appClickable(onClick = onOpenFaqStorageSection),
                                )
                            }
                            val showAllFilesActionButton =
                                when {
                                    selectiveLike && !allFilesAccessGranted -> false
                                    selectiveLike && allFilesAccessGranted -> true
                                    allFilesModeSelected && !allFilesAccessGranted -> true
                                    allFilesModeSelected && allFilesAccessGranted -> false
                                    else -> false
                                }
                            if (showAllFilesActionButton) {
                                Spacer(Modifier.height(8.dp))
                                FilePipeOutlinedButton(
                                    onClick = {
                                        val manageIntent =
                                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = "package:${context.packageName}".toUri()
                                            }
                                        context.startActivity(manageIntent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        stringResource(
                                            if (selectiveLike) {
                                                R.string.settings_folder_access_open_manage
                                            } else {
                                                R.string.settings_folder_access_grant_all_files
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Schedule
            if (shouldRenderSection(SettingsSectionKey.Schedule)) {
                item {
                    val notificationsHighlightPulse =
                        rememberSectionHighlightPulseAlpha(notificationsHighlightActive)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .pulsingSectionHighlightOutline(
                                    active = notificationsHighlightActive,
                                    outlineColor =
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = notificationsHighlightPulse,
                                        ),
                                ),
                    ) {
                        SettingsExpandableSection(
                            sectionKey = SettingsSectionKey.Schedule.routeKey,
                            iconName = SettingsSectionKey.Schedule.iconName,
                            title = stringResource(R.string.settings_schedule_section),
                            collapsedSectionKeys = currentCollapsedSectionKeys,
                            onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                            showHeader = showSectionHeaders,
                            forceExpanded = forceExpandedSections,
                        ) {
                            GroupedListColumn {
                                GroupedListItem(position = GroupPosition.FIRST) {
                                    SettingsToggleRow(
                                        iconName = "notifications",
                                        title = stringResource(R.string.settings_notifications),
                                        subtitle = stringResource(R.string.settings_notifications_desc),
                                        checked = notificationsGranted,
                                        onCheckedChange = { wantEnabled ->
                                            when {
                                                wantEnabled &&
                                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                                                    pendingEnableUpdateNotificationsAfterPermission = false
                                                    requestPostNotificationPermissionOrOpenAppSettings()
                                                }

                                                wantEnabled &&
                                                    !NotificationManagerCompat
                                                        .from(context)
                                                        .areNotificationsEnabled() -> {
                                                    viewModel.openAppNotificationSettings()
                                                }

                                                !wantEnabled -> {
                                                    viewModel.openAppNotificationSettings()
                                                }
                                            }
                                        },
                                    )
                                }
                                GroupedListItem(position = GroupPosition.MIDDLE) {
                                    SettingsToggleRow(
                                        iconName = "alarm_on",
                                        title = stringResource(R.string.settings_reliable_schedules),
                                        subtitle =
                                            stringResource(
                                                if (canScheduleExactAlarms) {
                                                    R.string.settings_reliable_schedules_desc_enabled
                                                } else {
                                                    R.string.settings_reliable_schedules_desc_disabled
                                                },
                                            ),
                                        checked = canScheduleExactAlarms,
                                        onCheckedChange = { openExactAlarmSettings() },
                                    )
                                }
                                GroupedListItem(position = GroupPosition.LAST) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "history",
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                stringResource(R.string.settings_log_retention),
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                            Text(
                                                stringResource(R.string.settings_log_retention_hint),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        LogRetentionDropdown(
                                            currentDays = preferences.logRetentionDays,
                                            onSelect = { viewModel.setLogRetentionDays(it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Touch & Sound
            if (shouldRenderSection(SettingsSectionKey.TouchSound)) {
                item {
                    SettingsExpandableSection(
                        sectionKey = SettingsSectionKey.TouchSound.routeKey,
                        iconName = SettingsSectionKey.TouchSound.iconName,
                        title = stringResource(R.string.settings_touch_sound_section),
                        collapsedSectionKeys = currentCollapsedSectionKeys,
                        onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                        showHeader = showSectionHeaders,
                        forceExpanded = forceExpandedSections,
                    ) {
                        GroupedListColumn {
                            GroupedListItem(position = GroupPosition.ONLY) {
                                SettingsToggleRow(
                                    iconName = "vibration",
                                    title = stringResource(R.string.settings_haptic_feedback),
                                    subtitle = stringResource(R.string.settings_haptic_feedback_desc),
                                    checked = preferences.hapticFeedbackEnabled,
                                    onCheckedChange = viewModel::setHapticFeedbackEnabled,
                                )
                            }
                        }
                    }
                }
            }

            // Swipe Actions
            if (shouldRenderSection(SettingsSectionKey.SwipeActions)) {
                item {
                    SettingsExpandableSection(
                        sectionKey = SettingsSectionKey.SwipeActions.routeKey,
                        iconName = SettingsSectionKey.SwipeActions.iconName,
                        title = stringResource(R.string.settings_swipe_gestures_section),
                        collapsedSectionKeys = currentCollapsedSectionKeys,
                        onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                        showHeader = showSectionHeaders,
                        forceExpanded = forceExpandedSections,
                    ) {
                        GroupedListColumn {
                            GroupedListItem(position = GroupPosition.ONLY) {
                                SwipeExecuteOneActionsEditor(
                                    startTitle = stringResource(R.string.settings_swipe_right),
                                    endTitle = stringResource(R.string.settings_swipe_left),
                                    startAction = preferences.swipeStartToEnd,
                                    endAction = preferences.swipeEndToStart,
                                    onStartActionChange = { viewModel.setSwipeStartToEnd(it) },
                                    onEndActionChange = { viewModel.setSwipeEndToStart(it) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Import/Export ────────────────────────────────────────────────
            if (shouldRenderSection(SettingsSectionKey.Backup)) {
                item {
                    SettingsExpandableSection(
                        sectionKey = SettingsSectionKey.Backup.routeKey,
                        iconName = SettingsSectionKey.Backup.iconName,
                        title = stringResource(R.string.settings_backup_section),
                        collapsedSectionKeys = currentCollapsedSectionKeys,
                        onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                        showHeader = showSectionHeaders,
                        forceExpanded = forceExpandedSections,
                    ) {
                        BackupSection(
                            preferences = preferences,
                            snackbarHostState = snackbarHostState,
                            scope = coroutineScope,
                            onPickLocalFolder = {
                                pendingBackupFolderTarget = BackupFolderTarget.Local
                                folderLauncher.launch(null)
                            },
                            onPickCloudFolder = {
                                pendingBackupFolderTarget = BackupFolderTarget.Cloud
                                folderLauncher.launch(null)
                            },
                            onLaunchImportMerge = {
                                pendingBackupPickAction = BackupImportPickAction.ImportMerge
                                importLauncher.launch("application/json")
                            },
                            onLaunchImportReplace = {
                                pendingBackupPickAction = BackupImportPickAction.RestoreFull
                                importLauncher.launch("application/json")
                            },
                            onClearLocalFolder = { viewModel.setExportFolderUri("") },
                            onClearCloudFolder = { viewModel.setCloudExportFolderUri("") },
                            onAutoExportChange = viewModel::setAutoExportOnChange,
                            onScheduledExportChange = viewModel::setScheduledExportEnabled,
                            onExportNow = { viewModel.exportToConfiguredBackupFolders() },
                        )
                    }
                }
            }

            // ── Updates (GitHub APK or Play in-app updates by flavor) ─────────
            if (BuildConfig.SHOW_UPDATES && shouldRenderSection(SettingsSectionKey.Updates)) {
                item {
                    Column {
                        SettingsExpandableSection(
                            sectionKey = SettingsSectionKey.Updates.routeKey,
                            iconName = SettingsSectionKey.Updates.iconName,
                            title = stringResource(R.string.settings_updates_section),
                            collapsedSectionKeys = currentCollapsedSectionKeys,
                            onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                            showHeader = showSectionHeaders,
                            forceExpanded = forceExpandedSections,
                        ) {
                            GroupedListColumn {
                                GroupedListItem(position = GroupPosition.FIRST) {
                                    UpdateCheckScheduleDropdown(
                                        selected = preferences.updateCheckSchedule,
                                        onSelect = { schedule ->
                                            viewModel.setUpdateCheckSchedule(schedule)
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                if (BuildConfig.FLAVOR == "github") {
                                    GroupedListItem(position = GroupPosition.MIDDLE) {
                                        SettingsToggleRow(
                                            title = stringResource(R.string.settings_save_update_apk_to_downloads),
                                            checked = preferences.saveUpdateApkToDownloads,
                                            onCheckedChange = viewModel::setSaveUpdateApkToDownloads,
                                        )
                                    }
                                }
                                GroupedListItem(position = GroupPosition.MIDDLE) {
                                    SettingsToggleRow(
                                        title = stringResource(R.string.settings_notify_new_updates),
                                        checked = preferences.notifyOnNewUpdates,
                                        onCheckedChange = { enabled ->
                                            when {
                                                !enabled -> {
                                                    pendingEnableUpdateNotificationsAfterPermission = false
                                                    viewModel.setNotifyOnNewUpdates(false)
                                                }

                                                preferences.updateCheckSchedule == UpdateCheckSchedule.NEVER -> {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            resources.getString(
                                                                R.string.settings_notify_updates_need_auto_check,
                                                            ),
                                                        )
                                                    }
                                                }

                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                    ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.POST_NOTIFICATIONS,
                                                    ) != PackageManager.PERMISSION_GRANTED -> {
                                                    pendingEnableUpdateNotificationsAfterPermission = true
                                                    requestPostNotificationPermissionOrOpenAppSettings()
                                                }

                                                !NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            resources.getString(
                                                                R.string.settings_notify_updates_enable_notifications,
                                                            ),
                                                        )
                                                    }
                                                    viewModel.openAppNotificationSettings()
                                                }

                                                else -> {
                                                    viewModel.setNotifyOnNewUpdates(true)
                                                }
                                            }
                                        },
                                    )
                                }
                                GroupedListItem(position = GroupPosition.LAST) {
                                    ListItem(
                                        leadingContent = {
                                            FilePipeMaterialRoundedSymbol(
                                                name = "new_releases",
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        modifier =
                                            Modifier.appClickable {
                                                onUpdateCheckStarted()
                                                updateVm.openSheetFromSettingsRow()
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        val available = updateInfo
                                        Text(
                                            text =
                                                if (available != null) {
                                                    stringResource(
                                                        R.string.settings_update_available_button,
                                                        available.versionName,
                                                    )
                                                } else {
                                                    stringResource(R.string.settings_check_for_updates)
                                                },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (developerOptionsEnabled && selectedSectionKey == null) {
                item {
                    SettingsStandaloneNavigationRow(
                        iconName = "developer_board",
                        title = stringResource(R.string.settings_developer_options_section),
                        onClick = onOpenDevOptions,
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            if (shouldRenderSection(SettingsSectionKey.About)) {
                item {
                    AboutSection(
                        modifier =
                            Modifier.padding(
                                top =
                                    if (isSmallLandscape) {
                                        12.dp
                                    } else if (developerOptionsEnabled) {
                                        0.dp
                                    } else {
                                        24.dp
                                    },
                            ),
                        onOpenIntro = onOpenIntro,
                        onOpenDevOptions = onOpenDevOptions,
                        onDeveloperOptionsUnlocked = { viewModel.setDeveloperOptionsEnabled(true) },
                        onLaunchPlayReview = { onFlowFinished ->
                            val hostActivity = context as? ComponentActivity
                            if (hostActivity != null) {
                                updateVm.launchPlayInAppReviewFromSettings(hostActivity, onFlowFinished)
                            } else {
                                onFlowFinished()
                            }
                        },
                        developerOptionsEnabled = developerOptionsEnabled,
                        preferences = preferences,
                        showHeader = showAboutHeader && showSectionHeaders,
                    )
                }
            }
        }
    }
}
