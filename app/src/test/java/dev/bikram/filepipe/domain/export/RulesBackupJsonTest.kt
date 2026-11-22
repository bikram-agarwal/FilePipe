package dev.bikram.filepipe.domain.export

import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.FileUndoStatus
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.domain.model.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RulesBackupJsonTest {
    @Test
    fun backupCanBeDecodedDirectlyFromStream() {
        val json =
            buildAppBackupJson(
                rules =
                    listOf(
                        Rule(
                            name = "Streamed",
                            sourceFolderPaths = listOf("content://source"),
                            destinationFolderPath = "content://destination",
                            fileExtensions = listOf("txt"),
                        ),
                    ),
            )

        val backup =
            ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)).use { inputStream ->
                parseRulesBackupJson(inputStream).getOrThrow()
            }

        assertEquals("Streamed", backup.rules.single().name)
    }

    @Test
    fun legacyFileUndoStatusDefaultsToPending() {
        val legacyBackup =
            parseRulesBackupJson(
                """
                {
                  "version": 11,
                  "rules": [],
                  "history": [{
                    "ruleName": "Legacy",
                    "triggeredBy": "MANUAL",
                    "startedAt": 100,
                    "status": "SUCCESS",
                    "totalFilesMoved": 1,
                    "totalFilesFailed": 0,
                    "files": [{
                      "fileName": "legacy.txt",
                      "sourceUri": "content://source/legacy.txt",
                      "destinationUri": "content://destination/legacy.txt",
                      "fileSizeBytes": 10,
                      "movedAt": 123,
                      "success": true
                    }]
                  }]
                }
                """.trimIndent(),
            ).getOrThrow()

        assertEquals(
            FileUndoStatus.PENDING.name,
            legacyBackup.history
                .single()
                .files
                .single()
                .undoStatus,
        )
    }

    @Test
    fun appBackupRoundTripPreservesRulesHistoryFilesAndSettings() {
        val rule =
            Rule(
                id = 42L,
                name = "Screenshots",
                sourceFolderPaths = listOf("content://source"),
                destinationFolderPath = "content://destination",
                fileExtensions = listOf(".png", "jpg"),
                isEnabled = true,
                sortOrder = 3,
                schedule = RuleSchedule(ScheduleType.WEEKLY, dayOfWeek = 2, hour = 8, minute = 30),
                conflictPolicy = ConflictPolicy.OVERWRITE,
                operationMode = OperationMode.COPY,
                scanSubdirectories = true,
                recreateDestinationSubfolders = true,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.SCREENSHOT,
                iconEmoji = "\uD83D\uDCF7",
                filenamePattern = "IMG_*",
                minFileSizeBytes = 1_024L,
                maxFileSizeBytes = 9_999L,
                minAgeDays = 1,
                maxAgeDays = 30,
                excludePatterns = listOf("*tmp*"),
                isRegexPattern = true,
                isExcludeRegexPattern = true,
            )
        val movedFile =
            FileMoved(
                runHistoryId = 7L,
                fileName = "IMG_1.png",
                sourceUri = "content://source/IMG_1.png",
                destinationUri = "content://destination/IMG_1.png",
                fileSizeBytes = 2_048L,
                relativeParentSegments = listOf("Camera", "Raw"),
                movedAt = 123L,
                success = true,
                undoStatus = FileUndoStatus.UNDONE,
            )
        val history =
            RunHistory(
                id = 100L,
                ruleId = rule.id,
                ruleName = "Screenshots",
                triggeredBy = TriggerType.SCHEDULED,
                startedAt = 1000L,
                completedAt = 2000L,
                status = RunStatus.SUCCESS,
                totalFilesMoved = 1,
                totalFilesFailed = 0,
                operationMode = OperationMode.COPY,
                copyCreatedDestFolderUris = listOf("content://destination/Camera"),
            )
        val settings =
            AppPreferences(
                autoExportOnRuleChange = true,
                scheduledExportEnabled = true,
                cloudExportFolderUri = "content://backup/cloud",
                bookmarkedFolders = listOf("content://source"),
            )

        val backup = parseRulesBackupJson(buildAppBackupJson(listOf(rule), listOf(history to listOf(movedFile)), settings)).getOrThrow()

        assertEquals(APP_DATABASE_SCHEMA_VERSION, backup.version)
        val restoredRule = backup.rules.single().toDomain()
        assertEquals(0L, restoredRule.id)
        assertEquals(rule.name, restoredRule.name)
        assertEquals(rule.conflictPolicy, restoredRule.conflictPolicy)
        assertEquals(rule.operationMode, restoredRule.operationMode)
        assertEquals(rule.schedule, restoredRule.schedule)
        assertTrue(restoredRule.recreateDestinationSubfolders)
        assertTrue(restoredRule.suppressMissingSourceFolderCardWarning)
        assertEquals(RuleIcon.SCREENSHOT, restoredRule.icon)
        assertEquals(rule.excludePatterns, restoredRule.excludePatterns)
        assertTrue(restoredRule.isRegexPattern)
        assertTrue(restoredRule.isExcludeRegexPattern)

        val restoredHistory = backup.history.single()
        assertEquals(0, restoredHistory.ruleIndexInBackup)
        assertEquals("SCHEDULED", restoredHistory.triggeredBy)
        assertEquals("COPY", restoredHistory.operationMode)
        assertEquals(listOf("content://destination/Camera"), restoredHistory.copyCreatedDestFolderUris)
        assertEquals(listOf("Camera", "Raw"), restoredHistory.files.single().relativeParentSegments)
        assertEquals(FileUndoStatus.UNDONE.name, restoredHistory.files.single().undoStatus)

        val restoredSettings = backup.settings!!
        assertTrue(restoredSettings.autoExportOnRuleChange)
        assertTrue(restoredSettings.scheduledExportEnabled)
        assertEquals("content://backup/cloud", restoredSettings.cloudExportFolderUri)
        assertEquals(listOf("content://source"), restoredSettings.bookmarkedFolders)
    }

    @Test
    fun olderBackupWildcardFilenamePatternBecomesOnePerLine() {
        // Written before patterns were one per line: no marker field, and every comma split the box.
        val legacy =
            RuleBackupDto(
                name = "Legacy glob",
                sourceFolderPaths = listOf("content://source"),
                destinationFolderPath = "content://destination",
                fileExtensions = listOf("jpg"),
                filenamePattern = "IMG_*, PXL_*",
            ).toDomain()

        assertEquals("IMG_*\nPXL_*", legacy.filenamePattern)
    }

    @Test
    fun olderBackupRegexFilenamePatternIsAlreadyOneLine() {
        // A RegEx was one expression, where its commas were literal; it stays exactly as it was.
        val legacy =
            RuleBackupDto(
                name = "Legacy regex",
                sourceFolderPaths = listOf("content://source"),
                destinationFolderPath = "content://destination",
                fileExtensions = listOf("jpg"),
                filenamePattern = "^backup, old \\d{2,4}",
                isRegexPattern = true,
            ).toDomain()

        assertEquals("^backup, old \\d{2,4}", legacy.filenamePattern)
    }

    @Test
    fun newBackupRoundTripsLinesAndCommasUnchanged() {
        val rule =
            Rule(
                name = "Two patterns",
                sourceFolderPaths = listOf("content://source"),
                destinationFolderPath = "content://destination",
                fileExtensions = listOf("pdf"),
                filenamePattern = "Invoice, *\nReceipt*",
            )

        val dto = rule.toBackupDto()
        assertTrue(dto.patternsOnePerLine)
        assertEquals("Invoice, *\nReceipt*", dto.toDomain().filenamePattern)
    }

    @Test
    fun malformedLegacyRuleFieldsFallbackToSafeDefaults() {
        val domainRule =
            RuleBackupDto(
                name = "Legacy",
                sourceFolderPaths = listOf("content://source"),
                destinationFolderPath = "content://destination",
                fileExtensions = listOf("pdf"),
                schedule = ScheduleBackupDto(type = "SUNRISE", hour = 6, minute = 15),
                conflictPolicy = "PROMPT",
                operationMode = "ARCHIVE",
                recreateDestinationSubfolders = null,
                scanSubdirectories = false,
                iconKey = "UNKNOWN",
                iconEmoji = "",
            ).toDomain()

        assertEquals(ConflictPolicy.RENAME_SUFFIX, domainRule.conflictPolicy)
        assertEquals(OperationMode.MOVE, domainRule.operationMode)
        assertNull(domainRule.schedule)
        assertFalse(domainRule.recreateDestinationSubfolders)
        assertEquals(RuleIcon.DEFAULT, domainRule.icon)
        assertNull(domainRule.iconEmoji)
    }

    @Test
    fun deleteOperationModeRoundTrip() {
        val rule =
            Rule(
                name = "Delete Temp Files",
                sourceFolderPaths = listOf("content://source"),
                destinationFolderPath = "",
                fileExtensions = listOf("tmp", "bak"),
                operationMode = OperationMode.DELETE,
            )

        val backup = parseRulesBackupJson(buildAppBackupJson(listOf(rule), emptyList(), null)).getOrThrow()
        val restoredRule = backup.rules.single().toDomain()
        assertEquals(OperationMode.DELETE, restoredRule.operationMode)
    }

    @Test
    fun intervalScheduleWithoutStartTimeSurvivesRoundTrip() {
        val rule =
            Rule(
                name = "Every few hours",
                sourceFolderPaths = listOf("content://source"),
                destinationFolderPath = "content://destination",
                fileExtensions = listOf("txt"),
                schedule =
                    RuleSchedule(
                        type = ScheduleType.EVERY_N_HOURS,
                        hour = 0,
                        minute = 0,
                        repeatInterval = 3,
                        usesStartTime = false,
                    ),
            )

        val restoredRule =
            parseRulesBackupJson(buildAppBackupJson(listOf(rule)))
                .getOrThrow()
                .rules
                .single()
                .toDomain()

        assertEquals(rule.schedule, restoredRule.schedule)
        assertFalse(restoredRule.schedule!!.usesStartTime)
    }

    @Test
    fun legacyIntervalScheduleDefaultsToUsingStartTime() {
        val legacySchedule =
            ScheduleBackupDto(
                type = ScheduleType.EVERY_N_HOURS.name,
                hour = 8,
                minute = 30,
                intervalHours = 2,
            )

        assertTrue(legacySchedule.toDomain()!!.usesStartTime)
    }
}
