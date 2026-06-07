package dev.bikram.filepipe.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.TriggerType
import dev.bikram.filepipe.domain.model.isEffectivelyUndone
import dev.bikram.filepipe.domain.model.isNoChangesRun
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryCard(
    history: RunHistory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColors = elevatedCardColors()
    val historyCardShape = MaterialTheme.shapes.medium
    FilePipeSurface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                },
        shape = historyCardShape,
        color = cardColors.containerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = history.ruleName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val noChanges = history.isNoChangesRun()
                val chipStatus = if (history.isEffectivelyUndone()) RunStatus.UNDONE else history.status
                StatusChip(status = chipStatus, noChanges = noChanges)
            }

            Spacer(Modifier.height(6.dp))

            val triggerLabel =
                when (history.triggeredBy) {
                    TriggerType.MANUAL -> stringResource(R.string.history_triggered_manual)
                    TriggerType.SCHEDULED -> stringResource(R.string.history_triggered_scheduled)
                }
            val cardContext = androidx.compose.ui.platform.LocalContext.current
            val timeLabel = formatTime(cardContext, history.startedAt)
            val successPart =
                if (history.totalFilesMoved > 0) {
                    when (history.operationMode) {
                        OperationMode.COPY -> {
                            pluralStringResource(
                                R.plurals.history_files_copied,
                                history.totalFilesMoved,
                                history.totalFilesMoved,
                            )
                        }

                        OperationMode.MOVE -> {
                            pluralStringResource(
                                R.plurals.history_files_moved,
                                history.totalFilesMoved,
                                history.totalFilesMoved,
                            )
                        }
                    }
                } else {
                    ""
                }
            val failedPart =
                if (history.totalFilesFailed > 0) {
                    pluralStringResource(
                        R.plurals.history_files_failed,
                        history.totalFilesFailed,
                        history.totalFilesFailed,
                    )
                } else {
                    ""
                }
            val cancelledPart =
                if (history.cancelledUnprocessedCount > 0) {
                    pluralStringResource(
                        R.plurals.history_files_cancelled_remaining,
                        history.cancelledUnprocessedCount,
                        history.cancelledUnprocessedCount,
                    )
                } else {
                    ""
                }
            val fileSummary =
                when {
                    history.status == RunStatus.CANCELLED &&
                        history.totalFilesMoved == 0 &&
                        history.totalFilesFailed == 0 &&
                        history.cancelledUnprocessedCount == 0 -> {
                        stringResource(R.string.status_cancelled)
                    }

                    history.totalFilesMoved == 0 && history.totalFilesFailed == 0 && cancelledPart.isEmpty() -> {
                        stringResource(R.string.history_no_files_affected)
                    }

                    else -> {
                        buildString {
                            if (successPart.isNotEmpty()) append(successPart)
                            if (failedPart.isNotEmpty()) {
                                if (isNotEmpty()) append(", ")
                                append(failedPart)
                            }
                            if (cancelledPart.isNotEmpty()) {
                                if (isNotEmpty()) append(", ")
                                append(cancelledPart)
                            }
                        }
                    }
                }
            Text(
                text = "$triggerLabel · $timeLabel · $fileSummary",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusChip(
    status: RunStatus,
    modifier: Modifier = Modifier,
    noChanges: Boolean = false,
) {
    val (label, targetColor) =
        when {
            noChanges -> {
                stringResource(R.string.status_no_changes) to MaterialTheme.colorScheme.surfaceVariant
            }

            else -> {
                when (status) {
                    RunStatus.SUCCESS -> stringResource(R.string.status_success) to MaterialTheme.colorScheme.primaryContainer
                    RunStatus.PARTIAL_FAILURE -> stringResource(R.string.status_partial) to MaterialTheme.colorScheme.tertiaryContainer
                    RunStatus.FAILED -> stringResource(R.string.status_failed) to MaterialTheme.colorScheme.errorContainer
                    RunStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress) to MaterialTheme.colorScheme.secondaryContainer
                    RunStatus.CANCELLED -> stringResource(R.string.status_cancelled) to MaterialTheme.colorScheme.surfaceVariant
                    RunStatus.UNDONE -> stringResource(R.string.status_undone) to MaterialTheme.colorScheme.surfaceVariant
                }
            }
        }
    val containerColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec()),
        label = "chipColor",
    )
    Surface(
        modifier = modifier,
        shape = SuggestionChipDefaults.shape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

fun formatTime(context: android.content.Context, millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    val locale = Locale.getDefault()
    val isSystem24Hour = android.text.format.DateFormat.is24HourFormat(context)
    val timePattern = if (isSystem24Hour) "HH:mm" else "h:mm a"
    val dateTimePattern = if (isSystem24Hour) "MMM d, HH:mm" else "MMM d, h:mm a"
    val pattern = if (diff < 24 * 60 * 60 * 1000L) timePattern else dateTimePattern
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}
