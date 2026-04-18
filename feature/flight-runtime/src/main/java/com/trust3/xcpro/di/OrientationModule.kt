package com.trust3.xcpro.di

import com.trust3.xcpro.orientation.OrientationClock
import com.trust3.xcpro.orientation.SystemOrientationClock
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
