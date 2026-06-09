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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RunProgress
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.feedback.tapSoundClickable
import dev.bikram.filepipe.ui.feedback.tapSoundCombinedClickable
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import dev.bikram.filepipe.ui.theme.pillShape
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import dev.bikram.filepipe.ui.theme.reducedMotionEnterTransition
import dev.bikram.filepipe.ui.theme.reducedMotionExitTransition

data class RuleCardAction(
    val iconName: String,
    val label: String,
    val onClick: () -> Unit,
)

enum class RuleCardFolderIssueSeverity {
    WARNING,
    ERROR,
}

private data class RuleCardFolderIssueColors(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@Composable
private fun ruleCardFolderIssueColors(severity: RuleCardFolderIssueSeverity): RuleCardFolderIssueColors {
    val scheme = MaterialTheme.colorScheme
    if (severity == RuleCardFolderIssueSeverity.ERROR) {
        return RuleCardFolderIssueColors(
            container = scheme.errorContainer,
            content = scheme.onErrorContainer,
            accent = scheme.error,
        )
    }

    val darkUi = ColorUtils.calculateLuminance(scheme.background.toArgb()) < 0.35
    return if (darkUi) {
        RuleCardFolderIssueColors(
            container = Color(0xFF4A3000).copy(alpha = 0.94f),
            content = Color(0xFFFFDFA3),
            accent = Color(0xFFFFB300),
        )
    } else {
        RuleCardFolderIssueColors(
            container = Color(0xFFFFF1CC).copy(alpha = 0.96f),
            content = Color(0xFF5F3B00),
            accent = Color(0xFFB26A00),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.ruleCardBodyGestures(
    suppressLongClickForReorder: Boolean,
    reorderModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    clickLabel: String,
    longClickLabel: String,
): Modifier =
    then(reorderModifier).then(
        if (suppressLongClickForReorder) {
            Modifier.tapSoundClickable(
                onClick = onClick,
                onClickLabel = clickLabel,
                role = Role.Button,
            )
        } else {
            Modifier.tapSoundCombinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = clickLabel,
                onLongClickLabel = longClickLabel,
                role = Role.Button,
            )
        },
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleCardClickableBody(
    suppressLongClickForReorder: Boolean,
    reorderModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    clickLabel: String,
    longClickLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val widthModifier = Modifier.fillMaxWidth()
    Column(
        modifier =
            widthModifier
                .then(modifier)
                .ruleCardBodyGestures(
                    suppressLongClickForReorder = suppressLongClickForReorder,
                    reorderModifier = reorderModifier,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    clickLabel = clickLabel,
                    longClickLabel = longClickLabel,
                ),
        content = content,
    )
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun RuleCard(
    rule: Rule,
    isSelected: Boolean,
    isExpanded: Boolean,
    progress: RunProgress?,
    onClick: () -> Unit, // toggles expansion (or selection when in selection mode)
    onLongClick: () -> Unit, // toggles selection
    cardActions: List<RuleCardAction>, // non-swipe action icons shown in card
    onToggleEnabled: (Boolean) -> Unit,
    onRunClick: () -> Unit,
    onCancelRunClick: () -> Unit,
    isAnyRuleRunning: Boolean,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    showInlineProgressCancel: Boolean = false,
    hasStaleFolder: Boolean = false,
    folderIssueSeverity: RuleCardFolderIssueSeverity? = null,
    onStaleWarningClick: () -> Unit = {},
    /** When non-null (My order + reorder gesture): long-press on rule icon toggles selection; card body long-press drags. */
    onLeadingLongClick: (() -> Unit)? = null,
    /** Long-press drag to reorder (My order); must be built inside [ReorderableItem]. */
    reorderLongPressDrag: () -> Modifier = { Modifier },
    suppressLongClickForReorder: Boolean = false,
    showOperationalControls: Boolean = true,
) {
    val cardColors = elevatedCardColors()
    val cardShape = MaterialTheme.shapes.medium
    val spatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec<IntSize>())
    val fadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val fadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val effectiveFolderIssueSeverity =
        folderIssueSeverity ?: if (hasStaleFolder) RuleCardFolderIssueSeverity.ERROR else null
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                    } else {
                        Modifier
                    },
                ),
        shape = cardShape,
        color = cardColors.containerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (
                    expandVertically(animationSpec = spatialSpec) + fadeIn(fadeInSpec)
                ) togetherWith (
                    shrinkVertically(animationSpec = spatialSpec) + fadeOut(fadeOutSpec)
                )
            },
            label = "card_expansion",
        ) { expanded ->
            if (expanded) {
                ExpandedContent(
                    rule = rule,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    progress = progress,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    cardActions = cardActions,
                    onToggleEnabled = onToggleEnabled,
                    onRunClick = onRunClick,
                    onCancelRunClick = onCancelRunClick,
                    showInlineProgressCancel = showInlineProgressCancel,
                    isAnyRuleRunning = isAnyRuleRunning,
                    folderIssueSeverity = effectiveFolderIssueSeverity,
                    onStaleWarningClick = onStaleWarningClick,
                    onLeadingLongClick = onLeadingLongClick,
                    modifier = reorderLongPressDrag(),
                    suppressLongClickForReorder = suppressLongClickForReorder,
                    showOperationalControls = showOperationalControls,
                )
            } else {
                CompactContent(
                    rule = rule,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode,
                    progress = progress,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onToggleEnabled = onToggleEnabled,
                    onRunClick = onRunClick,
                    onCancelRunClick = onCancelRunClick,
                    showInlineProgressCancel = showInlineProgressCancel,
                    isAnyRuleRunning = isAnyRuleRunning,
                    folderIssueSeverity = effectiveFolderIssueSeverity,
                    onLeadingLongClick = onLeadingLongClick,
                    modifier = reorderLongPressDrag(),
                    suppressLongClickForReorder = suppressLongClickForReorder,
                    showOperationalControls = showOperationalControls,
                )
            }
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun CompactContent(
    rule: Rule,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    progress: RunProgress?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRunClick: () -> Unit,
    onCancelRunClick: () -> Unit,
    showInlineProgressCancel: Boolean,
    isAnyRuleRunning: Boolean,
    folderIssueSeverity: RuleCardFolderIssueSeverity?,
    modifier: Modifier = Modifier,
    onLeadingLongClick: (() -> Unit)? = null,
    suppressLongClickForReorder: Boolean = false,
    showOperationalControls: Boolean = true,
) {
    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val activeProgress = progress?.takeUnless { progressValue -> progressValue.isComplete }
    val runBlocked = isAnyRuleRunning && progress == null

    val typesText =
        rule.fileExtensions
            .take(4)
            .map { it.removePrefix(".") }
            .joinToString(" · ") +
            if (rule.fileExtensions.size > 4) " +${rule.fileExtensions.size - 4}" else ""
    val destText = displayPath(rule.destinationFolderPath, internalStorageDisplayName).takeIf { it.isNotBlank() } ?: ""
    val infoText = listOf(typesText, destText).filter { it.isNotBlank() }.joinToString("  |  ")
    val clickLabel =
        if (isSelectionMode) {
            stringResource(
                if (isSelected) R.string.rule_card_deselect_cd else R.string.rule_card_select_cd,
                rule.name,
            )
        } else {
            stringResource(R.string.rule_card_expand_cd, rule.name)
        }
    val longClickLabel = stringResource(R.string.rule_card_select_cd, rule.name)

    val columnLongClick: (() -> Unit)? =
        when {
            suppressLongClickForReorder -> null
            onLeadingLongClick != null -> null
            else -> onLongClick
        }
    RuleCardClickableBody(
        suppressLongClickForReorder = suppressLongClickForReorder,
        reorderModifier = modifier,
        onClick = onClick,
        onLongClick = columnLongClick,
        clickLabel = clickLabel,
        longClickLabel = longClickLabel,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent =
                if (infoText.isNotBlank()) {
                    {
                        Text(
                            text = infoText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    null
                },
            leadingContent = {
                val iconBoxModifier =
                    Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ).then(
                            folderIssueSeverity
                                ?.let { severity ->
                                    Modifier.border(
                                        width = 2.dp,
                                        color = ruleCardFolderIssueColors(severity).accent,
                                        shape = CircleShape,
                                    )
                                } ?: Modifier,
                        ).then(
                            if (onLeadingLongClick != null) {
                                Modifier.tapSoundCombinedClickable(
                                    onClick = onClick,
                                    onLongClick = onLeadingLongClick,
                                    onClickLabel = clickLabel,
                                    onLongClickLabel = longClickLabel,
                                    role = Role.Button,
                                )
                            } else {
                                Modifier
                            },
                        )
                Box(
                    modifier = iconBoxModifier,
                    contentAlignment = Alignment.Center,
                ) {
                    RuleIconOrEmoji(
                        iconEmoji = rule.iconEmoji,
                        icon = rule.icon,
                        vectorSize = 22.dp,
                        emojiFontSize = 18.sp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier,
                    )
                }
            },
            trailingContent =
                if (showOperationalControls) {
                    {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilePipeSwitch(
                                checked = rule.isEnabled,
                                onCheckedChange = { enabled ->
                                    onToggleEnabled(enabled)
                                },
                            )
                            if (activeProgress != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        CircularWavyProgressIndicator(
                                            progress = { if (activeProgress.totalFiles > 0) activeProgress.progress else 0f },
                                            modifier = Modifier.size(26.dp),
                                        )
                                    }
                                    if (showInlineProgressCancel) {
                                        FilePipeOutlinedButton(
                                            onClick = onCancelRunClick,
                                            shape = pillShape,
                                            contentPadding =
                                                androidx.compose.foundation.layout.PaddingValues(
                                                    horizontal = 10.dp,
                                                    vertical = 6.dp,
                                                ),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.cancel),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }
                                }
                            } else {
                                FilePipeFilledTonalIconButton(
                                    onClick = onRunClick,
                                    enabled = rule.isEnabled && !runBlocked,
                                ) {
                                    FilePipeMaterialRoundedSymbol(
                                        name = "play_arrow",
                                        contentDescription = stringResource(R.string.run_now),
                                        size = 20.dp,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    null
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun ExpandedContent(
    rule: Rule,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    progress: RunProgress?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    cardActions: List<RuleCardAction>,
    onToggleEnabled: (Boolean) -> Unit,
    onRunClick: () -> Unit,
    onCancelRunClick: () -> Unit,
    showInlineProgressCancel: Boolean,
    isAnyRuleRunning: Boolean,
    modifier: Modifier = Modifier,
    folderIssueSeverity: RuleCardFolderIssueSeverity? = null,
    onStaleWarningClick: () -> Unit = {},
    onLeadingLongClick: (() -> Unit)? = null,
    suppressLongClickForReorder: Boolean = false,
    showOperationalControls: Boolean = true,
) {
    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val progressSpatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.slowSpatialSpec<IntSize>())
    val progressFadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val progressFadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val clickLabel =
        if (isSelectionMode) {
            stringResource(
                if (isSelected) R.string.rule_card_deselect_cd else R.string.rule_card_select_cd,
                rule.name,
            )
        } else {
            stringResource(R.string.rule_card_collapse_cd, rule.name)
        }
    val longClickLabel = stringResource(R.string.rule_card_select_cd, rule.name)
    val expandedColumnLongClick: (() -> Unit)? =
        when {
            suppressLongClickForReorder -> null
            onLeadingLongClick != null -> null
            else -> onLongClick
        }
    Column(modifier = Modifier.padding(16.dp)) {
        RuleCardClickableBody(
            suppressLongClickForReorder = suppressLongClickForReorder,
            reorderModifier = modifier,
            onClick = onClick,
            onLongClick = expandedColumnLongClick,
            clickLabel = clickLabel,
            longClickLabel = longClickLabel,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.then(
                                if (onLeadingLongClick != null) {
                                    Modifier.tapSoundCombinedClickable(
                                        onClick = onClick,
                                        onLongClick = onLeadingLongClick,
                                        onClickLabel = clickLabel,
                                        onLongClickLabel = longClickLabel,
                                        role = Role.Button,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        RuleIconOrEmoji(
                            iconEmoji = rule.iconEmoji,
                            icon = rule.icon,
                            vectorSize = 28.dp,
                            emojiFontSize = 22.sp,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier,
                        )
                    }
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (showOperationalControls) {
                    FilePipeSwitch(
                        checked = rule.isEnabled,
                        onCheckedChange = { enabled ->
                            onToggleEnabled(enabled)
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))

                if (rule.fileExtensions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.rule_card_types_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        rule.fileExtensions.forEach { extension ->
                            FilePipeFilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text(extension.removePrefix("."), style = MaterialTheme.typography.bodyMedium) },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                border =
                                    FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = true,
                                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                    ),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val notSet = stringResource(R.string.rule_card_destination_not_set)
                val fromText =
                    if (rule.sourceFolderPaths.isEmpty()) {
                        stringResource(R.string.rule_card_from_none)
                    } else {
                        val shown = rule.sourceFolderPaths.take(3)
                        val extra = rule.sourceFolderPaths.size - shown.size
                        shown.joinToString(", ") { displayPath(it, internalStorageDisplayName) } +
                            if (extra > 0) ", +$extra" else ""
                    }
                LabeledInfoSingleLine(
                    label = stringResource(R.string.rule_card_from),
                    value = fromText,
                )
                Spacer(Modifier.height(4.dp))
                LabeledInfoSingleLine(
                    label = stringResource(R.string.rule_card_to),
                    value =
                        if (rule.destinationFolderPath.isEmpty()) {
                            notSet
                        } else {
                            displayPath(rule.destinationFolderPath, internalStorageDisplayName)
                        },
                )

                if (folderIssueSeverity != null) {
                    Spacer(Modifier.height(8.dp))
                    val issueColors = ruleCardFolderIssueColors(folderIssueSeverity)
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .tapSoundClickable(
                                    onClickLabel = stringResource(R.string.edit_rule),
                                    role = Role.Button,
                                    onClick = onStaleWarningClick,
                                ),
                        shape = MaterialTheme.shapes.large,
                        color = issueColors.container,
                        contentColor = issueColors.content,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilePipeMaterialRoundedSymbol(
                                name = "warning",
                                contentDescription = null,
                                size = 22.dp,
                                tint = issueColors.content,
                                weight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.rule_card_stale_folder_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = issueColors.content,
                                modifier = Modifier.weight(1f),
                            )
                            FilePipeMaterialRoundedSymbol(
                                name = "edit",
                                contentDescription = stringResource(R.string.edit_rule),
                                size = 20.dp,
                                tint = issueColors.content,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                }

                rule.schedule?.let { schedule ->
                    Spacer(Modifier.height(4.dp))
                    val cardContext = androidx.compose.ui.platform.LocalContext.current
                    val isSystem24Hour =
                        android.text.format.DateFormat
                            .is24HourFormat(cardContext)
                    val timeStr =
                        if (isSystem24Hour) {
                            "%02d:%02d".format(schedule.hour, schedule.minute)
                        } else {
                            val hour12 =
                                when (val hourMod = schedule.hour % 12) {
                                    0 -> 12
                                    else -> hourMod
                                }
                            val amPm = if (schedule.hour < 12) "AM" else "PM"
                            "%d:%02d %s".format(hour12, schedule.minute, amPm)
                        }
                    val scheduleText =
                        when (schedule.type) {
                            ScheduleType.DAILY -> {
                                "Daily at $timeStr"
                            }

                            ScheduleType.WEEKLY -> {
                                val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                                val dayName = schedule.dayOfWeek?.let { days.getOrNull(it - 2) } ?: "?"
                                "Weekly $dayName at $timeStr"
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
                    enter =
                        reducedMotionEnterTransition(
                            expandVertically(animationSpec = progressSpatialSpec) + fadeIn(animationSpec = progressFadeInSpec),
                        ),
                    exit =
                        reducedMotionExitTransition(
                            shrinkVertically(animationSpec = progressSpatialSpec) + fadeOut(animationSpec = progressFadeOutSpec),
                        ),
                ) {
                    progress?.let { runProgress ->
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (runProgress.isComplete) {
                                val summary =
                                    when {
                                        runProgress.error != null -> {
                                            stringResource(R.string.rule_card_progress_error, runProgress.error)
                                        }

                                        runProgress.totalFiles == 0 -> {
                                            stringResource(R.string.rule_card_progress_no_matching_files)
                                        }

                                        else -> {
                                            when (rule.operationMode) {
                                                OperationMode.COPY -> {
                                                    pluralStringResource(
                                                        R.plurals.rule_card_progress_files_copied_summary,
                                                        runProgress.totalFiles,
                                                        runProgress.filesMoved,
                                                        runProgress.totalFiles,
                                                    )
                                                }

                                                OperationMode.MOVE -> {
                                                    pluralStringResource(
                                                        R.plurals.rule_card_progress_files_moved_summary,
                                                        runProgress.totalFiles,
                                                        runProgress.filesMoved,
                                                        runProgress.totalFiles,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                    CircularWavyProgressIndicator(
                                        progress = { 1f },
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color =
                                            if (runProgress.error != null) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                    )
                                }
                            } else if (runProgress.totalFiles > 0) {
                                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                                val progressSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                                val animatedProgress by animateFloatAsState(
                                    targetValue = runProgress.progress,
                                    animationSpec = reducedMotionAwareSpec(progressSpec),
                                    label = "progress",
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val currentFileName =
                                        runProgress.currentFileName.ifBlank {
                                            stringResource(R.string.rule_card_progress_unknown_file)
                                        }
                                    LinearWavyProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                                    )
                                    Text(
                                        text =
                                            when (rule.operationMode) {
                                                OperationMode.COPY -> {
                                                    stringResource(
                                                        R.string.rule_card_progress_copying_file,
                                                        currentFileName,
                                                        runProgress.filesMoved + 1,
                                                        runProgress.totalFiles,
                                                    )
                                                }

                                                OperationMode.MOVE -> {
                                                    stringResource(
                                                        R.string.rule_card_progress_moving_file,
                                                        currentFileName,
                                                        runProgress.filesMoved + 1,
                                                        runProgress.totalFiles,
                                                    )
                                                }
                                            },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LinearWavyProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                                    )
                                    Text(
                                        text = stringResource(R.string.rule_card_progress_scanning),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showOperationalControls || cardActions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    cardActions.forEach { action ->
                        FilePipeIconButton(
                            onClick = action.onClick,
                            tooltipLabel = action.label,
                        ) {
                            FilePipeMaterialRoundedSymbol(
                                name = action.iconName,
                                contentDescription = action.label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .ruleCardBodyGestures(
                                suppressLongClickForReorder = suppressLongClickForReorder,
                                reorderModifier = modifier,
                                onClick = onClick,
                                onLongClick = expandedColumnLongClick,
                                clickLabel = clickLabel,
                                longClickLabel = longClickLabel,
                            ),
                )
                if (showOperationalControls) {
                    val runInProgress = progress != null && !progress.isComplete
                    val runBlocked = isAnyRuleRunning && progress == null
                    if (runInProgress && showInlineProgressCancel) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { CenteredTooltipText(stringResource(R.string.cancel)) } },
                            state = rememberTooltipState(),
                        ) {
                            FilePipeOutlinedButton(
                                onClick = onCancelRunClick,
                                shape = pillShape,
                            ) {
                                Text(text = stringResource(R.string.cancel))
                            }
                        }
                    } else if (!runInProgress) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { CenteredTooltipText(stringResource(R.string.run_now)) } },
                            state = rememberTooltipState(),
                        ) {
                            FilePipeFilledTonalButton(
                                onClick = onRunClick,
                                enabled = rule.isEnabled && !runBlocked,
                                shape = pillShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name = "play_arrow",
                                    contentDescription = null,
                                    size = 20.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(text = stringResource(R.string.run_now))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledInfo(
    label: String,
    value: String,
) {
    Row {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = " $value",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LabeledInfoSingleLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = " $value",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
