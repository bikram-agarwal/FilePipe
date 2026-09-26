package dev.bikram.filepipe.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.isLandscape
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeDropdownMenuItem
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.FilePipeTextButton
import dev.bikram.filepipe.ui.components.ToggleLabelHelpDropdown
import dev.bikram.filepipe.ui.components.containers.GroupPosition
import dev.bikram.filepipe.ui.components.containers.GroupedListColumn
import dev.bikram.filepipe.ui.components.containers.GroupedListItem
import dev.bikram.filepipe.ui.components.text.SimpleMarkdown
import dev.bikram.filepipe.ui.feedback.appClickable
import dev.bikram.filepipe.ui.theme.compactControlShape
import dev.bikram.filepipe.ui.theme.pillShape
import dev.bikram.filepipe.update.UpdateInfo
import dev.bikram.filepipe.update.isObtainXInstalled
import dev.bikram.filepipe.update.supportsObtainXTracking
import dev.bikram.filepipe.update.trackUpdatesViaObtainX
import kotlinx.coroutines.launch

@Composable
internal fun UpdateCheckScheduleDropdown(
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

internal fun summaryLabelBeforeColon(fullScheduleLabel: String): String {
    val colonIndex = fullScheduleLabel.indexOf(':')
    return if (colonIndex >= 0) {
        fullScheduleLabel.substring(0, colonIndex).trim()
    } else {
        fullScheduleLabel
    }
}

@Composable
internal fun updateScheduleSummaryBeforeColon(schedule: UpdateCheckSchedule): String = summaryLabelBeforeColon(updateScheduleLabel(schedule))

@Composable
internal fun updateScheduleLabel(schedule: UpdateCheckSchedule): String =
    when (schedule) {
        UpdateCheckSchedule.AT_APP_START -> stringResource(R.string.settings_update_schedule_app_start)
        UpdateCheckSchedule.DAILY_AT_21 -> stringResource(R.string.settings_update_schedule_daily_21)
        UpdateCheckSchedule.WEEKLY_MONDAY_AT_21 -> stringResource(R.string.settings_update_schedule_monday_21)
        UpdateCheckSchedule.NEVER -> stringResource(R.string.settings_update_schedule_never)
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun UpdateCheckBottomSheetContent(
    maxSheetHeight: Dp,
    isCheckingUpdate: Boolean,
    updateInfo: UpdateInfo?,
    manualUpdateNoResult: Boolean,
    updateCheckFailed: Boolean,
    downloadProgress: Float?,
    changelogState: ChangelogUiState,
    showGithubExtraUi: Boolean,
    useFdroidUpdates: Boolean,
    usePlayInAppUpdates: Boolean,
    onDownloadClick: (UpdateInfo) -> Unit,
    onSkipVersionClick: () -> Unit,
) {
    val sheetScroll = rememberScrollState()
    val pagerCoroutineScope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val isLandscape = isLandscape()
    val isChangelogReady = changelogState is ChangelogUiState.Ready
    val outerScrollable = sheetScroll.maxValue > 0 && sheetScroll.maxValue != Int.MAX_VALUE
    val outerModifier =
        Modifier
            .fillMaxWidth()
            .let { modifier ->
                if (isLandscape && isChangelogReady) {
                    modifier.height(maxSheetHeight).verticalScroll(sheetScroll, enabled = outerScrollable)
                } else {
                    modifier.heightIn(max = maxSheetHeight)
                }
            }.padding(horizontal = 16.dp, vertical = 8.dp)
    Column(
        outerModifier,
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
                                    when {
                                        useFdroidUpdates -> {
                                            stringResource(R.string.settings_open_fdroid)
                                        }

                                        usePlayInAppUpdates && availableUpdate.isPlayStoreUpdateInProgress -> {
                                            stringResource(R.string.settings_update_resume_play)
                                        }

                                        else -> {
                                            stringResource(
                                                R.string.settings_download_install,
                                                availableUpdate.versionName,
                                            )
                                        }
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

                updateCheckFailed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilePipeMaterialRoundedSymbol(
                            name = "warning",
                            contentDescription = null,
                            size = 40.dp,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.settings_update_check_failed),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                val changelogPagerMaxHeight = maxSheetHeight * 0.72f
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp,
                ) {
                    if (changelogPages.size <= 1) {
                        val singleScroll = rememberScrollState()
                        val singleScrollable = singleScroll.maxValue > 0 && singleScroll.maxValue != Int.MAX_VALUE
                        val singleModifier =
                            Modifier
                                .fillMaxWidth()
                                .let { modifier ->
                                    if (isLandscape) {
                                        modifier.wrapContentHeight().padding(8.dp)
                                    } else {
                                        modifier.heightIn(max = changelogPagerMaxHeight).padding(8.dp)
                                    }
                                }
                        Surface(
                            modifier = singleModifier,
                            shape = compactControlShape,
                            color = scheme.surfaceContainerLow,
                            contentColor = scheme.onSurface,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .let { modifier ->
                                            if (isLandscape) {
                                                modifier.fillMaxWidth().wrapContentHeight().padding(16.dp)
                                            } else {
                                                modifier.fillMaxSize().verticalScroll(singleScroll, enabled = singleScrollable).padding(16.dp)
                                            }
                                        },
                            ) {
                                SimpleMarkdown(
                                    content = readyMarkdown,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    } else {
                        val pagerModifier =
                            Modifier
                                .fillMaxWidth()
                                .let { modifier ->
                                    if (isLandscape) {
                                        modifier.wrapContentHeight().padding(horizontal = 8.dp, vertical = 2.dp)
                                    } else {
                                        modifier.height(changelogPagerMaxHeight).padding(horizontal = 8.dp, vertical = 2.dp)
                                    }
                                }
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
                                            .appClickable(
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
                                            .appClickable(
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
                                modifier = pagerModifier,
                                shape = compactControlShape,
                                color = scheme.surfaceContainerLow,
                                contentColor = scheme.onSurface,
                            ) {
                                HorizontalPager(
                                    state = changelogPagerState,
                                    modifier =
                                        Modifier
                                            .let { modifier ->
                                                if (isLandscape) {
                                                    modifier.fillMaxWidth().wrapContentHeight()
                                                } else {
                                                    modifier.fillMaxSize()
                                                }
                                            },
                                ) { pageIndex ->
                                    val innerScroll = rememberScrollState()
                                    val innerScrollable = innerScroll.maxValue > 0 && innerScroll.maxValue != Int.MAX_VALUE
                                    Column(
                                        modifier =
                                            Modifier
                                                .let { modifier ->
                                                    if (isLandscape) {
                                                        modifier.fillMaxWidth().wrapContentHeight().padding(16.dp)
                                                    } else {
                                                        modifier.fillMaxSize().verticalScroll(innerScroll, enabled = innerScrollable).padding(16.dp)
                                                    }
                                                },
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
internal fun UpToDatePhoneIcon() {
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
internal fun UpdateSheetDownloadProgressBar(downloadProgress: Float) {
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

/** The check-for-updates row is last unless the ObtainX row follows it. */
internal fun checkForUpdatesRowPosition(): GroupPosition = if (supportsObtainXTracking) GroupPosition.MIDDLE else GroupPosition.LAST

/**
 * Offline can't check for updates itself, so ObtainX is its only update option. The single-list
 * settings page shows it as a card of its own instead of an Updates section holding one row. The
 * two-pane layout keeps that Updates section, because its section list is how the option is reached.
 */
private val obtainXIsOnlyUpdateOption: Boolean
    get() = supportsObtainXTracking && !BuildConfig.CHECK_UPDATES

/** Whether the Updates section is emitted. [singleList] is the phone layout, with no section selected. */
internal fun showsUpdatesSection(singleList: Boolean): Boolean = BuildConfig.CHECK_UPDATES || (obtainXIsOnlyUpdateOption && !singleList)

/** Whether the ObtainX card stands on its own on the settings page. See [showsUpdatesSection]. */
internal fun showsStandaloneObtainXCard(singleList: Boolean): Boolean = obtainXIsOnlyUpdateOption && singleList

/**
 * The ObtainX item inside the Updates section: last after the update-check rows, or the only row in
 * the offline two-pane layout. Absent on Play.
 */
@Composable
internal fun TrackUpdatesViaObtainXItem() {
    if (!supportsObtainXTracking) return
    GroupedListItem(position = if (BuildConfig.CHECK_UPDATES) GroupPosition.LAST else GroupPosition.ONLY) {
        TrackUpdatesViaObtainXRow()
    }
}

/** The ObtainX row as its own card, outside any section. See [showsStandaloneObtainXCard]. */
@Composable
internal fun StandaloneTrackUpdatesViaObtainXCard(modifier: Modifier = Modifier) {
    GroupedListColumn(modifier = modifier) {
        GroupedListItem(position = GroupPosition.ONLY) {
            TrackUpdatesViaObtainXRow()
        }
    }
}

/**
 * Row that hands this build to ObtainX, or opens ObtainX's page when it isn't installed. Offered on
 * every non-Play flavor; on offline it is the only way to get updates without installing them by
 * hand.
 */
@Composable
internal fun TrackUpdatesViaObtainXRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val obtainXInstalled = rememberObtainXInstalled()
    val subtitleRes =
        when {
            !obtainXInstalled -> R.string.settings_track_updates_via_obtainx_get_desc
            BuildConfig.FLAVOR == "fdroid" -> R.string.settings_track_updates_via_obtainx_desc_fdroid
            else -> R.string.settings_track_updates_via_obtainx_desc
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .appClickable { trackUpdatesViaObtainX(context) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_obtainx),
            contentDescription = null,
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_track_updates_via_obtainx),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        SettingsArrowOutwardBadge()
    }
}

/** Re-checked on resume, so the subtitle updates when the user comes back after installing ObtainX. */
@Composable
private fun rememberObtainXInstalled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var installed by remember(context) { mutableStateOf(isObtainXInstalled(context)) }

    DisposableEffect(context, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    installed = isObtainXInstalled(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return installed
}

internal fun openFdroidPackagePage(context: Context) {
    val fdroidIntent =
        Intent(Intent.ACTION_VIEW, "fdroid.app:${context.packageName}".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    try {
        context.startActivity(fdroidIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent =
            Intent(
                Intent.ACTION_VIEW,
                "https://f-droid.org/packages/${context.packageName}/".toUri(),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(webIntent)
    }
}

sealed class ChangelogUiState {
    data object Hidden : ChangelogUiState()

    data object Loading : ChangelogUiState()

    data class Ready(
        val text: String,
    ) : ChangelogUiState()

    data class Failed(
        val message: String,
    ) : ChangelogUiState()
}
