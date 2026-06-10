package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileExtensionChips(
    extensions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUseTemplate: () -> Unit,
    modifier: Modifier = Modifier,
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
            FilePipeInputChip(
                selected = true,
                onClick = {
                    onRemove(ext)
                },
                label = { Text(ext.removePrefix(".")) },
                colors =
                    InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                trailingIcon = {
                    FilePipeMaterialRoundedSymbol(
                        name = "close",
                        contentDescription = stringResource(R.string.file_type_remove_content_description, ext),
                        size = InputChipDefaults.AvatarSize,
                        modifier = Modifier.size(InputChipDefaults.AvatarSize),
                    )
                },
            )
        }
        FilePipeOutlinedButton(
            onClick = {
                showAddDialog = true
            },
            shape = FilterChipDefaults.shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            FileTypeActionButtonContent(
                iconName = "add",
                text = addTypeLabel,
            )
        }
        FilePipeOutlinedButton(
            onClick = onUseTemplate,
            shape = FilterChipDefaults.shape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            FileTypeActionButtonContent(
                iconName = "auto_awesome",
                text = useTemplateLabel,
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
private fun FileTypeActionButtonContent(
    iconName: String,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilePipeMaterialRoundedSymbol(
            name = iconName,
            contentDescription = null,
            size = 18.dp,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun AddExtensionDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.file_type_dialog_label)) },
                placeholder = { Text(stringResource(R.string.file_type_dialog_placeholder)) },
                singleLine = true,
            )
        },
        confirmButton = {
            FilePipeTextButton(
                onClick = {
                    val extensions =
                        text
                            .split(Regex("[,;\\s]+"))
                            .map { it.trim().lowercase() }
                            .filter { it.isNotEmpty() }
                            .map { if (it.startsWith(".")) it else ".$it" }
                            .filter { it.length > 1 }
                    extensions.forEach { onAdd(it) }
                },
                enabled = text.isNotBlank(),
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
