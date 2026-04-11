package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.domain.model.PreviewFileResult
import dev.bikram.filepipe.domain.model.Rule
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SimulateRuleUseCase @Inject constructor(
    private val fileOperationRepository: FileOperationRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(rule: Rule): List<PreviewFileResult> {
        if (rule.sourceFolderPaths.isEmpty() || rule.fileExtensions.isEmpty()) return emptyList()

        val filesystemAccessEnabled =
            isFilesystemAccessEffective(userPreferencesRepository.preferencesFlow.first().folderAccessMode)
        val fileEntries = rule.sourceFolderPaths.flatMap { path ->
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
                filesystemAccessEnabled = filesystemAccessEnabled
            )
        }

        return fileEntries.map { entry ->
            fileOperationRepository.simulateMove(
                sourceEntry = entry,
                destFolderUriString = rule.destinationFolderPath,
                conflictPolicy = rule.conflictPolicy,
                filesystemAccessEnabled = filesystemAccessEnabled
            )
        }
    }
}
