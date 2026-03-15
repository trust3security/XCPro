package com.example.xcpro.di

import com.example.xcpro.orientation.OrientationClock
import com.example.xcpro.orientation.SystemOrientationClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OrientationModule {
    @Binds
    abstract fun bindOrientationClock(impl: SystemOrientationClock): OrientationClock
}
