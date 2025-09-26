package com.example.kwh.di

import android.content.Context
import androidx.room.Room
import com.example.kwh.data.MeterDao
import com.example.kwh.data.MeterDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeterDatabase {
        return Room.databaseBuilder(
            context,
            MeterDatabase::class.java,
            "meter.db"
        ).build()
    }

    @Provides
    fun provideMeterDao(database: MeterDatabase): MeterDao = database.meterDao()
}
