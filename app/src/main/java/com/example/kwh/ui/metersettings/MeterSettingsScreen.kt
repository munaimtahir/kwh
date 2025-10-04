package com.example.kwh.ui.metersettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kwh.R
import com.example.kwh.ui.components.LabeledTextField
import com.example.kwh.ui.components.NumberField
import com.example.kwh.ui.components.PrimaryButton
import com.example.kwh.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterSettingsScreen(
    viewModel: MeterSettingsViewModel,
    onBack: () -> Unit
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
                .fillMaxSize(),
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
        }
    }
}
