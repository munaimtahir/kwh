package com.example.kwh.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kwh.R
import com.example.kwh.ui.components.NumberField
import com.example.kwh.ui.components.PrimaryButton
import com.example.kwh.ui.components.SectionCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddMeterClick: () -> Unit,
    onAddMeter: (String, Int, Int, Int) -> Unit,
    onDismissAddMeter: () -> Unit,
    onAddReadingClick: (Long) -> Unit,
    onAddReading: (Long, Double, String?) -> Unit,
    onDismissReading: () -> Unit,
    onReminderChanged: (Long, Boolean, Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = stringResource(id = R.string.meters)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMeterClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(id = R.string.add_meter)
                )
            }
        }
    ) { padding ->
        if (uiState.meters.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.no_readings_yet),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.meters, key = { it.id }) { meter ->
                    MeterCard(
                        meter = meter,
                        onAddReadingClick = { onAddReadingClick(meter.id) },
                        onReminderChanged = { enabled, frequency, hour, minute ->
                            onReminderChanged(meter.id, enabled, frequency, hour, minute)
                        }
                    )
                }
            }
        }
    }

    if (uiState.showAddMeterDialog) {
        AddMeterDialog(
            onDismiss = onDismissAddMeter,
            onSave = { name, frequency, hour, minute ->
                onAddMeter(name, frequency, hour, minute)
                onDismissAddMeter()
            }
        )
    }

    if (uiState.showAddReadingDialog && uiState.meterIdForReading != null) {
        AddReadingDialog(
            meterId = uiState.meterIdForReading,
            onDismiss = onDismissReading,
            onSave = { id, value, notes ->
                onAddReading(id, value, notes)
                onDismissReading()
            }
        )
    }
}

@Composable
private fun MeterCard(
    meter: MeterItem,
    onAddReadingClick: () -> Unit,
    onReminderChanged: (Boolean, Int, Int, Int) -> Unit
) {
    SectionCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = meter.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            meter.latestReading?.let { reading ->
                val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }
                val formatted = remember(reading.recordedAt) {
                    formatter.format(reading.recordedAt.atZone(ZoneId.systemDefault()))
                }
                Text(
                    text = "${stringResource(id = R.string.last_recorded)}: ${reading.value} kWh",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.bodySmall
                )
            } ?: Text(
                text = stringResource(id = R.string.no_readings_yet),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(id = R.string.add_reading),
                onClick = onAddReadingClick
            )
            Spacer(modifier = Modifier.height(12.dp))
            ReminderSettings(
                meter = meter,
                onReminderChanged = onReminderChanged
            )
        }
    }
}

@Composable
private fun ReminderSettings(
    meter: MeterItem,
    onReminderChanged: (Boolean, Int, Int, Int) -> Unit
) {
    var reminderEnabled by remember(meter.id, meter.reminderEnabled) {
        mutableStateOf(meter.reminderEnabled)
    }
    var frequencyText by remember(meter.id, meter.reminderFrequencyDays) {
        mutableStateOf(meter.reminderFrequencyDays.toString())
    }
    var hourText by remember(meter.id, meter.reminderHour) {
        mutableStateOf(meter.reminderHour.toString())
    }
    var minuteText by remember(meter.id, meter.reminderMinute) {
        mutableStateOf(meter.reminderMinute.toString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(id = R.string.set_reminder))
            Switch(
                checked = reminderEnabled,
                onCheckedChange = { enabled ->
                    reminderEnabled = enabled
                    submitReminderChange(
                        enabled = enabled,
                        frequencyText = frequencyText,
                        hourText = hourText,
                        minuteText = minuteText,
                        fallback = meter,
                        onReminderChanged = onReminderChanged
                    )
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                value = frequencyText,
                onValueChange = { frequencyText = it },
                label = stringResource(id = R.string.reminder_frequency_days),
                modifier = Modifier.weight(1f),
                maxLength = 3
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(
                value = hourText,
                onValueChange = { hourText = it },
                label = stringResource(id = R.string.reminder_time) + " (h)",
                modifier = Modifier.weight(1f),
                maxLength = 2
            )
            NumberField(
                value = minuteText,
                onValueChange = { minuteText = it },
                label = "(m)",
                modifier = Modifier.weight(1f),
                maxLength = 2
            )
        }
        PrimaryButton(
            text = stringResource(id = R.string.save),
            onClick = {
                submitReminderChange(
                    enabled = reminderEnabled,
                    frequencyText = frequencyText,
                    hourText = hourText,
                    minuteText = minuteText,
                    fallback = meter,
                    onReminderChanged = onReminderChanged
                )
            }
        )
        meter.nextReminder?.let { instant ->
            val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }
            Text(
                text = "${stringResource(id = R.string.next_reminder)}: ${formatter.format(instant.atZone(ZoneId.systemDefault()))}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun submitReminderChange(
    enabled: Boolean,
    frequencyText: String,
    hourText: String,
    minuteText: String,
    fallback: MeterItem,
    onReminderChanged: (Boolean, Int, Int, Int) -> Unit
) {
    val frequency = frequencyText.toIntOrNull()?.coerceAtLeast(1) ?: fallback.reminderFrequencyDays
    val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: fallback.reminderHour
    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: fallback.reminderMinute
    onReminderChanged(enabled, frequency, hour, minute)
}

@Composable
private fun AddMeterDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var frequencyText by remember { mutableStateOf("30") }
    var hourText by remember { mutableStateOf("9") }
    var minuteText by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val frequency = frequencyText.toIntOrNull()?.coerceAtLeast(1) ?: 30
                val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 9
                val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onSave(name.ifBlank { "Meter" }, frequency, hour, minute)
            }) {
                Text(text = stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        title = { Text(text = stringResource(id = R.string.add_meter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(id = R.string.meter)) },
                    singleLine = true
                )
                NumberField(
                    value = frequencyText,
                    onValueChange = { frequencyText = it },
                    label = stringResource(id = R.string.reminder_frequency_days),
                    maxLength = 3
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = hourText,
                        onValueChange = { hourText = it },
                        label = "Hour",
                        modifier = Modifier.weight(1f),
                        maxLength = 2
                    )
                    NumberField(
                        value = minuteText,
                        onValueChange = { minuteText = it },
                        label = "Minute",
                        modifier = Modifier.weight(1f),
                        maxLength = 2
                    )
                }
            }
        }
    )
}

@Composable
private fun AddReadingDialog(
    meterId: Long,
    onDismiss: () -> Unit,
    onSave: (Long, Double, String?) -> Unit
) {
    var readingText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val value = readingText.toDoubleOrNull() ?: return@TextButton
                onSave(meterId, value, notes.ifBlank { null })
            }) {
                Text(text = stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        title = { Text(text = stringResource(id = R.string.add_reading)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = readingText,
                    onValueChange = { readingText = it },
                    label = stringResource(id = R.string.reading_value),
                    allowDecimal = true
                )
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(text = stringResource(id = R.string.reading_notes)) }
                )
            }
        }
    )
}
