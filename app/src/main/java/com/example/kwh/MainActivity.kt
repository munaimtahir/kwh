package com.example.kwh

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kwh.ui.app.KwhTheme
import com.example.kwh.settings.SettingsScreen
import com.example.kwh.settings.SettingsViewModel
import com.example.kwh.ui.home.HomeEvent
import com.example.kwh.ui.home.HomeScreen
import com.example.kwh.ui.home.HomeViewModel
import com.example.kwh.ui.history.HistoryScreen
import com.example.kwh.ui.history.HistoryViewModel
import com.example.kwh.ui.metersettings.MeterSettingsScreen
import com.example.kwh.ui.metersettings.MeterSettingsViewModel
import com.example.kwh.settings.SettingsScreen
import com.example.kwh.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

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
                            onAddReadingClick = { meterId ->
                                homeViewModel.showAddReadingDialog(meterId, true)
                            },
                            onAddReading = { id, value, notes ->
                                homeViewModel.addReading(id, value, notes)
                            },
                            onDismissReading = {
                                homeViewModel.showAddReadingDialog(null, false)
                            },
                            onReminderChanged = { meterId, enabled, freq, hour, minute ->
                                handleReminderChange(meterId, enabled, freq, hour, minute)
                            },
                            onViewHistory = { meterId ->
                                navController.navigate("history/$meterId")
                            },
                            onDeleteMeter = { meterId ->
                                homeViewModel.deleteMeter(meterId)
                            },
                            onOpenMeterSettings = { meterId ->
                                navController.navigate("meterSettings/$meterId")
                            },
                            onOpenAppSettings = {
                                navController.navigate("settings")
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
                            onBack = { navController.popBackStack() }
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
