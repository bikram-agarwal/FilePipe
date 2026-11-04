package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.DestinationFolderCache
import dev.bikram.filepipe.data.repository.FileEntry
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.repository.canonicalIdentity
import dev.bikram.filepipe.data.repository.normalizeSourcePath
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.PreviewFileResult
import dev.bikram.filepipe.domain.model.Rule
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class PreparedRuleSimulation(
    val fileEntries: List<FileEntry>,
    val previewResults: List<PreviewFileResult>,
)

class SimulateRuleUseCase
    @Inject
    constructor(
        private val fileOperationRepository: FileOperationRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) {
        suspend operator fun invoke(rule: Rule): List<PreviewFileResult> = prepare(rule).previewResults

        suspend fun prepare(rule: Rule): PreparedRuleSimulation {
            if (rule.sourceFolderPaths.isEmpty() || rule.fileExtensions.isEmpty()) {
                return PreparedRuleSimulation(fileEntries = emptyList(), previewResults = emptyList())
            }

            val filesystemAccessEnabled =
                isFilesystemAccessEffective(userPreferencesRepository.preferencesFlow.first().folderAccessMode)
            val destinationFolderCache = DestinationFolderCache()
            val fileEntries =
                rule.sourceFolderPaths
                    .distinctBy { path -> normalizeSourcePath(path, filesystemAccessEnabled) }
                    .flatMap { path ->
                        fileOperationRepository.listMatchingFiles(
                            folderUriString = path,
                            extensions = rule.fileExtensions,
                            scanSubdirectories = rule.scanSubdirectories,
                            filenamePattern = rule.filenamePattern,
                            minFileSizeBytes = rule.minFileSizeBytes,
                            maxFileSizeBytes = rule.maxFileSizeBytes,
                            minAgeDays = rule.minAgeDays,
                            maxAgeDays = rule.maxAgeDays,
                            excludePatterns = rule.excludePatterns,
                            filesystemAccessEnabled = filesystemAccessEnabled,
                            orientation = rule.orientation,
                            isRegexPattern = rule.isRegexPattern,
                            isExcludeRegexPattern = rule.isExcludeRegexPattern,
                            // Never rescan the destination when it sits inside a recursively scanned source.
                            excludedFolderUriString =
                                rule.destinationFolderPath.takeIf { rule.operationMode != OperationMode.DELETE },
                        )
                    }.distinctBy { entry -> entry.canonicalIdentity() }

            return PreparedRuleSimulation(
                fileEntries = fileEntries,
                previewResults =
                    fileEntries.map { entry ->
                        val destinationEntry =
                            if (rule.recreateDestinationSubfolders) {
                                entry
                            } else {
                                entry.copy(relativeParentSegments = emptyList())
                            }
                        fileOperationRepository.simulateMove(
                            sourceEntry = destinationEntry,
                            destFolderUriString = rule.destinationFolderPath,
                            conflictPolicy = rule.conflictPolicy,
                            operationMode = rule.operationMode,
                            destinationFolderCache = destinationFolderCache,
                            filesystemAccessEnabled = filesystemAccessEnabled,
                        )
                    },
            )
        }
    }
