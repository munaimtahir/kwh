package com.example.kwh.di

import android.content.Context
import com.example.kwh.settings.SettingsRepository
import com.example.kwh.settings.SnoozePreferenceReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides application-scoped instances for settings-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideSnoozePreferenceReader(
        repository: SettingsRepository
    ): SnoozePreferenceReader {
        return repository
    }
}