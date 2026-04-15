package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.ui.feedback.rememberPlayTapSound
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    initialSchedule: RuleSchedule?,
    onDismiss: () -> Unit,
    onSave: (RuleSchedule?) -> Unit
) {
    val playTap = rememberPlayTapSound()
    var scheduleType by remember { mutableStateOf(initialSchedule?.type ?: ScheduleType.DAILY) }
    var hour by remember { mutableIntStateOf(initialSchedule?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(initialSchedule?.minute ?: 0) }
    var dayOfWeek by remember { mutableIntStateOf(initialSchedule?.dayOfWeek ?: Calendar.MONDAY) }
    var intervalHoursText by remember {
        mutableStateOf(
            (initialSchedule?.intervalHours?.coerceIn(1, 24) ?: 6).toString()
        )
    }
    var intervalFieldError by remember { mutableStateOf(false) }

    var typeExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val days = listOf(
        Calendar.MONDAY to "Monday",
        Calendar.TUESDAY to "Tuesday",
        Calendar.WEDNESDAY to "Wednesday",
        Calendar.THURSDAY to "Thursday",
        Calendar.FRIDAY to "Friday",
        Calendar.SATURDAY to "Saturday",
        Calendar.SUNDAY to "Sunday"
    )

    fun validateIntervalText(): Boolean {
        val parsed = intervalHoursText.toIntOrNull()
        val valid = parsed != null && parsed in 1..24
        intervalFieldError = scheduleType == ScheduleType.EVERY_N_HOURS && !valid
        return valid
    }

    if (showTimePicker) {
        ScheduleTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { pickedHour, pickedMinute ->
                hour = pickedHour
                minute = pickedMinute
                playTap()
                showTimePicker = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.schedule_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it }
                    ) {
                        val frequencyLabel = stringResource(
                            when (scheduleType) {
                                ScheduleType.EVERY_N_HOURS -> R.string.schedule_hourly
                                ScheduleType.DAILY -> R.string.schedule_daily
                                ScheduleType.WEEKLY -> R.string.schedule_weekly
                            }
                        )
                        OutlinedTextField(
                            value = frequencyLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.schedule_frequency)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.schedule_hourly)) },
                                onClick = {
                                    scheduleType = ScheduleType.EVERY_N_HOURS
                                    typeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.schedule_daily)) },
                                onClick = {
                                    scheduleType = ScheduleType.DAILY
                                    typeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.schedule_weekly)) },
                                onClick = {
                                    scheduleType = ScheduleType.WEEKLY
                                    typeExpanded = false
                                }
                            )
                        }
                    }

                    if (scheduleType == ScheduleType.WEEKLY) {
                        val selectedDayName = days.find { it.first == dayOfWeek }?.second ?: "Monday"
                        ExposedDropdownMenuBox(
                            expanded = dayExpanded,
                            onExpandedChange = { dayExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedDayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.schedule_day)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dayExpanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = dayExpanded,
                                onDismissRequest = { dayExpanded = false }
                            ) {
                                days.forEach { (calDay, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            dayOfWeek = calDay
                                            dayExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (scheduleType == ScheduleType.EVERY_N_HOURS) {
                        OutlinedTextField(
                            value = intervalHoursText,
                            onValueChange = { newValue ->
                                intervalHoursText = newValue.filter { character -> character.isDigit() }.take(2)
                                intervalFieldError = false
                            },
                            isError = intervalFieldError,
                            label = { Text(stringResource(R.string.schedule_interval_hours)) },
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.schedule_interval_hours_helper),
                                    color = if (intervalFieldError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (scheduleType == ScheduleType.DAILY || scheduleType == ScheduleType.WEEKLY) {
                        Text(
                            text = stringResource(R.string.schedule_time),
                            style = MaterialTheme.typography.labelMedium
                        )
                        val hour12 = when (val hourMod = hour % 12) {
                            0 -> 12
                            else -> hourMod
                        }
                        val amPm = if (hour < 12) "AM" else "PM"
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.schedule_pick_time) + ": " +
                                    "%d:%02d %s".format(hour12, minute, amPm)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initialSchedule != null) {
                        TextButton(
                            onClick = {
                                playTap()
                                onSave(null)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.schedule_remove_short),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            playTap()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            if (scheduleType == ScheduleType.EVERY_N_HOURS && !validateIntervalText()) {
                                return@Button
                            }
                            playTap()
                            val intervalParsed = intervalHoursText.toIntOrNull()?.coerceIn(1, 24) ?: 6
                            onSave(
                                RuleSchedule(
                                    type = scheduleType,
                                    dayOfWeek = if (scheduleType == ScheduleType.WEEKLY) dayOfWeek else null,
                                    hour = if (scheduleType == ScheduleType.EVERY_N_HOURS) 0 else hour,
                                    minute = if (scheduleType == ScheduleType.EVERY_N_HOURS) 0 else minute,
                                    intervalHours = if (scheduleType == ScheduleType.EVERY_N_HOURS) {
                                        intervalParsed
                                    } else {
                                        null
                                    }
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScheduleTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var showDial by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        key(initialHour, initialMinute) {
            val timePickerState = rememberTimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute,
                is24Hour = false
            )
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.schedule_time_picker_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    if (showDial) {
                        TimePicker(state = timePickerState)
                    } else {
                        TimeInput(state = timePickerState)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = {
                                PlainTooltip {
                                    Text(
                                        text = if (showDial) {
                                            stringResource(R.string.schedule_time_input_mode_cd)
                                        } else {
                                            stringResource(R.string.schedule_time_dial_mode_cd)
                                        }
                                    )
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = { showDial = !showDial }
                            ) {
                                Icon(
                                    imageVector = if (showDial) Icons.Filled.Keyboard else Icons.Filled.Schedule,
                                    contentDescription = if (showDial) {
                                        stringResource(R.string.schedule_time_input_mode_cd)
                                    } else {
                                        stringResource(R.string.schedule_time_dial_mode_cd)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                onConfirm(timePickerState.hour, timePickerState.minute)
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}
