@file:Suppress("ConfigurationScreenWidthHeight")

package dev.bikram.filepipe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.bikram.filepipe.R
import dev.bikram.filepipe.domain.formatTimeOfDay
import dev.bikram.filepipe.domain.model.RuleSchedule
import dev.bikram.filepipe.domain.model.ScheduleType
import dev.bikram.filepipe.ui.common.FilePipeMaterialRoundedSymbol
import dev.bikram.filepipe.ui.theme.compactControlShape
import java.util.Calendar

private const val TIME_PICKER_MIN_DENSITY_SCALE = 0.74f
private val TIME_PICKER_HEIGHT = 420.dp
private val TIME_PICKER_ACTION_AREA_HEIGHT = 92.dp
private val TIME_PICKER_LANDSCAPE_ACTION_WIDTH = 144.dp
private val TIME_PICKER_LANDSCAPE_ACTION_GAP = 8.dp
private val TIME_PICKER_DIALOG_MARGIN = 8.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    initialSchedule: RuleSchedule?,
    onDismiss: () -> Unit,
    onSave: (RuleSchedule?) -> Unit,
) {
    var scheduleType by remember { mutableStateOf(initialSchedule?.type ?: ScheduleType.DAILY) }
    var hour by remember { mutableIntStateOf(initialSchedule?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(initialSchedule?.minute ?: 0) }

    // selectedDays bitmask initialized from model helper
    var selectedDays by remember {
        mutableStateOf(
            RuleSchedule.bitmaskToDaysOfWeek(initialSchedule?.dayOfWeek).toSet(),
        )
    }

    var intervalText by remember {
        mutableStateOf(
            (initialSchedule?.repeatInterval ?: RuleSchedule.DEFAULT_REPEAT_INTERVAL).toString(),
        )
    }
    var intervalFieldError by remember { mutableStateOf(false) }

    var typeExpanded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    fun isIntervalTextValid(
        type: ScheduleType,
        text: String = intervalText,
    ): Boolean {
        val parsed = text.toIntOrNull()
        return RuleSchedule.isRepeatIntervalValid(type, parsed)
    }

    fun validateIntervalText(): Boolean {
        val valid = isIntervalTextValid(scheduleType)
        intervalFieldError = !valid
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
                showTimePicker = false
            },
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .widthIn(max = 400.dp)
                    .heightIn(max = screenHeight - 32.dp)
                    .padding(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.schedule_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                val useVerticalLayout = fontScale > 1.15f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val inputContent = @Composable { modifier1: Modifier, modifier2: Modifier ->
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { newValue ->
                                val filteredText = newValue.filter { character -> character.isDigit() }.take(3)
                                intervalText = filteredText
                                intervalFieldError = !isIntervalTextValid(scheduleType, filteredText)
                            },
                            isError = intervalFieldError,
                            label = { Text(stringResource(R.string.schedule_every)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = modifier1,
                        )

                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = it },
                            modifier = modifier2,
                        ) {
                            val selectedLabel =
                                stringResource(
                                    when (scheduleType) {
                                        ScheduleType.EVERY_N_HOURS -> R.string.schedule_unit_hours
                                        ScheduleType.DAILY -> R.string.schedule_unit_days
                                        ScheduleType.WEEKLY -> R.string.schedule_unit_weeks
                                    },
                                )
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.schedule_frequency)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                                modifier =
                                    Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                                        .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                FilePipeDropdownMenuItem(
                                    text = { Text(stringResource(R.string.schedule_unit_hours)) },
                                    onClick = {
                                        scheduleType = ScheduleType.EVERY_N_HOURS
                                        intervalFieldError = !isIntervalTextValid(ScheduleType.EVERY_N_HOURS)
                                        typeExpanded = false
                                    },
                                )
                                FilePipeDropdownMenuItem(
                                    text = { Text(stringResource(R.string.schedule_unit_days)) },
                                    onClick = {
                                        scheduleType = ScheduleType.DAILY
                                        intervalFieldError = !isIntervalTextValid(ScheduleType.DAILY)
                                        typeExpanded = false
                                    },
                                )
                                FilePipeDropdownMenuItem(
                                    text = { Text(stringResource(R.string.schedule_unit_weeks)) },
                                    onClick = {
                                        scheduleType = ScheduleType.WEEKLY
                                        intervalFieldError = !isIntervalTextValid(ScheduleType.WEEKLY)
                                        typeExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    if (useVerticalLayout) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            inputContent(Modifier.fillMaxWidth(), Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            inputContent(Modifier.weight(1f), Modifier.weight(1.2f))
                        }
                    }

                    if (intervalFieldError) {
                        val intervalErrorText =
                            when (scheduleType) {
                                ScheduleType.EVERY_N_HOURS -> R.string.schedule_interval_hours_helper
                                ScheduleType.DAILY -> R.string.schedule_interval_days_helper
                                ScheduleType.WEEKLY -> R.string.schedule_interval_weeks_helper
                            }
                        Text(
                            text = stringResource(intervalErrorText),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_start_time),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        val dialogContext = androidx.compose.ui.platform.LocalContext.current
                        val timeStr = formatTimeOfDay(dialogContext, hour, minute)
                        FilePipeOutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = compactControlShape,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilePipeMaterialRoundedSymbol(
                                    name = "schedule",
                                    contentDescription = null,
                                    size = 18.dp,
                                )
                                Text(text = timeStr)
                            }
                        }
                    }

                    if (scheduleType == ScheduleType.WEEKLY) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.schedule_day),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            val weekdays =
                                listOf(
                                    Calendar.SUNDAY to stringResource(R.string.day_sun).first().toString(),
                                    Calendar.MONDAY to stringResource(R.string.day_mon).first().toString(),
                                    Calendar.TUESDAY to stringResource(R.string.day_tue).first().toString(),
                                    Calendar.WEDNESDAY to stringResource(R.string.day_wed).first().toString(),
                                    Calendar.THURSDAY to stringResource(R.string.day_thu).first().toString(),
                                    Calendar.FRIDAY to stringResource(R.string.day_fri).first().toString(),
                                    Calendar.SATURDAY to stringResource(R.string.day_sat).first().toString(),
                                )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                weekdays.forEach { (calDay, shortLabel) ->
                                    val isSelected = selectedDays.contains(calDay)
                                    val dayBoxModifier =
                                        Modifier
                                            .size(36.dp)
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                                shape = CircleShape,
                                            ).clip(CircleShape)
                                            .clickable {
                                                selectedDays =
                                                    if (isSelected) {
                                                        selectedDays - calDay
                                                    } else {
                                                        selectedDays + calDay
                                                    }
                                            }
                                    Box(
                                        modifier = dayBoxModifier,
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = shortLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                val buttonModifier = Modifier.fillMaxWidth()
                val onSaveClick = {
                    val validInterval = validateIntervalText()
                    val validDays = scheduleType != ScheduleType.WEEKLY || selectedDays.isNotEmpty()
                    if (validInterval && validDays) {
                        val intervalParsed = intervalText.toIntOrNull() ?: RuleSchedule.DEFAULT_REPEAT_INTERVAL
                        onSave(
                            RuleSchedule(
                                type = scheduleType,
                                dayOfWeek =
                                    if (scheduleType == ScheduleType.WEEKLY) {
                                        RuleSchedule.daysOfWeekToBitmask(selectedDays.toList())
                                    } else {
                                        null
                                    },
                                hour = hour,
                                minute = minute,
                                repeatInterval = intervalParsed,
                            ),
                        )
                    }
                }

                if (useVerticalLayout || initialSchedule != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilePipeButton(
                            onClick = onSaveClick,
                            modifier = buttonModifier,
                            shape = compactControlShape,
                        ) {
                            Text(stringResource(R.string.save))
                        }
                        FilePipeOutlinedButton(
                            onClick = onDismiss,
                            modifier = buttonModifier,
                            shape = compactControlShape,
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        if (initialSchedule != null) {
                            FilePipeTextButton(
                                onClick = {
                                    onSave(null)
                                },
                                modifier = buttonModifier,
                            ) {
                                Text(
                                    text = stringResource(R.string.schedule_remove_short),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilePipeOutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = compactControlShape,
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        FilePipeButton(
                            onClick = onSaveClick,
                            modifier = Modifier.weight(1f),
                            shape = compactControlShape,
                        ) {
                            Text(stringResource(R.string.save))
                        }
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
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    var showDial by remember { mutableStateOf(true) }

    // Capture the app-capped density BEFORE opening the Dialog. A Dialog opens its own window
    // that resets LocalDensity to the raw OS density/fontScale, so reading it inside would
    // bypass the app-wide font cap and size the picker off the uncapped OS font. Kept in parity
    // with Remember's ReminderTimePickerDialog.
    val baseDensity = LocalDensity.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val landscape = maxWidth > maxHeight
            val dialogMaxHeight = (maxHeight - TIME_PICKER_DIALOG_MARGIN * 2).coerceAtLeast(0.dp)
            val requiredTimeHeight =
                if (landscape) {
                    TIME_PICKER_HEIGHT
                } else {
                    TIME_PICKER_HEIGHT + TIME_PICKER_ACTION_AREA_HEIGHT
                }
            val availableTimeHeight =
                if (landscape) {
                    dialogMaxHeight
                } else {
                    dialogMaxHeight - TIME_PICKER_ACTION_AREA_HEIGHT
                }
            val pickerDensityScale =
                if (dialogMaxHeight < requiredTimeHeight) {
                    (availableTimeHeight / TIME_PICKER_HEIGHT)
                        .coerceIn(TIME_PICKER_MIN_DENSITY_SCALE, 1f)
                } else {
                    1f
                }
            val pickerDensity =
                remember(baseDensity, pickerDensityScale) {
                    Density(
                        density = baseDensity.density * pickerDensityScale,
                        // When compact (short landscape), cap font so the fixed-size picker fits;
                        // otherwise use the full app font scale so picker text stays close to the
                        // rest of the app instead of rendering conspicuously tiny. Kept in parity
                        // with Remember's ReminderTimePickerDialog.
                        fontScale =
                            if (pickerDensityScale < 1f) {
                                baseDensity.fontScale.coerceAtMost(0.90f)
                            } else {
                                baseDensity.fontScale
                            },
                    )
                }
            key(initialHour, initialMinute) {
                val pickerContext = androidx.compose.ui.platform.LocalContext.current
                val timePickerState =
                    rememberTimePickerState(
                        initialHour = initialHour,
                        initialMinute = initialMinute,
                        is24Hour =
                            android.text.format.DateFormat
                                .is24HourFormat(pickerContext),
                    )
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                    modifier =
                        Modifier
                            .padding(TIME_PICKER_DIALOG_MARGIN)
                            .widthIn(max = if (landscape) 640.dp else 432.dp)
                            .fillMaxWidth()
                            .heightIn(max = dialogMaxHeight)
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = {},
                            ),
                ) {
                    // Ambient app-capped density for title/actions; only the dial is wrapped in the
                    // compact pickerDensity so it fits. Keeps the action buttons at a readable
                    // app-scale size instead of shrinking them with the dial. Kept in parity with
                    // Remember's ReminderTimePickerDialog (which additionally drops the title text).
                    CompositionLocalProvider(LocalDensity provides baseDensity) {
                        if (landscape) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = stringResource(R.string.schedule_time_picker_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp),
                                    )
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CompositionLocalProvider(LocalDensity provides pickerDensity) {
                                            if (showDial) {
                                                TimePicker(state = timePickerState)
                                            } else {
                                                TimeInput(state = timePickerState)
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.width(TIME_PICKER_LANDSCAPE_ACTION_GAP))
                                Column(
                                    modifier = Modifier.width(TIME_PICKER_LANDSCAPE_ACTION_WIDTH),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                ) {
                                    TooltipBox(
                                        positionProvider =
                                            TooltipDefaults.rememberTooltipPositionProvider(
                                                TooltipAnchorPosition.Above,
                                            ),
                                        tooltip = {
                                            PlainTooltip {
                                                CenteredTooltipText(
                                                    text =
                                                        if (showDial) {
                                                            stringResource(R.string.schedule_time_input_mode_cd)
                                                        } else {
                                                            stringResource(R.string.schedule_time_dial_mode_cd)
                                                        },
                                                )
                                            }
                                        },
                                        state = rememberTooltipState(),
                                    ) {
                                        FilePipeIconButton(
                                            onClick = { showDial = !showDial },
                                        ) {
                                            FilePipeMaterialRoundedSymbol(
                                                name = if (showDial) "keyboard" else "schedule",
                                                contentDescription =
                                                    if (showDial) {
                                                        stringResource(R.string.schedule_time_input_mode_cd)
                                                    } else {
                                                        stringResource(R.string.schedule_time_dial_mode_cd)
                                                    },
                                            )
                                        }
                                    }
                                    FilePipeTextButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                    FilePipeTextButton(
                                        onClick = {
                                            onConfirm(timePickerState.hour, timePickerState.minute)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.save))
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.schedule_time_picker_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f, fill = false)
                                            .fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CompositionLocalProvider(LocalDensity provides pickerDensity) {
                                        if (showDial) {
                                            TimePicker(state = timePickerState)
                                        } else {
                                            TimeInput(state = timePickerState)
                                        }
                                    }
                                }
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TooltipBox(
                                        positionProvider =
                                            TooltipDefaults.rememberTooltipPositionProvider(
                                                TooltipAnchorPosition.Above,
                                            ),
                                        tooltip = {
                                            PlainTooltip {
                                                CenteredTooltipText(
                                                    text =
                                                        if (showDial) {
                                                            stringResource(R.string.schedule_time_input_mode_cd)
                                                        } else {
                                                            stringResource(R.string.schedule_time_dial_mode_cd)
                                                        },
                                                )
                                            }
                                        },
                                        state = rememberTooltipState(),
                                    ) {
                                        FilePipeIconButton(
                                            onClick = { showDial = !showDial },
                                        ) {
                                            FilePipeMaterialRoundedSymbol(
                                                name = if (showDial) "keyboard" else "schedule",
                                                contentDescription =
                                                    if (showDial) {
                                                        stringResource(R.string.schedule_time_input_mode_cd)
                                                    } else {
                                                        stringResource(R.string.schedule_time_dial_mode_cd)
                                                    },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    FilePipeTextButton(onClick = onDismiss) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                    FilePipeTextButton(
                                        onClick = {
                                            onConfirm(timePickerState.hour, timePickerState.minute)
                                        },
                                    ) {
                                        Text(stringResource(R.string.save))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
