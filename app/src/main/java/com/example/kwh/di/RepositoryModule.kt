package com.example.kwh.di

import com.example.kwh.data.MeterDao
import com.example.kwh.repository.MeterRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMeterRepository(meterDao: MeterDao): MeterRepository {
        return MeterRepository(meterDao)
    }
}
