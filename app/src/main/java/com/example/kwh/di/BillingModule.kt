package com.example.kwh.di

import com.example.kwh.billing.BillingCycleCalculator
import com.example.kwh.billing.DefaultBillingCycleCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideBillingCycleCalculator(): BillingCycleCalculator {
        return DefaultBillingCycleCalculator()
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
