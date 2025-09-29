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
                    onAddReadingClick = {},
                    onAddReading = { _, _, _ -> },
                    onDismissReading = {},
                    onReminderChanged = { _, _, _, _, _ -> },
                    onViewHistory = {},
                    onDeleteMeter = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("No readings yet").assertIsDisplayed()
        composeRule.onNodeWithText("Tap the + button to add your first meter.").assertIsDisplayed()
    }

    @Test
    fun historyButton_invokesCallback() {
        var clicked = false
        composeRule.setContent {
            KwhTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        meters = listOf(
                            MeterItem(
                                id = 1,
                                name = "Home",
                                reminderEnabled = false,
                                reminderFrequencyDays = 30,
                                reminderHour = 9,
                                reminderMinute = 0,
                                latestReading = MeterReading(120.0, Instant.now(), null),
                                nextReminder = null
                            )
                        )
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onAddMeterClick = {},
                    onAddMeter = { _, _, _, _ -> },
                    onDismissAddMeter = {},
                    onAddReadingClick = {},
                    onAddReading = { _, _, _ -> },
                    onDismissReading = {},
                    onReminderChanged = { _, _, _, _, _ -> },
                    onViewHistory = { clicked = true },
                    onDeleteMeter = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("History").performClick()
        assert(clicked)
    }
}
