package dev.bikram.filepipe.domain.usecase

import dev.bikram.filepipe.domain.model.Rule
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
            val errors =
                buildList {
                    if (rule.name.isBlank()) add("Rule name is required")
                    if (rule.sourceFolderPaths.isEmpty()) add("At least one source folder is required")
                    if (rule.destinationFolderPath.isBlank()) add("Destination folder is required")
                    if (rule.fileExtensions.isEmpty()) add("At least one file type is required")
                    if (rule.destinationFolderPath.isNotBlank() &&
                        rule.sourceFolderPaths.any { it == rule.destinationFolderPath }
                    ) {
                        add("Source and destination folders cannot be the same")
                    }
                    rule.schedule?.let { schedule ->
                        when (schedule.type) {
                            ScheduleType.EVERY_N_HOURS -> {
                                val interval = schedule.intervalHours
                                if (interval == null || interval !in 1..24) {
                                    add("Interval must be between 1 and 24 hours")
                                }
                            }

                            ScheduleType.WEEKLY -> {
                                if (schedule.dayOfWeek == null) add("Weekday is required for weekly schedule")
                                if (schedule.hour !in 0..23) add("Invalid hour in schedule")
                                if (schedule.minute !in 0..59) add("Invalid minute in schedule")
                            }

                            ScheduleType.DAILY -> {
                                if (schedule.hour !in 0..23) add("Invalid hour in schedule")
                                if (schedule.minute !in 0..59) add("Invalid minute in schedule")
                            }
                        }
                    }
                }
            return if (errors.isEmpty()) Result.Valid else Result.Invalid(errors)
        }
    }
