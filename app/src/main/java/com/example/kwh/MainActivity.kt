package com.example.kwh

import android.Manifest
import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kwh.ui.app.KwhTheme
import com.example.kwh.ui.components.NumberField
import com.example.kwh.ui.home.HomeEvent
import com.example.kwh.ui.home.HomeScreen
import com.example.kwh.ui.home.HomeViewModel
import com.example.kwh.ui.meterdetail.MeterDetailScreen
import com.example.kwh.ui.meterdetail.MeterDetailViewModel
import com.example.kwh.ui.history.HistoryScreen
import com.example.kwh.ui.history.HistoryViewModel
import com.example.kwh.ui.metersettings.MeterSettingsScreen
import com.example.kwh.ui.metersettings.MeterSettingsViewModel
import com.example.kwh.settings.SettingsScreen
import com.example.kwh.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** The shared home view model instance used across screens. */
    private val homeViewModel: HomeViewModel by viewModels()

    private var pendingReminderRequest: ReminderRequest? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingReminderRequest?.let { request ->
                    homeViewModel.updateReminder(
                        meterId = request.meterId,
                        enabled = request.enabled,
                        frequencyDays = request.frequency,
                        hour = request.hour,
                        minute = request.minute
                    )
                }
            } else {
                // Permission denied – disable reminder
                pendingReminderRequest?.let { request ->
                    homeViewModel.updateReminder(
                        meterId = request.meterId,
                        enabled = false,
                        frequencyDays = request.frequency,
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
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

                // State for add reading dialog (now handled at activity level for use across screens)
                var showAddReadingDialog by remember { mutableStateOf(false) }
                var addReadingMeterId by remember { mutableStateOf<Long?>(null) }

                // Collect home view model events and display snackbars
                LaunchedEffect(Unit) {
                    homeViewModel.events.collect { event ->
                        when (event) {
                            is HomeEvent.Error -> snackbarHostState.showSnackbar(event.message)
                            is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            uiState = homeUiState,
                            snackbarHostState = snackbarHostState,
                            onAddMeterClick = { homeViewModel.showAddMeterDialog(true) },
                            onAddMeter = { name, frequency, hour, minute ->
                                homeViewModel.addMeter(name, frequency, hour, minute)
                            },
                            onDismissAddMeter = { homeViewModel.showAddMeterDialog(false) },
                            onMeterClick = { meterId ->
                                navController.navigate("meterDetail/$meterId")
                            },
                            onOpenAppSettings = {
                                navController.navigate("settings")
                            }
                        )
                    }
                    composable(
                        route = "meterDetail/{meterId}",
                        arguments = listOf(navArgument("meterId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val meterId = backStackEntry.arguments?.getLong("meterId") ?: 0L
                        val meterDetailViewModel: MeterDetailViewModel = hiltViewModel()
                        MeterDetailScreen(
                            viewModel = meterDetailViewModel,
                            snackbarHostState = snackbarHostState,
                            onBack = { navController.popBackStack() },
                            onAddReading = {
                                addReadingMeterId = meterId
                                showAddReadingDialog = true
                            },
                            onViewGraph = {
                                navController.navigate("history/$meterId")
                            },
                            onViewLog = {
                                navController.navigate("history/$meterId")
                            },
                            onOpenSettings = {
                                navController.navigate("meterSettings/$meterId")
                            }
                        )
                    }
                    composable(
                        route = "history/{meterId}",
                        arguments = listOf(navArgument("meterId") { type = NavType.LongType })
                    ) {
                        val historyViewModel: HistoryViewModel = hiltViewModel()
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = "meterSettings/{meterId}",
                        arguments = listOf(navArgument("meterId") { type = NavType.LongType })
                    ) {
                        val meterSettingsViewModel: MeterSettingsViewModel = hiltViewModel()
                        MeterSettingsScreen(
                            viewModel = meterSettingsViewModel,
                            onBack = { navController.popBackStack() },
                            onDeleted = {
                                // Navigate back to home after meter deletion
                                navController.popBackStack(route = "home", inclusive = false)
                            }
                        )
                    }
                    composable("settings") {
                        val settingsViewModel: SettingsViewModel = hiltViewModel()
                        val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                        SettingsScreen(
                            state = settingsState,
                            onDarkThemeChanged = settingsViewModel::setDarkTheme,
                            onSnoozeChanged = settingsViewModel::setSnoozeMinutes,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Add Reading Dialog
                if (showAddReadingDialog && addReadingMeterId != null) {
                    AddReadingDialog(
                        meterId = addReadingMeterId!!,
                        onDismiss = {
                            showAddReadingDialog = false
                            addReadingMeterId = null
                        },
                        onSave = { id, value, notes, recordedAt ->
                            homeViewModel.addReading(id, value, notes, recordedAt)
                            showAddReadingDialog = false
                            addReadingMeterId = null
                        }
                    )
                }
            }
        }
    }

    /**
     * Handle changes to reminder settings, requesting notification permission if required.
     */
    private fun handleReminderChange(
        meterId: Long,
        enabled: Boolean,
        frequency: Int,
        hour: Int,
        minute: Int
    ) {
        if (!enabled) {
            homeViewModel.updateReminder(meterId, false, frequency, hour, minute)
            return
        }
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            homeViewModel.updateReminder(meterId, true, frequency, hour, minute)
        } else {
            pendingReminderRequest = ReminderRequest(meterId, true, frequency, hour, minute)
            notificationPermissionLauncher.launch(permission)
        }
    }

    /** Simple data holder to defer reminder requests until permissions are granted. */
    private data class ReminderRequest(
        val meterId: Long,
        val enabled: Boolean,
        val frequency: Int,
        val hour: Int,
        val minute: Int
    )
}

@androidx.compose.runtime.Composable
private fun AddReadingDialog(
    meterId: Long,
    onDismiss: () -> Unit,
    onSave: (Long, Double, String?, Long) -> Unit
) {
    var readingText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val zoneId = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
    }
    val formattedDate = remember(selectedDate) { dateFormatter.format(selectedDate) }

    fun showDatePicker() {
        val initialDate = selectedDate
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val value = readingText.toDoubleOrNull()
                if (value == null || value <= 0.0) {
                    showError = true
                    return@TextButton
                }
                val recordedAt = selectedDate
                    .atTime(LocalTime.now(zoneId))
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
                onSave(meterId, value, notes.ifBlank { null }, recordedAt)
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
                OutlinedButton(
                    onClick = ::showDatePicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.reading_recorded_on, formattedDate))
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
