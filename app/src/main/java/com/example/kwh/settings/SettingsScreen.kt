package com.example.kwh.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: UserSettings,
    onDarkThemeChanged: (Boolean) -> Unit,
    onSnoozeChanged: (Int) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            RowSetting(
                title = stringResource(id = R.string.settings_dark_mode),
                description = stringResource(id = R.string.settings_dark_mode_desc)
            ) {
                Switch(checked = state.darkTheme, onCheckedChange = onDarkThemeChanged)
            }

            var sliderValue by remember(state.snoozeMinutes) { mutableStateOf(state.snoozeMinutes.toFloat()) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(id = R.string.settings_snooze_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(id = R.string.settings_snooze_summary, state.snoozeMinutes),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        val minutes = (it / 15f).roundToInt() * 15
                        sliderValue = minutes.toFloat()
                        onSnoozeChanged(minutes)
                    },
                    valueRange = 15f..240f,
                    steps = ((240 - 15) / 15) - 1
                )
            }
        }
    }
}

@Composable
private fun RowSetting(
    title: String,
    description: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}
