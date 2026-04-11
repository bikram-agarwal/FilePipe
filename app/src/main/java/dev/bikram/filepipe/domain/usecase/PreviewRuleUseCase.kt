package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.data.preferences.UserPreferencesRepository
import dev.bikram.filepipe.data.repository.FileEntry
import dev.bikram.filepipe.data.repository.FileOperationRepository
import dev.bikram.filepipe.data.storage.isFilesystemAccessEffective
import dev.bikram.filepipe.domain.model.Rule
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PreviewRuleUseCase @Inject constructor(
    private val fileOperationRepository: FileOperationRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(rule: Rule): List<FileEntry> {
        if (rule.sourceFolderPaths.isEmpty() || rule.fileExtensions.isEmpty()) return emptyList()
        val filesystemAccessEnabled =
            isFilesystemAccessEffective(userPreferencesRepository.preferencesFlow.first().folderAccessMode)
        return rule.sourceFolderPaths.flatMap { path ->
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
    }
}
