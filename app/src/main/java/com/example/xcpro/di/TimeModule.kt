package com.example.xcpro.di

import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.time.SystemClockProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClockProvider): Clock
}
