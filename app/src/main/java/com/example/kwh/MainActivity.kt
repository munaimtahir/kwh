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
import com.example.kwh.ui.home.HomeEvent
import com.example.kwh.ui.home.HomeScreen
import com.example.kwh.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
            }
            if (granted.not()) {
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

    private data class ReminderRequest(
        val meterId: Long,
        val enabled: Boolean,
        val frequency: Int,
        val hour: Int,
        val minute: Int
    )
}
