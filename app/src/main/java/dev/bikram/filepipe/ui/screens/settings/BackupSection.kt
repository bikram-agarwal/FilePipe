package dev.bikram.filepipe.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.domain.usecase.BackupImportPickAction
import dev.bikram.filepipe.ui.common.ResponsiveActionLayout
import dev.bikram.filepipe.ui.common.responsiveActionLayout
import dev.bikram.filepipe.ui.components.FilePipeActionLabel
import dev.bikram.filepipe.ui.components.FilePipeOutlinedButton
import dev.bikram.filepipe.ui.components.containers.GroupPosition
import dev.bikram.filepipe.ui.components.containers.GroupedListColumn
import dev.bikram.filepipe.ui.components.containers.GroupedListItem
import dev.bikram.filepipe.ui.components.displayPath
import dev.bikram.filepipe.ui.feedback.appClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun BackupSection(
    preferences: AppPreferences,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onPickLocalFolder: () -> Unit,
    onPickCloudFolder: () -> Unit,
    onLaunchImportMerge: () -> Unit,
    onLaunchImportReplace: () -> Unit,
    onClearLocalFolder: () -> Unit,
    onClearCloudFolder: () -> Unit,
    onAutoExportChange: (Boolean) -> Unit,
    onScheduledExportChange: (Boolean) -> Unit,
    onExportNow: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val internalStorageDisplayName = stringResource(R.string.filesystem_folder_picker_internal_storage)
    val sdCardDisplayName = stringResource(R.string.filesystem_folder_picker_sd_card)
    val localFolderLabel =
        preferences.exportFolderUri
            .takeIf { it.isNotBlank() }
            ?.let { displayPath(it, internalStorageDisplayName, sdCardDisplayName) }
            ?: stringResource(R.string.settings_choose_local_backup_folder)
    val cloudFolderLabel =
        preferences.cloudExportFolderUri
            .takeIf { it.isNotBlank() }
            ?.let { backupDestinationDisplayLabel(context, it, internalStorageDisplayName) }
            ?: stringResource(R.string.settings_choose_cloud_backup_file)

    val exportFolderReady =
        preferences.exportFolderUri.isNotBlank() ||
            preferences.cloudExportFolderUri.isNotBlank()
    val autoExportSwitchEnabled = exportFolderReady || preferences.autoExportOnRuleChange
    val scheduledExportSwitchEnabled = exportFolderReady || preferences.scheduledExportEnabled

    GroupedListColumn {
        GroupedListItem(position = GroupPosition.FIRST) {
            BackupFolderPickerItem(
                title = localFolderLabel,
                subtitle = stringResource(R.string.settings_local_backup_folder_hint),
                onClick = onPickLocalFolder,
                onLongClick = {
                    if (preferences.exportFolderUri.isNotBlank()) {
                        onClearLocalFolder()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.settings_local_backup_folder_cleared),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            BackupFolderPickerItem(
                title = cloudFolderLabel,
                subtitle = stringResource(R.string.settings_cloud_backup_folder_hint),
                onClick = onPickCloudFolder,
                onLongClick = {
                    if (preferences.cloudExportFolderUri.isNotBlank()) {
                        onClearCloudFolder()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = resources.getString(R.string.settings_cloud_backup_file_cleared),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_auto_export_on_change),
                subtitle = stringResource(R.string.settings_auto_export_on_change_hint),
                checked = preferences.autoExportOnRuleChange,
                switchEnabled = autoExportSwitchEnabled,
                onDisabledInteraction = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.settings_export_select_folder_first),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onCheckedChange = onAutoExportChange,
            )
        }
        GroupedListItem(position = GroupPosition.MIDDLE) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_scheduled_export),
                subtitle = stringResource(R.string.settings_scheduled_export_hint),
                checked = preferences.scheduledExportEnabled,
                switchEnabled = scheduledExportSwitchEnabled,
                onDisabledInteraction = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.settings_export_select_folder_first),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onCheckedChange = onScheduledExportChange,
            )
        }
        GroupedListItem(position = GroupPosition.LAST) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val stacked =
                        responsiveActionLayout(
                            availableWidth = maxWidth,
                            effectiveFontScale = LocalDensity.current.fontScale,
                            itemCount = 2,
                        ) == ResponsiveActionLayout.STACKED
                    if (stacked) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilePipeOutlinedButton(
                                onClick = onLaunchImportMerge,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                FilePipeActionLabel(stringResource(R.string.settings_import_rules))
                            }
                            FilePipeOutlinedButton(
                                onClick = {
                                    if (exportFolderReady) {
                                        onExportNow()
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.settings_export_select_folder_first),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                FilePipeActionLabel(stringResource(R.string.settings_export_now))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilePipeOutlinedButton(
                                onClick = onLaunchImportMerge,
                                modifier = Modifier.weight(1f),
                            ) {
                                FilePipeActionLabel(stringResource(R.string.settings_import_rules))
                            }
                            FilePipeOutlinedButton(
                                onClick = {
                                    if (exportFolderReady) {
                                        onExportNow()
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(R.string.settings_export_select_folder_first),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                FilePipeActionLabel(stringResource(R.string.settings_export_now))
                            }
                        }
                    }
                }
                val restoreOutline = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                val restoreLabelColor = MaterialTheme.colorScheme.error
                val restoreButtonShape = ButtonDefaults.outlinedShape
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(restoreButtonShape)
                            .border(BorderStroke(1.dp, restoreOutline), restoreButtonShape),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .appClickable(onClick = onLaunchImportReplace),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_restore_backup),
                            style = MaterialTheme.typography.labelLarge,
                            color = restoreLabelColor,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SettingsInfoDropdown(
                            title = stringResource(R.string.settings_backup_import_restore_help_title),
                            tipText = stringResource(R.string.settings_backup_import_restore_help_body),
                            contentDescription = stringResource(R.string.settings_backup_help_icon_cd),
                            iconTint = restoreLabelColor.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}
