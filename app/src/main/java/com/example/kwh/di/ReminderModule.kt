package com.example.kwh.di

import android.content.Context
import com.example.kwh.reminders.ReminderScheduler
import com.example.kwh.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {

    @Provides
    @Singleton
    fun provideReminderScheduler(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): ReminderScheduler {
        return ReminderScheduler(context, settingsRepository)
    }
}
