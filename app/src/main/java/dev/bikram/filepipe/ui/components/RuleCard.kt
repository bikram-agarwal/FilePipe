package dev.bikram.filepipe.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunProgress
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import dev.bikram.filepipe.ui.feedback.tapSoundCombinedClickable
import dev.bikram.filepipe.ui.theme.elevatedCardColors

data class RuleCardAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

private val CardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun RuleCard(
    rule: Rule,
    isSelected: Boolean,
    isExpanded: Boolean,
    progress: RunProgress?,
    onClick: () -> Unit,          // toggles expansion (or selection when in selection mode)
    onLongClick: () -> Unit,      // toggles selection
    cardActions: List<RuleCardAction>, // non-swipe action icons shown in card
    onToggleEnabled: (Boolean) -> Unit,
    onRunClick: () -> Unit,
    onCancelRunClick: () -> Unit,
    showInlineProgressCancel: Boolean = false,
    isAnyRuleRunning: Boolean,
    onPreviewRule: () -> Unit = {},
    onViewHistory: () -> Unit = {},
    hasStaleFolder: Boolean = false,
    onStaleWarningClick: () -> Unit = {},
    /** When non-null (My order + reorder gesture): long-press on rule icon toggles selection; card body long-press drags. */
    onLeadingLongClick: (() -> Unit)? = null,
    /** Long-press drag to reorder (My order); must be built inside [ReorderableItem]. */
    reorderLongPressDragModifier: Modifier = Modifier,
    suppressLongClickForReorder: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardColors = elevatedCardColors()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardShape)
                else Modifier
            ),
        shape = CardShape,
        color = cardColors.containerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (expandVertically() + fadeIn()) togetherWith (shrinkVertically() + fadeOut())
            },
            label = "card_expansion"
        ) { expanded ->
            if (expanded) {
                ExpandedContent(
                    rule = rule,
                    progress = progress,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    cardActions = cardActions,
                    onToggleEnabled = onToggleEnabled,
                    onRunClick = onRunClick,
                    onCancelRunClick = onCancelRunClick,
                    showInlineProgressCancel = showInlineProgressCancel,
                    isAnyRuleRunning = isAnyRuleRunning,
                    hasStaleFolder = hasStaleFolder,
                    onStaleWarningClick = onStaleWarningClick,
                    onLeadingLongClick = onLeadingLongClick,
                    reorderLongPressDragModifier = reorderLongPressDragModifier,
                    suppressLongClickForReorder = suppressLongClickForReorder
                )
            } else {
                CompactContent(
                    rule = rule,
                    progress = progress,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onToggleEnabled = onToggleEnabled,
                    onRunClick = onRunClick,
                    onCancelRunClick = onCancelRunClick,
                    showInlineProgressCancel = showInlineProgressCancel,
                    isAnyRuleRunning = isAnyRuleRunning,
                    onLeadingLongClick = onLeadingLongClick,
                    reorderLongPressDragModifier = reorderLongPressDragModifier,
                    suppressLongClickForReorder = suppressLongClickForReorder
                )
            }
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
private fun CompactContent(
    rule: Rule,
    progress: RunProgress?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRunClick: () -> Unit,
    onCancelRunClick: () -> Unit,
    showInlineProgressCancel: Boolean,
    isAnyRuleRunning: Boolean,
    onLeadingLongClick: (() -> Unit)? = null,
    reorderLongPressDragModifier: Modifier = Modifier,
    suppressLongClickForReorder: Boolean = false
) {
    val playTap = rememberPlayTapSound()
    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val runInProgress = progress != null && !progress.isComplete
    val runBlocked = isAnyRuleRunning && progress == null

    val typesText = rule.fileExtensions.take(4).joinToString(" · ") +
        if (rule.fileExtensions.size > 4) " +${rule.fileExtensions.size - 4}" else ""
    val destText = displayPath(rule.destinationFolderPath, internalStorageDisplayName).takeIf { it.isNotBlank() } ?: ""
    val infoText = listOf(typesText, destText).filter { it.isNotBlank() }.joinToString("  |  ")

    val columnLongClick: (() -> Unit)? = when {
        suppressLongClickForReorder -> null
        onLeadingLongClick != null -> null
        else -> onLongClick
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tapSoundCombinedClickable(
                onClick = onClick,
                onLongClick = columnLongClick
            )
            .then(reorderLongPressDragModifier)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = if (infoText.isNotBlank()) {
                {
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else null,
            leadingContent = {
                val iconBoxModifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    )
                    .then(
                        if (onLeadingLongClick != null) {
                            Modifier.tapSoundCombinedClickable(
                                onClick = onClick,
                                onLongClick = onLeadingLongClick
                            )
                        } else {
                            Modifier
                        }
                    )
                Box(
                    modifier = iconBoxModifier,
                    contentAlignment = Alignment.Center
                ) {
                    RuleIconOrEmoji(
                        iconEmoji = rule.iconEmoji,
                        icon = rule.icon,
                        vectorSize = 22.dp,
                        emojiFontSize = 18.sp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                    )
                }
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { enabled ->
                            playTap()
                            onToggleEnabled(enabled)
                        }
                    )
                    if (runInProgress) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                CircularWavyProgressIndicator(
                                    progress = { if (progress!!.totalFiles > 0) progress.progress else 0f },
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            if (showInlineProgressCancel) {
                                OutlinedButton(
                                    onClick = { playTap(); onCancelRunClick() },
                                    shape = RoundedCornerShape(50),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 10.dp, vertical = 6.dp
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.cancel),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = { playTap(); onRunClick() },
                            enabled = rule.isEnabled && !runBlocked,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.run_now),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
private fun ExpandedContent(
    rule: Rule,
    progress: RunProgress?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    cardActions: List<RuleCardAction>,
    onToggleEnabled: (Boolean) -> Unit,
    onRunClick: () -> Unit,
    onCancelRunClick: () -> Unit,
    showInlineProgressCancel: Boolean,
    isAnyRuleRunning: Boolean,
    hasStaleFolder: Boolean = false,
    onStaleWarningClick: () -> Unit = {},
    onLeadingLongClick: (() -> Unit)? = null,
    reorderLongPressDragModifier: Modifier = Modifier,
    suppressLongClickForReorder: Boolean = false
) {
    val playTap = rememberPlayTapSound()
    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val expandedColumnLongClick: (() -> Unit)? = when {
        suppressLongClickForReorder -> null
        onLeadingLongClick != null -> null
        else -> onLongClick
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tapSoundCombinedClickable(
                onClick = onClick,
                onLongClick = expandedColumnLongClick
            )
            .then(reorderLongPressDragModifier)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.then(
                        if (onLeadingLongClick != null) {
                            Modifier.tapSoundCombinedClickable(
                                onClick = onClick,
                                onLongClick = onLeadingLongClick
                            )
                        } else {
                            Modifier
                        }
                    )
                ) {
                    RuleIconOrEmoji(
                        iconEmoji = rule.iconEmoji,
                        icon = rule.icon,
                        vectorSize = 28.dp,
                        emojiFontSize = 22.sp,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                    )
                }
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = { enabled ->
                    playTap()
                    onToggleEnabled(enabled)
                },
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(8.dp))

            if (rule.fileExtensions.isEmpty()) {
                Text(
                    text = stringResource(R.string.rule_card_types_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rule.fileExtensions.forEach { extension ->
                        FilterChip(
                            selected = true,
                            onClick = { playTap() },
                            label = { Text(extension, style = MaterialTheme.typography.bodyMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = true,
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val notSet = stringResource(R.string.rule_card_destination_not_set)
            val fromText = if (rule.sourceFolderPaths.isEmpty()) {
                stringResource(R.string.rule_card_from_none)
            } else {
                val shown = rule.sourceFolderPaths.take(3)
                val extra = rule.sourceFolderPaths.size - shown.size
                shown.joinToString(", ") { displayPath(it, internalStorageDisplayName) } +
                    if (extra > 0) ", +$extra" else ""
            }
            LabeledInfoSingleLine(
                label = stringResource(R.string.rule_card_from),
                value = fromText
            )
            Spacer(Modifier.height(4.dp))
            LabeledInfoSingleLine(
                label = stringResource(R.string.rule_card_to),
                value = if (rule.destinationFolderPath.isEmpty()) notSet
                        else displayPath(rule.destinationFolderPath, internalStorageDisplayName)
            )

            if (hasStaleFolder) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { playTap(); onStaleWarningClick() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.rule_card_stale_folder_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_rule),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            rule.schedule?.let { schedule ->
                Spacer(Modifier.height(4.dp))
                val scheduleText = when (schedule.type) {
                    ScheduleType.DAILY -> "Daily at %02d:%02d".format(schedule.hour, schedule.minute)
                    ScheduleType.WEEKLY -> {
                        val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        val dayName = schedule.dayOfWeek?.let { days.getOrNull(it - 2) } ?: "?"
                        "Weekly $dayName at %02d:%02d".format(schedule.hour, schedule.minute)
                    }
                    ScheduleType.EVERY_N_HOURS -> {
                        val hours = schedule.intervalHours ?: 1
                        "Every ${hours}h"
                    }
                }
                LabeledInfo(label = stringResource(R.string.schedule_card_label), value = scheduleText)
            }

            AnimatedVisibility(
                visible = progress != null,
                enter = expandVertically() + fadeIn()
            ) {
                progress?.let { runProgress ->
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (runProgress.isComplete) {
                            val summary = when {
                                runProgress.error != null -> "Error: ${runProgress.error}"
                                runProgress.totalFiles == 0 -> "No matching files found"
                                else -> when (rule.operationMode) {
                                    OperationMode.COPY -> stringResource(
                                        R.string.rule_card_progress_files_copied_summary,
                                        runProgress.filesMoved,
                                        runProgress.totalFiles
                                    )
                                    OperationMode.MOVE -> stringResource(
                                        R.string.rule_card_progress_files_moved_summary,
                                        runProgress.filesMoved,
                                        runProgress.totalFiles
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                CircularWavyProgressIndicator(
                                    progress = { 1f },
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (runProgress.error != null) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (runProgress.totalFiles > 0) {
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            val progressSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                            val animatedProgress by animateFloatAsState(
                                targetValue = runProgress.progress,
                                animationSpec = progressSpec,
                                label = "progress"
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                CircularWavyProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = when (rule.operationMode) {
                                        OperationMode.COPY -> stringResource(
                                            R.string.rule_card_progress_copying_file,
                                            runProgress.currentFileName.ifBlank { "…" },
                                            runProgress.filesMoved + 1,
                                            runProgress.totalFiles
                                        )
                                        OperationMode.MOVE -> stringResource(
                                            R.string.rule_card_progress_moving_file,
                                            runProgress.currentFileName.ifBlank { "…" },
                                            runProgress.filesMoved + 1,
                                            runProgress.totalFiles
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
                                Text("Scanning…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cardActions.forEach { action ->
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(action.label) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {
                            playTap()
                            action.onClick()
                        }) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            val runInProgress = progress != null && !progress.isComplete
            val runBlocked = isAnyRuleRunning && progress == null
            if (runInProgress && showInlineProgressCancel) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.cancel)) } },
                    state = rememberTooltipState()
                ) {
                    OutlinedButton(
                        onClick = { playTap(); onCancelRunClick() },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            } else if (!runInProgress) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.run_now)) } },
                    state = rememberTooltipState()
                ) {
                    FilledTonalButton(
                        onClick = {
                            playTap()
                            onRunClick()
                        },
                        enabled = rule.isEnabled && !runBlocked,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.run_now))
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledInfo(label: String, value: String) {
    Row {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = " $value",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LabeledInfoSingleLine(label: String, value: String, rowModifier: Modifier = Modifier) {
    Row(modifier = rowModifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = " $value",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
