package dev.bikram.filepipe.ui.screens.rules

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.FilePipeButton
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.FilePipeTextButton
import dev.bikram.filepipe.ui.theme.reducedMotionAwareSpec

// The Rules preview sheet's in-place run UI: once Run is tapped the sheet stays open, ticks off its
// file rows as the run reaches them, and ends with Done / View in History instead of navigating away.

/** Indent of a preview file row; it also hosts the row's run-status mark ([PreviewRowStatusSlot]). */
internal val PreviewRowIndent = 16.dp

private val StatusMarkSize = 14.dp

// The slot's measured height. Kept below the file name's line height so the row is always sized by
// its text: the mark glyph (a font character whose line box is taller than the glyph itself) draws
// unbounded around this slot instead of growing the row when it appears.
private val StatusSlotHeight = 12.dp

/**
 * The run-status slot in a preview row's indent: always laid out (empty before the run and for files
 * the run hasn't reached), so neither row height nor text position changes as marks appear.
 */
@Composable
internal fun PreviewRowStatusSlot(status: PreviewFileRunStatus?) {
    Box(
        modifier = Modifier.width(PreviewRowIndent).height(StatusSlotHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (status != null) {
            Box(modifier = Modifier.wrapContentSize(align = Alignment.CenterStart, unbounded = true)) {
                PreviewFileRunStatusMark(status)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreviewFileRunStatusMark(status: PreviewFileRunStatus) {
    when (status) {
        PreviewFileRunStatus.IN_PROGRESS -> {
            // The wavy ring's defaults are tuned for ~48dp; scaled down to the mark, it needs a
            // thinner stroke and a much shorter wavelength for the waves to read at all.
            val stroke = Stroke(width = with(LocalDensity.current) { 1.5.dp.toPx() }, cap = StrokeCap.Round)
            Box(modifier = Modifier.size(StatusMarkSize), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    trackColor = Color.Transparent,
                    stroke = stroke,
                    trackStroke = stroke,
                    wavelength = 4.dp,
                )
            }
        }

        PreviewFileRunStatus.DONE -> {
            FilePipeMaterialRoundedSymbol(
                name = "check_circle",
                contentDescription = stringResource(R.string.status_success),
                size = StatusMarkSize,
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        PreviewFileRunStatus.SKIPPED -> {
            FilePipeMaterialRoundedSymbol(
                name = "do_not_disturb_on",
                contentDescription = stringResource(R.string.history_detail_file_skipped),
                size = StatusMarkSize,
                tint = MaterialTheme.colorScheme.outline,
            )
        }

        PreviewFileRunStatus.FAILED -> {
            FilePipeMaterialRoundedSymbol(
                name = "error",
                contentDescription = stringResource(R.string.status_failed),
                size = StatusMarkSize,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * A rule group's second line once the sheet's run includes it: "6/10 files moved", plus what didn't
 * go through once that's known ("• 1 failed", "• 3 cancelled"), or the rule's error.
 */
@Composable
internal fun previewGroupRunLine(
    group: PreviewRuleGroup,
    run: PreviewSheetRun,
): Pair<String, Color> {
    run.outcome?.ruleErrors?.get(group.ruleId)?.let { error ->
        return stringResource(R.string.rule_card_progress_error, error) to MaterialTheme.colorScheme.error
    }
    val statuses = run.fileStatuses[group.ruleId].orEmpty().values
    val done = statuses.count { status -> status == PreviewFileRunStatus.DONE }
    val failed = statuses.count { status -> status == PreviewFileRunStatus.FAILED }
    val total = group.results.size
    val pluralRes =
        when (group.operationMode) {
            OperationMode.MOVE -> R.plurals.rule_card_progress_files_moved_summary
            OperationMode.COPY -> R.plurals.rule_card_progress_files_copied_summary
            OperationMode.DELETE -> R.plurals.rule_card_progress_files_deleted_summary
        }
    // Files a Cancel stopped the run from reaching (the interrupted file's spinner is already dropped).
    val cancelled = if (run.outcome?.cancelled == true) total - statuses.size else 0
    val suffixes =
        buildList {
            if (failed > 0) add(pluralStringResource(R.plurals.preview_run_status_failed, failed, failed))
            if (cancelled > 0) add(pluralStringResource(R.plurals.preview_run_status_cancelled, cancelled, cancelled))
        }
    var line = pluralStringResource(pluralRes, total, done, total)
    for (suffix in suffixes) {
        line = stringResource(R.string.preview_run_line_with_status, line, suffix)
    }
    return line to if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
}

/**
 * Replaces the sheet's Cancel/Run row once the run starts: overall progress with Cancel while it
 * runs, then Done and View in History (how it went is on each rule's line above).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PreviewSheetRunFooter(
    run: PreviewSheetRun,
    ruleGroups: List<PreviewRuleGroup>,
    onCancelRun: () -> Unit,
    onDone: () -> Unit,
    onViewHistory: (List<Long>) -> Unit,
) {
    val outcome = run.outcome
    if (outcome == null) {
        val runGroups = ruleGroups.filter { group -> group.ruleId in run.ruleIds }
        val total = runGroups.sumOf { group -> group.results.size }
        val processed =
            runGroups.sumOf { group ->
                run.fileStatuses[group.ruleId].orEmpty().values.count { status ->
                    status != PreviewFileRunStatus.IN_PROGRESS
                }
            }
        val progress by animateFloatAsState(
            targetValue = if (total > 0) processed.toFloat() / total else 0f,
            animationSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
            label = "previewSheetRunProgress",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f).height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
            )
            Box(Modifier.width(16.dp))
            FilePipeOutlinedButton(onClick = onCancelRun) {
                Text(stringResource(R.string.cancel))
            }
        }
    } else {
        // Same end-aligned shape as the Cancel/Run row it replaces.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilePipeTextButton(onClick = onDone) {
                Text(stringResource(R.string.preview_run_done))
            }
            FilePipeButton(onClick = { onViewHistory(outcome.historyIds) }) {
                Text(stringResource(R.string.preview_run_view_in_history))
            }
        }
    }
}
