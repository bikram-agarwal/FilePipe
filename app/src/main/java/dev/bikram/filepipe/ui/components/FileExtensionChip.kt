package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.formatExtensionLabel
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileExtensionChips(
    extensions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUseTemplate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val addTypeLabel = stringResource(R.string.file_type_add_type)
    val useTemplateLabel = stringResource(R.string.file_type_use_template)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        extensions.forEach { ext ->
            val labelText =
                formatExtensionLabel(
                    ext = ext,
                    allFilesLabel = stringResource(R.string.file_type_all_files),
                    noExtensionLabel = stringResource(R.string.file_type_no_extension),
                )
            FilePipeInputChip(
                selected = true,
                onClick = {
                    if (enabled) {
                        onRemove(ext)
                    }
                },
                enabled = enabled,
                label = { Text(labelText) },
                colors =
                    InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                trailingIcon =
                    if (enabled) {
                        {
                            FilePipeMaterialRoundedSymbol(
                                name = "close",
                                contentDescription = stringResource(R.string.file_type_remove_content_description, labelText),
                                size = InputChipDefaults.AvatarSize,
                                modifier = Modifier.size(InputChipDefaults.AvatarSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
        if (enabled) {
            FilePipeFilterChip(
                selected = false,
                onClick = { showAddDialog = true },
                leadingIcon = {
                    Box(
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        FilePipeMaterialRoundedSymbol(
                            name = "add",
                            contentDescription = null,
                            size = FilterChipDefaults.IconSize,
                            opticalCenterYOffset = (-1).dp,
                        )
                    }
                },
                label = { Text(addTypeLabel) },
            )
            FilePipeFilterChip(
                selected = false,
                onClick = onUseTemplate,
                leadingIcon = {
                    Box(
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        FilePipeMaterialRoundedSymbol(
                            name = "auto_awesome",
                            contentDescription = null,
                            size = FilterChipDefaults.IconSize,
                            opticalCenterYOffset = (-1).dp,
                        )
                    }
                },
                label = { Text(useTemplateLabel) },
            )
        }
    }

    if (showAddDialog) {
        AddExtensionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { ext ->
                onAdd(ext)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddExtensionDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val textFieldState = rememberTextFieldState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.file_type_dialog_title))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.file_type_dialog_supporting_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    state = textFieldState,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.file_type_dialog_label)) },
                    placeholder = { Text(stringResource(R.string.file_type_dialog_placeholder)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilePipeFilterChip(
                        selected = false,
                        onClick = {
                            onAdd(dev.bikram.filepipe.domain.model.ALL_FILES_EXTENSION)
                        },
                        label = { Text(stringResource(R.string.file_type_all_files)) },
                    )
                    FilePipeFilterChip(
                        selected = false,
                        onClick = {
                            onAdd(dev.bikram.filepipe.domain.model.NO_EXTENSION_TOKEN)
                        },
                        label = { Text(stringResource(R.string.file_type_no_extension)) },
                    )
                }
            }
        },
        confirmButton = {
            FilePipeTextButton(
                onClick = {
                    val extensions =
                        textFieldState.text
                            .toString()
                            .split(Regex("[,;\\s]+"))
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { ext ->
                                when {
                                    dev.bikram.filepipe.domain.model
                                        .isAllFilesExtension(ext) -> dev.bikram.filepipe.domain.model.ALL_FILES_EXTENSION

                                    dev.bikram.filepipe.domain.model
                                        .isNoExtensionToken(ext) -> dev.bikram.filepipe.domain.model.NO_EXTENSION_TOKEN

                                    else -> ext.lowercase().let { if (it.startsWith(".")) it else ".$it" }
                                }
                            }
                    extensions.forEach { onAdd(it) }
                },
                enabled = textFieldState.text.isNotBlank(),
            ) {
                Text(stringResource(R.string.file_type_dialog_add))
            }
        },
        dismissButton = {
            FilePipeTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
