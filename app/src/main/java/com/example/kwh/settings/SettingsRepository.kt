package com.example.kwh.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Stores user preferences such as dark theme and snooze duration using DataStore.
 */
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Flow of the current user settings. Collecting this will emit updates whenever
     * preferences change.
     */
    val settings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val darkTheme = preferences[PreferencesKeys.DARK_THEME] ?: false
        val snoozeMinutes = preferences[PreferencesKeys.SNOOZE_MINUTES] ?: DEFAULT_SNOOZE
        UserSettings(darkTheme = darkTheme, snoozeMinutes = snoozeMinutes)
    }

    /** Persist whether the dark theme is enabled. */
    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DARK_THEME] = enabled }
    }

    /** Persist the snooze duration in minutes. */
    suspend fun setSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.SNOOZE_MINUTES] = minutes }
    }

    private object PreferencesKeys {
        val DARK_THEME: Preferences.Key<Boolean> = booleanPreferencesKey("dark_theme")
        val SNOOZE_MINUTES: Preferences.Key<Int> = intPreferencesKey("snooze_minutes")
    }

    companion object {
        /** Default snooze duration in minutes. */
        const val DEFAULT_SNOOZE: Int = 60
    }
}

/**
 * Immutable representation of user preferences. Both fields are non-null.
 */
data class UserSettings(
    val darkTheme: Boolean,
    val snoozeMinutes: Int
)

// Define an extension property for DataStore. This must live at the top level.
private val Context.dataStore by preferencesDataStore(name = "settings")