package dev.bikram.filepipe.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.bikram.filepipe.R

/**
 * Shared confirmation dialog for the app's two-action confirms.
 *
 * Button emphasis follows one rule: the recommended action is a filled [FilePipeButton] **only when
 * it is safe** ([destructive] = false). Destructive confirms (delete, trash, reset, clear) use a
 * low-emphasis, error-colored text button so the dialog never visually pushes an irreversible
 * action. The dismiss/cancel action is always a plain text button.
 */
@Composable
fun FilePipeConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    dismissLabel: String = stringResource(R.string.cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            if (destructive) {
                FilePipeTextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = MaterialTheme.colorScheme.error)
                }
            } else {
                FilePipeButton(onClick = onConfirm) {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            FilePipeTextButton(onClick = onDismiss) {
                // Neutral, not theme-accented: the dismiss action shouldn't compete with the
                // recommended (filled) or destructive (error) action for the eye.
                Text(dismissLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
