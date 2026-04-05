package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.isEffectivelyUndone
import dev.bikram.filepipe.domain.model.isNoChangesRun
import dev.bikram.filepipe.domain.model.TriggerType
import dev.bikram.filepipe.ui.theme.elevatedCardColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryCardShape = RoundedCornerShape(16.dp)

@Composable
fun HistoryCard(
    history: RunHistory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColors = elevatedCardColors()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = HistoryCardShape,
        color = cardColors.containerColor,
        contentColor = cardColors.contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = history.ruleName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val noChanges = history.isNoChangesRun()
                val chipStatus = if (history.isEffectivelyUndone()) RunStatus.UNDONE else history.status
                StatusChip(status = chipStatus, noChanges = noChanges)
            }

            Spacer(Modifier.height(6.dp))

            val triggerLabel = when (history.triggeredBy) {
                TriggerType.MANUAL -> "Manual"
                TriggerType.SCHEDULED -> "Scheduled"
            }
            val timeLabel = formatTime(history.startedAt)
            val successPart = if (history.totalFilesMoved > 0) {
                when (history.operationMode) {
                    OperationMode.COPY ->
                        stringResource(R.string.history_files_copied, history.totalFilesMoved)
                    OperationMode.MOVE ->
                        stringResource(R.string.history_files_moved, history.totalFilesMoved)
                }
            } else {
                ""
            }
            val failedPart = if (history.totalFilesFailed > 0) {
                stringResource(R.string.history_files_failed, history.totalFilesFailed)
            } else {
                ""
            }
            val cancelledPart = if (history.cancelledUnprocessedCount > 0) {
                stringResource(R.string.history_files_cancelled_remaining, history.cancelledUnprocessedCount)
            } else {
                ""
            }
            val fileSummary = when {
                history.status == RunStatus.CANCELLED &&
                    history.totalFilesMoved == 0 &&
                    history.totalFilesFailed == 0 &&
                    history.cancelledUnprocessedCount == 0 ->
                    stringResource(R.string.status_cancelled)
                history.totalFilesMoved == 0 && history.totalFilesFailed == 0 && cancelledPart.isEmpty() ->
                    "No files affected"
                else -> buildString {
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
            Text(
                text = "$triggerLabel · $timeLabel · $fileSummary",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusChip(status: RunStatus, noChanges: Boolean = false, modifier: Modifier = Modifier) {
    val (label, targetColor) = when {
        noChanges -> "No changes" to MaterialTheme.colorScheme.surfaceVariant
        else -> when (status) {
            RunStatus.SUCCESS -> "Success" to MaterialTheme.colorScheme.primaryContainer
            RunStatus.PARTIAL_FAILURE -> "Partial" to MaterialTheme.colorScheme.tertiaryContainer
            RunStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.errorContainer
            RunStatus.IN_PROGRESS -> "Running" to MaterialTheme.colorScheme.secondaryContainer
            RunStatus.CANCELLED ->
                stringResource(R.string.status_cancelled) to MaterialTheme.colorScheme.surfaceVariant
            RunStatus.UNDONE ->
                stringResource(R.string.status_undone) to MaterialTheme.colorScheme.surfaceVariant
        }
    }
    val containerColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "chipColor"
    )
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = containerColor)
    )
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

fun formatTime(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return if (diff < 24 * 60 * 60 * 1000L) {
        timeFormat.format(Date(millis))
    } else {
        dateTimeFormat.format(Date(millis))
    }
}
