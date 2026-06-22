package dev.bikram.filepipe.ui.screens.devoptions

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.APP_DATABASE_NAME
import dev.bikram.filepipe.AppDatabase
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.MainActivity
import dev.bikram.filepipe.R
import dev.bikram.filepipe.data.local.dao.FileMovedDao
import dev.bikram.filepipe.data.local.dao.RuleDao
import dev.bikram.filepipe.data.local.dao.RunHistoryDao
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.devtools.DevMockFileMove
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.diagnostics.DiagnosticLog
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.usecase.ScheduleRulesUseCase
import dev.bikram.filepipe.shortcuts.PendingShortcutRepository
import dev.bikram.filepipe.update.FILEPIPE_UPDATE_APK_CACHE_NAME
import dev.bikram.filepipe.update.UpdateAvailableNotifier
import dev.bikram.filepipe.update.UpdateCheckWorkScheduler
import dev.bikram.filepipe.update.UpdateInfo
import dev.bikram.filepipe.worker.FileOrganizerWorker
import dev.bikram.filepipe.worker.RunNotificationChannels
import dev.bikram.filepipe.worker.LogPruneWorker
import dev.bikram.filepipe.worker.RunNotificationChannels
import dev.bikram.filepipe.worker.ScheduledRulesExportWorker
import dev.bikram.filepipe.worker.UpdateCheckWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class DevOptionsInfoRow(
    @param:StringRes val labelRes: Int,
    val value: String,
)

data class DevRuleFolderAccessUiItem(
    val label: String,
    val uri: String,
    val accessTypeLabel: String,
    val accessLabel: String,
)

data class DevRuleAccessUiItem(
    val ruleName: String,
    val folderAccesses: List<DevRuleFolderAccessUiItem>,
)

data class DevFolderAccessUiState(
    val rules: List<DevRuleAccessUiItem> = emptyList(),
    val unassociatedGrants: List<DevRuleFolderAccessUiItem> = emptyList(),
)

data class DevOptionsUiState(
    val loading: Boolean = true,
    val developerOptionsEnabled: Boolean = false,
    val preferences: AppPreferences = AppPreferences.DEFAULT,
    val overview: List<DevOptionsInfoRow> = emptyList(),
    val permissionsAndStorage: List<DevOptionsInfoRow> = emptyList(),
    val folderAccess: DevFolderAccessUiState = DevFolderAccessUiState(),
    val database: List<DevOptionsInfoRow> = emptyList(),
    val workers: List<DevOptionsInfoRow> = emptyList(),
    val showUpdates: Boolean = false,
    val usePlayInAppUpdates: Boolean = false,
    val isGithubFlavor: Boolean = false,
)

@HiltViewModel
class DevOptionsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val appDatabase: AppDatabase,
        private val ruleDao: RuleDao,
        private val runHistoryDao: RunHistoryDao,
        private val fileMovedDao: FileMovedDao,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val ruleRepository: RuleRepository,
        private val scheduleRulesUseCase: ScheduleRulesUseCase,
        private val updateCheckWorkScheduler: UpdateCheckWorkScheduler,
        private val updateAvailableNotifier: UpdateAvailableNotifier,
        private val workManager: WorkManager,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DevOptionsUiState())
        val uiState: StateFlow<DevOptionsUiState> = _uiState.asStateFlow()

        private val _events =
            MutableSharedFlow<String>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val events: SharedFlow<String> = _events.asSharedFlow()

        init {
            viewModelScope.launch {
                userPreferencesRepository.developerOptionsEnabledFlow.collect { enabled ->
                    _uiState.update { it.copy(developerOptionsEnabled = enabled) }
                }
            }
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true) }
                val snapshot =
                    withContext(ioDispatcher) {
                        buildSnapshot()
                    }
                _uiState.value = snapshot
            }
        }

        fun openAppDetails() {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                },
            )
        }

        fun openNotificationSettings() {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                },
            )
        }

        fun openManageAllFilesAccessSettings() {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                },
            )
        }

        fun openBatteryOptimizationSettings() {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        fun syncScheduledRules() {
            viewModelScope.launch {
                val rules = withContext(ioDispatcher) { ruleRepository.getAllRulesOrderedBySortOrder() }
                scheduleRulesUseCase.scheduleCoalesced(rules)
                _events.emit(context.getString(R.string.dev_options_event_scheduled_rules_synced))
                refresh()
            }
        }

        fun syncUpdateCheckWorker() {
            viewModelScope.launch {
                updateCheckWorkScheduler.syncFromPreferences()
                _events.emit(context.getString(R.string.dev_options_event_update_check_worker_synced))
                refresh()
            }
        }

        fun syncLogPruneWorker() {
            val request = PeriodicWorkRequestBuilder<LogPruneWorker>(1, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(
                LogPruneWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            viewModelScope.launch {
                _events.emit(context.getString(R.string.dev_options_event_log_prune_worker_synced))
                refresh()
            }
        }

        fun addMockLargeFileMoveRule() {
            viewModelScope.launch {
                val existingMockRule =
                    withContext(ioDispatcher) {
                        ruleRepository.getAllRulesOrderedBySortOrder().firstOrNull(DevMockFileMove::isMockRule)
                    }
                if (existingMockRule != null) {
                    _events.emit(context.getString(R.string.dev_options_event_mock_large_file_rule_exists))
                    return@launch
                }
                val now = System.currentTimeMillis()
                ruleRepository.saveRule(
                    Rule(
                        name = context.getString(R.string.dev_options_mock_large_file_rule_name),
                        sourceFolderPaths = listOf(DevMockFileMove.SOURCE_FOLDER_URI),
                        destinationFolderPath = DevMockFileMove.DESTINATION_FOLDER_URI,
                        fileExtensions = listOf("mp4", "mov", "jpg", "png", "mp3", "flac", "pdf", "docx", "zip", "dat"),
                        isEnabled = true,
                        createdAt = now,
                        updatedAt = now,
                        conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                        operationMode = OperationMode.MOVE,
                        suppressMissingSourceFolderCardWarning = true,
                        icon = RuleIcon.DEFAULT,
                    ),
                )
                _events.emit(context.getString(R.string.dev_options_event_mock_large_file_rule_added))
                refresh()
            }
        }

        fun addMockSafPermissionLostRule() {
            viewModelScope.launch {
                addMockFolderIssueRule(
                    ruleName = context.getString(R.string.dev_options_mock_saf_permission_lost_rule_name),
                    sourceFolderPaths = listOf(MOCK_LOST_SAF_SOURCE_URI),
                    destinationFolderPath = MOCK_LOST_SAF_DESTINATION_URI,
                    addedMessageRes = R.string.dev_options_event_mock_saf_permission_lost_rule_added,
                    existsMessageRes = R.string.dev_options_event_mock_folder_issue_rule_exists,
                )
            }
        }

        fun addMockSafDownloadAccessRule() {
            viewModelScope.launch {
                addMockFolderIssueRule(
                    ruleName = context.getString(R.string.dev_options_mock_saf_download_rule_name),
                    sourceFolderPaths = listOf(MOCK_SAF_DOWNLOAD_URI),
                    destinationFolderPath = MOCK_LOST_SAF_DESTINATION_URI,
                    addedMessageRes = R.string.dev_options_event_mock_saf_download_rule_added,
                    existsMessageRes = R.string.dev_options_event_mock_folder_issue_rule_exists,
                )
            }
        }

        fun removeMockRulesAndHistory() {
            viewModelScope.launch {
                val mockFolderIssueNames =
                    setOf(
                        context.getString(R.string.dev_options_mock_saf_permission_lost_rule_name),
                        context.getString(R.string.dev_options_mock_saf_download_rule_name),
                    )
                withContext(ioDispatcher) {
                    val mockRuleIds =
                        ruleRepository
                            .getAllRulesOrderedBySortOrder()
                            .filter { rule ->
                                DevMockFileMove.isMockRule(rule) || rule.name in mockFolderIssueNames
                            }.map { rule -> rule.id }
                    val mockHistoryRows =
                        runHistoryDao.getAllHistoryOnce().filter { history ->
                            history.ruleId in mockRuleIds ||
                                fileMovedDao.getFilesForRunOnce(history.id).any { movedFile ->
                                    DevMockFileMove.isMockMovedFile(
                                        sourceUri = movedFile.sourceUri,
                                        destinationUri = movedFile.destinationUri,
                                    )
                                }
                        }
                    mockHistoryRows.forEach { history -> runHistoryDao.deleteHistoryById(history.id) }
                    mockRuleIds.forEach { ruleId -> ruleRepository.deleteRule(ruleId) }
                }
                _events.emit(context.getString(R.string.dev_options_event_mock_rules_removed))
                refresh()
            }
        }

        private suspend fun addMockFolderIssueRule(
            ruleName: String,
            sourceFolderPaths: List<String>,
            destinationFolderPath: String,
            @StringRes addedMessageRes: Int,
            @StringRes existsMessageRes: Int,
        ) {
            val existingRule =
                withContext(ioDispatcher) {
                    ruleRepository.getAllRulesOrderedBySortOrder().firstOrNull { rule -> rule.name == ruleName }
                }
            if (existingRule != null) {
                _events.emit(context.getString(existsMessageRes))
                return
            }
            val now = System.currentTimeMillis()
            ruleRepository.saveRule(
                Rule(
                    name = ruleName,
                    sourceFolderPaths = sourceFolderPaths,
                    destinationFolderPath = destinationFolderPath,
                    fileExtensions = listOf("jpg", "png", "pdf"),
                    isEnabled = true,
                    createdAt = now,
                    updatedAt = now,
                    conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                    operationMode = OperationMode.MOVE,
                    suppressMissingSourceFolderCardWarning = false,
                    icon = RuleIcon.DEFAULT,
                ),
            )
            _events.emit(context.getString(addedMessageRes))
            refresh()
        }

        fun postMockUpdateNotification() {
            viewModelScope.launch {
                if (!BuildConfig.SHOW_UPDATES) {
                    _events.emit(context.getString(R.string.dev_options_event_updates_hidden))
                    return@launch
                }
                updateAvailableNotifier.notifyDevMockUpdateAvailable(mockUpdateInfo())
                _events.emit(context.getString(R.string.dev_options_event_mock_update_notification_requested))
            }
        }

        @SuppressLint("MissingPermission")
        fun postMockFileOperationNotification() {
            viewModelScope.launch {
                if (!notificationsAllowed()) return@launch
                ensureFileOperationChannels()
                val contentPendingIntent = openHistoryPendingIntent()
                val ruleName = context.getString(R.string.dev_options_mock_file_operation_rule_name)
                val largeFileName = context.getString(R.string.dev_options_mock_file_operation_large_file_name)
                for (progress in 0..100 step 10) {
                    val notification =
                        NotificationCompat
                            .Builder(context, FileOrganizerWorker.CHANNEL_ID)
                            .setContentTitle(context.getString(R.string.notification_running, ruleName))
                            .setContentText(
                                context.getString(
                                    R.string.dev_options_mock_file_operation_progress,
                                    largeFileName,
                                    progress,
                                ),
                            ).setSmallIcon(R.drawable.ic_notification)
                            .setContentIntent(contentPendingIntent)
                            .setOngoing(progress < 100)
                            .setOnlyAlertOnce(true)
                            .setProgress(100, progress, false)
                            .build()
                    NotificationManagerCompat.from(context).notify(MOCK_FILE_OPERATION_PROGRESS_NOTIFICATION_ID, notification)
                    delay(350L)
                }
                NotificationManagerCompat.from(context).cancel(MOCK_FILE_OPERATION_PROGRESS_NOTIFICATION_ID)

                val movedFileNames =
                    listOf(
                        largeFileName,
                        context.getString(R.string.dev_options_mock_file_operation_file_2),
                        context.getString(R.string.dev_options_mock_file_operation_file_3),
                    )
                val body =
                    context.resources.getQuantityString(
                        R.plurals.history_files_moved,
                        movedFileNames.size,
                        movedFileNames.size,
                    )
                val style =
                    NotificationCompat
                        .InboxStyle()
                        .setBigContentTitle(context.getString(R.string.notification_summary_title, ruleName))
                        .setSummaryText(body)
                movedFileNames.forEach { fileName -> style.addLine(fileName) }

                val notification =
                    NotificationCompat
                        .Builder(context, FileOrganizerWorker.SUMMARY_CHANNEL_ID)
                        .setContentTitle(context.getString(R.string.notification_summary_title, ruleName))
                        .setContentText(body)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setStyle(style)
                        .setContentIntent(contentPendingIntent)
                        .setAutoCancel(true)
                        .build()
                NotificationManagerCompat.from(context).notify(MOCK_FILE_OPERATION_NOTIFICATION_ID, notification)
                _events.emit(context.getString(R.string.dev_options_event_mock_file_operation_notification_requested))
            }
        }

        fun resetSkippedGithubReleaseAck() {
            viewModelScope.launch {
                userPreferencesRepository.clearGithubReleaseAck()
                _events.emit(context.getString(R.string.dev_options_event_github_ack_reset))
            }
        }

        fun clearUpdateNotificationDedupe() {
            viewModelScope.launch {
                userPreferencesRepository.clearUpdateLastNotifiedDedupeKey()
                _events.emit(context.getString(R.string.dev_options_event_update_dedupe_cleared))
                refresh()
            }
        }

        fun deleteCachedUpdateApk() {
            viewModelScope.launch {
                val deleted =
                    withContext(ioDispatcher) {
                        val file = File(context.cacheDir, FILEPIPE_UPDATE_APK_CACHE_NAME)
                        file.exists() && file.delete()
                    }
                _events.emit(
                    context.getString(
                        if (deleted) {
                            R.string.dev_options_event_cached_update_apk_deleted
                        } else {
                            R.string.dev_options_event_no_cached_update_apk
                        },
                    ),
                )
                refresh()
            }
        }

        fun clearDiagnosticsLog() {
            DiagnosticLog.clear(context)
            viewModelScope.launch { _events.emit(context.getString(R.string.dev_options_event_diagnostics_log_cleared)) }
        }

        fun resetSettingsPreferences() {
            viewModelScope.launch {
                userPreferencesRepository.resetAppSettingsPreferencesToDefaults()
                _events.emit(context.getString(R.string.dev_options_event_settings_preferences_reset))
                refresh()
            }
        }

        fun resetFirstLaunchFlag() {
            viewModelScope.launch {
                userPreferencesRepository.resetFirstLaunchFlag()
                _events.emit(context.getString(R.string.dev_options_event_first_launch_flag_reset))
                refresh()
            }
        }

        fun setDeveloperOptionsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                userPreferencesRepository.setDeveloperOptionsEnabled(enabled)
                _events.emit(
                    context.getString(
                        if (enabled) {
                            R.string.settings_developer_options_unlocked
                        } else {
                            R.string.settings_developer_options_disabled
                        },
                    ),
                )
            }
        }

        fun forceCrash(): Nothing = throw IllegalStateException(context.getString(R.string.dev_options_forced_crash_message))

        private suspend fun buildSnapshot(): DevOptionsUiState {
            val prefs = userPreferencesRepository.getPreferencesSnapshot()
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appInfo = packageInfo.applicationInfo
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val cacheApk = File(context.cacheDir, FILEPIPE_UPDATE_APK_CACHE_NAME)
            val uriPermissions = context.contentResolver.persistedUriPermissions
            val readGrants = uriPermissions.count { it.isReadPermission }
            val writeGrants = uriPermissions.count { it.isWritePermission }
            val backupDestinations =
                listOf(
                    prefs.exportFolderUri,
                    prefs.cloudExportFolderUri,
                ).count { it.isNotBlank() }
            val historyByStatus =
                RunStatus.entries.associateWith { status ->
                    runHistoryDao.countHistoryByStatus(status.name)
                }
            val rules = ruleRepository.getAllRulesOrderedBySortOrder()
            val folderAccess = buildFolderAccess(uriPermissions, rules)
            val scheduledRules = rules.filter { it.isEnabled && it.schedule != null }
            val ruleWorkerStates =
                scheduledRules
                    .flatMap { rule ->
                        workManager.getWorkInfosByTagFlow("rule_${rule.id}").first()
                    }.map { it.state }
            val overview =
                listOf(
                    DevOptionsInfoRow(
                        R.string.dev_options_info_version,
                        context.getString(
                            R.string.dev_options_value_version_format,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                    ),
                    DevOptionsInfoRow(R.string.dev_options_info_package, context.packageName),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_variant,
                        context.getString(
                            R.string.dev_options_value_variant_format,
                            BuildConfig.FLAVOR,
                            BuildConfig.BUILD_TYPE,
                        ),
                    ),
                    DevOptionsInfoRow(R.string.dev_options_info_installer, installerPackageName()),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_device,
                        context.getString(
                            R.string.dev_options_value_device_format,
                            Build.MANUFACTURER,
                            Build.MODEL,
                        ),
                    ),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_android,
                        context.getString(
                            R.string.dev_options_value_android_format,
                            Build.VERSION.RELEASE,
                            Build.VERSION.SDK_INT,
                        ),
                    ),
                    DevOptionsInfoRow(R.string.dev_options_info_target_sdk, "${appInfo?.targetSdkVersion ?: unknownValue()}"),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_database,
                        context.getString(
                            R.string.dev_options_value_database_format,
                            DATABASE_NAME,
                            appDatabase.openHelper.readableDatabase.version,
                        ),
                    ),
                    DevOptionsInfoRow(R.string.dev_options_info_first_install, formatInstant(packageInfo.firstInstallTime)),
                    DevOptionsInfoRow(R.string.dev_options_info_last_update, formatInstant(packageInfo.lastUpdateTime)),
                )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val exactAlarmAllowed =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
            val permissions =
                listOf(
                    DevOptionsInfoRow(R.string.dev_options_info_folder_access_mode, prefs.folderAccessMode.name),
                    DevOptionsInfoRow(R.string.dev_options_info_all_files_access, allFilesAccessGranted()),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_notifications_enabled,
                        NotificationManagerCompat.from(context).areNotificationsEnabled().toString(),
                    ),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_exact_alarm_access,
                        exactAlarmAllowed.toString(),
                    ),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_battery_optimization,
                        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                            context.getString(R.string.dev_options_value_ignored)
                        } else {
                            context.getString(R.string.dev_options_value_active)
                        },
                    ),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_backup_destinations,
                        context.resources.getQuantityString(
                            R.plurals.dev_options_value_configured_count_format,
                            backupDestinations,
                            backupDestinations,
                        ),
                    ),
                )
            val database =
                buildList {
                    add(
                        DevOptionsInfoRow(
                            R.string.dev_options_info_rules,
                            context.resources.getQuantityString(
                                R.plurals.dev_options_value_total_count_format,
                                ruleDao.countRules(),
                                ruleDao.countRules(),
                            ),
                        ),
                    )
                    add(DevOptionsInfoRow(R.string.dev_options_info_enabled_rules, "${ruleDao.countEnabledRules()}"))
                    add(DevOptionsInfoRow(R.string.dev_options_info_scheduled_rules, "${ruleDao.countScheduledRules()}"))
                    add(
                        DevOptionsInfoRow(
                            R.string.dev_options_info_history_runs,
                            context.resources.getQuantityString(
                                R.plurals.dev_options_value_total_count_format,
                                runHistoryDao.countHistory(),
                                runHistoryDao.countHistory(),
                            ),
                        ),
                    )
                    historyByStatus.forEach { (status, count) ->
                        add(DevOptionsInfoRow(historyStatusLabelRes(status), "$count"))
                    }
                    add(DevOptionsInfoRow(R.string.dev_options_info_moved_file_rows, "${fileMovedDao.countFilesMoved()}"))
                    add(DevOptionsInfoRow(R.string.dev_options_info_database_file_size, formatBytes(dbFile.length())))
                    add(
                        DevOptionsInfoRow(
                            R.string.dev_options_info_cached_update_apk,
                            if (cacheApk.isFile) {
                                formatBytes(cacheApk.length())
                            } else {
                                context.getString(R.string.dev_options_value_not_cached)
                            },
                        ),
                    )
                    add(DevOptionsInfoRow(R.string.dev_options_info_cache_free_space, formatBytes(context.cacheDir.freeSpace)))
                    add(DevOptionsInfoRow(R.string.dev_options_info_files_free_space, formatBytes(context.filesDir.freeSpace)))
                }
            val workers =
                listOf(
                    DevOptionsInfoRow(
                        R.string.dev_options_info_log_prune,
                        workerSummary(workManager.getWorkInfosForUniqueWorkFlow(LogPruneWorker.WORK_NAME).first()),
                    ),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_update_checks,
                        workerSummary(workManager.getWorkInfosForUniqueWorkFlow(UpdateCheckWorker.UNIQUE_WORK_NAME).first()),
                    ),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_scheduled_exports,
                        workerSummary(workManager.getWorkInfosForUniqueWorkFlow(ScheduledRulesExportWorker.WORK_NAME).first()),
                    ),
                    DevOptionsInfoRow(
                        R.string.dev_options_info_scheduled_rule_workers,
                        workerStateSummary(ruleWorkerStates, scheduledRules.size),
                    ),
                )
            return DevOptionsUiState(
                loading = false,
                developerOptionsEnabled = userPreferencesRepository.developerOptionsEnabledFlow.first(),
                preferences = prefs,
                overview = overview,
                permissionsAndStorage = permissions,
                folderAccess = folderAccess,
                database = database,
                workers = workers,
                showUpdates = BuildConfig.SHOW_UPDATES,
                usePlayInAppUpdates = BuildConfig.USE_PLAY_IN_APP_UPDATES,
                isGithubFlavor = BuildConfig.FLAVOR == "github",
            )
        }

        private fun startActivity(intent: Intent) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        private fun notificationsAllowed(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        private fun openHistoryPendingIntent(): PendingIntent {
            val openIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(PendingShortcutRepository.EXTRA_OPEN_HISTORY, true)
                }
            return PendingIntent.getActivity(
                context,
                REQUEST_CODE_OPEN_HISTORY,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun ensureFileOperationChannels() {
            RunNotificationChannels.ensure(context)
        }

        private fun workerSummary(infos: List<WorkInfo>): String =
            if (infos.isEmpty()) {
                context.getString(R.string.dev_options_value_not_scheduled)
            } else {
                infos
                    .groupingBy { it.state }
                    .eachCount()
                    .entries
                    .joinToString { (state, count) ->
                        context.getString(R.string.dev_options_value_worker_state_count_format, state.name, count)
                    }
            }

        private fun workerStateSummary(
            states: List<WorkInfo.State>,
            configuredRules: Int,
        ): String =
            if (configuredRules == 0) {
                context.getString(R.string.dev_options_value_no_scheduled_rules)
            } else if (states.isEmpty()) {
                context.resources.getQuantityString(
                    R.plurals.dev_options_value_no_per_rule_workers_format,
                    configuredRules,
                    configuredRules,
                )
            } else {
                context.resources.getQuantityString(
                    R.plurals.dev_options_value_configured_worker_states_format,
                    configuredRules,
                    configuredRules,
                    states
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .joinToString { (state, count) ->
                            context.getString(R.string.dev_options_value_worker_state_count_format, state.name, count)
                        },
                )
            }

        private fun mockUpdateInfo(): UpdateInfo =
            UpdateInfo(
                versionName = "9.9.9",
                downloadUrl = "",
                releaseNotes = context.getString(R.string.dev_options_mock_update_release_notes),
                remoteApkAssetUpdatedAt = if (BuildConfig.FLAVOR == "github") DEV_MOCK_GITHUB_ASSET_UPDATED_AT else "",
                isDevReleaseMock = true,
            )

        private fun allFilesAccessGranted(): String = Environment.isExternalStorageManager().toString()

        private fun buildFolderAccess(
            uriPermissions: List<UriPermission>,
            rules: List<Rule>,
        ): DevFolderAccessUiState {
            val permissionByUri = uriPermissions.associateBy { it.uri.toString() }
            val usedSafUris = mutableSetOf<String>()
            val ruleAccesses =
                rules.map { rule ->
                    DevRuleAccessUiItem(
                        ruleName = rule.name,
                        folderAccesses =
                            buildList {
                                val sourceCount = rule.sourceFolderPaths.size
                                rule.sourceFolderPaths.forEachIndexed { sourceIndex, uriString ->
                                    val sourceLabel =
                                        if (sourceCount == 1) {
                                            context.getString(R.string.dev_options_folder_access_source)
                                        } else {
                                            context.getString(
                                                R.string.dev_options_folder_access_source_format,
                                                sourceIndex + 1,
                                            )
                                        }
                                    add(
                                        buildRuleFolderAccess(
                                            label = sourceLabel,
                                            uriString = uriString,
                                            permissionByUri = permissionByUri,
                                        ),
                                    )
                                    if (uriString.startsWith(CONTENT_URI_PREFIX) && uriString in permissionByUri) {
                                        usedSafUris += uriString
                                    }
                                }
                                if (rule.destinationFolderPath.isNotBlank()) {
                                    add(
                                        buildRuleFolderAccess(
                                            label = context.getString(R.string.dev_options_folder_access_target),
                                            uriString = rule.destinationFolderPath,
                                            permissionByUri = permissionByUri,
                                        ),
                                    )
                                    if (
                                        rule.destinationFolderPath.startsWith(CONTENT_URI_PREFIX) &&
                                        rule.destinationFolderPath in permissionByUri
                                    ) {
                                        usedSafUris += rule.destinationFolderPath
                                    }
                                }
                            },
                    )
                }
            val unassociatedGrants =
                uriPermissions
                    .filter { uriPermission -> uriPermission.uri.toString() !in usedSafUris }
                    .sortedBy { it.uri.toString() }
                    .mapIndexed { grantIndex, uriPermission ->
                        DevRuleFolderAccessUiItem(
                            label =
                                context.getString(
                                    R.string.dev_options_folder_access_unassociated_grant_format,
                                    grantIndex + 1,
                                ),
                            uri = uriPermission.uri.toString(),
                            accessTypeLabel = context.getString(R.string.dev_options_folder_access_type_saf),
                            accessLabel = safAccessLabel(uriPermission),
                        )
                    }
            return DevFolderAccessUiState(rules = ruleAccesses, unassociatedGrants = unassociatedGrants)
        }

        private fun buildRuleFolderAccess(
            label: String,
            uriString: String,
            permissionByUri: Map<String, UriPermission>,
        ): DevRuleFolderAccessUiItem {
            val uriPermission = permissionByUri[uriString]
            return if (uriString.startsWith(CONTENT_URI_PREFIX)) {
                DevRuleFolderAccessUiItem(
                    label = label,
                    uri = uriString,
                    accessTypeLabel = context.getString(R.string.dev_options_folder_access_type_saf),
                    accessLabel =
                        if (uriPermission == null) {
                            context.getString(R.string.dev_options_folder_access_missing)
                        } else {
                            safAccessLabel(uriPermission)
                        },
                )
            } else {
                DevRuleFolderAccessUiItem(
                    label = label,
                    uri = uriString,
                    accessTypeLabel = context.getString(R.string.dev_options_folder_access_type_all_files),
                    accessLabel = allFilesAccessStatusLabel(),
                )
            }
        }

        private fun safAccessLabel(uriPermission: UriPermission): String =
            when {
                uriPermission.isReadPermission && uriPermission.isWritePermission -> {
                    context.getString(R.string.dev_options_saf_access_read_write)
                }

                uriPermission.isReadPermission -> {
                    context.getString(R.string.dev_options_saf_access_read)
                }

                uriPermission.isWritePermission -> {
                    context.getString(R.string.dev_options_saf_access_write)
                }

                else -> {
                    context.getString(R.string.dev_options_saf_access_none)
                }
            }

        private fun allFilesAccessStatusLabel(): String =
            if (Environment.isExternalStorageManager()) {
                context.getString(R.string.dev_options_folder_access_granted)
            } else {
                context.getString(R.string.dev_options_folder_access_missing)
            }

        private fun installerPackageName(): String =
            runCatching {
                context.packageManager
                    .getInstallSourceInfo(context.packageName)
                    .installingPackageName
                    .orEmpty()
                    .ifBlank { unknownValue() }
            }.getOrDefault(unknownValue())

        private fun formatInstant(epochMillis: Long): String = runCatching { Instant.ofEpochMilli(epochMillis).toString() }.getOrDefault(unknownValue())

        private fun formatBytes(bytes: Long): String {
            val kb = 1024.0
            val mb = kb * 1024
            val gb = mb * 1024
            return when {
                bytes >= gb -> context.getString(R.string.dev_options_bytes_gb_format, bytes / gb)
                bytes >= mb -> context.getString(R.string.dev_options_bytes_mb_format, bytes / mb)
                bytes >= kb -> context.getString(R.string.dev_options_bytes_kb_format, bytes / kb)
                else -> context.getString(R.string.dev_options_bytes_b_format, bytes)
            }
        }

        private fun unknownValue(): String = context.getString(R.string.dev_options_value_unknown)

        @StringRes
        private fun historyStatusLabelRes(status: RunStatus): Int =
            when (status) {
                RunStatus.IN_PROGRESS -> R.string.dev_options_info_history_in_progress
                RunStatus.SUCCESS -> R.string.dev_options_info_history_success
                RunStatus.PARTIAL_FAILURE -> R.string.dev_options_info_history_partial_failure
                RunStatus.FAILED -> R.string.dev_options_info_history_failed
                RunStatus.CANCELLED -> R.string.dev_options_info_history_cancelled
                RunStatus.UNDONE -> R.string.dev_options_info_history_undone
            }

        companion object {
            private const val MOCK_FILE_OPERATION_PROGRESS_NOTIFICATION_ID = 71004
            private const val MOCK_FILE_OPERATION_NOTIFICATION_ID = 71003
            private const val REQUEST_CODE_OPEN_HISTORY = 1003
            private const val DATABASE_NAME = APP_DATABASE_NAME
            private const val DEV_MOCK_GITHUB_ASSET_UPDATED_AT = "2000-01-01T00:00:00Z"
            private const val CONTENT_URI_PREFIX = "content://"
            private const val MOCK_LOST_SAF_SOURCE_URI =
                "content://com.android.externalstorage.documents/tree/primary%3AFilePipeMockLostSource"
            private const val MOCK_LOST_SAF_DESTINATION_URI =
                "content://com.android.externalstorage.documents/tree/primary%3AFilePipeMockLostDestination"
            private const val MOCK_SAF_DOWNLOAD_URI =
                "content://com.android.externalstorage.documents/tree/primary%3ADownload"
        }
    }
