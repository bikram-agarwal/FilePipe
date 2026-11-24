package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateRuleUseCaseTest {
    private val validate = ValidateRuleUseCase()

    @Test
    fun validRuleWithDailySchedulePasses() {
        val result =
            validate(
                Rule(
                    name = "Docs",
                    sourceFolderPaths = listOf("content://source"),
                    destinationFolderPath = "content://destination",
                    fileExtensions = listOf("pdf"),
                    schedule = RuleSchedule(ScheduleType.DAILY, hour = 9, minute = 45),
                ),
            )

        assertEquals(ValidateRuleUseCase.Result.Valid, result)
    }

    @Test
    fun invalidRuleReportsRequiredPathsTypesAndSameDestination() {
        val result =
            validate(
                Rule(
                    name = " ",
                    sourceFolderPaths = listOf("content://same"),
                    destinationFolderPath = "content://same",
                    fileExtensions = emptyList(),
                ),
            ) as ValidateRuleUseCase.Result.Invalid

        assertTrue(result.errors.contains("Rule name is required"))
        assertTrue(result.errors.contains("At least one file type is required"))
        assertTrue(result.errors.contains("Source and destination folders cannot be the same"))
    }

    @Test
    fun filesystemDestinationWithTrailingSlashMatchesSource() {
        val result =
            validate(
                baseRule().copy(
                    sourceFolderPaths = listOf("/storage/emulated/0/Download"),
                    destinationFolderPath = "/storage/emulated/0/Download/",
                ),
            ) as ValidateRuleUseCase.Result.Invalid

        assertTrue(result.errors.contains("Source and destination folders cannot be the same"))
    }

    @Test
    fun equivalentSafTreeAndDocumentUrisMatch() {
        val result =
            validate(
                baseRule().copy(
                    sourceFolderPaths =
                        listOf(
                            "content://com.android.externalstorage.documents/tree/primary%3ADownload",
                        ),
                    destinationFolderPath =
                        "content://com.android.externalstorage.documents/document/primary%3ADownload",
                ),
            ) as ValidateRuleUseCase.Result.Invalid

        assertTrue(result.errors.contains("Source and destination folders cannot be the same"))
    }

    @Test
    fun scheduledRulesValidateTheirRequiredTimeFields() {
        val weeklyErrors =
            validate(
                baseRule().copy(
                    schedule = RuleSchedule(ScheduleType.WEEKLY, dayOfWeek = null, hour = 24, minute = 60),
                ),
            ) as ValidateRuleUseCase.Result.Invalid
        val intervalErrors =
            validate(
                baseRule().copy(
                    schedule = RuleSchedule(ScheduleType.EVERY_N_HOURS, hour = 0, minute = 0, repeatInterval = 25),
                ),
            ) as ValidateRuleUseCase.Result.Invalid

        assertTrue(weeklyErrors.errors.contains("Weekday is required for weekly schedule"))
        assertTrue(weeklyErrors.errors.contains("Invalid hour in schedule"))
        assertTrue(weeklyErrors.errors.contains("Invalid minute in schedule"))
        assertEquals(listOf("Interval must be between 1 and 24 hours"), intervalErrors.errors)
    }

    @Test
    fun scheduledRulesValidateRepeatIntervalsForTheirUnits() {
        val dailyErrors =
            validate(
                baseRule().copy(
                    schedule = RuleSchedule(ScheduleType.DAILY, hour = 9, minute = 0, repeatInterval = 366),
                ),
            ) as ValidateRuleUseCase.Result.Invalid
        val weeklyErrors =
            validate(
                baseRule().copy(
                    schedule = RuleSchedule(ScheduleType.WEEKLY, dayOfWeek = 2, hour = 9, minute = 0, repeatInterval = 53),
                ),
            ) as ValidateRuleUseCase.Result.Invalid

        assertEquals(listOf("Interval must be between 1 and 365 days"), dailyErrors.errors)
        assertEquals(listOf("Interval must be between 1 and 52 weeks"), weeklyErrors.errors)
    }

    @Test
    fun invalidRegexPatternFailsValidation() {
        val result =
            validate(
                baseRule().copy(
                    isRegexPattern = true,
                    filenamePattern = "IMG_\\d+(",
                ),
            ) as ValidateRuleUseCase.Result.Invalid

        assertTrue(result.errors.contains("Invalid regular expression syntax"))
    }

    @Test
    fun validRegexPatternPassesValidation() {
        val result =
            validate(
                baseRule().copy(
                    isRegexPattern = true,
                    filenamePattern = "^IMG_\\d+\\.(jpg|png)$",
                ),
            )

        assertEquals(ValidateRuleUseCase.Result.Valid, result)
    }

    @Test
    fun validAllFilesRulePassesValidation() {
        val result =
            validate(
                baseRule().copy(
                    fileExtensions = listOf("*"),
                ),
            )

        assertEquals(ValidateRuleUseCase.Result.Valid, result)
    }

    @Test
    fun validNoExtensionRulePassesValidation() {
        val result =
            validate(
                baseRule().copy(
                    fileExtensions = listOf("[no_ext]"),
                ),
            )

        assertEquals(ValidateRuleUseCase.Result.Valid, result)
    }

    @Test
    fun invalidExcludeRegexPatternFailsValidation() {
        val result =
            validate(
                baseRule().copy(
                    isExcludeRegexPattern = true,
                    excludePatterns = listOf(".nomedia", "([invalid"),
                ),
            ) as ValidateRuleUseCase.Result.Invalid

        assertTrue(result.errors.contains("Invalid regular expression syntax in exclude patterns"))
    }

    @Test
    fun deleteRuleWithoutDestinationPassesValidation() {
        val result =
            validate(
                Rule(
                    name = "Delete Temp Files",
                    sourceFolderPaths = listOf("content://source"),
                    destinationFolderPath = "",
                    fileExtensions = listOf("tmp"),
                    operationMode = OperationMode.DELETE,
                ),
            )

        assertEquals(ValidateRuleUseCase.Result.Valid, result)
    }

    @Test
    fun excludeRegexSplitIntoAnInvalidPieceIsRefused() {
        // A stored pattern that doesn't compile on its own - e.g. split naively at a comma by an older
        // build - is refused, whatever the rest of the list holds.
        val result =
            validate(
                baseRule().copy(isExcludeRegexPattern = true, excludePatterns = listOf("^tmp_\\d{2", "4}$")),
            )

        assertTrue(result is ValidateRuleUseCase.Result.Invalid)
    }

    @Test
    fun excludeRegexFolderPatternIsChecked() {
        assertEquals(
            ValidateRuleUseCase.Result.Valid,
            validate(baseRule().copy(isExcludeRegexPattern = true, excludePatterns = listOf("""\.thumbnails/""", "^cache$/"))),
        )
        assertTrue(
            validate(baseRule().copy(isExcludeRegexPattern = true, excludePatterns = listOf("[/"))) is ValidateRuleUseCase.Result.Invalid,
        )
    }

    private fun baseRule(): Rule =
        Rule(
            name = "Screenshots",
            sourceFolderPaths = listOf("content://source"),
            destinationFolderPath = "content://destination",
            fileExtensions = listOf("png"),
        )
}
