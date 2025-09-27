package com.example.kwh.settings

import android.content.Context

class SettingsRepository {
    companion object {
        const val DEFAULT_SNOOZE = 60
    }
}

data class UserSettings(
    val darkTheme: Boolean,
    val snoozeMinutes: Int