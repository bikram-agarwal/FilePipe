package dev.bikram.filepipe.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.bikram.filepipe.R
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.components.FilePipeDropdownMenuItem
import dev.bikram.filepipe.ui.components.FilePipeIconButton
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.displayPath
import dev.bikram.filepipe.ui.feedback.appCombinedClickable

@Composable
internal fun BackupFolderPickerItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .appCombinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        FilePipeOutlinedButton(
            onClick = {
                onClick()
            },
        ) {
            FilePipeMaterialRoundedSymbol(
                name = "folder_open",
                contentDescription = null,
                size = 18.dp,
            )
        }
    }
}

internal val LOG_RETENTION_OPTIONS = listOf(7, 14, 30, 90, -1)

internal fun backupDestinationDisplayLabel(
    context: Context,
    uriString: String,
    internalStorageRootDisplayName: String,
): String {
    if (uriString.isBlank()) return ""
    val uri = uriString.toUri()
    if (!DocumentsContract.isTreeUri(uri)) {
        providerDisplayName(context, uri.authority)?.let { return it }
    }
    val providerName =
        providerDisplayName(context, uri.authority)
            ?.takeUnless { displayName -> displayName == uri.authority }
    val relativeTreePath =
        runCatching {
            val treeId = DocumentsContract.getTreeDocumentId(uri)
            val decodedTreeId = Uri.decode(treeId)
            decodedTreeId.substringAfter(':', decodedTreeId)
        }.getOrNull()
    val documentName = DocumentFile.fromTreeUri(context, uri)?.name
    val folderLabel =
        if (providerName != null) {
            documentName?.takeIf { it.isNotBlank() }
                ?: relativeTreePath?.takeIf { it.isNotBlank() }
        } else {
            relativeTreePath?.takeIf { it.isNotBlank() }
                ?: documentName?.takeIf { it.isNotBlank() }
        } ?: displayPath(uriString, internalStorageRootDisplayName)
    return if (providerName != null && !folderLabel.equals(providerName, ignoreCase = true)) {
        context.getString(R.string.cloud_provider_folder_path, providerName, folderLabel)
    } else {
        folderLabel
    }
}

internal fun providerDisplayName(
    context: Context,
    authority: String?,
): String? {
    val providerAuthority = authority?.takeIf { it.isNotBlank() } ?: return null
    val normalizedAuthority = providerAuthority.lowercase()
    return when {
        normalizedAuthority.contains("google.android.apps.docs") -> {
            context.getString(R.string.cloud_provider_google_drive)
        }

        normalizedAuthority.contains("skydrive") || normalizedAuthority.contains("onedrive") -> {
            context.getString(R.string.cloud_provider_onedrive)
        }

        normalizedAuthority.contains("dropbox") -> {
            context.getString(R.string.cloud_provider_dropbox)
        }

        normalizedAuthority.contains("box.android") -> {
            context.getString(R.string.cloud_provider_box)
        }

        else -> {
            providerAuthority
        }
    }
}

@Composable
internal fun logRetentionLabel(days: Int): String =
    when (days) {
        7 -> stringResource(R.string.log_retention_7_days)
        14 -> stringResource(R.string.log_retention_14_days)
        30 -> stringResource(R.string.log_retention_30_days)
        90 -> stringResource(R.string.log_retention_90_days)
        else -> stringResource(R.string.log_retention_never)
    }

@Composable
internal fun LogRetentionDropdown(
    currentDays: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    FilePipeOutlinedButton(onClick = { expanded = true }) {
        Text(logRetentionLabel(currentDays))
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            LOG_RETENTION_OPTIONS.forEach { option ->
                FilePipeDropdownMenuItem(
                    text = { Text(logRetentionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun SettingsInfoDropdown(
    tipText: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bulletItems: List<String> = emptyList(),
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilePipeIconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(32.dp),
        ) {
            FilePipeMaterialRoundedSymbol(
                name = "info",
                size = 20.dp,
                tint = iconTint,
                weight = FontWeight.Medium,
                filled = false,
                modifier = Modifier.semantics { this.contentDescription = contentDescription },
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(max = 260.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 236.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                bulletItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "\u2022",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
