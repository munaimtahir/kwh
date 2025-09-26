package com.example.kwh.settings

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Simple settings repository for test support.
 * This is a minimal implementation to support the test fix.
 */
class SettingsRepository(private val context: Context) {
    
    private val _settings = MutableStateFlow(UserSettings(darkTheme = false, snoozeMinutes = DEFAULT_SNOOZE))
    val settings: Flow<UserSettings> = _settings

    suspend fun setSnoozeMinutes(minutes: Int) {
        _settings.value = _settings.value.copy(snoozeMinutes = minutes.coerceIn(15, 240))
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        _settings.value = _settings.value.copy(darkTheme = enabled)
    }

    companion object {
        const val DEFAULT_SNOOZE = 60
    }
}

data class UserSettings(
    val darkTheme: Boolean,
    val snoozeMinutes: Int
)