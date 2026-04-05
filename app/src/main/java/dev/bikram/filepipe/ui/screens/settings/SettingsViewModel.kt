package dev.bikram.filepipe.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.ThemePaletteStyle
import dev.bikram.filepipe.data.preferences.SwipeAction
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.R
import dev.bikram.filepipe.BuildConfig
import dev.bikram.filepipe.domain.usecase.ExportRulesUseCase
import dev.bikram.filepipe.domain.usecase.ImportRulesUseCase
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import dev.bikram.filepipe.update.PlayInAppUpdateStarter
import dev.bikram.filepipe.update.PlayUpdateSessionHandle
import dev.bikram.filepipe.update.UpdateChecker
import dev.bikram.filepipe.update.UpdateInfo
import dev.bikram.filepipe.worker.ScheduledRulesExportWorker
import dev.bikram.filepipe.data.storage.treeUriFromDocumentUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val exportRulesUseCase: ExportRulesUseCase,
    private val importRulesUseCase: ImportRulesUseCase,
    private val workManager: WorkManager,
    private val rulesAutoExportTrigger: RulesAutoExportTrigger,
    private val updateChecker: UpdateChecker,
    private val playUpdateSessionHandle: PlayUpdateSessionHandle,
    private val playInAppUpdateStarter: PlayInAppUpdateStarter
) : ViewModel() {

    val preferencesFlow = userPreferencesRepository.preferencesFlow

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    /**
     * null = not downloading. 0f..100f = determinate progress. -2f = indeterminate download (unknown size).
     * -1f = installing (package installer).
     */
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _updateSheetChangelog = MutableStateFlow<ChangelogUiState>(ChangelogUiState.Hidden)
    val updateSheetChangelog: StateFlow<ChangelogUiState> = _updateSheetChangelog.asStateFlow()

    private val _manualUpdateNoResult = MutableStateFlow(false)
    val manualUpdateNoResult: StateFlow<Boolean> = _manualUpdateNoResult.asStateFlow()

    private val _manualExportPickerRequested = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val manualExportPickerRequested = _manualExportPickerRequested.asSharedFlow()

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.getPreferencesSnapshot()
            if (prefs.scheduledExportEnabled && prefs.exportFolderUri.isNotBlank()) {
                enqueueScheduledExportWork()
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun markIntroSeen(onComplete: () -> Unit = {}) = viewModelScope.launch {
        userPreferencesRepository.markIntroSeen()
        onComplete()
    }

    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch {
        userPreferencesRepository.setThemeMode(mode)
    }

    fun setColorSource(source: AppColorSource) = viewModelScope.launch {
        userPreferencesRepository.setColorSource(source)
    }

    fun addCustomSeedHex(hex: String) = viewModelScope.launch {
        userPreferencesRepository.addCustomSeedHex(hex)
    }

    fun selectCustomSeedHex(hex: String) = viewModelScope.launch {
        userPreferencesRepository.selectCustomSeedHex(hex)
    }

    fun removeCustomSeedHex(hex: String) = viewModelScope.launch {
        userPreferencesRepository.removeCustomSeedHex(hex)
    }

    fun setThemePaletteStyle(style: ThemePaletteStyle) = viewModelScope.launch {
        userPreferencesRepository.setThemePaletteStyle(style)
    }

    fun setExportFolderUri(uriString: String) = viewModelScope.launch {
        persistExportFolderUri(uriString)
    }

    private suspend fun persistExportFolderUri(uriString: String) {
        if (uriString.startsWith("content://")) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    Uri.parse(uriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        userPreferencesRepository.setExportFolderUri(uriString)
        if (uriString.isBlank()) {
            userPreferencesRepository.setAutoExportOnRuleChange(false)
            userPreferencesRepository.setScheduledExportEnabled(false)
            workManager.cancelUniqueWork(SCHEDULED_EXPORT_WORK_NAME)
        }
    }

    fun setAutoExportOnChange(enabled: Boolean) = viewModelScope.launch {
        if (enabled && userPreferencesRepository.getPreferencesSnapshot().exportFolderUri.isBlank()) {
            _userMessage.value = context.getString(R.string.settings_export_select_folder_first)
            return@launch
        }
        userPreferencesRepository.setAutoExportOnRuleChange(enabled)
        if (enabled) {
            rulesAutoExportTrigger.maybeExportAfterRuleChange()
        }
    }

    fun setScheduledExportEnabled(enabled: Boolean) = viewModelScope.launch {
        if (enabled && userPreferencesRepository.getPreferencesSnapshot().exportFolderUri.isBlank()) {
            _userMessage.value = context.getString(R.string.settings_export_select_folder_first)
            return@launch
        }
        userPreferencesRepository.setScheduledExportEnabled(enabled)
        if (enabled) {
            enqueueScheduledExportWork()
        } else {
            workManager.cancelUniqueWork(SCHEDULED_EXPORT_WORK_NAME)
        }
    }

    private fun enqueueScheduledExportWork() {
        val request = PeriodicWorkRequestBuilder<ScheduledRulesExportWorker>(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SCHEDULED_EXPORT_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun setLogRetentionDays(days: Int) = viewModelScope.launch {
        userPreferencesRepository.setLogRetentionDays(days)
    }

    fun setSwipeStartToEnd(action: SwipeAction) = viewModelScope.launch {
        userPreferencesRepository.setSwipeStartToEnd(action)
    }

    fun setSwipeEndToStart(action: SwipeAction) = viewModelScope.launch {
        userPreferencesRepository.setSwipeEndToStart(action)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setHapticFeedbackEnabled(enabled)
    }

    fun setProgressiveBlurEnabled(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setProgressiveBlurEnabled(enabled)
    }

    fun setUseGradientBackground(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setUseGradientBackground(enabled)
    }

    fun setUseFixedCardColors(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setUseFixedCardColors(enabled)
    }

    fun setAutoCheckForUpdates(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setAutoCheckForUpdates(enabled)
    }

    fun requestManualExportPicker() {
        _manualExportPickerRequested.tryEmit(defaultManualExportFileName())
    }

    fun completeManualExportToUri(targetUri: Uri) = viewModelScope.launch {
        exportRulesUseCase.exportBackupJsonToDocumentUri(targetUri).fold(
            onSuccess = { displayName ->
                val prefs = userPreferencesRepository.getPreferencesSnapshot()
                if (prefs.exportFolderUri.isBlank()) {
                    val treeUri = treeUriFromDocumentUri(context, targetUri)
                    if (treeUri != null) {
                        persistExportFolderUri(treeUri.toString())
                    }
                }
                _userMessage.value = context.getString(R.string.settings_export_success, displayName)
            },
            onFailure = { err ->
                _userMessage.value = "Export failed: ${err.message}"
            }
        )
    }

    private fun defaultManualExportFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "filepipe_backup_$stamp.json"
    }

    fun importFromUri(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().decodeToString()
            }
        } ?: run {
            _userMessage.value = "Could not read file"
            return@launch
        }
        importRulesUseCase.importFromJson(text).fold(
            onSuccess = { result ->
                val parts = buildList {
                    add("${result.rulesImported} rules")
                    if (result.historyRunsImported > 0) {
                        add("${result.historyRunsImported} history runs")
                    }
                    if (result.settingsRestored) add("settings")
                }
                _userMessage.value = context.getString(
                    R.string.settings_import_success,
                    parts.joinToString(", ")
                )
            },
            onFailure = { _userMessage.value = "Import failed: ${it.message}" }
        )
    }

    fun checkForUpdate(silent: Boolean = false) = viewModelScope.launch {
        playUpdateSessionHandle.clearPendingPlayUpdate()
        _isCheckingUpdate.value = true
        _downloadProgress.value = null
        _updateInfo.value = null
        val info = updateChecker.checkForUpdate()
        _isCheckingUpdate.value = false
        if (info != null) {
            _updateInfo.value = info
        } else if (!silent) {
            _userMessage.value = null
            yield()
            _userMessage.value = context.getString(R.string.settings_up_to_date)
        }
    }

    /**
     * Manual flow from the update sheet: sets checking immediately on the main thread, then runs the check.
     * No snackbar; the sheet shows up-to-date vs available.
     */
    fun beginManualUpdateCheckFromSheet() {
        playUpdateSessionHandle.clearPendingPlayUpdate()
        _isCheckingUpdate.value = true
        _downloadProgress.value = null
        _updateInfo.value = null
        _manualUpdateNoResult.value = false
        viewModelScope.launch {
            val info = updateChecker.checkForUpdate()
            _isCheckingUpdate.value = false
            if (info != null) {
                _updateInfo.value = info
            } else {
                _manualUpdateNoResult.value = true
            }
        }
    }

    fun loadChangelogForUpdateSheet() = viewModelScope.launch {
        if (BuildConfig.CHANGELOG_GITHUB_REPO.isBlank()) {
            _updateSheetChangelog.value = ChangelogUiState.Failed(
                context.getString(R.string.settings_changelog_load_failed)
            )
            return@launch
        }
        _updateSheetChangelog.value = ChangelogUiState.Loading
        val loaded = withContext(Dispatchers.IO) {
            runCatching { fetchRawChangelog() }
        }
        _updateSheetChangelog.value = loaded.fold(
            onSuccess = { ChangelogUiState.Ready(it) },
            onFailure = {
                ChangelogUiState.Failed(
                    it.message ?: context.getString(R.string.settings_changelog_load_failed)
                )
            }
        )
    }

    fun dismissUpdateSheet() {
        playUpdateSessionHandle.clearPendingPlayUpdate()
        _updateSheetChangelog.value = ChangelogUiState.Hidden
        _manualUpdateNoResult.value = false
    }

    fun tryStartPlayInAppUpdate(
        activity: ComponentActivity?,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean {
        if (!BuildConfig.USE_PLAY_IN_APP_UPDATES) return false
        if (activity == null) {
            _userMessage.value = context.getString(R.string.settings_play_in_app_update_failed)
            return false
        }
        val started = playInAppUpdateStarter.startUpdateIfPending(activity, launcher)
        if (!started) {
            _userMessage.value = context.getString(R.string.settings_play_in_app_update_failed)
        }
        return started
    }

    fun downloadAndInstall(downloadUrl: String) = viewModelScope.launch {
        _downloadProgress.value = 0f
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                try {
                    connection.connect()
                    val contentLength = connection.contentLength
                    val file = File(context.cacheDir, "filepipe_update.apk")
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var totalRead = 0L
                            var readCount: Int
                            if (contentLength > 0) {
                                while (input.read(buffer).also { readCount = it } != -1) {
                                    output.write(buffer, 0, readCount)
                                    totalRead += readCount
                                    val percent = (100f * totalRead / contentLength).coerceIn(0f, 100f)
                                    _downloadProgress.value = percent
                                }
                            } else {
                                _downloadProgress.value = -2f
                                while (input.read(buffer).also { readCount = it } != -1) {
                                    output.write(buffer, 0, readCount)
                                }
                            }
                        }
                    }
                    _downloadProgress.value = -1f
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    withContext(Dispatchers.Main) {
                        context.startActivity(installIntent)
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
        result.onFailure {
            _userMessage.value = "Download failed: ${it.message}"
        }
        _downloadProgress.value = null
    }

    fun openAppNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun fetchRawChangelog(): String {
        val repo = BuildConfig.CHANGELOG_GITHUB_REPO
        val branch = BuildConfig.CHANGELOG_GITHUB_BRANCH
        val connection =
            URL("https://raw.githubusercontent.com/$repo/$branch/CHANGELOG.md").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        return try {
            connection.connect()
            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val SCHEDULED_EXPORT_WORK_NAME = "scheduled_rules_export"
    }
}

sealed class ChangelogUiState {
    data object Hidden : ChangelogUiState()
    data object Loading : ChangelogUiState()
    data class Ready(val text: String) : ChangelogUiState()
    data class Failed(val message: String) : ChangelogUiState()
}
