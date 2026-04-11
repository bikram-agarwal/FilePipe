package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound

private data class ExtensionGroup(val label: String, val extensions: List<String>)

private val EXTENSION_GROUPS = listOf(
    ExtensionGroup("Images", listOf("jpg", "jpeg", "png", "gif", "heic", "webp", "bmp")),
    ExtensionGroup("Videos", listOf("mp4", "mkv", "avi", "mov", "m4v", "webm")),
    ExtensionGroup("Audio", listOf("mp3", "flac", "aac", "ogg", "m4a", "wav")),
    ExtensionGroup("Documents", listOf("pdf", "docx", "xlsx", "pptx", "txt", "odt")),
    ExtensionGroup("Installables", listOf("apk", "apkm", "xapk", "zip")),
    ExtensionGroup("Archives", listOf("zip", "rar", "7z", "tar", "gz")),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileExtensionChips(
    extensions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddGroup: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val playTap = rememberPlayTapSound()
    var showAddDialog by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        extensions.forEach { ext ->
            InputChip(
                selected = true,
                onClick = {
                    playTap()
                    onRemove(ext)
                },
                label = { Text(ext) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove $ext",
                        modifier = Modifier.size(InputChipDefaults.AvatarSize)
                    )
                }
            )
        }
        FilterChip(
            selected = false,
            onClick = {
                playTap()
                showAddDialog = true
            },
            label = { Text("Add type") },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add file type",
                    modifier = Modifier.size(InputChipDefaults.AvatarSize)
                )
            }
        )
        FilterChip(
            selected = false,
            onClick = {
                playTap()
                showGroupMenu = true
            },
            label = { Text("Add group") },
            leadingIcon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add extension group",
                    modifier = Modifier.size(InputChipDefaults.AvatarSize)
                )
            }
        )
        DropdownMenu(
            expanded = showGroupMenu,
            onDismissRequest = { showGroupMenu = false }
        ) {
            EXTENSION_GROUPS.forEachIndexed { index, group ->
                DropdownMenuItem(
                    text = {
                        Text("${group.label}  (${group.extensions.joinToString(", ") { ".$it" }})")
                    },
                    onClick = {
                        playTap()
                        onAddGroup(group.extensions)
                        showGroupMenu = false
                    }
                )
                if (index < EXTENSION_GROUPS.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        AddExtensionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { ext ->
                onAdd(ext)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddExtensionDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    val playTap = rememberPlayTapSound()
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add file type") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Extension (e.g. .jpg, mp4)") },
                singleLine = true,
                placeholder = { Text(".jpg") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    playTap()
                    val ext = text.trim().let {
                        if (it.startsWith(".")) it else ".$it"
                    }.lowercase()
                    if (ext.length > 1) onAdd(ext)
                },
                enabled = text.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                playTap()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}
