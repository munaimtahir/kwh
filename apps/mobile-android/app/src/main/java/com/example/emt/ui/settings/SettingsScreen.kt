package com.example.emt.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.emt.workers.ReminderScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val reminderTime by viewModel.reminderTime.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.setRemindersEnabled(true)
            ReminderScheduler.scheduleReminder(context, reminderTime)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable daily reminders")
            Switch(
                checked = remindersEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            when (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            )) {
                                PackageManager.PERMISSION_GRANTED -> {
                                    viewModel.setRemindersEnabled(true)
                                    ReminderScheduler.scheduleReminder(context, reminderTime)
                                }
                                else -> {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        } else {
                            viewModel.setRemindersEnabled(true)
                            ReminderScheduler.scheduleReminder(context, reminderTime)
                        }
                    } else {
                        viewModel.setRemindersEnabled(false)
                        ReminderScheduler.cancelReminder(context)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showTimePicker = true },
            enabled = remindersEnabled
        ) {
            Text("Reminder time: $reminderTime")
        }
    }

    if (showTimePicker) {
        val timeParts = reminderTime.split(":").map { it.toInt() }
        val timePickerState = rememberTimePickerState(
            initialHour = timeParts[0],
            initialMinute = timeParts[1],
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                        viewModel.setReminderTime(newTime)
                        if (remindersEnabled) {
                            ReminderScheduler.scheduleReminder(context, newTime)
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false }
                ) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}
