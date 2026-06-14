package dev.bikram.filepipe.ui.screens.ruledetail

import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.RuleRepository
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.data.storage.isFilesystemFolderPathAllowedForRules
import dev.bikram.filepipe.data.storage.normalizeFilesystemFolderPath
import dev.bikram.filepipe.data.storage.primaryDownloadsDirectoryPath
import dev.bikram.filepipe.data.storage.primaryScreenshotsDirectoryPath
import dev.bikram.filepipe.di.IoDispatcher
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FolderAccessResult
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.PreviewFileResult
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.RuleTemplate
import dev.bikram.filepipe.domain.model.TemplateAutoSource
import dev.bikram.filepipe.domain.usecase.RulesAutoExportTrigger
import dev.bikram.filepipe.domain.usecase.ScheduleRulesUseCase
import dev.bikram.filepipe.domain.usecase.SimulateRuleUseCase
import dev.bikram.filepipe.domain.usecase.ValidateRuleUseCase
import dev.bikram.filepipe.ui.navigation.Screen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class RuleDetailUiState(
    val id: Long = 0,
    val sortOrder: Int = 0,
    val name: String = "",
    val sourceFolderPaths: List<String> = emptyList(),
    val destinationFolderPath: String = "",
    val fileExtensions: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val schedule: RuleSchedule? = null,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.RENAME_SUFFIX,
    val operationMode: OperationMode = OperationMode.MOVE,
    val scanSubdirectories: Boolean = false,
    val recreateDestinationSubfolders: Boolean = false,
    val suppressMissingSourceFolderCardWarning: Boolean = false,
    val icon: RuleIcon = RuleIcon.DEFAULT,
    val iconEmoji: String? = null,
    // Advanced filters (shown as display strings in UI)
    val filenamePattern: String = "",
    val minFileSizeMb: String = "",
    val maxFileSizeMb: String = "",
    val minAgeDays: String = "",
    val maxAgeDays: String = "",
    val excludePatternsText: String = "",
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val errors: List<String> = emptyList(),
    val previewFiles: List<PreviewFileResult>? = null,
    val isPreviewLoading: Boolean = false,
    val removedRedundantFolders: List<String> = emptyList(),
    /**
     * Source tree URIs that are not usable. Values are [FolderAccessResult.Unavailable] or
     * [FolderAccessResult.PermissionDenied] only.
     */
    val inaccessibleSourceIssues: Map<String, FolderAccessResult> = emptyMap(),
    /**
     * When the destination is set but not readable: [FolderAccessResult.Unavailable] or
     * [FolderAccessResult.PermissionDenied]. Null when destination is blank or accessible.
     */
    val destinationFolderAccessIssue: FolderAccessResult? = null,
    val folderAccessMode: FolderAccessMode = FolderAccessMode.SAF_ONLY,
    val allFilesAccessGranted: Boolean = false,
    val isTrashed: Boolean = false,
)

private data class RuleSnapshot(
    val name: String,
    val sourceFolderPaths: List<String>,
    val destinationFolderPath: String,
    val fileExtensions: List<String>,
    val schedule: RuleSchedule?,
    val conflictPolicy: ConflictPolicy,
    val operationMode: OperationMode,
    val scanSubdirectories: Boolean,
    val recreateDestinationSubfolders: Boolean,
    val suppressMissingSourceFolderCardWarning: Boolean,
    val icon: RuleIcon,
    val iconEmoji: String?,
    val filenamePattern: String,
    val minFileSizeMb: String,
    val maxFileSizeMb: String,
    val minAgeDays: String,
    val maxAgeDays: String,
    val excludePatternsText: String,
)

private fun RuleDetailUiState.toSnapshot(): RuleSnapshot =
    RuleSnapshot(
        name = name.trim(),
        sourceFolderPaths = sourceFolderPaths.toList(),
        destinationFolderPath = destinationFolderPath,
        fileExtensions = fileExtensions.toList(),
        schedule = schedule,
        conflictPolicy = conflictPolicy,
        operationMode = operationMode,
        scanSubdirectories = scanSubdirectories,
        recreateDestinationSubfolders = recreateDestinationSubfolders,
        suppressMissingSourceFolderCardWarning = suppressMissingSourceFolderCardWarning,
        icon = icon,
        iconEmoji = iconEmoji,
        filenamePattern = filenamePattern,
        minFileSizeMb = minFileSizeMb,
        maxFileSizeMb = maxFileSizeMb,
        minAgeDays = minAgeDays,
        maxAgeDays = maxAgeDays,
        excludePatternsText = excludePatternsText,
    )

private fun RuleDetailUiState.withSnapshot(snapshot: RuleSnapshot): RuleDetailUiState =
    copy(
        name = snapshot.name,
        sourceFolderPaths = snapshot.sourceFolderPaths,
        destinationFolderPath = snapshot.destinationFolderPath,
        fileExtensions = snapshot.fileExtensions,
        schedule = snapshot.schedule,
        conflictPolicy = snapshot.conflictPolicy,
        operationMode = snapshot.operationMode,
        scanSubdirectories = snapshot.scanSubdirectories,
        recreateDestinationSubfolders = snapshot.recreateDestinationSubfolders,
        suppressMissingSourceFolderCardWarning = snapshot.suppressMissingSourceFolderCardWarning,
        icon = snapshot.icon,
        iconEmoji = snapshot.iconEmoji,
        filenamePattern = snapshot.filenamePattern,
        minFileSizeMb = snapshot.minFileSizeMb,
        maxFileSizeMb = snapshot.maxFileSizeMb,
        minAgeDays = snapshot.minAgeDays,
        maxAgeDays = snapshot.maxAgeDays,
        excludePatternsText = snapshot.excludePatternsText,
        errors = emptyList(),
        previewFiles = null,
        isPreviewLoading = false,
        removedRedundantFolders = emptyList(),
        isSaved = false,
    )

@HiltViewModel
class RuleDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ruleRepository: RuleRepository,
        private val scheduleRulesUseCase: ScheduleRulesUseCase,
        private val validateRuleUseCase: ValidateRuleUseCase,
        private val simulateRuleUseCase: SimulateRuleUseCase,
        private val rulesAutoExportTrigger: RulesAutoExportTrigger,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val fileOperationRepository: FileOperationRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val ruleId: Long = savedStateHandle[Screen.RuleDetail.ARG_RULE_ID] ?: Screen.RuleDetail.NEW_RULE_ID
        private val templateIndex: Int = savedStateHandle[Screen.RuleDetail.ARG_TEMPLATE_INDEX] ?: -1
        val isNewRule = ruleId == Screen.RuleDetail.NEW_RULE_ID
        val showInitialTemplatePicker: Boolean = false

        private val _uiState = MutableStateFlow(RuleDetailUiState())
        val uiState: StateFlow<RuleDetailUiState> = _uiState.asStateFlow()

        private val _baseline = MutableStateFlow<RuleSnapshot?>(null)

        val isDirty: StateFlow<Boolean> =
            combine(_uiState, _baseline) { state, baseline ->
                baseline != null && state.toSnapshot() != baseline
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val bookmarkedFolders: StateFlow<List<String>> =
            userPreferencesRepository.preferencesFlow
                .map { it.bookmarkedFolders }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        init {
            userPreferencesRepository.preferencesFlow
                .map { prefs -> prefs.folderAccessMode }
                .distinctUntilChanged()
                .onEach { scheduleFolderAccessRecompute() }
                .launchIn(viewModelScope)
            if (!isNewRule) {
                loadRule()
            } else {
                val template = RuleTemplate.ALL.getOrNull(templateIndex)
                viewModelScope.launch {
                    if (template != null) {
                        applyTemplateInternal(template)
                    } else {
                        scheduleFolderAccessRecompute()
                    }
                    _uiState.update { it.copy(isLoading = false) }
                    _baseline.value = _uiState.value.toSnapshot()
                }
            }
        }

        private fun loadRule() =
            viewModelScope.launch {
                val rule = ruleRepository.getRuleByIdIncludingTrashed(ruleId)
                if (rule != null) {
                    _uiState.update {
                        it.copy(
                            id = rule.id,
                            sortOrder = rule.sortOrder,
                            name = rule.name,
                            sourceFolderPaths = rule.sourceFolderPaths,
                            destinationFolderPath = rule.destinationFolderPath,
                            fileExtensions = rule.fileExtensions,
                            isEnabled = rule.isEnabled,
                            schedule = rule.schedule,
                            conflictPolicy = rule.conflictPolicy,
                            operationMode = rule.operationMode,
                            scanSubdirectories = rule.scanSubdirectories,
                            recreateDestinationSubfolders = rule.recreateDestinationSubfolders,
                            suppressMissingSourceFolderCardWarning = rule.suppressMissingSourceFolderCardWarning,
                            icon = rule.icon,
                            iconEmoji = rule.iconEmoji,
                            filenamePattern = rule.filenamePattern ?: "",
                            minFileSizeMb = rule.minFileSizeBytes?.let { bytes -> "${bytes / 1024 / 1024}" } ?: "",
                            maxFileSizeMb = rule.maxFileSizeBytes?.let { bytes -> "${bytes / 1024 / 1024}" } ?: "",
                            minAgeDays = rule.minAgeDays?.toString() ?: "",
                            maxAgeDays = rule.maxAgeDays?.toString() ?: "",
                            excludePatternsText = rule.excludePatterns.joinToString(", "),
                            isLoading = false,
                            isTrashed = rule.trashedAt != null,
                        )
                    }
                    _baseline.value = _uiState.value.toSnapshot()
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                    _baseline.value = _uiState.value.toSnapshot()
                }
                scheduleFolderAccessRecompute()
            }

        private fun scheduleFolderAccessRecompute() {
            viewModelScope.launch(ioDispatcher) {
                val prefs = userPreferencesRepository.preferencesFlow.first()
                val filesystemAccessEnabled = isFilesystemAccessEffective(prefs.folderAccessMode)
                val allFilesGranted = Environment.isExternalStorageManager()
                val snapshot = _uiState.value
                val sourceIssues =
                    snapshot.sourceFolderPaths
                        .mapNotNull { path ->
                            val result = fileOperationRepository.resolveFolderAccess(path, filesystemAccessEnabled)
                            if (result == FolderAccessResult.Accessible) null else path to result
                        }.toMap()
                val destinationIssue =
                    if (snapshot.destinationFolderPath.isBlank()) {
                        null
                    } else {
                        val result =
                            fileOperationRepository.resolveFolderAccess(
                                snapshot.destinationFolderPath,
                                filesystemAccessEnabled,
                            )
                        if (result == FolderAccessResult.Accessible) null else result
                    }
                _uiState.update {
                    it.copy(
                        folderAccessMode = prefs.folderAccessMode,
                        allFilesAccessGranted = allFilesGranted,
                        inaccessibleSourceIssues = sourceIssues,
                        destinationFolderAccessIssue = destinationIssue,
                    )
                }
            }
        }

        fun refreshFolderAccessAfterPermissionChange() {
            fileOperationRepository.invalidateAccessCache()
            scheduleFolderAccessRecompute()
        }

        fun setName(name: String) = _uiState.update { it.copy(name = name, errors = emptyList()) }

        private fun removeRedundantPaths(paths: List<String>): Pair<List<String>, List<String>> {
            val toRemove =
                paths.filter { child ->
                    paths.any { parent -> parent != child && isChildFolder(child, parent) }
                }
            return (paths - toRemove.toSet()) to toRemove
        }

        private fun isChildFolder(
            child: String,
            parent: String,
        ): Boolean {
            if (child.startsWith("content://") && parent.startsWith("content://")) {
                return try {
                    val childId = DocumentsContract.getTreeDocumentId(child.toUri())
                    val parentId = DocumentsContract.getTreeDocumentId(parent.toUri())
                    val childVolume = childId.substringBefore(":")
                    val parentVolume = parentId.substringBefore(":")
                    if (childVolume != parentVolume) return false
                    val childPath = childId.substringAfter(":", "")
                    val parentPath = parentId.substringAfter(":", "")
                    parentPath.isNotEmpty() && childPath.startsWith("$parentPath/")
                } catch (_: Exception) {
                    false
                }
            }
            return child.startsWith("$parent/")
        }

        fun addSourceFolder(path: String) {
            _uiState.update { state ->
                val newPaths =
                    if (path in state.sourceFolderPaths) {
                        state.sourceFolderPaths
                    } else {
                        state.sourceFolderPaths + path
                    }
                if (state.scanSubdirectories) {
                    val (kept, removed) = removeRedundantPaths(newPaths)
                    state.copy(sourceFolderPaths = kept, removedRedundantFolders = removed)
                } else {
                    state.copy(sourceFolderPaths = newPaths, removedRedundantFolders = emptyList())
                }
            }
            scheduleFolderAccessRecompute()
        }

        fun removeSourceFolder(path: String) {
            _uiState.update {
                it.copy(sourceFolderPaths = it.sourceFolderPaths - path)
            }
            scheduleFolderAccessRecompute()
        }

        fun replaceSourceFolder(
            previousPath: String,
            newPath: String,
        ) {
            _uiState.update { state ->
                if (previousPath !in state.sourceFolderPaths) {
                    state
                } else {
                    val withoutPrevious = state.sourceFolderPaths - previousPath
                    val nextPaths =
                        if (newPath in withoutPrevious) {
                            withoutPrevious
                        } else {
                            withoutPrevious + newPath
                        }
                    state.copy(sourceFolderPaths = nextPaths)
                }
            }
            scheduleFolderAccessRecompute()
        }

        fun setDestination(path: String) {
            _uiState.update { it.copy(destinationFolderPath = path) }
            scheduleFolderAccessRecompute()
        }

        fun addExtension(ext: String) =
            _uiState.update {
                val normalized = ext.lowercase().let { e -> if (e.startsWith(".")) e else ".$e" }
                if (normalized in it.fileExtensions) {
                    it
                } else {
                    it.copy(fileExtensions = it.fileExtensions + normalized)
                }
            }

        fun addExtensions(exts: List<String>) {
            exts.forEach { addExtension(it) }
        }

        fun removeExtension(ext: String) =
            _uiState.update {
                it.copy(fileExtensions = it.fileExtensions - ext)
            }

        fun setSchedule(schedule: RuleSchedule?) = _uiState.update { it.copy(schedule = schedule) }

        fun setConflictPolicy(policy: ConflictPolicy) = _uiState.update { it.copy(conflictPolicy = policy) }

        fun setOperationMode(mode: OperationMode) = _uiState.update { it.copy(operationMode = mode) }

        fun setScanSubdirectories(enabled: Boolean) {
            _uiState.update { state ->
                if (enabled) {
                    val (kept, removed) = removeRedundantPaths(state.sourceFolderPaths)
                    state.copy(
                        scanSubdirectories = true,
                        recreateDestinationSubfolders = true,
                        sourceFolderPaths = kept,
                        removedRedundantFolders = removed,
                    )
                } else {
                    state.copy(
                        scanSubdirectories = false,
                        recreateDestinationSubfolders = false,
                        removedRedundantFolders = emptyList(),
                    )
                }
            }
            scheduleFolderAccessRecompute()
        }

        fun setRecreateDestinationSubfolders(enabled: Boolean) {
            _uiState.update { state ->
                state.copy(recreateDestinationSubfolders = enabled && state.scanSubdirectories)
            }
        }

        fun setSuppressMissingSourceFolderCardWarning(enabled: Boolean) = _uiState.update { it.copy(suppressMissingSourceFolderCardWarning = enabled) }

        fun dismissRedundantFolderNotice() = _uiState.update { it.copy(removedRedundantFolders = emptyList()) }

        fun setIcon(icon: RuleIcon) = _uiState.update { it.copy(icon = icon, iconEmoji = null) }

        fun setIconEmoji(emoji: String?) {
            if (emoji == null) {
                _uiState.update { it.copy(iconEmoji = null) }
                return
            }
            val trimmed = emoji.trim()
            if (trimmed.isEmpty()) {
                _uiState.update { it.copy(iconEmoji = null) }
                return
            }
            val boundary = java.text.BreakIterator.getCharacterInstance()
            boundary.setText(trimmed)
            val start = boundary.first()
            val end = boundary.next()
            val singleGrapheme = trimmed.substring(start, end)
            _uiState.update { it.copy(iconEmoji = singleGrapheme) }
        }

        // Advanced filter setters
        fun setFilenamePattern(pattern: String) = _uiState.update { it.copy(filenamePattern = pattern) }

        fun setMinFileSizeMb(value: String) = _uiState.update { it.copy(minFileSizeMb = value) }

        fun setMaxFileSizeMb(value: String) = _uiState.update { it.copy(maxFileSizeMb = value) }

        fun setMinAgeDays(value: String) = _uiState.update { it.copy(minAgeDays = value) }

        fun setMaxAgeDays(value: String) = _uiState.update { it.copy(maxAgeDays = value) }

        fun setExcludePatternsText(text: String) = _uiState.update { it.copy(excludePatternsText = text) }

        // Bookmark actions
        fun toggleBookmark(path: String) =
            viewModelScope.launch {
                val bookmarks = bookmarkedFolders.value
                if (path in bookmarks) {
                    userPreferencesRepository.removeBookmark(path)
                } else {
                    userPreferencesRepository.addBookmark(path)
                }
            }

        fun applyTemplate(template: RuleTemplate) =
            viewModelScope.launch {
                applyTemplateInternal(template)
            }

        private suspend fun applyTemplateInternal(template: RuleTemplate) {
            val prefs = userPreferencesRepository.preferencesFlow.first()
            val useAutoFilesystemSources =
                isFilesystemAccessEffective(prefs.folderAccessMode) &&
                    Environment.isExternalStorageManager()
            val autoSourcePaths: List<String> =
                if (!useAutoFilesystemSources || template.autoFilesystemSource == null) {
                    emptyList()
                } else {
                    val candidatePath =
                        when (template.autoFilesystemSource) {
                            TemplateAutoSource.SCREENSHOTS -> primaryScreenshotsDirectoryPath()
                            TemplateAutoSource.DOWNLOADS -> primaryDownloadsDirectoryPath()
                        }
                    val normalized = normalizeFilesystemFolderPath(candidatePath)
                    if (normalized != null &&
                        isFilesystemFolderPathAllowedForRules(normalized) &&
                        File(normalized).isDirectory &&
                        File(normalized).canRead()
                    ) {
                        listOf(normalized)
                    } else {
                        emptyList()
                    }
                }
            _uiState.update { state ->
                val sourcePaths =
                    if (autoSourcePaths.isNotEmpty()) autoSourcePaths else state.sourceFolderPaths
                val nextState =
                    state.copy(
                        name = if (state.name.isBlank()) template.name else state.name,
                        fileExtensions = template.extensions,
                        operationMode = template.operationMode,
                        scanSubdirectories = template.scanSubdirectories,
                        recreateDestinationSubfolders = template.scanSubdirectories,
                        icon = template.suggestedIcon,
                        sourceFolderPaths = sourcePaths,
                    )
                if (nextState.scanSubdirectories) {
                    val (kept, removed) = removeRedundantPaths(nextState.sourceFolderPaths)
                    nextState.copy(sourceFolderPaths = kept, removedRedundantFolders = removed)
                } else {
                    nextState
                }
            }
            scheduleFolderAccessRecompute()
        }

        fun dismissPreview() = _uiState.update { it.copy(previewFiles = null) }

        fun discardChanges() {
            val baseline = _baseline.value ?: return
            _uiState.update { state -> state.withSnapshot(baseline) }
            scheduleFolderAccessRecompute()
        }

        fun loadPreview() =
            viewModelScope.launch {
                val state = _uiState.value
                if (state.sourceFolderPaths.isEmpty() ||
                    state.destinationFolderPath.isBlank() ||
                    state.fileExtensions.isEmpty()
                ) {
                    return@launch
                }
                _uiState.update { it.copy(isPreviewLoading = true, previewFiles = null) }
                val rule = buildRuleFromState(state)
                val files = simulateRuleUseCase(rule)
                _uiState.update { it.copy(previewFiles = files, isPreviewLoading = false) }
            }

        fun save() =
            viewModelScope.launch {
                val state = _uiState.value
                val rule = buildRuleFromState(state)

                when (val result = validateRuleUseCase(rule)) {
                    is ValidateRuleUseCase.Result.Invalid -> {
                        _uiState.update { it.copy(errors = result.errors) }
                        return@launch
                    }

                    is ValidateRuleUseCase.Result.Valid -> {}
                }

                val savedId = ruleRepository.saveRule(rule)
                val savedRule = rule.copy(id = savedId)

                if (savedRule.isEnabled && savedRule.schedule != null) {
                    scheduleRulesUseCase.scheduleRule(savedRule)
                } else {
                    scheduleRulesUseCase.cancelRule(savedRule)
                }

                _uiState.update {
                    it.copy(id = savedId, errors = emptyList())
                }
                _baseline.value = _uiState.value.toSnapshot()
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
                _uiState.update { it.copy(isSaved = true) }
            }

        fun restoreRule() =
            viewModelScope.launch {
                ruleRepository.restoreRuleFromTrash(ruleId)
                val rule = ruleRepository.getRuleById(ruleId)
                if (rule != null && rule.isEnabled && rule.schedule != null) {
                    scheduleRulesUseCase.scheduleRule(rule)
                }
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
                _uiState.update { it.copy(isSaved = true) }
            }

        fun deleteRuleForever() =
            viewModelScope.launch {
                ruleRepository.deleteRuleForever(ruleId)
                rulesAutoExportTrigger.maybeExportAfterRuleChange()
                _uiState.update { it.copy(isSaved = true) }
            }

        private fun buildRuleFromState(state: RuleDetailUiState) =
            Rule(
                id = state.id,
                sortOrder = state.sortOrder,
                name = state.name.trim(),
                sourceFolderPaths = state.sourceFolderPaths,
                destinationFolderPath = state.destinationFolderPath,
                fileExtensions = state.fileExtensions,
                isEnabled = state.isEnabled,
                schedule = state.schedule,
                conflictPolicy = state.conflictPolicy,
                operationMode = state.operationMode,
                scanSubdirectories = state.scanSubdirectories,
                recreateDestinationSubfolders = state.scanSubdirectories && state.recreateDestinationSubfolders,
                suppressMissingSourceFolderCardWarning = state.suppressMissingSourceFolderCardWarning,
                icon = state.icon,
                iconEmoji = state.iconEmoji?.takeIf { it.isNotBlank() },
                filenamePattern = state.filenamePattern.takeIf { it.isNotBlank() },
                minFileSizeBytes =
                    state.minFileSizeMb
                        .toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.let { it * 1024 * 1024 },
                maxFileSizeBytes =
                    state.maxFileSizeMb
                        .toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.let { it * 1024 * 1024 },
                minAgeDays = state.minAgeDays.toIntOrNull()?.takeIf { it > 0 },
                maxAgeDays = state.maxAgeDays.toIntOrNull()?.takeIf { it > 0 },
                excludePatterns =
                    state.excludePatternsText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() },
            )
    }
