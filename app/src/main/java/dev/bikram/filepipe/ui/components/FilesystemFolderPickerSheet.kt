package dev.bikram.filepipe.ui.components

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathAllowedForRules
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.common.isLandscape
import dev.bikram.filepipe.ui.feedback.tapSoundClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun validationMessageForNewFolderName(trimmedName: String): Int? {
    if (trimmedName.isEmpty()) return R.string.filesystem_folder_picker_new_folder_error_empty
    if (trimmedName == "." || trimmedName == "..") return R.string.filesystem_folder_picker_new_folder_error_invalid
    if (trimmedName.contains('/') || trimmedName.contains('\\')) {
        return R.string.filesystem_folder_picker_new_folder_error_invalid
    }
    if (trimmedName.any { character -> character.code < 32 }) {
        return R.string.filesystem_folder_picker_new_folder_error_invalid
    }
    return null
}

private data class BreadcrumbSegment(
    val label: String,
    val path: String,
)

private data class FolderPickerDirectoryEntry(
    val name: String,
    val path: String,
    val listKey: String,
)

private fun folderPickerDirectoryEntry(file: File): FolderPickerDirectoryEntry? {
    if (!file.isDirectory || !file.canRead() || file.name.startsWith(".")) return null
    val resolvedPath =
        runCatching { file.canonicalFile.absolutePath }
            .getOrElse { file.absolutePath }
    val normalized = normalizeFilesystemFolderPath(resolvedPath) ?: return null
    if (!isFilesystemFolderPathAllowedForRules(normalized)) return null
    return FolderPickerDirectoryEntry(
        name = file.name,
        path = normalized,
        listKey = normalized,
    )
}

private fun buildBreadcrumbSegments(
    canonicalPath: String,
    primaryRoot: String,
    internalStorageLabel: String,
    sdCardLabel: String,
): List<BreadcrumbSegment> {
    val normalized = normalizeFilesystemFolderPath(canonicalPath) ?: return emptyList()
    val primary = primaryRoot.trimEnd('/')

    if (normalized == primary || normalized.startsWith(primary + File.separator)) {
        val tail = if (normalized == primary) "" else normalized.removePrefix(primary + File.separator)
        val parts = if (tail.isEmpty()) emptyList() else tail.split('/').filter { it.isNotEmpty() }
        val out = ArrayList<BreadcrumbSegment>()
        out.add(BreadcrumbSegment(internalStorageLabel, primary))
        var accumulated = primary
        for (part in parts) {
            accumulated = accumulated + File.separator + part
            out.add(BreadcrumbSegment(part, accumulated))
        }
        return out
    }

    val sdMatch =
        Regex("^(/storage/[0-9A-F]{4}-[0-9A-F]{4})(?:/(.*))?$", RegexOption.IGNORE_CASE).find(normalized)
    if (sdMatch != null) {
        val sdRoot = sdMatch.groupValues[1].trimEnd('/')
        val rest = sdMatch.groupValues[2].orEmpty().trim('/')
        val parts = if (rest.isEmpty()) emptyList() else rest.split('/').filter { it.isNotEmpty() }
        val out = ArrayList<BreadcrumbSegment>()
        out.add(BreadcrumbSegment(sdCardLabel, sdRoot))
        var accumulated = sdRoot
        for (part in parts) {
            accumulated = accumulated + File.separator + part
            out.add(BreadcrumbSegment(part, accumulated))
        }
        return out
    }

    val fallbackLabel = normalized.substringAfterLast(File.separator).ifEmpty { normalized }
    return listOf(BreadcrumbSegment(fallbackLabel, normalized))
}

/**
 * Bottom sheet body for choosing a folder using direct filesystem paths (requires All files access).
 * Does not use [androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree].
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FilesystemFolderPickerSheetContent(
    initialDirectory: String,
    onDismiss: () -> Unit,
    onFolderChosen: (normalizedAbsolutePath: String) -> Unit,
) {
    val internalStorageLabel = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val sdCardLabel = stringResource(R.string.filesystem_folder_picker_sd_card)
    val primaryRoot =
        remember {
            runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrNull()
                ?: "/storage/emulated/0"
        }

    val startPath =
        remember(initialDirectory) {
            normalizeFilesystemFolderPath(initialDirectory)
                ?.takeIf { path ->
                    val file = File(path)
                    file.isDirectory && file.canRead()
                }
                ?: runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrNull()
                ?: "/storage/emulated/0"
        }
    var currentPath by remember(startPath) { mutableStateOf(startPath) }
    var childListRefreshKey by remember(startPath) { mutableIntStateOf(0) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var newFolderDialogErrorResId by remember { mutableStateOf<Int?>(null) }

    val breadcrumbSegments =
        remember(currentPath, primaryRoot, internalStorageLabel, sdCardLabel) {
            buildBreadcrumbSegments(currentPath, primaryRoot, internalStorageLabel, sdCardLabel)
        }

    var childDirectories by remember(currentPath, childListRefreshKey) {
        mutableStateOf<List<FolderPickerDirectoryEntry>>(emptyList())
    }
    LaunchedEffect(currentPath, childListRefreshKey) {
        childDirectories =
            withContext(Dispatchers.IO) {
                File(currentPath)
                    .listFiles()
                    ?.mapNotNull(::folderPickerDirectoryEntry)
                    ?.distinctBy { entry -> entry.listKey }
                    ?.sortedBy { entry -> entry.name.lowercase() }
                    ?: emptyList()
            }
    }

    val canConfirmSelection =
        remember(currentPath) {
            val normalized = normalizeFilesystemFolderPath(currentPath)
            normalized != null &&
                isFilesystemFolderPathAllowedForRules(normalized) &&
                File(normalized).isDirectory &&
                File(normalized).canRead()
        }

    val canCreateSubfolder =
        remember(currentPath) {
            val normalized = normalizeFilesystemFolderPath(currentPath) ?: return@remember false
            if (!isFilesystemFolderPathAllowedForRules(normalized)) return@remember false
            val dir = File(normalized)
            dir.isDirectory && dir.canRead() && dir.canWrite()
        }

    val breadcrumbScroll = rememberScrollState()

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewFolderDialog = false
                newFolderNameInput = ""
                newFolderDialogErrorResId = null
            },
            title = { Text(stringResource(R.string.filesystem_folder_picker_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { typed ->
                        newFolderNameInput = typed
                        newFolderDialogErrorResId = null
                    },
                    label = { Text(stringResource(R.string.filesystem_folder_picker_new_folder_name_label)) },
                    singleLine = true,
                    isError = newFolderDialogErrorResId != null,
                    supportingText =
                        if (newFolderDialogErrorResId != null) {
                            { Text(stringResource(newFolderDialogErrorResId!!)) }
                        } else {
                            null
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                FilePipeTextButton(
                    onClick = {
                        val trimmed = newFolderNameInput.trim()
                        val validationError = validationMessageForNewFolderName(trimmed)
                        if (validationError != null) {
                            newFolderDialogErrorResId = validationError
                            return@FilePipeTextButton
                        }
                        val parentNormalized = normalizeFilesystemFolderPath(currentPath)
                        if (parentNormalized == null ||
                            !isFilesystemFolderPathAllowedForRules(parentNormalized)
                        ) {
                            newFolderDialogErrorResId =
                                R.string.filesystem_folder_picker_new_folder_error_cannot_write
                            return@FilePipeTextButton
                        }
                        val parentFile = File(parentNormalized)
                        if (!parentFile.isDirectory || !parentFile.canWrite()) {
                            newFolderDialogErrorResId =
                                R.string.filesystem_folder_picker_new_folder_error_cannot_write
                            return@FilePipeTextButton
                        }
                        val newFolder = File(parentFile, trimmed)
                        if (newFolder.exists()) {
                            newFolderDialogErrorResId =
                                R.string.filesystem_folder_picker_new_folder_error_exists
                            return@FilePipeTextButton
                        }
                        val created = newFolder.mkdir()
                        if (!created) {
                            newFolderDialogErrorResId =
                                R.string.filesystem_folder_picker_new_folder_error_failed
                            return@FilePipeTextButton
                        }
                        val canonicalNew = runCatching { newFolder.canonicalFile.absolutePath }.getOrNull()
                        val normalizedNew = canonicalNew?.let { normalizeFilesystemFolderPath(it) }
                        if (normalizedNew == null ||
                            !isFilesystemFolderPathAllowedForRules(normalizedNew)
                        ) {
                            newFolderDialogErrorResId =
                                R.string.filesystem_folder_picker_new_folder_error_failed
                            return@FilePipeTextButton
                        }
                        showNewFolderDialog = false
                        newFolderNameInput = ""
                        newFolderDialogErrorResId = null
                        childListRefreshKey++
                        currentPath = normalizedNew
                    },
                ) {
                    Text(stringResource(R.string.filesystem_folder_picker_new_folder_create))
                }
            },
            dismissButton = {
                FilePipeTextButton(
                    onClick = {
                        showNewFolderDialog = false
                        newFolderNameInput = ""
                        newFolderDialogErrorResId = null
                    },
                ) {
                    Text(stringResource(R.string.filesystem_folder_picker_cancel))
                }
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(breadcrumbScroll)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            breadcrumbSegments.forEachIndexed { segmentIndex, segment ->
                if (segmentIndex > 0) {
                    Text(
                        text = "  >  ",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.tapSoundClickable {
                            val target = normalizeFilesystemFolderPath(segment.path) ?: return@tapSoundClickable
                            if (File(target).isDirectory &&
                                File(target).canRead() &&
                                isFilesystemFolderPathAllowedForRules(target)
                            ) {
                                currentPath = target
                            }
                        },
                )
            }
        }
        HorizontalDivider()
        Box(
            modifier =
                Modifier
                    .weight(1f, fill = false),
        ) {
            if (childDirectories.isEmpty()) {
                Text(
                    text = stringResource(R.string.filesystem_folder_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                ) {
                    items(childDirectories, key = { it.listKey }) { entry ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = {
                                Text(
                                    text = entry.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = {
                                FilePipeMaterialRoundedSymbol(
                                    name = "folder",
                                    contentDescription = null,
                                    size = 24.dp,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier =
                                Modifier.tapSoundClickable {
                                    currentPath = entry.path
                                },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val fontScale = LocalDensity.current.fontScale
        val isLandscape = isLandscape()
        val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
        val isTwoPane = calculatePaneScaffoldDirective(windowAdaptiveInfo).maxHorizontalPartitions > 1
        if (fontScale > 1.15f) {
            if (isLandscape || isTwoPane) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilePipeTextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight),
                    ) {
                        Text(
                            text = stringResource(R.string.filesystem_folder_picker_cancel),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilePipeOutlinedButton(
                        onClick = {
                            newFolderNameInput = ""
                            newFolderDialogErrorResId = null
                            showNewFolderDialog = true
                        },
                        enabled = canCreateSubfolder,
                        modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight),
                    ) {
                        Text(
                            text = stringResource(R.string.filesystem_folder_picker_new_folder),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FilePipeButton(
                        onClick = {
                            val normalized = normalizeFilesystemFolderPath(currentPath) ?: return@FilePipeButton
                            if (File(normalized).isDirectory &&
                                File(normalized).canRead() &&
                                isFilesystemFolderPathAllowedForRules(normalized)
                            ) {
                                onFolderChosen(normalized)
                            }
                        },
                        enabled = canConfirmSelection,
                        modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight),
                    ) {
                        Text(
                            text = stringResource(R.string.filesystem_folder_picker_use_folder),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FilePipeButton(
                        onClick = {
                            val normalized = normalizeFilesystemFolderPath(currentPath) ?: return@FilePipeButton
                            if (File(normalized).isDirectory &&
                                File(normalized).canRead() &&
                                isFilesystemFolderPathAllowedForRules(normalized)
                            ) {
                                onFolderChosen(normalized)
                            }
                        },
                        enabled = canConfirmSelection,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonDefaults.MinHeight),
                    ) {
                        Text(
                            text = stringResource(R.string.filesystem_folder_picker_use_folder),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    FilePipeOutlinedButton(
                        onClick = {
                            newFolderNameInput = ""
                            newFolderDialogErrorResId = null
                            showNewFolderDialog = true
                        },
                        enabled = canCreateSubfolder,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonDefaults.MinHeight),
                    ) {
                        Text(
                            text = stringResource(R.string.filesystem_folder_picker_new_folder),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    FilePipeTextButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonDefaults.MinHeight),
                    ) {
                        Text(stringResource(R.string.filesystem_folder_picker_cancel))
                    }
                }
            }
        } else {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilePipeTextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.filesystem_folder_picker_cancel))
                }
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilePipeOutlinedButton(
                        onClick = {
                            newFolderNameInput = ""
                            newFolderDialogErrorResId = null
                            showNewFolderDialog = true
                        },
                        enabled = canCreateSubfolder,
                        modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight),
                    ) {
                        Text(
                            text = stringResource(R.string.filesystem_folder_picker_new_folder),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    FilePipeButton(
                        onClick = {
                            val normalized = normalizeFilesystemFolderPath(currentPath) ?: return@FilePipeButton
                            if (File(normalized).isDirectory &&
                                File(normalized).canRead() &&
                                isFilesystemFolderPathAllowedForRules(normalized)
                            ) {
                                onFolderChosen(normalized)
                            }
                        },
                        enabled = canConfirmSelection,
                        modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight),
                    ) {
                        Text(
                            text = stringResource(R.string.filesystem_folder_picker_use_folder),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
