package com.example.emt.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.emt.EMTApplication
import com.example.emt.ui.analytics.AnalyticsScreen
import com.example.emt.ui.analytics.AnalyticsViewModel
import com.example.emt.ui.analytics.AnalyticsViewModelFactory
import com.example.emt.ui.settings.SettingsScreen
import com.example.emt.ui.settings.SettingsViewModel
import com.example.emt.ui.settings.SettingsViewModelFactory
import com.example.emt.ui.usage.AddReadingScreen
import com.example.emt.ui.usage.HistoryScreen
import com.example.emt.ui.usage.UsageViewModel
import com.example.emt.ui.usage.ViewModelFactory

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object History : Screen("history", "History", Icons.Default.List)
    object Add : Screen("add", "Add", Icons.Default.Add)
    object Analytics : Screen("analytics", "Analytics", Icons.Default.Insights)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(app: EMTApplication) {
    val usageViewModel: UsageViewModel = viewModel(
        factory = ViewModelFactory(app.usageRepository)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(app.settingsRepository)
    )
    val analyticsViewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModelFactory(app.usageRepository)
    )
    val usages by usageViewModel.allUsages.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.History) }

    val items by usageViewModel.allUsages.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf(Screen.History, Screen.Add, Screen.Analytics, Screen.Settings)
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                is Screen.History -> HistoryScreen(

                    onEdit = { usageViewModel.updateUsage(it) },
                    onDelete = { usageViewModel.deleteUsage(it) }
                )
                is Screen.Add -> AddReadingScreen(usageViewModel)
                is Screen.Analytics -> AnalyticsScreen(analyticsViewModel)
                is Screen.Settings -> SettingsScreen(settingsViewModel)
            }
        }
    }
}
