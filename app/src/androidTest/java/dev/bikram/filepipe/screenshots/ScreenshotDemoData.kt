package dev.bikram.filepipe.screenshots

import android.os.Environment
import dev.bikram.filepipe.data.preferences.AppColorSource
import dev.bikram.filepipe.data.preferences.AppPreferences
import dev.bikram.filepipe.data.preferences.AppThemeMode
import dev.bikram.filepipe.data.preferences.FolderAccessMode
import dev.bikram.filepipe.data.preferences.ThemePaletteStyle
import dev.bikram.filepipe.data.preferences.UpdateCheckSchedule
import dev.bikram.filepipe.devtools.DevMockFileMove
import dev.bikram.filepipe.domain.export.buildAppBackupJson
import dev.bikram.filepipe.domain.model.ConflictPolicy
import dev.bikram.filepipe.domain.model.FileMoved
import dev.bikram.filepipe.domain.model.FileUndoStatus
import dev.bikram.filepipe.domain.model.HistorySortDirection
import dev.bikram.filepipe.domain.model.HistorySortKey
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleIcon
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.RunHistory
import dev.bikram.filepipe.domain.model.RunStatus
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.domain.model.TriggerType
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ScreenshotDemoData {
    private const val STORAGE_ROOT = "/storage/emulated/0"

    private val demoDirectoryRelativePaths =
        listOf(
            "Pictures/Videos",
            "Pictures/Photos",
            "Pictures/Socials",
            "Pictures/WeChat",
            "Pictures/Reddit",
            "Pictures/Clips",
            "DCIM/TikTok",
            "Movies",
            "Movies/Exports",
            "Movies/Captures",
            "Download",
            "Download/Piko-Instagram",
            "Download/Telegram",
            "Documents",
            "Music",
            "Music/Incoming",
            "FilePipe Mock/Incoming",
            "FilePipe Mock/Organized",
        )

    private val mockMovedFileNames =
        listOf(
            "conference-recording-01.mp4",
            "camera-roll-raw-01.mov",
            "family-photo-archive.jpg",
            "screenshot-home-screen.png",
            "podcast-mixdown.mp3",
            "ambient-session.flac",
            "quarterly-report.pdf",
            "contract-draft.docx",
            "project-backup.zip",
            "sensor-capture.dat",
        )

    fun ensureDemoDirectoriesExist() {
        val rootPath =
            runCatching { Environment.getExternalStorageDirectory().canonicalPath }
                .getOrDefault(STORAGE_ROOT)
        demoDirectoryRelativePaths.forEach { relativePath ->
            runCatching {
                File(rootPath, relativePath).mkdirs()
            }
        }
    }

    fun buildBasePreferences(
        themeMode: AppThemeMode = AppThemeMode.DARK,
        useBlackTheme: Boolean = false,
        colorSource: AppColorSource = AppColorSource.CURATED_GROVE,
        themePaletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
        activeCustomSeedHex: String = "",
        savedCustomSeedHexes: List<String> = emptyList(),
        useGradientBackground: Boolean = true,
    ): AppPreferences =
        AppPreferences.DEFAULT.copy(
            themeMode = themeMode,
            useBlackTheme = useBlackTheme,
            colorSource = colorSource,
            themePaletteStyle = themePaletteStyle,
            activeCustomSeedHex = activeCustomSeedHex,
            savedCustomSeedHexes = savedCustomSeedHexes,
            useGradientBackground = useGradientBackground,
            progressiveBlurEnabled = true,
            hasSeenIntro = true,
            folderAccessMode = FolderAccessMode.ALL_FILES_PREFERRED,
            rulesSortKey = HistorySortKey.MY_ORDER,
            rulesSortDirection = HistorySortDirection.ASCENDING,
            rulesCompactMode = false,
            historySortKey = HistorySortKey.LAST_RAN,
            historySortDirection = HistorySortDirection.DESCENDING,
            settingsCollapsedSectionKeys = emptyList(),
            updateCheckSchedule = UpdateCheckSchedule.NEVER,
            notifyOnNewUpdates = false,
            autoExportOnRuleChange = false,
            scheduledExportEnabled = false,
        )

    fun buildPhoneRules(): List<Rule> {
        val createdTimestamp = System.currentTimeMillis() - 86_400_000L
        val dailyAtEightPm =
            RuleSchedule(
                type = ScheduleType.DAILY,
                hour = 20,
                minute = 0,
            )
        return listOf(
            Rule(
                id = 1L,
                name = "Videos",
                sourceFolderPaths =
                    listOf(
                        "$STORAGE_ROOT/Pictures/Videos",
                        "$STORAGE_ROOT/DCIM/TikTok",
                        "$STORAGE_ROOT/Movies",
                        "$STORAGE_ROOT/Movies/Exports",
                        "$STORAGE_ROOT/Download/Telegram",
                        "$STORAGE_ROOT/Pictures/Clips",
                    ),
                destinationFolderPath = "$STORAGE_ROOT/Pictures/Photos",
                fileExtensions = listOf("mp4", "mov", "mkv", "avi", "webm"),
                isEnabled = true,
                sortOrder = 0,
                cardModeOverride = false,
                groupName = "Media",
                schedule = dailyAtEightPm,
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.MOVE,
                scanSubdirectories = true,
                recreateDestinationSubfolders = true,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.VIDEO,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
            Rule(
                id = 2L,
                name = "Images",
                sourceFolderPaths =
                    listOf(
                        "$STORAGE_ROOT/DCIM/TikTok",
                        "$STORAGE_ROOT/Download/Piko-Instagram",
                        "$STORAGE_ROOT/Pictures/WeChat",
                        "$STORAGE_ROOT/Pictures/Reddit",
                    ),
                destinationFolderPath = "$STORAGE_ROOT/Pictures/Photos",
                fileExtensions = listOf("jpg", "jpeg", "png", "gif", "webp"),
                isEnabled = true,
                sortOrder = 1,
                cardModeOverride = false,
                groupName = "Media",
                schedule = dailyAtEightPm,
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.COPY,
                scanSubdirectories = true,
                recreateDestinationSubfolders = true,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.IMAGE,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
            Rule(
                id = 3L,
                name = "Music",
                sourceFolderPaths =
                    listOf(
                        "$STORAGE_ROOT/Download",
                        "$STORAGE_ROOT/Music/Incoming",
                    ),
                destinationFolderPath = "$STORAGE_ROOT/Music",
                fileExtensions = listOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus"),
                isEnabled = true,
                sortOrder = 2,
                cardModeOverride = true,
                groupName = "Media",
                schedule = null,
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.MOVE,
                scanSubdirectories = false,
                recreateDestinationSubfolders = false,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.MUSIC,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
            Rule(
                id = 4L,
                name = "Installables",
                sourceFolderPaths = listOf("$STORAGE_ROOT/Download"),
                destinationFolderPath = "",
                fileExtensions = listOf("apk", "apkm", "xapk", "zip"),
                isEnabled = true,
                sortOrder = 3,
                cardModeOverride = false,
                schedule =
                    RuleSchedule(
                        type = ScheduleType.DAILY,
                        hour = 22,
                        minute = 0,
                    ),
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.DELETE,
                scanSubdirectories = false,
                recreateDestinationSubfolders = false,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.INSTALLABLE,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
            Rule(
                id = 5L,
                name = "Documents",
                sourceFolderPaths = listOf("$STORAGE_ROOT/Download"),
                destinationFolderPath = "$STORAGE_ROOT/Documents",
                fileExtensions = listOf("pdf", "docx", "doc", "txt", "odt"),
                isEnabled = true,
                sortOrder = 4,
                cardModeOverride = false,
                schedule =
                    RuleSchedule(
                        type = ScheduleType.DAILY,
                        hour = 20,
                        minute = 0,
                    ),
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.MOVE,
                scanSubdirectories = false,
                recreateDestinationSubfolders = false,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.DOCUMENT,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
        )
    }

    fun buildTabletRules(): List<Rule> {
        val createdTimestamp = System.currentTimeMillis() - 86_400_000L
        return listOf(
            Rule(
                id = 1L,
                name = "Images",
                sourceFolderPaths =
                    listOf(
                        "$STORAGE_ROOT/DCIM/TikTok",
                        "$STORAGE_ROOT/Download/Piko-Instagram",
                    ),
                destinationFolderPath = "$STORAGE_ROOT/Pictures/Socials",
                fileExtensions = listOf("jpg", "jpeg", "png", "gif", "heic", "webp"),
                isEnabled = true,
                sortOrder = 0,
                cardModeOverride = false,
                groupName = "Media",
                schedule =
                    RuleSchedule(
                        type = ScheduleType.DAILY,
                        hour = 21,
                        minute = 0,
                    ),
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.MOVE,
                scanSubdirectories = true,
                recreateDestinationSubfolders = true,
                suppressMissingSourceFolderCardWarning = false,
                icon = RuleIcon.IMAGE,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
            Rule(
                id = 2L,
                name = "Videos",
                sourceFolderPaths =
                    listOf(
                        "$STORAGE_ROOT/Pictures/Videos",
                        "$STORAGE_ROOT/DCIM/TikTok",
                        "$STORAGE_ROOT/Movies",
                        "$STORAGE_ROOT/Movies/Captures",
                    ),
                destinationFolderPath = "$STORAGE_ROOT/Pictures/Socials",
                fileExtensions = listOf("mp4", "mov", "mkv", "avi", "webm"),
                isEnabled = true,
                sortOrder = 1,
                cardModeOverride = false,
                groupName = "Media",
                schedule =
                    RuleSchedule(
                        type = ScheduleType.DAILY,
                        hour = 21,
                        minute = 5,
                    ),
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.COPY,
                scanSubdirectories = true,
                recreateDestinationSubfolders = true,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.VIDEO,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
            Rule(
                id = 3L,
                name = "Music",
                sourceFolderPaths = listOf("$STORAGE_ROOT/Music/Incoming"),
                destinationFolderPath = "$STORAGE_ROOT/Music",
                fileExtensions = listOf("mp3", "flac", "wav"),
                isEnabled = true,
                sortOrder = 2,
                cardModeOverride = true,
                groupName = "Media",
                schedule = null,
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.MOVE,
                scanSubdirectories = false,
                recreateDestinationSubfolders = false,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.MUSIC,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
            Rule(
                id = 4L,
                name = "Installables",
                sourceFolderPaths = listOf("$STORAGE_ROOT/Download"),
                destinationFolderPath = "",
                fileExtensions = listOf("apk", "apkm", "xapk", "zip"),
                isEnabled = true,
                sortOrder = 3,
                cardModeOverride = false,
                schedule = null,
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.DELETE,
                scanSubdirectories = false,
                recreateDestinationSubfolders = false,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.INSTALLABLE,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
            Rule(
                id = 5L,
                name = "Documents",
                sourceFolderPaths = listOf("$STORAGE_ROOT/Download"),
                destinationFolderPath = "$STORAGE_ROOT/Documents",
                fileExtensions = listOf("pdf", "docx", "doc", "txt", "odt"),
                isEnabled = true,
                sortOrder = 4,
                cardModeOverride = false,
                schedule =
                    RuleSchedule(
                        type = ScheduleType.DAILY,
                        hour = 20,
                        minute = 0,
                    ),
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.MOVE,
                scanSubdirectories = false,
                recreateDestinationSubfolders = false,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.DOCUMENT,
                createdAt = createdTimestamp,
                updatedAt = createdTimestamp,
            ),
        )
    }

    fun buildPhoneDateGroupedHistoryBackupJson(settings: AppPreferences): String {
        val rules = buildPhoneRules()
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val yesterdayEightPm =
            today
                .minusDays(1)
                .atTime(LocalTime.of(20, 0))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        val twoDaysAgoLate =
            today
                .minusDays(2)
                .atTime(LocalTime.of(22, 52, 30))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        val twoDaysAgoLateEarlier = twoDaysAgoLate - 15_000L
        val twoDaysAgoEightPm =
            today
                .minusDays(2)
                .atTime(LocalTime.of(20, 0, 20))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        val twoDaysAgoEightPmEarlier = twoDaysAgoEightPm - 15_000L
        val threeDaysAgoEightPm =
            today
                .minusDays(3)
                .atTime(LocalTime.of(20, 0, 20))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        val threeDaysAgoEightPmEarlier = threeDaysAgoEightPm - 15_000L

        val historyEntries: List<Pair<RunHistory, List<FileMoved>>> =
            listOf(
                RunHistory(
                    ruleId = 1L,
                    ruleName = "Videos",
                    triggeredBy = TriggerType.SCHEDULED,
                    startedAt = yesterdayEightPm,
                    completedAt = yesterdayEightPm + 1_200L,
                    status = RunStatus.SUCCESS,
                    totalFilesFound = 1,
                    totalFilesMoved = 1,
                    operationMode = OperationMode.MOVE,
                ) to
                    listOf(
                        FileMoved(
                            fileName = "clip-evening-walk.mp4",
                            sourceUri = "file://$STORAGE_ROOT/DCIM/TikTok/clip-evening-walk.mp4",
                            destinationUri = "file://$STORAGE_ROOT/Pictures/Photos/clip-evening-walk.mp4",
                            fileSizeBytes = 42_500_000L,
                            movedAt = yesterdayEightPm + 900L,
                            success = true,
                        ),
                    ),
                RunHistory(
                    ruleId = 2L,
                    ruleName = "Images",
                    triggeredBy = TriggerType.MANUAL,
                    startedAt = twoDaysAgoLate,
                    completedAt = twoDaysAgoLate + 400L,
                    status = RunStatus.SUCCESS,
                    totalFilesFound = 0,
                    totalFilesMoved = 0,
                    operationMode = OperationMode.COPY,
                ) to emptyList(),
                RunHistory(
                    ruleId = 1L,
                    ruleName = "Videos",
                    triggeredBy = TriggerType.MANUAL,
                    startedAt = twoDaysAgoLateEarlier,
                    completedAt = twoDaysAgoLateEarlier + 450L,
                    status = RunStatus.SUCCESS,
                    totalFilesFound = 0,
                    totalFilesMoved = 0,
                    operationMode = OperationMode.MOVE,
                ) to emptyList(),
                RunHistory(
                    ruleId = 2L,
                    ruleName = "Images",
                    triggeredBy = TriggerType.SCHEDULED,
                    startedAt = twoDaysAgoEightPm,
                    completedAt = twoDaysAgoEightPm + 400L,
                    status = RunStatus.SUCCESS,
                    totalFilesFound = 0,
                    totalFilesMoved = 0,
                    operationMode = OperationMode.COPY,
                ) to emptyList(),
                RunHistory(
                    ruleId = 1L,
                    ruleName = "Videos",
                    triggeredBy = TriggerType.SCHEDULED,
                    startedAt = twoDaysAgoEightPmEarlier,
                    completedAt = twoDaysAgoEightPmEarlier + 2_100L,
                    status = RunStatus.SUCCESS,
                    totalFilesFound = 3,
                    totalFilesMoved = 3,
                    operationMode = OperationMode.MOVE,
                ) to
                    (1..3).map { clipNumber ->
                        FileMoved(
                            fileName = "reel-0$clipNumber.mp4",
                            sourceUri = "file://$STORAGE_ROOT/Movies/reel-0$clipNumber.mp4",
                            destinationUri = "file://$STORAGE_ROOT/Pictures/Photos/reel-0$clipNumber.mp4",
                            fileSizeBytes = 28_000_000L,
                            movedAt = twoDaysAgoEightPmEarlier + clipNumber * 500L,
                            success = true,
                        )
                    },
                RunHistory(
                    ruleId = 1L,
                    ruleName = "Videos",
                    triggeredBy = TriggerType.SCHEDULED,
                    startedAt = threeDaysAgoEightPm,
                    completedAt = threeDaysAgoEightPm + 2_000L,
                    status = RunStatus.SUCCESS,
                    totalFilesFound = 3,
                    totalFilesMoved = 3,
                    operationMode = OperationMode.MOVE,
                ) to
                    (1..3).map { clipNumber ->
                        FileMoved(
                            fileName = "highlight-0$clipNumber.mp4",
                            sourceUri = "file://$STORAGE_ROOT/Pictures/Videos/highlight-0$clipNumber.mp4",
                            destinationUri = "file://$STORAGE_ROOT/Pictures/Photos/highlight-0$clipNumber.mp4",
                            fileSizeBytes = 31_000_000L,
                            movedAt = threeDaysAgoEightPm + clipNumber * 500L,
                            success = true,
                        )
                    },
                RunHistory(
                    ruleId = 2L,
                    ruleName = "Images",
                    triggeredBy = TriggerType.SCHEDULED,
                    startedAt = threeDaysAgoEightPmEarlier,
                    completedAt = threeDaysAgoEightPmEarlier + 350L,
                    status = RunStatus.SUCCESS,
                    totalFilesFound = 0,
                    totalFilesMoved = 0,
                    operationMode = OperationMode.COPY,
                ) to emptyList(),
            )

        return buildAppBackupJson(
            rules = rules,
            history = historyEntries,
            settings = settings,
        )
    }

    fun buildMockMoveDetailBackupJson(
        settings: AppPreferences,
        includeTabletStatusVariety: Boolean,
    ): String {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val primaryRunStart =
            today
                .atTime(LocalTime.of(12, 36, 10))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        val mockMoveRule =
            Rule(
                id = 1L,
                name = "Mock move",
                sourceFolderPaths = listOf(DevMockFileMove.SOURCE_FOLDER_URI),
                destinationFolderPath = DevMockFileMove.DESTINATION_FOLDER_URI,
                fileExtensions = listOf("mp4", "mov", "jpg", "png", "mp3", "flac", "pdf", "docx", "zip", "dat"),
                isEnabled = true,
                sortOrder = 0,
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.MOVE,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.DEFAULT,
            )
        val testRule =
            Rule(
                id = 2L,
                name = "Test",
                sourceFolderPaths = listOf("$STORAGE_ROOT/Download"),
                destinationFolderPath = "$STORAGE_ROOT/Pictures/Photos",
                fileExtensions = listOf("jpg", "png"),
                isEnabled = true,
                sortOrder = 1,
                conflictPolicy = ConflictPolicy.RENAME_SUFFIX,
                operationMode = OperationMode.MOVE,
                suppressMissingSourceFolderCardWarning = true,
                icon = RuleIcon.DEFAULT,
            )

        val mockMovedFiles =
            mockMovedFileNames.mapIndexed { fileIndex, fileName ->
                FileMoved(
                    fileName = fileName,
                    sourceUri = DevMockFileMove.sourceUri(fileName),
                    destinationUri = DevMockFileMove.destinationUri(fileName),
                    fileSizeBytes = DevMockFileMove.FILE_SIZE_BYTES,
                    movedAt = primaryRunStart + (fileIndex + 1) * 350L,
                    success = true,
                    undoStatus = FileUndoStatus.PENDING,
                )
            }

        val primaryMockEntry =
            RunHistory(
                ruleId = 1L,
                ruleName = "Mock move",
                triggeredBy = TriggerType.MANUAL,
                startedAt = primaryRunStart,
                completedAt = primaryRunStart + 4_200L,
                status = RunStatus.SUCCESS,
                totalFilesFound = mockMovedFiles.size,
                totalFilesMoved = mockMovedFiles.size,
                operationMode = OperationMode.MOVE,
            ) to mockMovedFiles

        val historyList =
            if (!includeTabletStatusVariety) {
                listOf(primaryMockEntry)
            } else {
                val secondSuccessStart = primaryRunStart - 30_000L
                val thirdSuccessStart = primaryRunStart - 4_440_000L
                val failedStart = primaryRunStart - 2_940_000L
                val cancelledFirstStart = primaryRunStart - 540_000L
                val cancelledSecondStart = primaryRunStart - 3_600_000L
                listOf(
                    primaryMockEntry,
                    RunHistory(
                        ruleId = 2L,
                        ruleName = "Test",
                        triggeredBy = TriggerType.MANUAL,
                        startedAt = secondSuccessStart,
                        completedAt = secondSuccessStart + 1_100L,
                        status = RunStatus.SUCCESS,
                        totalFilesFound = 2,
                        totalFilesMoved = 2,
                        operationMode = OperationMode.MOVE,
                    ) to
                        listOf(
                            FileMoved(
                                fileName = "photo-01.jpg",
                                sourceUri = "file://$STORAGE_ROOT/Download/photo-01.jpg",
                                destinationUri = "file://$STORAGE_ROOT/Pictures/Photos/photo-01.jpg",
                                fileSizeBytes = 4_200_000L,
                                movedAt = secondSuccessStart + 500L,
                                success = true,
                            ),
                            FileMoved(
                                fileName = "photo-02.jpg",
                                sourceUri = "file://$STORAGE_ROOT/Download/photo-02.jpg",
                                destinationUri = "file://$STORAGE_ROOT/Pictures/Photos/photo-02.jpg",
                                fileSizeBytes = 3_800_000L,
                                movedAt = secondSuccessStart + 900L,
                                success = true,
                            ),
                        ),
                    RunHistory(
                        ruleId = 1L,
                        ruleName = "Mock move",
                        triggeredBy = TriggerType.MANUAL,
                        startedAt = thirdSuccessStart,
                        completedAt = thirdSuccessStart + 4_100L,
                        status = RunStatus.SUCCESS,
                        totalFilesFound = mockMovedFiles.size,
                        totalFilesMoved = mockMovedFiles.size,
                        operationMode = OperationMode.MOVE,
                    ) to mockMovedFiles,
                    RunHistory(
                        ruleId = 1L,
                        ruleName = "Mock move",
                        triggeredBy = TriggerType.MANUAL,
                        startedAt = failedStart,
                        completedAt = failedStart + 600L,
                        status = RunStatus.FAILED,
                        totalFilesFound = 0,
                        totalFilesMoved = 0,
                        totalFilesFailed = 1,
                        errorMessage = "Destination folder is not accessible",
                        operationMode = OperationMode.MOVE,
                    ) to emptyList(),
                    RunHistory(
                        ruleId = 2L,
                        ruleName = "Test",
                        triggeredBy = TriggerType.MANUAL,
                        startedAt = cancelledFirstStart,
                        completedAt = cancelledFirstStart + 1_500L,
                        status = RunStatus.CANCELLED,
                        totalFilesFound = 4,
                        totalFilesMoved = 1,
                        cancelledUnprocessedCount = 3,
                        operationMode = OperationMode.MOVE,
                    ) to
                        listOf(
                            FileMoved(
                                fileName = "sample-01.png",
                                sourceUri = "file://$STORAGE_ROOT/Download/sample-01.png",
                                destinationUri = "file://$STORAGE_ROOT/Pictures/Photos/sample-01.png",
                                fileSizeBytes = 2_100_000L,
                                movedAt = cancelledFirstStart + 700L,
                                success = true,
                            ),
                        ),
                    RunHistory(
                        ruleId = 2L,
                        ruleName = "Test",
                        triggeredBy = TriggerType.MANUAL,
                        startedAt = cancelledSecondStart,
                        completedAt = cancelledSecondStart + 1_200L,
                        status = RunStatus.CANCELLED,
                        totalFilesFound = 3,
                        totalFilesMoved = 0,
                        cancelledUnprocessedCount = 3,
                        operationMode = OperationMode.MOVE,
                    ) to emptyList(),
                )
            }

        return buildAppBackupJson(
            rules = listOf(mockMoveRule, testRule),
            history = historyList,
            settings = settings,
        )
    }
}
