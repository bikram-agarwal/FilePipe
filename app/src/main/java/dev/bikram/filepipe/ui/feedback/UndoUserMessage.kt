package dev.bikram.filepipe.ui.feedback

import android.content.Context
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.usecase.UndoResult

fun UndoResult.toUserMessage(context: Context): String = when {
    totalReversed == 0 ->
        context.getString(
            R.string.undo_failed_prefix,
            errors.firstOrNull() ?: context.getString(R.string.undo_unknown_error)
        )
    totalFailed == 0 -> when (operationMode) {
        OperationMode.COPY ->
            context.getString(R.string.undo_success_deleted_destination, totalReversed)
        OperationMode.MOVE ->
            context.getString(R.string.undo_success_restored, totalReversed)
    }
    else -> when (operationMode) {
        OperationMode.COPY ->
            context.getString(R.string.undo_partial_deleted_destination, totalReversed, totalFailed)
        OperationMode.MOVE ->
            context.getString(R.string.undo_partial_restored, totalReversed, totalFailed)
    }
}
