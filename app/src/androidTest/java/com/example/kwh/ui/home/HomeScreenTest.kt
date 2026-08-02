package com.example.kwh.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kwh.ui.app.KwhTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_showsHint() {
        composeRule.setContent {
            KwhTheme {
                HomeScreen(
                    uiState = HomeUiState(),
                    snackbarHostState = SnackbarHostState(),
                    onAddMeterClick = {},
                    onAddMeter = { _, _, _, _ -> },
                    onDismissAddMeter = {},
                    onMeterClick = {},
                    onOpenAppSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("No readings yet").assertIsDisplayed()
        composeRule.onNodeWithText("Tap the + button to add your first meter.").assertIsDisplayed()
    }

    @Test
    fun meterListItem_click_invokesOnMeterClick() {
        var clickedId: Long? = null
        val meter = MeterItem(
            id = 1,
            name = "Home",
            reminderEnabled = false,
            reminderFrequencyDays = 30,
            reminderHour = 9,
            reminderMinute = 0,
            latestReading = MeterReading(120.0, Instant.now(), null),
            nextReminder = null,
            cycle = CycleUiModel(
                start = Instant.now(),
                end = Instant.now().plusSeconds(2_592_000),
                usedUnits = 0.0,
                projectedUnits = 0.0,
                ratePerDay = 0.0,
                hasProjection = false,
                nextThresholdValue = null,
                nextThresholdDate = null
            )
        )
        composeRule.setContent {
            KwhTheme {
                HomeScreen(
                    uiState = HomeUiState(ungroupedMeters = listOf(meter)),
                    snackbarHostState = SnackbarHostState(),
                    onAddMeterClick = {},
                    onAddMeter = { _, _, _, _ -> },
                    onDismissAddMeter = {},
                    onMeterClick = { clickedId = it },
                    onOpenAppSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("Home").performClick()
        assert(clickedId == 1L)
    }
}
