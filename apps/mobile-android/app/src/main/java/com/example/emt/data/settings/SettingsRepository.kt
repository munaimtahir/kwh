package com.example.emt.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val REMINDERS_ENABLED_KEY = booleanPreferencesKey("reminders_enabled")
        val REMINDER_TIME_KEY = stringPreferencesKey("reminder_time")
    }

    val remindersEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[REMINDERS_ENABLED_KEY] ?: false
        }

    val reminderTimeFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[REMINDER_TIME_KEY] ?: "12:00" // Default time
        }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[REMINDERS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setReminderTime(time: String) {
        dataStore.edit { settings ->
            settings[REMINDER_TIME_KEY] = time
        }
    }
}
