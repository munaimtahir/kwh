package com.example.kwh.di

import com.example.kwh.billing.BillingCycleCalculator
import com.example.kwh.data.MeterDao
import com.example.kwh.repository.MeterRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMeterRepository(
        meterDao: MeterDao,
        clock: Clock,
        billingCycleCalculator: BillingCycleCalculator
    ): MeterRepository {
        return MeterRepository(meterDao, clock, billingCycleCalculator)
    }
}
