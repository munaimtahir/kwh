package com.example.kwh.di

import android.content.Context
import com.example.kwh.ui.common.AndroidStringResolver
import com.example.kwh.ui.common.StringResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UiModule {

    @Provides
    @Singleton
    fun provideStringResolver(
        @ApplicationContext context: Context
    ): StringResolver {
        return AndroidStringResolver(context)
    }
}
