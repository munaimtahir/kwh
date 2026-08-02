package com.example.kwh.ui.metersettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kwh.R
import com.example.kwh.ui.components.LabeledTextField
import com.example.kwh.ui.components.NumberField
import com.example.kwh.ui.components.PrimaryButton
import com.example.kwh.ui.components.SectionCard
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterSettingsScreen(
    viewModel: MeterSettingsViewModel,
    onBack: () -> Unit,
    onDeleted: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MeterSettingsEvent.Saved -> {
                    snackbarHostState.showSnackbar(event.message)
                    onBack()
                }
                is MeterSettingsEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is MeterSettingsEvent.Deleted -> {
                    onDeleted?.invoke() ?: onBack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.meter_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = uiState.meterName,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(id = R.string.meter_settings_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionCard(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LabeledTextField(
                    value = uiState.meterNameText,
                    onValueChange = viewModel::onMeterNameChanged,
                    label = stringResource(id = R.string.meter_settings_name_label)
                )
                NumberField(
                    value = uiState.anchorDayText,
                    onValueChange = viewModel::onAnchorDayChanged,
                    label = stringResource(id = R.string.meter_settings_anchor_label),
                    maxLength = 2,
                    allowDecimal = false
                )
                LabeledTextField(
                    value = uiState.thresholdsText,
                    onValueChange = viewModel::onThresholdsChanged,
                    label = stringResource(id = R.string.meter_settings_thresholds_label)
                )
                NumberField(
                    value = uiState.reminderFrequencyText,
                    onValueChange = viewModel::onReminderFrequencyChanged,
                    label = stringResource(id = R.string.meter_settings_frequency_label),
                    maxLength = 3,
                    allowDecimal = false
                )
                PrimaryButton(
                    text = stringResource(id = R.string.save),
                    onClick = { viewModel.save() },
                    enabled = uiState.canSave && uiState.isDirty,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Filled.Check,
                    iconContentDescription = stringResource(id = R.string.save)
                )
            }
            
            // Environment
            SectionCard(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(id = R.string.meter_settings_environment_section),
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiState.environmentName
                            ?: stringResource(id = R.string.meter_settings_no_environment),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.toggleEnvironmentDialog(true) }) {
                        Text(text = stringResource(id = R.string.meter_settings_change_environment))
                    }
                }
                if (uiState.environmentId != null) {
                    NumberField(
                        value = uiState.safetyBufferDaysText,
                        onValueChange = viewModel::onSafetyBufferDaysChanged,
                        label = stringResource(id = R.string.meter_settings_safety_buffer_label),
                        maxLength = 2,
                        allowDecimal = false
                    )
                    OutlinedButton(onClick = { viewModel.unassignEnvironment() }) {
                        Text(text = stringResource(id = R.string.meter_settings_unassign_environment))
                    }
                    if (uiState.isActiveMeter) {
                        Text(
                            text = stringResource(id = R.string.meter_settings_already_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        PrimaryButton(
                            text = stringResource(id = R.string.meter_settings_activate_meter),
                            onClick = { viewModel.activateMeter() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Calibration accuracy
            uiState.calibrationAccuracy?.let { accuracy ->
                SectionCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(id = R.string.meter_settings_accuracy_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val daysText = if (accuracy.averageDaysEarly >= 0) {
                        stringResource(id = R.string.meter_settings_accuracy_days_early, accuracy.averageDaysEarly)
                    } else {
                        stringResource(id = R.string.meter_settings_accuracy_days_late, -accuracy.averageDaysEarly)
                    }
                    Text(text = daysText, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(
                            id = R.string.meter_settings_accuracy_margin,
                            accuracy.averageUnitsMargin
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(id = R.string.meter_settings_accuracy_sample_size, accuracy.sampleSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Activation history
            SectionCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.meter_settings_activation_history),
                    style = MaterialTheme.typography.titleMedium
                )
                if (uiState.activationHistory.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.meter_settings_no_activation_history),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                    uiState.activationHistory.forEach { entry ->
                        val endedText = entry.endedAt?.let { formatter.format(Date(it)) }
                            ?: stringResource(id = R.string.meter_settings_activation_ongoing)
                        Text(
                            text = "${formatter.format(Date(entry.startedAt))} → $endedText",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Divider()
                    }
                }
            }

            // Danger Zone
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.meter_settings_danger_zone),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            SectionCard(tonalElevation = 1.dp) {
                OutlinedButton(
                    onClick = { viewModel.toggleDeleteDialog(true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(id = R.string.meter_settings_delete_button),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleDeleteDialog(false) },
            title = { Text(text = stringResource(id = R.string.delete_meter)) },
            text = { Text(text = stringResource(id = R.string.meter_settings_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteMeter() },
                    enabled = !uiState.isDeleting
                ) {
                    Text(
                        text = stringResource(id = R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleDeleteDialog(false) }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Environment assignment dialog
    if (uiState.showEnvironmentDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleEnvironmentDialog(false) },
            title = { Text(text = stringResource(id = R.string.meter_settings_environment_section)) },
            confirmButton = {
                TextButton(onClick = { viewModel.createAndAssignEnvironment() }) {
                    Text(text = stringResource(id = R.string.meter_settings_create_environment))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleEnvironmentDialog(false) }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.availableEnvironments.forEach { option ->
                        TextButton(
                            onClick = { viewModel.selectEnvironment(option.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = option.name, modifier = Modifier.weight(1f))
                        }
                    }
                    Divider()
                    LabeledTextField(
                        value = uiState.newEnvironmentNameText,
                        onValueChange = viewModel::onNewEnvironmentNameChanged,
                        label = stringResource(id = R.string.meter_settings_new_environment_label)
                    )
                }
            }
        )
    }
}
