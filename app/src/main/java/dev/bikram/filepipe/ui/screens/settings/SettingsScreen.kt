package dev.bikram.filepipe.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.usecase.BackupImportPickAction
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.ui.theme.semanticSwipeBackground
import dev.bikram.filepipe.ui.theme.semanticSwipeIconTint
import dev.bikram.filepipe.ui.components.AboutAuthorPhoto
import dev.bikram.filepipe.ui.components.AppIconImage
import dev.bikram.filepipe.ui.components.containers.GroupPosition
import dev.bikram.filepipe.ui.components.containers.GroupedListColumn
import dev.bikram.filepipe.ui.components.containers.GroupedListItem
import dev.bikram.filepipe.ui.components.displayPath
import dev.bikram.filepipe.data.storage.safTreeUriToPath
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import dev.bikram.filepipe.ui.modifiers.applyToScrollableList
import dev.bikram.filepipe.ui.theme.LocalProgressiveBlurStyle
import dev.bikram.filepipe.ui.theme.LocalUseGradientBackground
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import dev.bikram.filepipe.ui.theme.gradientOverlayTopAppBarColors
import dev.bikram.filepipe.update.UpdateInfo
import dev.bikram.filepipe.ui.components.text.SimpleMarkdown

private val themePickerOrder = listOf(
    AppThemeMode.SYSTEM,
    AppThemeMode.LIGHT,
    AppThemeMode.DARK,
    AppThemeMode.BLACK
)

/**
 * Indices of the first unconditional [LazyColumn] items (Appearance, Folder access, Touch & sound, …)
 * before the optional Updates block. Used to scroll from Help quick actions.
 */
private const val SETTINGS_LIST_INDEX_FOLDER_ACCESS = 1
private const val SETTINGS_LIST_INDEX_TOUCH_SOUND_NOTIFICATIONS = 2

@Composable
private fun rememberSectionHighlightPulseAlpha(active: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "settingsSectionHighlight")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    return if (active) pulse else 1f
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenIntro: () -> Unit = {},
    onOpenFaqStorageSection: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val playTap = rememberPlayTapSound()
    val preferences by viewModel.preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferences.DEFAULT)
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val scrollBlurModifier = LocalProgressiveBlurStyle.current?.applyToScrollableList() ?: Modifier
    val context = LocalContext.current

    fun computeNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
    var notificationsGranted by remember { mutableStateOf(computeNotificationsEnabled()) }
    var allFilesAccessGranted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    var pendingFolderAccessSwitch by remember { mutableStateOf<FolderAccessMode?>(null) }
    var pendingEnableUpdateNotificationsAfterPermission by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val settingsLazyListState = rememberLazyListState()
    val bringIntoViewTarget by viewModel.bringIntoViewSection.collectAsStateWithLifecycle()
    LaunchedEffect(bringIntoViewTarget) {
        when (bringIntoViewTarget) {
            SettingsBringIntoViewSection.None -> return@LaunchedEffect
            SettingsBringIntoViewSection.FolderAccess,
            SettingsBringIntoViewSection.Notifications -> {
                val targetIndex = when (bringIntoViewTarget) {
                    SettingsBringIntoViewSection.FolderAccess -> SETTINGS_LIST_INDEX_FOLDER_ACCESS
                    SettingsBringIntoViewSection.Notifications ->
                        SETTINGS_LIST_INDEX_TOUCH_SOUND_NOTIFICATIONS
                    else -> return@LaunchedEffect
                }
                delay(220)
                if (settingsLazyListState.layoutInfo.totalItemsCount > targetIndex) {
                    settingsLazyListState.animateScrollToItem(targetIndex)
                }
                viewModel.clearBringIntoViewSectionRequest()
            }
        }
    }
    val folderAccessSectionHighlight by viewModel.folderAccessSectionHighlight.collectAsStateWithLifecycle()
    LaunchedEffect(folderAccessSectionHighlight) {
        if (!folderAccessSectionHighlight) return@LaunchedEffect
        delay(4500)
        viewModel.clearFolderAccessSectionHighlight()
    }
    val notificationsSectionHighlight by viewModel.notificationsSectionHighlight.collectAsStateWithLifecycle()
    LaunchedEffect(notificationsSectionHighlight) {
        if (!notificationsSectionHighlight) return@LaunchedEffect
        delay(4500)
        viewModel.clearNotificationsSectionHighlight()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (pendingEnableUpdateNotificationsAfterPermission) {
            pendingEnableUpdateNotificationsAfterPermission = false
            if (granted) {
                coroutineScope.launch {
                    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.settings_notify_updates_enable_notifications)
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
        val useAppNotificationSettingsFallback = hostActivity != null &&
            postNotificationPermissionLaunchAttempted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                hostActivity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        if (useAppNotificationSettingsFallback) {
            viewModel.openAppNotificationSettings()
        } else {
            postNotificationPermissionLaunchAttempted = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = computeNotificationsEnabled()
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
    val updateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val maxUpdateSheetHeight = (configuration.screenHeightDp * 0.85f).dp
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    LaunchedEffect(userMessage) {
        val message = userMessage ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        } finally {
            viewModel.clearUserMessage()
        }
    }

    DisposableEffect(lifecycleOwner, snackbarHostState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = safTreeUriToPath(uri) ?: uri.toString()
            viewModel.setExportFolderUri(path)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
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
    var showBackupImportRestoreHelp by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val action = pendingBackupPickAction
        pendingBackupPickAction = null
        if (uri != null && action != null) {
            viewModel.importFromUri(uri, action)
        }
    }

    val playInAppUpdateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    LaunchedEffect(showUpdateSheet) {
        if (showUpdateSheet) {
            updateSheetState.expand()
        }
    }

    LaunchedEffect(openUpdateSheetFromNotification) {
        if (!openUpdateSheetFromNotification || !BuildConfig.SHOW_UPDATES) return@LaunchedEffect
        showUpdateSheet = true
        viewModel.loadChangelogForUpdateSheet()
        viewModel.beginManualUpdateCheckFromSheet()
        viewModel.consumeOpenUpdateSheetFromNotification()
    }

    if (showUpdateSheet && BuildConfig.SHOW_UPDATES) {
        val updateSheetContainerColors = elevatedCardColors()
        ModalBottomSheet(
            onDismissRequest = {
                showUpdateSheet = false
                viewModel.dismissUpdateSheet()
            },
            sheetState = updateSheetState,
            containerColor = updateSheetContainerColors.containerColor,
            contentColor = updateSheetContainerColors.contentColor
        ) {
            UpdateCheckBottomSheetContent(
                maxSheetHeight = maxUpdateSheetHeight,
                isCheckingUpdate = isCheckingUpdate,
                updateInfo = updateInfo,
                manualUpdateNoResult = manualUpdateNoResult,
                downloadProgress = downloadProgress,
                changelogState = updateSheetChangelog,
                showGithubExtraUi = BuildConfig.FLAVOR == "github",
                onDownloadClick = { info ->
                    playTap()
                    if (BuildConfig.USE_PLAY_IN_APP_UPDATES && info.downloadUrl.isBlank()) {
                        val hostActivity = context as? ComponentActivity
                        viewModel.tryStartPlayInAppUpdate(hostActivity, playInAppUpdateLauncher)
                    } else {
                        viewModel.downloadAndInstall(info)
                    }
                },
                onSkipVersionClick = {
                    playTap()
                    updateInfo?.let { info ->
                        viewModel.skipAcknowledgedGithubRelease(info)
                        showUpdateSheet = false
                        viewModel.dismissUpdateSheet()
                    }
                }
            )
        }
    }

    fun applyFolderAccessMode(mode: FolderAccessMode) {
        playTap()
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
                TextButton(onClick = {
                    playTap()
                    val confirmedTarget = targetMode
                    pendingFolderAccessSwitch = null
                    coroutineScope.launch {
                        val affectedRuleCount = viewModel.countRulesUsingFilesystemFolderPaths()
                        viewModel.setFolderAccessModeNow(confirmedTarget)
                        val message = when {
                            affectedRuleCount <= 0 ->
                                context.getString(R.string.settings_folder_access_switched_selective_zero_rules)
                            affectedRuleCount == 1 ->
                                context.getString(
                                    R.string.settings_folder_access_switched_selective_snackbar_one,
                                    affectedRuleCount
                                )
                            else ->
                                context.getString(
                                    R.string.settings_folder_access_switched_selective_snackbar_other,
                                    affectedRuleCount
                                )
                        }
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Long
                        )
                    }
                }) {
                    Text(stringResource(R.string.settings_folder_access_switch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingFolderAccessSwitch = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBackupImportRestoreHelp) {
        AlertDialog(
            onDismissRequest = { showBackupImportRestoreHelp = false },
            title = { Text(stringResource(R.string.settings_backup_import_restore_help_title)) },
            text = { Text(stringResource(R.string.settings_backup_import_restore_help_body)) },
            confirmButton = {
                TextButton(onClick = {
                    playTap()
                    showBackupImportRestoreHelp = false
                }) {
                    Text(stringResource(R.string.settings_backup_help_ok))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (LocalUseGradientBackground.current) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
                colors = gradientOverlayTopAppBarColors()
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding())
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = settingsLazyListState,
            modifier = Modifier
                .fillMaxSize()
                .then(scrollBlurModifier),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Appearance ───────────────────────────────────────────────────
            item {
                SettingsSectionHeader(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.settings_appearance_section)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    themePickerOrder.forEachIndexed { index, mode ->
                        ToggleButton(
                            checked = preferences.themeMode == mode,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    playTap()
                                    viewModel.setThemeMode(mode)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                themePickerOrder.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                        ) {
                            Text(
                                text = themeModeLabel(mode),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ThemeColorSection(
                    colorSource = preferences.colorSource,
                    savedCustomSeedHexes = preferences.savedCustomSeedHexes,
                    activeCustomSeedHex = preferences.activeCustomSeedHex,
                    themePaletteStyle = preferences.themePaletteStyle,
                    onColorSource = { source ->
                        playTap()
                        viewModel.setColorSource(source)
                    },
                    onPaletteStyle = { style ->
                        playTap()
                        viewModel.setThemePaletteStyle(style)
                    },
                    onAddCustomSeedHex = { hex ->
                        playTap()
                        viewModel.addCustomSeedHex(hex)
                    },
                    onSelectCustomSeedHex = { hex ->
                        playTap()
                        viewModel.selectCustomSeedHex(hex)
                    },
                    onRemoveCustomSeedHex = { hex ->
                        playTap()
                        viewModel.removeCustomSeedHex(hex)
                    }
                )
                if (preferences.colorSource == AppColorSource.MATERIAL_YOU && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_material_you_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                GroupedListColumn {
                    GroupedListItem(position = GroupPosition.FIRST) {
                        SettingsToggleItem(
                            title = stringResource(R.string.settings_gradient_background),
                            subtitle = stringResource(R.string.settings_gradient_background_desc),
                            checked = preferences.useGradientBackground,
                            onCheckedChange = { enabled ->
                                playTap()
                                viewModel.setUseGradientBackground(enabled)
                            }
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        SettingsToggleItem(
                            title = stringResource(R.string.settings_fixed_card_colors),
                            subtitle = stringResource(R.string.settings_fixed_card_colors_desc),
                            checked = preferences.useFixedCardColors,
                            onCheckedChange = { enabled ->
                                playTap()
                                viewModel.setUseFixedCardColors(enabled)
                            }
                        )
                    }
                    GroupedListItem(position = GroupPosition.LAST) {
                        SettingsToggleItem(
                            icon = Icons.Default.BlurOn,
                            title = stringResource(R.string.settings_progressive_blur),
                            subtitle = stringResource(R.string.settings_progressive_blur_desc),
                            checked = preferences.progressiveBlurEnabled,
                            onCheckedChange = { enabled ->
                                playTap()
                                viewModel.setProgressiveBlurEnabled(enabled)
                            }
                        )
                    }
                }
            }
            
            // ── Folder access ─────────────────────────────────────────────────
            item {
                val folderAccessHighlightShape = RoundedCornerShape(16.dp)
                val folderHighlightPulse = rememberSectionHighlightPulseAlpha(folderAccessSectionHighlight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (folderAccessSectionHighlight) {
                                Modifier
                                    .border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(
                                            alpha = folderHighlightPulse
                                        ),
                                        shape = folderAccessHighlightShape
                                    )
                                    .padding(10.dp)
                            } else {
                                Modifier
                            }
                        )
                ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                SettingsSectionHeader(
                    icon = Icons.Default.FolderOpen,
                    title = stringResource(R.string.settings_folder_access_section)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_permissions_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                GroupedListColumn {
                    GroupedListItem(position = GroupPosition.FIRST) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_folder_access_saf_only),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = preferences.folderAccessMode == FolderAccessMode.SAF_ONLY,
                                    onClick = { applyFolderAccessMode(FolderAccessMode.SAF_ONLY) }
                                )
                            },
                            modifier = Modifier.clickable {
                                applyFolderAccessMode(FolderAccessMode.SAF_ONLY)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    GroupedListItem(position = GroupPosition.LAST) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_folder_access_all_files),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = preferences.folderAccessMode == FolderAccessMode.ALL_FILES_PREFERRED,
                                    onClick = { applyFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED) }
                                )
                            },
                            modifier = Modifier.clickable {
                                applyFolderAccessMode(FolderAccessMode.ALL_FILES_PREFERRED)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val selectiveLike =
                    preferences.folderAccessMode == FolderAccessMode.SAF_ONLY ||
                        preferences.folderAccessMode == FolderAccessMode.DEFERRED
                val allFilesModeSelected =
                    preferences.folderAccessMode == FolderAccessMode.ALL_FILES_PREFERRED
                val allFilesStatusLine = when {
                    selectiveLike && allFilesAccessGranted ->
                        stringResource(R.string.settings_folder_access_all_files_status_granted_unused)
                    selectiveLike && !allFilesAccessGranted ->
                        stringResource(R.string.settings_folder_access_all_files_status_not_granted_idle)
                    allFilesModeSelected && allFilesAccessGranted ->
                        stringResource(R.string.settings_folder_access_all_files_status_granted_used)
                    else ->
                        stringResource(R.string.settings_folder_access_all_files_status_not_granted_required)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusStyle = MaterialTheme.typography.bodySmall
                    val statusColor = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = allFilesStatusLine,
                        style = statusStyle,
                        color = statusColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.onboarding_permissions_learn_more),
                        style = statusStyle,
                        color = statusColor,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable {
                                playTap()
                                onOpenFaqStorageSection()
                            }
                    )
                }
                val showAllFilesActionButton = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    when {
                        selectiveLike && !allFilesAccessGranted -> false
                        selectiveLike && allFilesAccessGranted -> true
                        allFilesModeSelected && !allFilesAccessGranted -> true
                        allFilesModeSelected && allFilesAccessGranted -> false
                        else -> false
                    }
                if (showAllFilesActionButton) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            playTap()
                            val manageIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(manageIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (selectiveLike) {
                                    R.string.settings_folder_access_open_manage
                                } else {
                                    R.string.settings_folder_access_grant_all_files
                                }
                            )
                        )
                    }
                }
                }
                }
            }

            // ── Touch & Sound ────────────────────────────────────────────────
            item {
                val notificationsHighlightShape = RoundedCornerShape(16.dp)
                val notificationsHighlightPulse =
                    rememberSectionHighlightPulseAlpha(notificationsSectionHighlight)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (notificationsSectionHighlight) {
                                Modifier
                                    .border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(
                                            alpha = notificationsHighlightPulse
                                        ),
                                        shape = notificationsHighlightShape
                                    )
                                    .padding(10.dp)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsSectionHeader(
                            icon = Icons.Default.Vibration,
                            title = stringResource(R.string.settings_touch_sound_section)
                        )
                        Spacer(Modifier.height(8.dp))
                        GroupedListColumn {
                            GroupedListItem(position = GroupPosition.FIRST) {
                                SettingsToggleItem(
                                    icon = Icons.Default.Vibration,
                                    title = stringResource(R.string.settings_haptic_feedback),
                                    subtitle = stringResource(R.string.settings_haptic_feedback_desc),
                                    checked = preferences.hapticFeedbackEnabled,
                                    onCheckedChange = { enabled ->
                                        playTap()
                                        viewModel.setHapticFeedbackEnabled(enabled)
                                    }
                                )
                            }
                            GroupedListItem(position = GroupPosition.LAST) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.settings_notifications),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            stringResource(R.string.settings_notifications_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = notificationsGranted,
                                            onCheckedChange = { wantEnabled ->
                                                playTap()
                                                when {
                                                    wantEnabled &&
                                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                                                        pendingEnableUpdateNotificationsAfterPermission = false
                                                        requestPostNotificationPermissionOrOpenAppSettings()
                                                    }
                                                    wantEnabled &&
                                                        !NotificationManagerCompat.from(context)
                                                            .areNotificationsEnabled() ->
                                                        viewModel.openAppNotificationSettings()
                                                    !wantEnabled ->
                                                        viewModel.openAppNotificationSettings()
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        playTap()
                                        if (!notificationsGranted) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                pendingEnableUpdateNotificationsAfterPermission = false
                                                requestPostNotificationPermissionOrOpenAppSettings()
                                            } else if (!NotificationManagerCompat.from(context)
                                                    .areNotificationsEnabled()
                                            ) {
                                                viewModel.openAppNotificationSettings()
                                            }
                                        } else {
                                            viewModel.openAppNotificationSettings()
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            // ── Swipe Actions ─────────────────────────────────────────────────
            item {
                SettingsSectionHeader(
                    icon = Icons.Default.SwipeLeft,
                    title = "Swipe actions"
                )
                Spacer(Modifier.height(8.dp))
                GroupedListColumn {
                    GroupedListItem(position = GroupPosition.FIRST) {
                        ListItem(
                            headlineContent = { Text("Swipe right \u2192", style = MaterialTheme.typography.bodyLarge) },
                            trailingContent = {
                                SwipeActionDropdown(
                                    current = preferences.swipeStartToEnd,
                                    excluded = preferences.swipeEndToStart,
                                    onSelect = { viewModel.setSwipeStartToEnd(it) }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    GroupedListItem(position = GroupPosition.LAST) {
                        ListItem(
                            headlineContent = { Text("\u2190 Swipe left", style = MaterialTheme.typography.bodyLarge) },
                            trailingContent = {
                                SwipeActionDropdown(
                                    current = preferences.swipeEndToStart,
                                    excluded = preferences.swipeStartToEnd,
                                    onSelect = { viewModel.setSwipeEndToStart(it) }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SwipeActionPreviewCard(
                    swipeStartToEnd = preferences.swipeStartToEnd,
                    swipeEndToStart = preferences.swipeEndToStart
                )
            }

            // ── History ───────────────────────────────────────────────────────
            item {
                SettingsSectionHeader(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.settings_history_section)
                )
                Spacer(Modifier.height(8.dp))
                GroupedListColumn {
                    GroupedListItem(position = GroupPosition.ONLY) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.settings_log_retention),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_log_retention_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                LogRetentionDropdown(
                                    currentDays = preferences.logRetentionDays,
                                    onSelect = { viewModel.setLogRetentionDays(it) }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            // ── Import/Export ────────────────────────────────────────────────
            item {
                SettingsSectionHeader(
                    icon = Icons.Default.Save,
                    title = stringResource(R.string.settings_backup_section)
                )
                Spacer(Modifier.height(8.dp))

                val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
                val folderLabel = preferences.exportFolderUri
                    .takeIf { it.isNotBlank() }
                    ?.let { displayPath(it, internalStorageDisplayName) }
                    ?: stringResource(R.string.settings_choose_export_folder)

                val exportFolderReady = preferences.exportFolderUri.isNotBlank()
                val autoExportSwitchEnabled = exportFolderReady || preferences.autoExportOnRuleChange
                val scheduledExportSwitchEnabled = exportFolderReady || preferences.scheduledExportEnabled

                GroupedListColumn {
                    GroupedListItem(position = GroupPosition.FIRST) {
                        ListItem(
                            headlineContent = { Text(folderLabel, style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_export_folder_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                OutlinedButton(onClick = {
                                    playTap()
                                    folderLauncher.launch(null)
                                }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        SettingsToggleItem(
                            title = stringResource(R.string.settings_auto_export_on_change),
                            subtitle = stringResource(R.string.settings_auto_export_on_change_hint),
                            checked = preferences.autoExportOnRuleChange,
                            switchEnabled = autoExportSwitchEnabled,
                            onDisabledInteraction = {
                                playTap()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.settings_export_select_folder_first),
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onCheckedChange = { enabled ->
                                playTap()
                                viewModel.setAutoExportOnChange(enabled)
                            }
                        )
                    }
                    GroupedListItem(position = GroupPosition.MIDDLE) {
                        SettingsToggleItem(
                            title = stringResource(R.string.settings_scheduled_export),
                            subtitle = stringResource(R.string.settings_scheduled_export_hint),
                            checked = preferences.scheduledExportEnabled,
                            switchEnabled = scheduledExportSwitchEnabled,
                            onDisabledInteraction = {
                                playTap()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.settings_export_select_folder_first),
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onCheckedChange = { enabled ->
                                playTap()
                                viewModel.setScheduledExportEnabled(enabled)
                            }
                        )
                    }
                    GroupedListItem(position = GroupPosition.LAST) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        playTap()
                                        pendingBackupPickAction = BackupImportPickAction.ImportMerge
                                        importLauncher.launch("application/json")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.settings_import_rules))
                                }
                                OutlinedButton(
                                    onClick = { playTap(); viewModel.requestManualExportPicker() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.settings_export_now))
                                }
                            }
                            val restoreOutline = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            val restoreLabelColor = MaterialTheme.colorScheme.error
                            val restoreButtonShape = ButtonDefaults.outlinedShape
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(restoreButtonShape)
                                    .border(BorderStroke(1.dp, restoreOutline), restoreButtonShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable {
                                            playTap()
                                            pendingBackupPickAction = BackupImportPickAction.RestoreFull
                                            importLauncher.launch("application/json")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_restore_backup),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = restoreLabelColor
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .width(40.dp)
                                        .clickable {
                                            playTap()
                                            showBackupImportRestoreHelp = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = stringResource(R.string.settings_backup_help_icon_cd),
                                        modifier = Modifier.size(20.dp),
                                        tint = restoreLabelColor.copy(alpha = 0.75f)
                                    )
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
                        SettingsSectionHeader(
                            icon = Icons.Default.SystemUpdate,
                            title = stringResource(R.string.settings_updates_section)
                        )
                        Spacer(Modifier.height(8.dp))
                        GroupedListColumn {
                            GroupedListItem(position = GroupPosition.FIRST) {
                                UpdateCheckScheduleDropdown(
                                    selected = preferences.updateCheckSchedule,
                                    onSelect = { schedule ->
                                        playTap()
                                        viewModel.setUpdateCheckSchedule(schedule)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            if (BuildConfig.FLAVOR == "github") {
                                GroupedListItem(position = GroupPosition.MIDDLE) {
                                    SettingsToggleItem(
                                        title = stringResource(R.string.settings_save_update_apk_to_downloads),
                                        checked = preferences.saveUpdateApkToDownloads,
                                        onCheckedChange = { enabled ->
                                            playTap()
                                            viewModel.setSaveUpdateApkToDownloads(enabled)
                                        }
                                    )
                                }
                            }
                            GroupedListItem(position = GroupPosition.MIDDLE) {
                                SettingsToggleItem(
                                    title = stringResource(R.string.settings_notify_new_updates),
                                    checked = preferences.notifyOnNewUpdates,
                                    onCheckedChange = { enabled ->
                                        playTap()
                                        when {
                                            !enabled -> {
                                                pendingEnableUpdateNotificationsAfterPermission = false
                                                viewModel.setNotifyOnNewUpdates(false)
                                            }
                                            preferences.updateCheckSchedule == UpdateCheckSchedule.NEVER -> {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(
                                                            R.string.settings_notify_updates_need_auto_check
                                                        )
                                                    )
                                                }
                                            }
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) != PackageManager.PERMISSION_GRANTED -> {
                                                pendingEnableUpdateNotificationsAfterPermission = true
                                                requestPostNotificationPermissionOrOpenAppSettings()
                                            }
                                            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(
                                                            R.string.settings_notify_updates_enable_notifications
                                                        )
                                                    )
                                                }
                                                viewModel.openAppNotificationSettings()
                                            }
                                            else -> viewModel.setNotifyOnNewUpdates(true)
                                        }
                                    }
                                )
                            }
                            GroupedListItem(position = GroupPosition.LAST) {
                                ListItem(
                                    headlineContent = {
                                        val available = updateInfo
                                        Text(
                                            text = if (available != null) {
                                                stringResource(
                                                    R.string.settings_update_available_button,
                                                    available.versionName
                                                )
                                            } else {
                                                stringResource(R.string.settings_check_for_updates)
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Default.NewReleases,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        playTap()
                                        viewModel.beginManualUpdateCheckFromSheet()
                                        viewModel.loadChangelogForUpdateSheet()
                                        showUpdateSheet = true
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            // ── About ─────────────────────────────────────────────────────────
            item {
                val aboutContext = LocalContext.current
                val githubRepoForSourceLink = BuildConfig.GITHUB_REPO.trim()
                    .ifEmpty { BuildConfig.CHANGELOG_GITHUB_REPO.trim() }
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    SettingsSectionHeader(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.settings_about_section)
                    )
                    Spacer(Modifier.height(8.dp))
                    GroupedListColumn {
                        GroupedListItem(position = GroupPosition.ONLY) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.app_tagline),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconImage(
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(RoundedCornerShape(percent = 25))
                                            .clickable {
                                                playTap()
                                                onOpenIntro()
                                            }
                                    )
                                    Spacer(Modifier.width(20.dp))
                                    AboutAuthorPhoto(
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                playTap()
                                                val profileUrl =
                                                    aboutContext.getString(R.string.about_author_github_profile_url)
                                                runCatching {
                                                    aboutContext.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl))
                                                    )
                                                }
                                            }
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                                Text(
                                    text = stringResource(R.string.settings_byline),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                if (githubRepoForSourceLink.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    OutlinedButton(
                                        onClick = {
                                            playTap()
                                            val url = "https://github.com/$githubRepoForSourceLink"
                                            runCatching {
                                                aboutContext.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_github_mark),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.settings_view_on_github))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCheckScheduleDropdown(
    selected: UpdateCheckSchedule,
    onSelect: (UpdateCheckSchedule) -> Unit,
    modifier: Modifier = Modifier
) {
    val playTap = rememberPlayTapSound()
    var expanded by remember { mutableStateOf(false) }
    val options = remember { UpdateCheckSchedule.entries }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_update_check_frequency),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = { playTap(); expanded = true }) {
            Text(updateScheduleSummaryBeforeColon(selected))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(updateScheduleLabel(option)) },
                        onClick = { playTap(); onSelect(option); expanded = false }
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
private fun updateScheduleSummaryBeforeColon(schedule: UpdateCheckSchedule): String =
    summaryLabelBeforeColon(updateScheduleLabel(schedule))

@Composable
private fun updateScheduleLabel(schedule: UpdateCheckSchedule): String = when (schedule) {
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
    onDownloadClick: (UpdateInfo) -> Unit,
    onSkipVersionClick: () -> Unit
) {
    val sheetScroll = rememberScrollState()
    val pagerCoroutineScope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(sheetScroll)
    ) {
        if (isCheckingUpdate) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        if (showGithubExtraUi) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_update_available,
                                        availableUpdate.versionName
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Above
                                    ),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(
                                                text = stringResource(
                                                    R.string.settings_update_sheet_false_positive_tooltip
                                                ),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(onClick = { }) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = stringResource(
                                                R.string.settings_update_sheet_false_positive_tooltip
                                            )
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.settings_update_available,
                                    availableUpdate.versionName
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onDownloadClick(availableUpdate) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.settings_download_install,
                                    availableUpdate.versionName
                                ),
                                maxLines = 1
                            )
                        }
                        if (showGithubExtraUi && availableUpdate.remoteApkAssetUpdatedAt.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onSkipVersionClick) {
                                Text(stringResource(R.string.settings_update_skip_version))
                            }
                        }
                    }
                }
                manualUpdateNoResult -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UpToDatePhoneIcon()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.settings_up_to_date),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
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
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerLow,
                        contentColor = scheme.onSurface
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
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
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp
                ) {
                    if (changelogPages.size <= 1) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = scheme.surfaceContainerLow,
                            contentColor = scheme.onSurface
                        ) {
                            SimpleMarkdown(
                                content = readyMarkdown,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .padding(horizontal = 2.dp, vertical = 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val canGoBack = changelogPagerState.currentPage > 0
                                val canGoForward = changelogPagerState.currentPage < changelogPages.lastIndex
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .clickable(
                                            enabled = canGoBack,
                                            onClick = {
                                                pagerCoroutineScope.launch {
                                                    changelogPagerState.animateScrollToPage(
                                                        changelogPagerState.currentPage - 1
                                                    )
                                                }
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.settings_changelog_previous),
                                        modifier = Modifier.size(20.dp),
                                        tint = if (canGoBack) {
                                            scheme.primary
                                        } else {
                                            scheme.onSurface.copy(alpha = 0.38f)
                                        }
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        R.string.settings_changelog_page_indicator,
                                        changelogPagerState.currentPage + 1,
                                        changelogPages.size
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 6.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .clickable(
                                            enabled = canGoForward,
                                            onClick = {
                                                pagerCoroutineScope.launch {
                                                    changelogPagerState.animateScrollToPage(
                                                        changelogPagerState.currentPage + 1
                                                    )
                                                }
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = stringResource(R.string.settings_changelog_next),
                                        modifier = Modifier.size(20.dp),
                                        tint = if (canGoForward) {
                                            scheme.primary
                                        } else {
                                            scheme.onSurface.copy(alpha = 0.38f)
                                        }
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(changelogPagerMaxHeight)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = scheme.surfaceContainerLow,
                                contentColor = scheme.onSurface
                            ) {
                                HorizontalPager(
                                    state = changelogPagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { pageIndex ->
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(16.dp)
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
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerLow,
                        contentColor = scheme.onSurface
                    ) {
                        Text(
                            text = changelogState.message,
                            color = scheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
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
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Smartphone,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = primary
        )
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .offset(x = 2.dp, y = 2.dp),
            tint = primary
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSheetDownloadProgressBar(downloadProgress: Float) {
    val scheme = MaterialTheme.colorScheme
    val buttonHeight = 48.dp
    val shape = RoundedCornerShape(24.dp)
    val label = when {
        downloadProgress == -1f -> stringResource(R.string.settings_installing)
        downloadProgress == -2f -> stringResource(R.string.settings_downloading)
        else -> stringResource(
            R.string.settings_downloading_percent,
            downloadProgress.toInt().coerceIn(0, 100)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .clip(shape)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.onSurface.copy(alpha = 0.12f))
        )
        when {
            downloadProgress >= 0f && downloadProgress <= 100f -> {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((downloadProgress / 100f).coerceIn(0f, 1f))
                        .align(Alignment.CenterStart)
                        .background(scheme.primary.copy(alpha = 0.85f))
                )
            }
            downloadProgress == -1f || downloadProgress == -2f -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scheme.primary.copy(alpha = 0.22f))
                )
            }
        }
        if (downloadProgress == -1f || downloadProgress == -2f) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(4.dp),
                color = scheme.primary.copy(alpha = 0.48f),
                trackColor = Color.Transparent
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface.copy(alpha = 0.78f),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isLast: Boolean = false,
    switchEnabled: Boolean = true,
    onDisabledInteraction: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (subtitle != null) {
            {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        leadingContent = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        trailingContent = {
            val switchInteractive = switchEnabled || onDisabledInteraction != null
            Switch(
                checked = checked,
                onCheckedChange = { enabled ->
                    when {
                        switchEnabled -> onCheckedChange(enabled)
                        onDisabledInteraction != null && enabled -> onDisabledInteraction.invoke()
                        else -> { }
                    }
                },
                enabled = switchInteractive
            )
        },
        modifier = Modifier.clickable {
            if (!switchEnabled) {
                onDisabledInteraction?.invoke()
            } else {
                onCheckedChange(!checked)
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

private val LOG_RETENTION_OPTIONS = listOf(7, 14, 30, 90, -1)

@Composable
private fun logRetentionLabel(days: Int): String = when (days) {
    7 -> stringResource(R.string.log_retention_7_days)
    14 -> stringResource(R.string.log_retention_14_days)
    30 -> stringResource(R.string.log_retention_30_days)
    90 -> stringResource(R.string.log_retention_90_days)
    else -> stringResource(R.string.log_retention_never)
}

@Composable
private fun LogRetentionDropdown(currentDays: Int, onSelect: (Int) -> Unit) {
    val playTap = rememberPlayTapSound()
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { playTap(); expanded = true }) {
        Text(logRetentionLabel(currentDays))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LOG_RETENTION_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(logRetentionLabel(option)) },
                    onClick = { playTap(); onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun swipeActionLabel(action: SwipeAction): String = when (action) {
    SwipeAction.EDIT -> stringResource(R.string.action_edit)
    SwipeAction.DELETE -> stringResource(R.string.delete)
    SwipeAction.DUPLICATE -> stringResource(R.string.action_duplicate)
    SwipeAction.PREVIEW -> stringResource(R.string.preview_title)
    SwipeAction.VIEW_HISTORY -> stringResource(R.string.view_history)
}

@Composable
private fun SwipeActionDropdown(
    current: SwipeAction,
    excluded: SwipeAction,
    onSelect: (SwipeAction) -> Unit
) {
    val playTap = rememberPlayTapSound()
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { playTap(); expanded = true }) {
        Text(swipeActionLabel(current))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SwipeAction.entries.filter { it != excluded }.forEach { action ->
                DropdownMenuItem(
                    text = { Text(swipeActionLabel(action)) },
                    onClick = { playTap(); onSelect(action); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SwipeActionPreviewCard(
    swipeStartToEnd: SwipeAction,
    swipeEndToStart: SwipeAction
) {
    val leftBg by animateColorAsState(
        targetValue = swipeStartToEnd.semanticSwipeBackground(),
        animationSpec = tween(300),
        label = "leftBg"
    )
    val rightBg by animateColorAsState(
        targetValue = swipeEndToStart.semanticSwipeBackground(),
        animationSpec = tween(300),
        label = "rightBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        // Left action background (swipe right reveals this)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .background(leftBg)
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = swipeStartToEnd.semanticSwipeIconTint(),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = swipeStartToEnd.previewIcon(),
                    contentDescription = null,
                    tint = swipeStartToEnd.semanticSwipeIconTint(),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    swipeActionLabel(swipeStartToEnd),
                    style = MaterialTheme.typography.labelMedium,
                    color = swipeStartToEnd.semanticSwipeIconTint()
                )
            }
        }
        // Right action background (swipe left reveals this)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .background(rightBg)
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    swipeActionLabel(swipeEndToStart),
                    style = MaterialTheme.typography.labelMedium,
                    color = swipeEndToStart.semanticSwipeIconTint()
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = swipeEndToStart.previewIcon(),
                    contentDescription = null,
                    tint = swipeEndToStart.semanticSwipeIconTint(),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = swipeEndToStart.semanticSwipeIconTint(),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        // Center rule card placeholder
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Rule",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun SwipeAction.previewIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    SwipeAction.EDIT -> Icons.Default.Edit
    SwipeAction.DELETE -> Icons.Default.Delete
    SwipeAction.DUPLICATE -> Icons.Default.ContentCopy
    SwipeAction.PREVIEW -> Icons.Default.Visibility
    SwipeAction.VIEW_HISTORY -> Icons.Default.History
}

@Composable
private fun themeModeLabel(mode: AppThemeMode): String = when (mode) {
    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
    AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    AppThemeMode.BLACK -> stringResource(R.string.theme_black)
}
