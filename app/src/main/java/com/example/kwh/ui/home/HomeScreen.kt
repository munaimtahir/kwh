package com.example.kwh.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Lightbulb
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import com.example.kwh.R
import com.example.kwh.ui.components.NumberField
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onAddMeterClick: () -> Unit,
    onAddMeter: (String, Int, Int, Int) -> Unit,
    onDismissAddMeter: () -> Unit,
    onMeterClick: (Long) -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.meters)) },
                actions = {
                    IconButton(onClick = onOpenAppSettings) {
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
            EmptyState(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.meters, key = { it.id }) { meter ->
                    MeterListItem(
                        meter = meter,
                        onClick = { onMeterClick(meter.id) }
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
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val colorScheme = MaterialTheme.colorScheme
        val gradient = remember(colorScheme.primary, colorScheme.tertiary, colorScheme.surface) {
            Brush.linearGradient(
                colors = listOf(
                    colorScheme.primary.copy(alpha = 0.18f),
                    colorScheme.tertiary.copy(alpha = 0.12f),
                    colorScheme.surface
                )
            )
        }
        Surface(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
            tonalElevation = 8.dp,
            shape = MaterialTheme.shapes.extraLarge,
            color = colorScheme.surfaceColorAtElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(gradient)
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.no_readings_yet),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(id = R.string.empty_state_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MeterListItem(
    meter: MeterItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Meter icon
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ElectricMeter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Meter info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meter.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Last reading or status
                meter.latestReading?.let { reading ->
                    Text(
                        text = stringResource(
                            id = R.string.last_recorded_value,
                            reading.value
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } ?: Text(
                    text = stringResource(id = R.string.no_readings_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chevron
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddMeterDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var frequencyText by remember { mutableStateOf("7") }
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
