package com.example.kwh.di

import com.example.kwh.billing.BillingCycleCalculator
import com.example.kwh.billing.DefaultBillingCycleCalculator
import com.example.kwh.data.MeterDao
import com.example.kwh.repository.MeterRepository
import java.time.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideBillingCycleCalculator(): BillingCycleCalculator = DefaultBillingCycleCalculator()

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
