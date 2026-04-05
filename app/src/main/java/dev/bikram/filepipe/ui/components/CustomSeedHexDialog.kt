package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.bikram.filepipe.R
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import dev.bikram.filepipe.ui.theme.parseSeedColorHexToColorOrNull

@Composable
fun CustomSeedHexDialog(
    initialDraft: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val playTap = rememberPlayTapSound()
    var draftHex by remember(initialDraft) { mutableStateOf(initialDraft) }
    val previewColor = parseSeedColorHexToColorOrNull(draftHex.trim())
    val previewShape = RoundedCornerShape(12.dp)
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_custom_seed_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = stringResource(R.string.settings_custom_seed_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.settings_custom_seed_row_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_custom_seed_preview_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val swatchModifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(previewShape)
                        .then(
                            if (previewColor != null) {
                                Modifier
                                    .background(previewColor)
                                    .border(1.dp, outlineColor, previewShape)
                            } else {
                                Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, outlineColor, previewShape)
                            }
                        )
                    Box(
                        modifier = swatchModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewColor == null && draftHex.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.settings_custom_seed_preview_invalid),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = draftHex,
                    onValueChange = { draftHex = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_custom_seed_label)) },
                    placeholder = { Text(stringResource(R.string.settings_custom_seed_placeholder)) },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        playTap()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = {
                            playTap()
                            onConfirm(draftHex.trim())
                        },
                        enabled = previewColor != null
                    ) {
                        Text(stringResource(R.string.settings_custom_seed_dialog_add))
                    }
                }
            }
        }
    }
}
