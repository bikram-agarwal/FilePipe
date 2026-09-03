package dev.bikram.filepipe.ui.components

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import dev.bikram.filepipe.domain.model.formatExtensionLabel
import dev.bikram.filepipe.domain.usecase.RuleConflictDetector
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.feedback.appClickable
import dev.bikram.filepipe.ui.feedback.appCombinedClickable
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
            Modifier.appClickable(
                onClick = onClick,
                onClickLabel = clickLabel,
                role = Role.Button,
            )
        } else {
            Modifier.appCombinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = clickLabel,
                onLongClickLabel = longClickLabel,
                role = Role.Button,
            )
        },
    )

@SuppressLint("ModifierParameter")
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
    isActiveInDetailPane: Boolean,
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
    val hasConflicts = remember(rule) { RuleConflictDetector.detectConflicts(rule).isNotEmpty() }
    val effectiveFolderIssueSeverity =
        folderIssueSeverity ?: when {
            hasStaleFolder -> RuleCardFolderIssueSeverity.ERROR
            hasConflicts -> RuleCardFolderIssueSeverity.WARNING
            else -> null
        }
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                    } else if (isActiveInDetailPane) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.secondary, cardShape)
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

    val allFilesLabel = stringResource(R.string.file_type_all_files)
    val noExtensionLabel = stringResource(R.string.file_type_no_extension)
    val typesText =
        rule.fileExtensions
            .take(4)
            .map { formatExtensionLabel(it, allFilesLabel, noExtensionLabel) }
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
                                Modifier.appCombinedClickable(
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
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val operationIconName =
                    when (rule.operationMode) {
                        OperationMode.COPY -> "file_copy"
                        OperationMode.MOVE -> "move_item"
                        OperationMode.DELETE -> "delete"
                    }
                val modeLabel =
                    when (rule.operationMode) {
                        OperationMode.COPY -> stringResource(R.string.operation_copy)
                        OperationMode.MOVE -> stringResource(R.string.operation_move)
                        OperationMode.DELETE -> stringResource(R.string.operation_delete)
                    }
                FilePipeMaterialRoundedSymbol(
                    name = operationIconName,
                    contentDescription = modeLabel,
                    size = 14.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
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
    val allFilesLabel = stringResource(R.string.file_type_all_files)
    val noExtensionLabel = stringResource(R.string.file_type_no_extension)
    val hasConflicts = remember(rule) { RuleConflictDetector.detectConflicts(rule).isNotEmpty() }
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
                                    Modifier.appCombinedClickable(
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
                                label = { Text(formatExtensionLabel(extension, allFilesLabel, noExtensionLabel), style = MaterialTheme.typography.bodyMedium) },
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
                val sourceValues =
                    if (rule.sourceFolderPaths.isEmpty()) {
                        listOf(stringResource(R.string.rule_card_from_none))
                    } else {
                        rule.sourceFolderPaths.map { path -> displayPath(path, internalStorageDisplayName) }
                    }
                val operationIconName =
                    when (rule.operationMode) {
                        OperationMode.COPY -> "file_copy"
                        OperationMode.MOVE -> "move_item"
                        OperationMode.DELETE -> "delete"
                    }
                val modeLabel =
                    when (rule.operationMode) {
                        OperationMode.COPY -> stringResource(R.string.operation_copy)
                        OperationMode.MOVE -> stringResource(R.string.operation_move)
                        OperationMode.DELETE -> stringResource(R.string.operation_delete)
                    }

                LabeledInfoSingleLine(
                    label = stringResource(R.string.rule_card_from),
                    values = sourceValues,
                    maxVisibleValues = RULE_CARD_MAX_VISIBLE_SOURCES,
                    leadingIconName = if (rule.operationMode == OperationMode.DELETE) operationIconName else null,
                    leadingIconContentDescription = if (rule.operationMode == OperationMode.DELETE) modeLabel else null,
                )
                if (rule.operationMode != OperationMode.DELETE) {
                    Spacer(Modifier.height(4.dp))
                    LabeledInfoSingleLine(
                        label = stringResource(R.string.rule_card_to),
                        values =
                            listOf(
                                if (rule.destinationFolderPath.isEmpty()) {
                                    notSet
                                } else {
                                    displayPath(rule.destinationFolderPath, internalStorageDisplayName)
                                },
                            ),
                        leadingIconName = operationIconName,
                        leadingIconContentDescription = modeLabel,
                    )
                }

                if (folderIssueSeverity != null) {
                    Spacer(Modifier.height(6.dp))
                    val issueAccent = ruleCardFolderIssueColors(folderIssueSeverity).accent
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .appClickable(
                                    onClickLabel = stringResource(R.string.edit_rule),
                                    role = Role.Button,
                                    onClick = onStaleWarningClick,
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilePipeMaterialRoundedSymbol(
                            name = "warning",
                            contentDescription = null,
                            size = 16.dp,
                            tint = issueAccent,
                            weight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.rule_card_stale_folder_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = issueAccent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                rule.schedule?.let { schedule ->
                    Spacer(Modifier.height(4.dp))
                    val cardContext = LocalContext.current
                    val scheduleText = schedule.toReadableString(cardContext)
                    LabeledInfo(
                        label = stringResource(R.string.schedule_card_label),
                        value = scheduleText,
                        // Delete rules prefix the "From" row with the operation icon, so indent by the
                        // icon plus its spacing to keep the label colons vertically aligned.
                        modifier =
                            if (rule.operationMode == OperationMode.DELETE) {
                                Modifier.padding(start = LabelLeadingIconIndent)
                            } else {
                                Modifier
                            },
                    )
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

                                                OperationMode.DELETE -> {
                                                    pluralStringResource(
                                                        R.plurals.rule_card_progress_files_deleted_summary,
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

                                                OperationMode.DELETE -> {
                                                    stringResource(
                                                        R.string.rule_card_progress_deleting_file,
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
                                Text(
                                    text = stringResource(R.string.run_now),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val RULE_CARD_MAX_VISIBLE_SOURCES = 3

private val LabelLeadingIconSize = 14.dp
private val LabelLeadingIconSpacing = 4.dp
private val LabelLeadingIconIndent = LabelLeadingIconSize + LabelLeadingIconSpacing

@Composable
private fun LabeledInfo(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
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

/**
 * One-line label + value row. Values are dropped from the end until the joined text fits the
 * available width, and the hidden ones are reported as a trailing "+N" instead of being cut off
 * mid-path by an ellipsis.
 */
@Composable
private fun LabeledInfoSingleLine(
    label: String,
    values: List<String>,
    modifier: Modifier = Modifier,
    maxVisibleValues: Int = values.size,
    leadingIconName: String? = null,
    leadingIconContentDescription: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIconName != null) {
            FilePipeMaterialRoundedSymbol(
                name = leadingIconName,
                contentDescription = leadingIconContentDescription,
                size = LabelLeadingIconSize,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(LabelLeadingIconSpacing))
        }
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        val valueStyle = MaterialTheme.typography.bodyMedium
        val textMeasurer = rememberTextMeasurer()
        val moreCountFormat = stringResource(R.string.rule_card_more_count)
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val availableWidth = constraints.maxWidth
            val valueText =
                remember(values, maxVisibleValues, availableWidth, valueStyle, moreCountFormat) {
                    var visibleCount = minOf(values.size, maxVisibleValues).coerceAtLeast(1)
                    var candidate: String
                    while (true) {
                        val hiddenCount = values.size - visibleCount
                        candidate =
                            buildString {
                                append(' ')
                                values.take(visibleCount).joinTo(this, ", ")
                                if (hiddenCount > 0) {
                                    append(", ")
                                    append(java.lang.String.format(java.util.Locale.getDefault(), moreCountFormat, hiddenCount))
                                }
                            }
                        if (visibleCount == 1) break
                        val candidateWidth =
                            textMeasurer
                                .measure(
                                    text = candidate,
                                    style = valueStyle,
                                    softWrap = false,
                                    maxLines = 1,
                                ).size
                                .width
                        if (candidateWidth <= availableWidth) break
                        visibleCount--
                    }
                    candidate
                }
            Text(
                text = valueText,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
