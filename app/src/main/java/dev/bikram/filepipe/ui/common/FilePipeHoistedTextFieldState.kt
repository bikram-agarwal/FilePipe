package dev.bikram.filepipe.ui.common

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun rememberHoistedStringTextFieldState(
    text: String,
    onTextChange: (String) -> Unit,
): TextFieldState {
    val textFieldState = rememberTextFieldState(initialText = text)
    val currentText by rememberUpdatedState(text)
    val currentOnTextChange by rememberUpdatedState(onTextChange)
    LaunchedEffect(text) {
        val fieldText = textFieldState.text.toString()
        if (fieldText != text) {
            textFieldState.edit {
                replace(0, length, text)
                selection = TextRange(text.length)
            }
            // Every edit{} call records an undo entry, even this programmatic external-state sync
            // (as opposed to the user's own keystrokes, which land here as a no-op since the field
            // already has them). Android's back gesture can trigger the field's *own* undo before
            // navigating away when undo history is pending, which would otherwise let "back" revert
            // whatever caused this sync (e.g. an external state reset) instead of leaving the screen.
            textFieldState.undoState.clearHistory()
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { updatedText ->
                if (updatedText != currentText) {
                    currentOnTextChange(updatedText)
                }
            }
    }
    return textFieldState
}
