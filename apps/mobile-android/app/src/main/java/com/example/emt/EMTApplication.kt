package com.example.emt

import android.app.Application
import com.example.emt.data.AppDatabase
import com.example.emt.data.UsageRepository
import com.example.emt.data.settings.SettingsRepository

class EMTApplication : Application() {
    // Using by lazy so the database and repository are only created when they're needed
    // rather than when the application starts.
    val database by lazy { AppDatabase.getDatabase(this) }
    val usageRepository by lazy { UsageRepository(database.usageDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
}
