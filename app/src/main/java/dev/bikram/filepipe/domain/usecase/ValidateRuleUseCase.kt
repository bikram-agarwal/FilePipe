package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.data.repository.areExcludePatternsValid
import dev.bikram.filepipe.data.repository.isFilenameRegexValid
import dev.bikram.filepipe.data.repository.normalizeSourcePath
import dev.bikram.filepipe.domain.model.OperationMode
import dev.bikram.filepipe.domain.model.Rule
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.ScheduleType
import javax.inject.Inject

class ValidateRuleUseCase
    @Inject
    constructor() {
        sealed class Result {
            data object Valid : Result()

            data class Invalid(
                val errors: List<String>,
            ) : Result()
        }

        operator fun invoke(rule: Rule): Result {
            val destinationIdentity =
                rule.destinationFolderPath
                    .takeIf { it.isNotBlank() }
                    ?.let { path -> normalizeSourcePath(path, filesystemAccessEnabled = false) }
            val hasSameSourceAndDestination =
                destinationIdentity != null &&
                    rule.sourceFolderPaths.any { sourcePath ->
                        normalizeSourcePath(sourcePath, filesystemAccessEnabled = false) == destinationIdentity
                    }
            val errors =
                buildList {
                    if (rule.name.isBlank()) add("Rule name is required")
                    if (rule.sourceFolderPaths.isEmpty()) add("At least one source folder is required")
                    if (rule.operationMode != OperationMode.DELETE && rule.destinationFolderPath.isBlank()) {
                        add("Destination folder is required")
                    }
                    if (rule.fileExtensions.isEmpty()) add("At least one file type is required")
                    if (rule.operationMode != OperationMode.DELETE &&
                        hasSameSourceAndDestination
                    ) {
                        add("Source and destination folders cannot be the same")
                    }
                    rule.schedule?.let { schedule ->
                        val interval = schedule.repeatInterval ?: RuleSchedule.DEFAULT_REPEAT_INTERVAL
                        if (schedule.hour !in 0..23) add("Invalid hour in schedule")
                        if (schedule.minute !in 0..59) add("Invalid minute in schedule")

                        when (schedule.type) {
                            ScheduleType.EVERY_N_HOURS -> {
                                if (!RuleSchedule.isRepeatIntervalValid(schedule.type, interval)) {
                                    add("Interval must be between 1 and 24 hours")
                                }
                            }

                            ScheduleType.DAILY -> {
                                if (!RuleSchedule.isRepeatIntervalValid(schedule.type, interval)) {
                                    add("Interval must be between 1 and 365 days")
                                }
                            }

                            ScheduleType.WEEKLY -> {
                                if (!RuleSchedule.isRepeatIntervalValid(schedule.type, interval)) {
                                    add("Interval must be between 1 and 52 weeks")
                                }
                                if (schedule.dayOfWeek == null) {
                                    add("Weekday is required for weekly schedule")
                                } else {
                                    val days = RuleSchedule.bitmaskToDaysOfWeek(schedule.dayOfWeek)
                                    if (days.isEmpty()) add("At least one weekday is required for weekly schedule")
                                }
                            }
                        }
                    }
                    // Split and compiled exactly as a scan and the editor's field check do.
                    if (rule.isRegexPattern && !isFilenameRegexValid(rule.filenamePattern)) {
                        add("Invalid regular expression syntax")
                    }
                    // The same check as the editor's live field error and the scan itself.
                    if (rule.isExcludeRegexPattern && !areExcludePatternsValid(rule.excludePatterns, isRegexPattern = true)) {
                        add("Invalid regular expression syntax in exclude patterns")
                    }
                }
            return if (errors.isEmpty()) Result.Valid else Result.Invalid(errors)
        }
    }
