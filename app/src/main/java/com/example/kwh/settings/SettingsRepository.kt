package com.example.kwh.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            darkTheme = prefs[DARK_THEME] ?: false,
            snoozeMinutes = prefs[SNOOZE_MINUTES] ?: DEFAULT_SNOOZE
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DARK_THEME] = enabled
        }
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[SNOOZE_MINUTES] = minutes.coerceIn(15, 240)
        }
    }

    companion object {
        private val DARK_THEME = booleanPreferencesKey("dark_theme")
        private val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        const val DEFAULT_SNOOZE = 60
    }
}

data class UserSettings(
    val darkTheme: Boolean,
    val snoozeMinutes: Int
)
