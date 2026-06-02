package dev.bikram.filepipe.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import dev.bikram.filepipe.ui.components.AboutAuthorPhoto
import dev.bikram.filepipe.ui.components.AppIconImage
import dev.bikram.filepipe.ui.components.FilePipeButton
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
import dev.bikram.filepipe.ui.feedback.tapSoundClickable
import dev.bikram.filepipe.ui.feedback.tapSoundCombinedClickable
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

/**
 * Indices of the first unconditional [LazyColumn] items (Appearance, Folder access, Touch & sound, …)
 * before the optional Updates block. Used to scroll from Help quick actions.
 */
private const val SETTINGS_LIST_INDEX_FOLDER_ACCESS = 1
private const val SETTINGS_LIST_INDEX_SCHEDULE = 2
private const val SETTINGS_SECTION_HIGHLIGHT_DURATION_MS = 4_500L
private const val SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS = 900L
private const val DEVELOPER_OPTIONS_UNLOCK_TAPS = 7

private enum class BackupFolderTarget {
    Local,
    Cloud,
}

private enum class SwipeDirectionCue(
    val iconName: String,
) {
    LEFT("arrow_back"),
    RIGHT("arrow_forward"),
}

@Composable
private fun rememberSectionHighlightPulseAlpha(active: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "settingsSectionHighlight")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse",
    )
    return if (active) pulse else 1f
}

private fun Modifier.pulsingSectionHighlightOutline(
    active: Boolean,
    outlineColor: Color,
    expandDp: Dp = 10.dp,
    cornerRadiusDp: Dp = 18.dp,
    strokeWidthDp: Dp = 3.dp,
): Modifier {
    if (!active) return this
    return this
        .graphicsLayer { clip = false }
        .drawBehind {
            val expandPx = expandDp.toPx()
            val strokeWidthPx = strokeWidthDp.toPx()
            val cornerPx = cornerRadiusDp.toPx()
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(-expandPx, -expandPx),
                size = Size(size.width + 2f * expandPx, size.height + 2f * expandPx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokeWidthPx),
            )
        }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
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
    highlightSectionKey: String? = null,
    onHighlightHandled: () -> Unit = {},
) {
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferences.DEFAULT)
    val developerOptionsEnabled by viewModel.developerOptionsEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val playInAppUpdateBannerUiState by viewModel.playInAppUpdateBannerUiState.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
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
    val settingsLazyListState = rememberLazyListState()

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
        LocalProgressiveBlurStyle.current?.let { blurStyle ->
            Modifier.progressiveBlurScrollableList(blurStyle, topAlphaMultiplier = topAlphaMultiplier)
        } ?: Modifier

    var collapsedSettingsSectionKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val settingsExpandableSectionKeys =
        remember {
            buildSet {
                add("appearance")
                add("folder_access")
                add("schedule")
                add("touch_sound")
                add("swipe_actions")
                add("backup")
                if (BuildConfig.SHOW_UPDATES) add("updates")
            }
        }
    val allSettingsSectionsCollapsed =
        settingsExpandableSectionKeys.all { sectionKey ->
            sectionKey in collapsedSettingsSectionKeys
        }
    var folderAccessHighlight by rememberSaveable { mutableStateOf(false) }
    var folderAccessHighlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    var notificationsHighlight by rememberSaveable { mutableStateOf(false) }
    var notificationsHighlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    val highlightSection = highlightSectionKey?.substringBefore(".")
    LaunchedEffect(highlightSectionKey) {
        val key = highlightSection ?: return@LaunchedEffect
        val settingsSectionKey =
            when (key) {
                "notifications" -> "schedule"
                else -> key
            }
        val wasCollapsed = settingsSectionKey in collapsedSettingsSectionKeys
        collapsedSettingsSectionKeys = collapsedSettingsSectionKeys - settingsSectionKey
        if (wasCollapsed) delay(SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS)
        val targetIndex =
            when (key) {
                "folder_access" -> {
                    SETTINGS_LIST_INDEX_FOLDER_ACCESS
                }

                "notifications" -> {
                    SETTINGS_LIST_INDEX_SCHEDULE
                }

                else -> {
                    onHighlightHandled()
                    return@LaunchedEffect
                }
            }
        if (settingsLazyListState.layoutInfo.totalItemsCount > targetIndex) {
            settingsLazyListState.animateScrollToItem(targetIndex)
        }
        val highlightExpiresAtMillis = SystemClock.elapsedRealtime() + SETTINGS_SECTION_HIGHLIGHT_DURATION_MS
        when (key) {
            "folder_access" -> {
                folderAccessHighlight = true
                folderAccessHighlightExpiresAtMillis = highlightExpiresAtMillis
            }

            "notifications" -> {
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
    val updateSheetChangelog by viewModel.updateSheetChangelog.collectAsStateWithLifecycle()
    val manualUpdateNoResult by viewModel.manualUpdateNoResult.collectAsStateWithLifecycle()
    val openUpdateSheetFromNotification by viewModel.openUpdateSheetFromNotification.collectAsStateWithLifecycle()
    var showUpdateSheet by remember { mutableStateOf(false) }
    val updateSheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val windowHeight =
        with(density) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val maxUpdateSheetHeight = windowHeight * 0.85f
    val settingsScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = settingsLazyListState,
            additionalScrollEnabled = false,
            ignoredBottomPadding = 24.dp,
        )

    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        } finally {
            viewModel.clearUserMessage()
        }
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

    var pendingBackupFolderTarget by remember { mutableStateOf<BackupFolderTarget?>(null) }
    val folderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            val target = pendingBackupFolderTarget
            pendingBackupFolderTarget = null
            if (uri != null) {
                val path = safTreeUriToPath(uri) ?: uri.toString()
                when (target) {
                    BackupFolderTarget.Cloud -> viewModel.setCloudExportFolderUri(uri.toString())

                    BackupFolderTarget.Local,
                    null,
                    -> viewModel.setExportFolderUri(path)
                }
            }
        }

    val cloudBackupDocumentLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri: Uri? ->
            if (uri != null) {
                viewModel.completeCloudBackupDocumentSelection(uri)
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
                viewModel.onPlayInAppUpdateUserCanceled()
            }
        }

    LaunchedEffect(showUpdateSheet) {
        if (showUpdateSheet) {
            updateSheetState.expand()
        }
    }

    LaunchedEffect(playInAppUpdateBannerUiState, showUpdateSheet) {
        if (!showUpdateSheet || !BuildConfig.USE_PLAY_IN_APP_UPDATES) return@LaunchedEffect
        when (playInAppUpdateBannerUiState) {
            is PlayInAppUpdateBannerUiState.Downloading,
            PlayInAppUpdateBannerUiState.ReadyToInstall,
            -> {
                showUpdateSheet = false
                viewModel.dismissUpdateSheet()
            }

            else -> {}
        }
    }

    val openUpdateSheetFromRulesPromo by viewModel.openUpdateSheetFromRulesPromo.collectAsStateWithLifecycle()
    val startPlayAfterRulesPromoSheet by viewModel.startPlayInAppUpdateAfterRulesPromoSheet.collectAsStateWithLifecycle()

    LaunchedEffect(openUpdateSheetFromNotification) {
        if (!openUpdateSheetFromNotification || !BuildConfig.SHOW_UPDATES) return@LaunchedEffect
        showUpdateSheet = true
        viewModel.loadChangelogForUpdateSheet()
        viewModel.beginManualUpdateCheckFromSheet()
        viewModel.consumeOpenUpdateSheetFromNotification()
    }

    LaunchedEffect(openUpdateSheetFromRulesPromo) {
        if (!openUpdateSheetFromRulesPromo || !BuildConfig.SHOW_UPDATES) return@LaunchedEffect
        showUpdateSheet = true
        if (BuildConfig.FLAVOR == "github" && BuildConfig.CHANGELOG_GITHUB_REPO.isNotBlank()) {
            viewModel.loadChangelogForUpdateSheet()
        }
        viewModel.consumeOpenUpdateSheetFromRulesPromo()
    }

    LaunchedEffect(startPlayAfterRulesPromoSheet) {
        if (!startPlayAfterRulesPromoSheet || !BuildConfig.USE_PLAY_IN_APP_UPDATES) return@LaunchedEffect
        delay(400)
        val hostActivity = context as? ComponentActivity
        viewModel.tryStartPlayInAppUpdate(hostActivity, playInAppUpdateLauncher)
        viewModel.consumeStartPlayInAppUpdateAfterRulesPromoSheet()
    }

    if (showUpdateSheet && BuildConfig.SHOW_UPDATES) {
        val updateSheetContainerColors = elevatedCardColors()
        AppBottomSheet(
            title = "",
            onDismiss = {
                showUpdateSheet = false
                viewModel.dismissUpdateSheet()
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
                manualUpdateNoResult = manualUpdateNoResult,
                downloadProgress = downloadProgress,
                changelogState = updateSheetChangelog,
                showGithubExtraUi = BuildConfig.FLAVOR == "github",
                usePlayInAppUpdates = BuildConfig.USE_PLAY_IN_APP_UPDATES,
                onDownloadClick = { info ->
                    if (BuildConfig.USE_PLAY_IN_APP_UPDATES && info.downloadUrl.isBlank()) {
                        val hostActivity = context as? ComponentActivity
                        viewModel.tryStartPlayInAppUpdate(hostActivity, playInAppUpdateLauncher)
                    } else {
                        viewModel.downloadAndInstall(info)
                    }
                },
                onSkipVersionClick = {
                    updateInfo?.let { info ->
                        viewModel.skipAcknowledgedGithubRelease(info)
                        showUpdateSheet = false
                        viewModel.dismissUpdateSheet()
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
        AlertDialog(
            onDismissRequest = { pendingFolderAccessSwitch = null },
            title = { Text(stringResource(R.string.settings_folder_access_switch_to_saf_title)) },
            text = { Text(stringResource(R.string.settings_folder_access_switch_to_saf_body)) },
            confirmButton = {
                FilePipeTextButton(onClick = {
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
                }) {
                    Text(stringResource(R.string.settings_folder_access_switch_confirm))
                }
            },
            dismissButton = {
                FilePipeTextButton(onClick = { pendingFolderAccessSwitch = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        containerColor = if (LocalUseGradientBackground.current) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
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
                                collapsedSettingsSectionKeys =
                                    if (allSettingsSectionsCollapsed) {
                                        collapsedSettingsSectionKeys - settingsExpandableSectionKeys
                                    } else {
                                        collapsedSettingsSectionKeys + settingsExpandableSectionKeys
                                    }
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
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            userScrollEnabled = settingsScrollEnabled,
        ) {
            // ── Appearance ───────────────────────────────────────────────────
            item {
                SettingsExpandableSection(
                    sectionKey = "appearance",
                    iconName = "palette",
                    title = stringResource(R.string.settings_appearance_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    AppearanceSection(
                        themeMode = preferences.themeMode,
                        colorSource = preferences.colorSource,
                        savedCustomSeedHexes = preferences.savedCustomSeedHexes,
                        activeCustomSeedHex = preferences.activeCustomSeedHex,
                        themePaletteStyle = preferences.themePaletteStyle,
                        useGradientBackground = preferences.useGradientBackground,
                        shadingIntensity = preferences.shadingIntensity,
                        progressiveBlurEnabled = preferences.progressiveBlurEnabled,
                        onThemeMode = viewModel::setThemeMode,
                        onColorSource = viewModel::setColorSource,
                        onPaletteStyle = viewModel::setThemePaletteStyle,
                        onAddCustomSeedHex = viewModel::addCustomSeedHex,
                        onSelectCustomSeedHex = viewModel::selectCustomSeedHex,
                        onPreviewCustomSeedHex = viewModel::previewCustomSeedHex,
                        onRemoveCustomSeedHex = viewModel::removeCustomSeedHex,
                        onUseGradientBackground = viewModel::setUseGradientBackground,
                        onShadingIntensity = viewModel::setShadingIntensity,
                        onProgressiveBlurEnabled = viewModel::setProgressiveBlurEnabled,
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

            // ── Folder access ─────────────────────────────────────────────────
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
                        sectionKey = "folder_access",
                        iconName = "folder_open",
                        title = stringResource(R.string.settings_folder_access_section),
                        collapsedSectionKeys = collapsedSettingsSectionKeys,
                        onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                    ) {
                        GroupedListColumn {
                            GroupedListItem(position = GroupPosition.FIRST) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.settings_folder_access_saf_only),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    trailingContent = {
                                        RadioButton(
                                            selected = preferences.folderAccessMode == FolderAccessMode.SAF_ONLY,
                                            onClick = null,
                                        )
                                    },
                                    modifier =
                                        Modifier.tapSoundClickable {
                                            applyFolderAccessMode(FolderAccessMode.SAF_ONLY)
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                            GroupedListItem(position = GroupPosition.LAST) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.settings_folder_access_all_files),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    trailingContent = {
                                        RadioButton(
                                            selected = preferences.folderAccessMode == FolderAccessMode.ALL_FILES_PREFERRED,
                                            onClick = null,
                                        )
                                    },
                                    modifier =
                                        Modifier.tapSoundClickable {
                                            applyFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
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
                                        .tapSoundClickable(onClick = onOpenFaqStorageSection),
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

            // Schedule
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
                        sectionKey = "schedule",
                        iconName = "calendar_clock",
                        title = stringResource(R.string.settings_schedule_section),
                        collapsedSectionKeys = collapsedSettingsSectionKeys,
                        onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                    ) {
                        GroupedListColumn {
                            GroupedListItem(position = GroupPosition.FIRST) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.settings_notifications),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            stringResource(R.string.settings_notifications_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingContent = {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "notifications",
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    trailingContent = {
                                        FilePipeSwitch(
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
                                    },
                                    modifier =
                                        Modifier.tapSoundClickable {
                                            if (!notificationsGranted) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    pendingEnableUpdateNotificationsAfterPermission = false
                                                    requestPostNotificationPermissionOrOpenAppSettings()
                                                } else if (!NotificationManagerCompat
                                                        .from(context)
                                                        .areNotificationsEnabled()
                                                ) {
                                                    viewModel.openAppNotificationSettings()
                                                }
                                            } else {
                                                viewModel.openAppNotificationSettings()
                                            }
                                        },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SettingsToggleItem(
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
                                ListItem(
                                    leadingContent = {
                                        FilePipeMaterialRoundedSymbol(
                                            name = "history",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.settings_log_retention),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            stringResource(R.string.settings_log_retention_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    trailingContent = {
                                        LogRetentionDropdown(
                                            currentDays = preferences.logRetentionDays,
                                            onSelect = { viewModel.setLogRetentionDays(it) },
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }
                    }
                }
            }

            // Touch & Sound
            item {
                SettingsExpandableSection(
                    sectionKey = "touch_sound",
                    iconName = "vibration",
                    title = stringResource(R.string.settings_touch_sound_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.ONLY) {
                            SettingsToggleItem(
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

            // Swipe Actions
            item {
                SettingsExpandableSection(
                    sectionKey = "swipe_actions",
                    iconName = "swipe_left",
                    title = stringResource(R.string.settings_swipe_gestures_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
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

            // ── Import/Export ────────────────────────────────────────────────
            item {
                SettingsExpandableSection(
                    sectionKey = "backup",
                    iconName = "save",
                    title = stringResource(R.string.settings_backup_section),
                    collapsedSectionKeys = collapsedSettingsSectionKeys,
                    onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
                ) {
                    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
                    val localFolderLabel =
                        preferences.exportFolderUri
                            .takeIf { it.isNotBlank() }
                            ?.let { displayPath(it, internalStorageDisplayName) }
                            ?: stringResource(R.string.settings_choose_local_backup_folder)
                    val cloudFolderLabel =
                        preferences.cloudExportFolderUri
                            .takeIf { it.isNotBlank() }
                            ?.let { backupDestinationDisplayLabel(context, it, internalStorageDisplayName) }
                            ?: stringResource(R.string.settings_choose_cloud_backup_file)

                    val exportFolderReady =
                        preferences.exportFolderUri.isNotBlank() ||
                            preferences.cloudExportFolderUri.isNotBlank()
                    val autoExportSwitchEnabled = exportFolderReady || preferences.autoExportOnRuleChange
                    val scheduledExportSwitchEnabled = exportFolderReady || preferences.scheduledExportEnabled

                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.FIRST) {
                            BackupFolderPickerItem(
                                title = localFolderLabel,
                                subtitle = stringResource(R.string.settings_local_backup_folder_hint),
                                onClick = {
                                    pendingBackupFolderTarget = BackupFolderTarget.Local
                                    folderLauncher.launch(null)
                                },
                                onLongClick = {
                                    if (preferences.exportFolderUri.isNotBlank()) {
                                        viewModel.setExportFolderUri("")
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.settings_local_backup_folder_cleared),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            BackupFolderPickerItem(
                                title = cloudFolderLabel,
                                subtitle = stringResource(R.string.settings_cloud_backup_folder_hint),
                                onClick = {
                                    cloudBackupDocumentLauncher.launch("filepipe_cloud_backup.json")
                                },
                                onLongClick = {
                                    if (preferences.cloudExportFolderUri.isNotBlank()) {
                                        viewModel.setCloudExportFolderUri("")
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.settings_cloud_backup_file_cleared),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            SettingsToggleItem(
                                title = stringResource(R.string.settings_auto_export_on_change),
                                subtitle = stringResource(R.string.settings_auto_export_on_change_hint),
                                checked = preferences.autoExportOnRuleChange,
                                switchEnabled = autoExportSwitchEnabled,
                                onDisabledInteraction = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = resources.getString(R.string.settings_export_select_folder_first),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                                onCheckedChange = viewModel::setAutoExportOnChange,
                            )
                        }
                        GroupedListItem(position = GroupPosition.MIDDLE) {
                            SettingsToggleItem(
                                title = stringResource(R.string.settings_scheduled_export),
                                subtitle = stringResource(R.string.settings_scheduled_export_hint),
                                checked = preferences.scheduledExportEnabled,
                                switchEnabled = scheduledExportSwitchEnabled,
                                onDisabledInteraction = {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = resources.getString(R.string.settings_export_select_folder_first),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                                onCheckedChange = viewModel::setScheduledExportEnabled,
                            )
                        }
                        GroupedListItem(position = GroupPosition.LAST) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FilePipeOutlinedButton(
                                        onClick = {
                                            pendingBackupPickAction = BackupImportPickAction.ImportMerge
                                            importLauncher.launch("application/json")
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.settings_import_rules))
                                    }
                                    FilePipeOutlinedButton(
                                        onClick = {
                                            if (exportFolderReady) {
                                                viewModel.exportToConfiguredBackupFolders()
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = resources.getString(R.string.settings_export_select_folder_first),
                                                        duration = SnackbarDuration.Short,
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.settings_export_now))
                                    }
                                }
                                val restoreOutline = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                val restoreLabelColor = MaterialTheme.colorScheme.error
                                val restoreButtonShape = ButtonDefaults.outlinedShape
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(restoreButtonShape)
                                            .border(BorderStroke(1.dp, restoreOutline), restoreButtonShape),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .matchParentSize()
                                                .tapSoundClickable {
                                                    pendingBackupPickAction = BackupImportPickAction.RestoreFull
                                                    importLauncher.launch("application/json")
                                                },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_restore_backup),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = restoreLabelColor,
                                        )
                                    }
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .width(40.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        SettingsInfoDropdown(
                                            title = stringResource(R.string.settings_backup_import_restore_help_title),
                                            tipText = stringResource(R.string.settings_backup_import_restore_help_body),
                                            contentDescription = stringResource(R.string.settings_backup_help_icon_cd),
                                            iconTint = restoreLabelColor.copy(alpha = 0.75f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Updates (GitHub APK or Play in-app updates by flavor) ─────────
            if (BuildConfig.SHOW_UPDATES) {
                item {
                    Column {
                        SettingsExpandableSection(
                            sectionKey = "updates",
                            iconName = "system_update",
                            title = stringResource(R.string.settings_updates_section),
                            collapsedSectionKeys = collapsedSettingsSectionKeys,
                            onCollapsedSectionKeysChange = { collapsedSettingsSectionKeys = it },
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
                                        SettingsToggleItem(
                                            title = stringResource(R.string.settings_save_update_apk_to_downloads),
                                            checked = preferences.saveUpdateApkToDownloads,
                                            onCheckedChange = viewModel::setSaveUpdateApkToDownloads,
                                        )
                                    }
                                }
                                GroupedListItem(position = GroupPosition.MIDDLE) {
                                    SettingsToggleItem(
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
                                        headlineContent = {
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
                                        },
                                        leadingContent = {
                                            FilePipeMaterialRoundedSymbol(
                                                name = "new_releases",
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        modifier =
                                            Modifier.tapSoundClickable {
                                                viewModel.beginManualUpdateCheckFromSheet()
                                                viewModel.loadChangelogForUpdateSheet()
                                                showUpdateSheet = true
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (developerOptionsEnabled) {
                item {
                    SettingsStandaloneNavigationRow(
                        iconName = "developer_board",
                        title = stringResource(R.string.settings_developer_options_section),
                        onClick = onOpenDevOptions,
                    )
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item {
                val aboutContext = LocalContext.current
                val aboutResources = LocalResources.current
                val githubRepoForSourceLink =
                    BuildConfig.GITHUB_REPO
                        .trim()
                        .ifEmpty { BuildConfig.CHANGELOG_GITHUB_REPO.trim() }
                val playStoreListingUrl = BuildConfig.PLAY_STORE_LISTING_URL
                val buildFlavorLabel =
                    when (BuildConfig.FLAVOR) {
                        "github" -> stringResource(R.string.build_flavor_github)
                        "playstore" -> stringResource(R.string.build_flavor_playstore)
                        else -> BuildConfig.FLAVOR
                    }
                val buildTypeLabel =
                    when (BuildConfig.BUILD_TYPE) {
                        "debug" -> stringResource(R.string.build_type_debug)
                        "devRelease" -> stringResource(R.string.build_type_dev_release)
                        "release" -> stringResource(R.string.build_type_release)
                        else -> BuildConfig.BUILD_TYPE
                    }
                val buildVariantToastText = stringResource(R.string.about_build_variant_format, buildFlavorLabel, buildTypeLabel)
                val developerOptionsUnlockedToast = stringResource(R.string.settings_developer_options_unlocked)
                val diagnosticsChooserTitle = stringResource(R.string.settings_share_diagnostics_chooser)
                val diagnosticsTooltip = stringResource(R.string.settings_share_diagnostics)
                val aboutLinkCopiedToast = stringResource(R.string.toast_about_link_copied)
                val authorGithubProfileUrl = stringResource(R.string.about_author_github_profile_url)
                val shareDiagnostics =
                    rememberDiagnosticsShareAction(
                        context = aboutContext,
                        chooserTitle = diagnosticsChooserTitle,
                        preferences = preferences,
                    )
                val copyAboutLinkToClipboard =
                    remember(aboutContext, aboutLinkCopiedToast) {
                        { url: String ->
                            val clipboard =
                                aboutContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("link", url))
                            Toast
                                .makeText(
                                    aboutContext,
                                    aboutLinkCopiedToast,
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                var playStoreAboutUsesListingOnly by remember { mutableStateOf(false) }
                var developerOptionsTapCount by rememberSaveable { mutableIntStateOf(0) }
                Column(modifier = Modifier.padding(top = if (developerOptionsEnabled) 0.dp else 24.dp)) {
                    SettingsSectionHeader(
                        iconName = "info",
                        title = stringResource(R.string.settings_about_section),
                    ) {
                        FilePipeIconButton(
                            onClick = shareDiagnostics,
                            modifier = Modifier.size(40.dp),
                            tooltipLabel = diagnosticsTooltip,
                        ) {
                            FilePipeMaterialRoundedSymbol(
                                name = "bug_report",
                                contentDescription = diagnosticsTooltip,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.ONLY) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(top = 24.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.app_version_format,
                                            stringResource(R.string.app_name),
                                            BuildConfig.VERSION_NAME,
                                        ),
                                    modifier =
                                        Modifier.combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                if (developerOptionsEnabled) {
                                                    onOpenDevOptions()
                                                    return@combinedClickable
                                                }
                                                developerOptionsTapCount += 1
                                                val remaining = DEVELOPER_OPTIONS_UNLOCK_TAPS - developerOptionsTapCount
                                                if (remaining > 0) {
                                                    Toast
                                                        .makeText(
                                                            aboutContext,
                                                            aboutResources.getQuantityString(
                                                                R.plurals.settings_developer_options_taps_remaining,
                                                                remaining,
                                                                remaining,
                                                            ),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                } else {
                                                    developerOptionsTapCount = 0
                                                    viewModel.setDeveloperOptionsEnabled(true)
                                                    Toast
                                                        .makeText(
                                                            aboutContext,
                                                            developerOptionsUnlockedToast,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    onOpenDevOptions()
                                                }
                                            },
                                            onLongClick = {
                                                Toast
                                                    .makeText(aboutContext, buildVariantToastText, Toast.LENGTH_SHORT)
                                                    .show()
                                            },
                                        ),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.app_tagline),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIconImage(
                                        modifier =
                                            Modifier
                                                .size(84.dp)
                                                .clip(RoundedCornerShape(percent = 25))
                                                .tapSoundClickable(onClick = onOpenIntro),
                                    )
                                    Spacer(Modifier.width(20.dp))
                                    AboutAuthorPhoto(
                                        modifier =
                                            Modifier
                                                .size(84.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .tapSoundClickable {
                                                    runCatching {
                                                        aboutContext.startActivity(
                                                            Intent(Intent.ACTION_VIEW, authorGithubProfileUrl.toUri()),
                                                        )
                                                    }
                                                },
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    text = stringResource(R.string.settings_byline),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val hostActivity = context as? ComponentActivity
                                    val aboutPillShape = pillShape
                                    if (BuildConfig.FLAVOR == "github") {
                                        Surface(
                                            shape = aboutPillShape,
                                            color = Color.Transparent,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            modifier =
                                                Modifier
                                                    .clip(aboutPillShape)
                                                    .tapSoundCombinedClickable(
                                                        onClick = {
                                                            runCatching {
                                                                aboutContext.startActivity(
                                                                    Intent(
                                                                        Intent.ACTION_VIEW,
                                                                        playStoreListingUrl.toUri(),
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                        onLongClick = {
                                                            copyAboutLinkToClipboard(playStoreListingUrl)
                                                        },
                                                        role = Role.Button,
                                                    ),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                            ) {
                                                AboutPlayStoreIcon(tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.settings_rate_on_play_store),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                        if (githubRepoForSourceLink.isNotEmpty()) {
                                            Spacer(Modifier.width(12.dp))
                                            Surface(
                                                shape = aboutPillShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                modifier =
                                                    Modifier
                                                        .clip(aboutPillShape)
                                                        .tapSoundCombinedClickable(
                                                            onClick = {
                                                                val repoUrl = "https://github.com/$githubRepoForSourceLink"
                                                                runCatching {
                                                                    aboutContext.startActivity(
                                                                        Intent(Intent.ACTION_VIEW, repoUrl.toUri()),
                                                                    )
                                                                }
                                                            },
                                                            onLongClick = {
                                                                copyAboutLinkToClipboard(
                                                                    "https://github.com/$githubRepoForSourceLink",
                                                                )
                                                            },
                                                            role = Role.Button,
                                                        ),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_github_mark),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.settings_star_on_github),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        if (playStoreAboutUsesListingOnly) {
                                            Surface(
                                                shape = aboutPillShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                modifier =
                                                    Modifier
                                                        .clip(aboutPillShape)
                                                        .tapSoundCombinedClickable(
                                                            onClick = {
                                                                runCatching {
                                                                    aboutContext.startActivity(
                                                                        Intent(
                                                                            Intent.ACTION_VIEW,
                                                                            playStoreListingUrl.toUri(),
                                                                        ),
                                                                    )
                                                                }
                                                            },
                                                            onLongClick = {
                                                                copyAboutLinkToClipboard(playStoreListingUrl)
                                                            },
                                                            role = Role.Button,
                                                        ),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                ) {
                                                    AboutPlayStoreIcon(tint = MaterialTheme.colorScheme.primaryContainer)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.settings_rate_on_play_store),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                shape = aboutPillShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                modifier =
                                                    Modifier
                                                        .clip(aboutPillShape)
                                                        .tapSoundCombinedClickable(
                                                            onClick = {
                                                                if (hostActivity != null) {
                                                                    viewModel.launchPlayInAppReviewFromSettings(
                                                                        hostActivity,
                                                                    ) {
                                                                        playStoreAboutUsesListingOnly = true
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                copyAboutLinkToClipboard(playStoreListingUrl)
                                                            },
                                                            role = Role.Button,
                                                        ),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                ) {
                                                    AboutPlayStoreIcon(tint = MaterialTheme.colorScheme.primaryContainer)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.settings_rate_on_play_store),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        }
                                        if (githubRepoForSourceLink.isNotEmpty()) {
                                            Spacer(Modifier.width(12.dp))
                                            Surface(
                                                shape = aboutPillShape,
                                                color = Color.Transparent,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                                modifier =
                                                    Modifier
                                                        .clip(aboutPillShape)
                                                        .tapSoundCombinedClickable(
                                                            onClick = {
                                                                val repoUrl = "https://github.com/$githubRepoForSourceLink"
                                                                runCatching {
                                                                    aboutContext.startActivity(
                                                                        Intent(Intent.ACTION_VIEW, repoUrl.toUri()),
                                                                    )
                                                                }
                                                            },
                                                            onLongClick = {
                                                                copyAboutLinkToClipboard(
                                                                    "https://github.com/$githubRepoForSourceLink",
                                                                )
                                                            },
                                                            role = Role.Button,
                                                        ),
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(ButtonDefaults.ContentPadding),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_github_mark),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(R.string.settings_star_on_github),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                AboutOtherAppsAndLinks(
                                    context = aboutContext,
                                    copyLinkToClipboard = copyAboutLinkToClipboard,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPlayStoreIcon(tint: Color) {
    Icon(
        painter = painterResource(R.drawable.ic_google_play_mark),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = tint,
    )
}

@Composable
private fun AboutOtherAppsAndLinks(
    context: Context,
    copyLinkToClipboard: (String) -> Unit,
) {
    val rememberStoreUrl =
        stringResource(
            if (BuildConfig.FLAVOR == "github") {
                R.string.settings_about_remember_github_url
            } else {
                R.string.settings_about_remember_play_store_url
            },
        )
    val obtainXStoreUrl = stringResource(R.string.settings_about_obtainx_github_url)
    val websiteUrl = stringResource(R.string.settings_about_filepipe_website_url)
    val privacyUrl = stringResource(R.string.settings_about_filepipe_privacy_url)
    val termsUrl = stringResource(R.string.settings_about_filepipe_terms_url)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about_other_apps),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        AboutAppStoreButton(
            iconResId = R.drawable.remember_logo,
            name = stringResource(R.string.settings_about_remember_name),
            tagline = stringResource(R.string.settings_about_remember_tagline),
            url = rememberStoreUrl,
            accentColor = Color(0xFF4F7D43),
            context = context,
            copyLinkToClipboard = copyLinkToClipboard,
        )
        if (BuildConfig.FLAVOR == "github") {
            Spacer(Modifier.height(8.dp))
            AboutAppStoreButton(
                iconResId = R.drawable.obtainx_logo,
                name = stringResource(R.string.settings_about_obtainx_name),
                tagline = stringResource(R.string.settings_about_obtainx_tagline),
                url = obtainXStoreUrl,
                accentColor = Color(0xFF7C55D9),
                context = context,
                copyLinkToClipboard = copyLinkToClipboard,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AboutTextLink(
                label = stringResource(R.string.settings_about_website),
                url = websiteUrl,
                context = context,
                copyLinkToClipboard = copyLinkToClipboard,
            )
            AboutLinkSeparator()
            AboutTextLink(
                label = stringResource(R.string.settings_about_privacy_policy),
                url = privacyUrl,
                context = context,
                copyLinkToClipboard = copyLinkToClipboard,
            )
            AboutLinkSeparator()
            AboutTextLink(
                label = stringResource(R.string.settings_about_terms),
                url = termsUrl,
                context = context,
                copyLinkToClipboard = copyLinkToClipboard,
            )
        }
    }
}

@Composable
private fun AboutAppStoreButton(
    iconResId: Int,
    name: String,
    tagline: String,
    url: String,
    accentColor: Color,
    context: Context,
    copyLinkToClipboard: (String) -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = accentColor.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f)),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .tapSoundCombinedClickable(
                    onClick = { openAboutUrl(context, url) },
                    onLongClick = { copyLinkToClipboard(url) },
                    role = Role.Button,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconResId),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(compactControlShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilePipeMaterialRoundedSymbol(
                name = "chevron_right",
                contentDescription = null,
                size = 20.dp,
                tint = accentColor.copy(alpha = 0.86f),
            )
        }
    }
}

@Composable
private fun AboutLinkSeparator() {
    Text(
        text = "•",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
    )
}

@Composable
private fun AboutTextLink(
    label: String,
    url: String,
    context: Context,
    copyLinkToClipboard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier =
            modifier
                .tapSoundCombinedClickable(
                    onClick = { openAboutUrl(context, url) },
                    onLongClick = { copyLinkToClipboard(url) },
                    role = Role.Button,
                ).padding(horizontal = 4.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun openAboutUrl(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

/**
 * Opens the system share sheet with the same link and message as the Settings About section used for sharing.
 */
fun launchAppShareChooser(context: Context) {
    val githubRepoForSourceLink =
        BuildConfig.GITHUB_REPO
            .trim()
            .ifEmpty { BuildConfig.CHANGELOG_GITHUB_REPO.trim() }
    val playStoreListingUrl = BuildConfig.PLAY_STORE_LISTING_URL
    val shareUrl =
        when {
            BuildConfig.FLAVOR == "playstore" -> {
                playStoreListingUrl
            }

            githubRepoForSourceLink.isNotEmpty() -> {
                "https://github.com/$githubRepoForSourceLink/releases/latest"
            }

            else -> {
                ""
            }
        }
    if (shareUrl.isEmpty()) return
    val message =
        context.getString(
            R.string.about_share_text,
            context.getString(R.string.app_name),
            shareUrl,
        )
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
        }
    runCatching {
        context.startActivity(
            Intent.createChooser(sendIntent, context.getString(R.string.settings_share_app)),
        )
    }
}

@Composable
private fun rememberDiagnosticsShareAction(
    context: Context,
    chooserTitle: String,
    preferences: AppPreferences,
): () -> Unit =
    remember(context, chooserTitle, preferences) {
        {
            runCatching {
                DiagnosticLog.record(context, "Diagnostic log shared from Settings")
                val diagnosticsFile = DiagnosticLog.createShareFile(context, preferences)
                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        diagnosticsFile,
                    )
                val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_share_diagnostics_subject))
                        putExtra(Intent.EXTRA_TITLE, context.getString(R.string.settings_share_diagnostics))
                        clipData = ClipData.newUri(context.contentResolver, diagnosticsFile.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
            }.onFailure { error ->
                DiagnosticLog.record(context, "Diagnostic log share failed", error)
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.settings_share_diagnostics_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

@Composable
private fun UpdateCheckScheduleDropdown(
    selected: UpdateCheckSchedule,
    onSelect: (UpdateCheckSchedule) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember { UpdateCheckSchedule.entries }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_update_check_frequency),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        FilePipeOutlinedButton(onClick = { expanded = true }) {
            Text(updateScheduleSummaryBeforeColon(selected))
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                options.forEach { option ->
                    FilePipeDropdownMenuItem(
                        text = { Text(updateScheduleLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun summaryLabelBeforeColon(fullScheduleLabel: String): String {
    val colonIndex = fullScheduleLabel.indexOf(':')
    return if (colonIndex >= 0) {
        fullScheduleLabel.substring(0, colonIndex).trim()
    } else {
        fullScheduleLabel
    }
}

@Composable
private fun updateScheduleSummaryBeforeColon(schedule: UpdateCheckSchedule): String = summaryLabelBeforeColon(updateScheduleLabel(schedule))

@Composable
private fun updateScheduleLabel(schedule: UpdateCheckSchedule): String =
    when (schedule) {
        UpdateCheckSchedule.AT_APP_START -> stringResource(R.string.settings_update_schedule_app_start)
        UpdateCheckSchedule.DAILY_AT_21 -> stringResource(R.string.settings_update_schedule_daily_21)
        UpdateCheckSchedule.WEEKLY_MONDAY_AT_21 -> stringResource(R.string.settings_update_schedule_monday_21)
        UpdateCheckSchedule.NEVER -> stringResource(R.string.settings_update_schedule_never)
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun UpdateCheckBottomSheetContent(
    maxSheetHeight: Dp,
    isCheckingUpdate: Boolean,
    updateInfo: UpdateInfo?,
    manualUpdateNoResult: Boolean,
    downloadProgress: Float?,
    changelogState: ChangelogUiState,
    showGithubExtraUi: Boolean,
    usePlayInAppUpdates: Boolean,
    onDownloadClick: (UpdateInfo) -> Unit,
    onSkipVersionClick: () -> Unit,
) {
    val sheetScroll = rememberScrollState()
    val pagerCoroutineScope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(sheetScroll),
    ) {
        if (isCheckingUpdate) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
            }
        } else {
            when {
                downloadProgress != null -> {
                    UpdateSheetDownloadProgressBar(downloadProgress = downloadProgress)
                }

                updateInfo != null -> {
                    val availableUpdate = updateInfo
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilePipeMaterialRoundedSymbol(
                            name = "system_update",
                            contentDescription = null,
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (usePlayInAppUpdates && availableUpdate.isPlayStoreUpdateInProgress) {
                            Text(
                                text = stringResource(R.string.settings_update_play_in_progress_body),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        if (showGithubExtraUi) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(Modifier.width(48.dp))
                                Text(
                                    text =
                                        stringResource(
                                            R.string.settings_update_available,
                                            availableUpdate.versionName,
                                        ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                                ToggleLabelHelpDropdown(
                                    tipText = stringResource(R.string.settings_update_sheet_false_positive_tooltip),
                                    contentDescription = stringResource(R.string.rule_toggle_tip_show_help),
                                )
                            }
                        } else {
                            Text(
                                text =
                                    stringResource(
                                        R.string.settings_update_available,
                                        availableUpdate.versionName,
                                    ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        FilePipeButton(
                            onClick = { onDownloadClick(availableUpdate) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                            shape = pillShape,
                        ) {
                            Text(
                                text =
                                    if (usePlayInAppUpdates && availableUpdate.isPlayStoreUpdateInProgress) {
                                        stringResource(R.string.settings_update_resume_play)
                                    } else {
                                        stringResource(
                                            R.string.settings_download_install,
                                            availableUpdate.versionName,
                                        )
                                    },
                                maxLines = 1,
                            )
                        }
                        if (showGithubExtraUi && availableUpdate.remoteApkAssetUpdatedAt.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            FilePipeTextButton(onClick = onSkipVersionClick) {
                                Text(stringResource(R.string.settings_update_skip_version))
                            }
                        }
                    }
                }

                manualUpdateNoResult -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        UpToDatePhoneIcon()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.settings_up_to_date),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (changelogState != ChangelogUiState.Hidden) {
            Spacer(Modifier.height(12.dp))
        }
        when (changelogState) {
            ChangelogUiState.Hidden -> {}

            ChangelogUiState.Loading -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(8.dp),
                        shape = compactControlShape,
                        color = scheme.surfaceContainerLow,
                        contentColor = scheme.onSurface,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator(modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }

            is ChangelogUiState.Ready -> {
                val readyMarkdown = changelogState.text
                val changelogPages = remember(readyMarkdown) { splitChangelogIntoPages(readyMarkdown) }
                val changelogPagerState = rememberPagerState(pageCount = { changelogPages.size })
                val changelogPagerMaxHeight = maxSheetHeight * 0.68f
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    if (changelogPages.size <= 1) {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            shape = compactControlShape,
                            color = scheme.surfaceContainerLow,
                            contentColor = scheme.onSurface,
                        ) {
                            SimpleMarkdown(
                                content = readyMarkdown,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .padding(horizontal = 2.dp, vertical = 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                val canGoBack = changelogPagerState.currentPage > 0
                                val canGoForward = changelogPagerState.currentPage < changelogPages.lastIndex
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .tapSoundClickable(
                                                enabled = canGoBack,
                                                onClick = {
                                                    pagerCoroutineScope.launch {
                                                        changelogPagerState.animateScrollToPage(
                                                            changelogPagerState.currentPage - 1,
                                                        )
                                                    }
                                                },
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FilePipeMaterialRoundedSymbol(
                                        name = "arrow_back",
                                        contentDescription = stringResource(R.string.settings_changelog_previous),
                                        size = 20.dp,
                                        autoMirror = true,
                                        tint =
                                            if (canGoBack) {
                                                scheme.primary
                                            } else {
                                                scheme.onSurface.copy(alpha = 0.38f)
                                            },
                                    )
                                }
                                Text(
                                    text =
                                        stringResource(
                                            R.string.settings_changelog_page_indicator,
                                            changelogPagerState.currentPage + 1,
                                            changelogPages.size,
                                        ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .padding(horizontal = 6.dp),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .tapSoundClickable(
                                                enabled = canGoForward,
                                                onClick = {
                                                    pagerCoroutineScope.launch {
                                                        changelogPagerState.animateScrollToPage(
                                                            changelogPagerState.currentPage + 1,
                                                        )
                                                    }
                                                },
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FilePipeMaterialRoundedSymbol(
                                        name = "arrow_forward",
                                        contentDescription = stringResource(R.string.settings_changelog_next),
                                        size = 20.dp,
                                        autoMirror = true,
                                        tint =
                                            if (canGoForward) {
                                                scheme.primary
                                            } else {
                                                scheme.onSurface.copy(alpha = 0.38f)
                                            },
                                    )
                                }
                            }
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(changelogPagerMaxHeight)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                shape = compactControlShape,
                                color = scheme.surfaceContainerLow,
                                contentColor = scheme.onSurface,
                            ) {
                                HorizontalPager(
                                    state = changelogPagerState,
                                    modifier = Modifier.fillMaxSize(),
                                ) { pageIndex ->
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(16.dp),
                                    ) {
                                        SimpleMarkdown(content = changelogPages[pageIndex])
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is ChangelogUiState.Failed -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        shape = compactControlShape,
                        color = scheme.surfaceContainerLow,
                        contentColor = scheme.onSurface,
                    ) {
                        Text(
                            text = changelogState.message,
                            color = scheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UpToDatePhoneIcon() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = "smartphone",
            contentDescription = null,
            size = 40.dp,
            filled = false,
            tint = primary,
        )
        FilePipeMaterialRoundedSymbol(
            name = "check_circle",
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
            size = 22.dp,
            tint = primary,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSheetDownloadProgressBar(downloadProgress: Float) {
    val scheme = MaterialTheme.colorScheme
    val buttonHeight = 48.dp
    val shape = pillShape
    val label =
        when {
            downloadProgress == -1f -> {
                stringResource(R.string.settings_installing)
            }

            downloadProgress == -2f -> {
                stringResource(R.string.settings_downloading)
            }

            else -> {
                stringResource(
                    R.string.settings_downloading_percent,
                    downloadProgress.toInt().coerceIn(0, 100),
                )
            }
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(buttonHeight)
                .clip(shape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.onSurface.copy(alpha = 0.12f)),
        )
        when {
            downloadProgress >= 0f && downloadProgress <= 100f -> {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((downloadProgress / 100f).coerceIn(0f, 1f))
                        .align(Alignment.CenterStart)
                        .background(scheme.primary.copy(alpha = 0.85f)),
                )
            }

            downloadProgress == -1f || downloadProgress == -2f -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scheme.primary.copy(alpha = 0.22f)),
                )
            }
        }
        if (downloadProgress == -1f || downloadProgress == -2f) {
            LinearWavyProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(4.dp),
                color = scheme.primary.copy(alpha = 0.48f),
                trackColor = Color.Transparent,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SettingsExpandableSection(
    sectionKey: String,
    iconName: String,
    title: String,
    collapsedSectionKeys: Set<String>,
    onCollapsedSectionKeysChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val collapsed = sectionKey in collapsedSectionKeys
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    Column(modifier = modifier) {
        SettingsExpandableSectionHeader(
            iconName = iconName,
            title = title,
            collapsed = collapsed,
            onToggle = {
                onCollapsedSectionKeysChange(
                    if (collapsed) {
                        collapsedSectionKeys - sectionKey
                    } else {
                        collapsedSectionKeys + sectionKey
                    },
                )
            },
        )
        AnimatedVisibility(
            visible = !collapsed,
            enter =
                expandVertically(
                    animationSpec = spatialSpec,
                    expandFrom = Alignment.Top,
                ) + fadeIn(fadeInSpec),
            exit =
                shrinkVertically(
                    animationSpec = spatialSpec,
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(fadeOutSpec),
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun SettingsExpandableSectionHeader(
    iconName: String,
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (collapsed) 0f else 90f,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Float>()),
        label = "settings_section_chevron_rotation",
    )
    val cdExpand = stringResource(R.string.settings_section_expand_cd, title)
    val cdCollapse = stringResource(R.string.settings_section_collapse_cd, title)
    val interactionSource = remember { MutableInteractionSource() }
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Dp>())
    val colorSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Color>())
    val headerCorner by animateDpAsState(
        targetValue = if (collapsed) 28.dp else 4.dp,
        animationSpec = spatialSpec,
        label = "settings_section_header_corner",
    )
    val headerColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        animationSpec = colorSpec,
        label = "settings_section_header_color",
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (collapsed) 12.dp else 0.dp,
        animationSpec = spatialSpec,
        label = "settings_section_header_horizontal_padding",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 4.dp,
        animationSpec = spatialSpec,
        label = "settings_section_header_vertical_padding",
    )
    val iconContainerSize by animateDpAsState(
        targetValue = if (collapsed) 36.dp else 20.dp,
        animationSpec = spatialSpec,
        label = "settings_section_icon_container_size",
    )
    val iconSize by animateDpAsState(
        targetValue = if (collapsed) 21.dp else 19.dp,
        animationSpec = spatialSpec,
        label = "settings_section_icon_size",
    )
    val iconContainerColor by animateColorAsState(
        targetValue =
            if (collapsed) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
            } else {
                Color.Transparent
            },
        animationSpec = colorSpec,
        label = "settings_section_icon_container_color",
    )
    val chevronContainerSize by animateDpAsState(
        targetValue = if (collapsed) 32.dp else 20.dp,
        animationSpec = spatialSpec,
        label = "settings_section_chevron_container_size",
    )
    val chevronSize by animateDpAsState(
        targetValue = if (collapsed) 20.dp else 18.dp,
        animationSpec = spatialSpec,
        label = "settings_section_chevron_size",
    )
    val chevronContainerColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        animationSpec = colorSpec,
        label = "settings_section_chevron_container_color",
    )
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(headerCorner))
                .background(headerColor)
                .semantics { contentDescription = if (collapsed) cdExpand else cdCollapse }
                .tapSoundClickable(
                    onClick = onToggle,
                    indication = null,
                    interactionSource = interactionSource,
                ).padding(
                    horizontal = horizontalPadding.coerceAtLeast(0.dp),
                    vertical = verticalPadding.coerceAtLeast(0.dp),
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(iconContainerSize)
                    .then(
                        if (collapsed) {
                            Modifier.clip(MaterialTheme.shapes.extraExtraLarge)
                        } else {
                            Modifier
                        },
                    ).background(iconContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            FilePipeMaterialRoundedSymbol(
                name = iconName,
                contentDescription = null,
                size = iconSize,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .size(chevronContainerSize)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(chevronContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            FilePipeMaterialRoundedSymbol(
                name = "chevron_right",
                contentDescription = null,
                modifier =
                    Modifier
                        .graphicsLayer { rotationZ = rotation },
                size = chevronSize,
                autoMirror = true,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun SettingsStandaloneNavigationRow(
    iconName: String,
    title: String,
    onClick: () -> Unit,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val sharedBoundsModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(DEV_OPTIONS_SHARED_BOUNDS_KEY),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else {
            Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(sharedBoundsModifier)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .tapSoundClickable(
                    onClick = onClick,
                    indication = null,
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)),
            contentAlignment = Alignment.Center,
        ) {
            FilePipeMaterialRoundedSymbol(
                name = iconName,
                contentDescription = null,
                size = 21.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            FilePipeMaterialRoundedSymbol(
                name = "arrow_outward",
                contentDescription = null,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    iconName: String,
    title: String,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = if (trailingContent != null) Modifier.fillMaxWidth() else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilePipeMaterialRoundedSymbol(
            name = iconName,
            contentDescription = null,
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (trailingContent != null) {
            Spacer(Modifier.weight(1f))
            trailingContent()
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconName: String? = null,
    switchEnabled: Boolean = true,
    onDisabledInteraction: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundClickable {
                    if (!switchEnabled) {
                        onDisabledInteraction?.invoke()
                    } else {
                        onCheckedChange(!checked)
                    }
                }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconName != null) {
            FilePipeMaterialRoundedSymbol(
                name = iconName,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        val switchInteractive = switchEnabled || onDisabledInteraction != null
        FilePipeSwitch(
            checked = checked,
            onCheckedChange = { enabled ->
                when {
                    switchEnabled -> {
                        onCheckedChange(enabled)
                    }

                    onDisabledInteraction != null && enabled -> {
                        onDisabledInteraction.invoke()
                    }

                    else -> { }
                }
            },
            enabled = switchInteractive,
        )
    }
}

@Composable
private fun BackupFolderPickerItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tapSoundCombinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        FilePipeOutlinedButton(
            onClick = {
                onClick()
            },
        ) {
            FilePipeMaterialRoundedSymbol(
                name = "folder_open",
                contentDescription = null,
                size = 18.dp,
            )
        }
    }
}

private val LOG_RETENTION_OPTIONS = listOf(7, 14, 30, 90, -1)

private fun backupDestinationDisplayLabel(
    context: Context,
    uriString: String,
    internalStorageRootDisplayName: String,
): String {
    if (uriString.isBlank()) return ""
    val uri = uriString.toUri()
    if (!DocumentsContract.isTreeUri(uri)) {
        providerDisplayName(context, uri.authority)?.let { return it }
    }
    val documentName = DocumentFile.fromTreeUri(context, uri)?.name
    return documentName?.takeIf { it.isNotBlank() }
        ?: displayPath(uriString, internalStorageRootDisplayName)
}

private fun providerDisplayName(
    context: Context,
    authority: String?,
): String? {
    val providerAuthority = authority?.takeIf { it.isNotBlank() } ?: return null
    val normalizedAuthority = providerAuthority.lowercase()
    return when {
        normalizedAuthority.contains("google.android.apps.docs") -> {
            context.getString(R.string.cloud_provider_google_drive)
        }

        normalizedAuthority.contains("skydrive") || normalizedAuthority.contains("onedrive") -> {
            context.getString(R.string.cloud_provider_onedrive)
        }

        normalizedAuthority.contains("dropbox") -> {
            context.getString(R.string.cloud_provider_dropbox)
        }

        normalizedAuthority.contains("box.android") -> {
            context.getString(R.string.cloud_provider_box)
        }

        else -> {
            providerAuthority
        }
    }
}

@Composable
private fun logRetentionLabel(days: Int): String =
    when (days) {
        7 -> stringResource(R.string.log_retention_7_days)
        14 -> stringResource(R.string.log_retention_14_days)
        30 -> stringResource(R.string.log_retention_30_days)
        90 -> stringResource(R.string.log_retention_90_days)
        else -> stringResource(R.string.log_retention_never)
    }

@Composable
private fun LogRetentionDropdown(
    currentDays: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FilePipeOutlinedButton(onClick = { expanded = true }) {
        Text(logRetentionLabel(currentDays))
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            LOG_RETENTION_OPTIONS.forEach { option ->
                FilePipeDropdownMenuItem(
                    text = { Text(logRetentionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun swipeActionLabel(action: SwipeAction): String =
    when (action) {
        SwipeAction.EDIT -> stringResource(R.string.action_edit)
        SwipeAction.DELETE -> stringResource(R.string.settings_swipe_action_trash)
        SwipeAction.DUPLICATE -> stringResource(R.string.action_duplicate)
        SwipeAction.PREVIEW -> stringResource(R.string.preview_title)
        SwipeAction.VIEW_HISTORY -> stringResource(R.string.settings_swipe_action_history)
    }

@Composable
private fun SwipeExecuteOneActionsEditor(
    startTitle: String,
    endTitle: String,
    startAction: SwipeAction,
    endAction: SwipeAction,
    onStartActionChange: (SwipeAction) -> Unit,
    onEndActionChange: (SwipeAction) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwipeExecuteDirectionColumn(
                title = startTitle,
                direction = SwipeDirectionCue.RIGHT,
                action = startAction,
                availableActions = SwipeAction.entries.filter { it != endAction },
                onActionChange = onStartActionChange,
                modifier = Modifier.weight(1f),
            )
            SwipeExecuteDirectionColumn(
                title = endTitle,
                direction = SwipeDirectionCue.LEFT,
                action = endAction,
                availableActions = SwipeAction.entries.filter { it != startAction },
                onActionChange = onEndActionChange,
                modifier = Modifier.weight(1f),
            )
        }
        SwipePanelDivider()
        SwipeHintText(text = stringResource(R.string.settings_swipe_execute_hint))
    }
}

@Composable
private fun SwipeExecuteDirectionColumn(
    title: String,
    direction: SwipeDirectionCue,
    action: SwipeAction,
    availableActions: List<SwipeAction>,
    onActionChange: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SwipeExecuteDirectionHeader(
            title = title,
            direction = direction,
        )
        SwipeExecuteActionPicker(
            action = action,
            availableActions = availableActions,
            onActionChange = onActionChange,
        )
    }
}

@Composable
private fun SwipeExecuteDirectionHeader(
    title: String,
    direction: SwipeDirectionCue,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            if (direction == SwipeDirectionCue.LEFT) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
    ) {
        if (direction == SwipeDirectionCue.RIGHT) {
            SwipeDirectionIcon(direction = direction)
            Spacer(Modifier.size(7.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (direction == SwipeDirectionCue.LEFT) {
            Spacer(Modifier.size(7.dp))
            SwipeDirectionIcon(direction = direction)
        }
    }
}

@Composable
private fun SwipeDirectionIcon(direction: SwipeDirectionCue) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = direction.iconName,
            size = 15.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SwipeExecuteActionPicker(
    action: SwipeAction,
    availableActions: List<SwipeAction>,
    onActionChange: (SwipeAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    val actionAccent = action.settingsSwipeAccent()
    Box {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(shape)
                    .tapSoundClickable(role = Role.Button) { expanded = true },
            shape = shape,
            color = action.settingsSwipeTileColor(),
            border = BorderStroke(1.dp, actionAccent.copy(alpha = 0.55f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SwipeActionIcon(action = action)
                Text(
                    text = swipeActionLabel(action),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FilePipeMaterialRoundedSymbol(
                    name = "expand_more",
                    size = 17.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            availableActions.forEach { action ->
                FilePipeDropdownMenuItem(
                    text = { Text(swipeActionLabel(action)) },
                    leadingIcon = { SwipeActionIcon(action = action) },
                    onClick = {
                        onActionChange(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwipeActionIcon(action: SwipeAction) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(action.settingsSwipeIconContainerColor()),
        contentAlignment = Alignment.Center,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = action.materialSymbolName(),
            size = 15.dp,
            tint = action.settingsSwipeIconColor(),
            weight = FontWeight.Medium,
        )
    }
}

private fun SwipeAction.settingsSwipeTileColor(): Color = settingsSwipeAccent().copy(alpha = 0.14f)

private fun SwipeAction.settingsSwipeAccent(): Color = swipeActionAccent()

private fun SwipeAction.settingsSwipeIconContainerColor(): Color = settingsSwipeAccent()

private fun SwipeAction.settingsSwipeIconColor(): Color = Color.White

@Composable
private fun SwipePanelDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)),
    )
}

@Composable
private fun SwipeHintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SettingsInfoDropdown(
    tipText: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilePipeIconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(32.dp),
        ) {
            FilePipeMaterialRoundedSymbol(
                name = "info",
                size = 20.dp,
                tint = iconTint,
                weight = FontWeight.Medium,
                filled = false,
                modifier = Modifier.semantics { this.contentDescription = contentDescription },
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(max = 260.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 236.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
