package com.example.kwh.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kwh.R
import com.example.kwh.ui.components.NumberField
import com.example.kwh.ui.components.PrimaryButton
import com.example.kwh.ui.components.SectionCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onAddMeterClick: () -> Unit,
    onAddMeter: (String, Int, Int, Int) -> Unit,
    onDismissAddMeter: () -> Unit,
    onAddReadingClick: (Long) -> Unit,
    onAddReading: (Long, Double, String?) -> Unit,
    onDismissReading: () -> Unit,
    onReminderChanged: (Long, Boolean, Int, Int, Int) -> Unit,
    onViewHistory: (Long) -> Unit,
    onDeleteMeter: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.meters)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = stringResource(id = R.string.settings_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(id = R.string.empty_state_hint),
                    style = MaterialTheme.typography.bodyMedium
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
                        },
                        onViewHistory = { onViewHistory(meter.id) },
                        onDeleteMeter = { onDeleteMeter(meter.id) }
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
    onReminderChanged: (Boolean, Int, Int, Int) -> Unit,
    onViewHistory: () -> Unit,
    onDeleteMeter: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    SectionCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meter.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    meter.nextReminder?.let { instant ->
                        val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }
                        Text(
                            text = stringResource(id = R.string.next_reminder, formatter.format(instant.atZone(ZoneId.systemDefault()))),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(id = R.string.delete_meter)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            meter.latestReading?.let { reading ->
                val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }
                val formatted = remember(reading.recordedAt) {
                    formatter.format(reading.recordedAt.atZone(ZoneId.systemDefault()))
                }
                Text(
                    text = stringResource(id = R.string.last_recorded_value, reading.value),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.bodySmall
                )
                reading.notes?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            } ?: Text(
                text = stringResource(id = R.string.no_readings_yet),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    text = stringResource(id = R.string.add_reading),
                    onClick = onAddReadingClick,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onViewHistory) {
                    Icon(imageVector = Icons.Filled.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(id = R.string.view_history))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            ReminderSettings(
                meter = meter,
                onReminderChanged = onReminderChanged
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(id = R.string.delete_meter)) },
            text = { Text(text = stringResource(id = R.string.delete_meter_confirm, meter.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteMeter()
                }) {
                    Text(text = stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
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
                label = stringResource(id = R.string.reminder_time_hour),
                modifier = Modifier.weight(1f),
                maxLength = 2
            )
            NumberField(
                value = minuteText,
                onValueChange = { minuteText = it },
                label = stringResource(id = R.string.reminder_time_minute),
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
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    showError = true
                    return@TextButton
                }
                val frequency = frequencyText.toIntOrNull()?.coerceAtLeast(1) ?: 30
                val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 9
                val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onSave(name.trim(), frequency, hour, minute)
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
                    onValueChange = {
                        name = it
                        if (showError && it.isNotBlank()) showError = false
                    },
                    label = { Text(text = stringResource(id = R.string.meter)) },
                    isError = showError,
                    singleLine = true
                )
                if (showError) {
                    Text(
                        text = stringResource(id = R.string.meter_name_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
                        label = stringResource(id = R.string.reminder_time_hour),
                        modifier = Modifier.weight(1f),
                        maxLength = 2
                    )
                    NumberField(
                        value = minuteText,
                        onValueChange = { minuteText = it },
                        label = stringResource(id = R.string.reminder_time_minute),
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
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val value = readingText.toDoubleOrNull()
                if (value == null || value <= 0.0) {
                    showError = true
                    return@TextButton
                }
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
                    onValueChange = {
                        readingText = it
                        if (showError) showError = false
                    },
                    label = stringResource(id = R.string.reading_value),
                    allowDecimal = true
                )
                if (showError) {
                    Text(
                        text = stringResource(id = R.string.reading_value_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(text = stringResource(id = R.string.reading_notes)) }
                )
            }
        }
    )
}
