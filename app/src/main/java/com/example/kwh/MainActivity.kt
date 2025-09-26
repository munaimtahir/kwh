package com.example.kwh

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kwh.data.MeterDatabase
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.repository.MeterRepository
import com.example.kwh.ui.app.KwhTheme
import com.example.kwh.ui.home.HomeEvent
import com.example.kwh.ui.home.HomeScreen
import com.example.kwh.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels {
        val database = MeterDatabase.get(applicationContext)
        val repository = MeterRepository(database.meterDao())
        val scheduler = ReminderScheduler(applicationContext)
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(repository, scheduler, applicationContext) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    private var pendingReminderRequest: ReminderRequest? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingReminderRequest?.let { request ->
                    viewModel.updateReminder(
                        meterId = request.meterId,
                        enabled = request.enabled,
                        frequency = request.frequency,
                        hour = request.hour,
                        minute = request.minute
                    )
                }
            }
            if (granted.not()) {
                pendingReminderRequest?.let { request ->
                    // Revert the toggle in the data layer.
                    viewModel.updateReminder(
                        meterId = request.meterId,
                        enabled = false,
                        frequency = request.frequency,
                        hour = request.hour,
                        minute = request.minute
                    )
                }
            }
            pendingReminderRequest = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KwhTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is HomeEvent.ShowMessage -> {
                                snackbarHostState.showSnackbar(event.message)
                            }
                            is HomeEvent.Error -> {
                                snackbarHostState.showSnackbar(event.message)
                            }
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    HomeScreen(
                        uiState = uiState,
                        onAddMeterClick = { viewModel.showAddMeterDialog(true) },
                        onAddMeter = { name, frequency, hour, minute ->
                            viewModel.addMeter(name, frequency, hour, minute)
                        },
                        onDismissAddMeter = { viewModel.showAddMeterDialog(false) },
                        onAddReadingClick = { meterId -> viewModel.showAddReadingDialog(meterId, true) },
                        onAddReading = { meterId, value, notes ->
                            viewModel.addReading(meterId, value, notes)
                        },
                        onDismissReading = { viewModel.showAddReadingDialog(null, false) },
                        onReminderChanged = { meterId, enabled, frequency, hour, minute ->
                            handleReminderChange(
                                meterId = meterId,
                                enabled = enabled,
                                frequency = frequency,
                                hour = hour,
                                minute = minute
                            )
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }

    private fun handleReminderChange(
        meterId: Long,
        enabled: Boolean,
        frequency: Int,
        hour: Int,
        minute: Int
    ) {
        if (!enabled) {
            viewModel.updateReminder(meterId, false, frequency, hour, minute)
            return
        }
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.updateReminder(meterId, true, frequency, hour, minute)
        } else {
            pendingReminderRequest = ReminderRequest(meterId, true, frequency, hour, minute)
            notificationPermissionLauncher.launch(permission)
        }
    }

    private data class ReminderRequest(
        val meterId: Long,
        val enabled: Boolean,
        val frequency: Int,
        val hour: Int,
        val minute: Int
    )
}
