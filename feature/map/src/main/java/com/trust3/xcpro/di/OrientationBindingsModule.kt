package com.trust3.xcpro.di

import com.trust3.xcpro.MapOrientationHeadingPolicy
import com.trust3.xcpro.MapOrientationSensorInputSource
import com.trust3.xcpro.orientation.OrientationSensorInputSource
import com.trust3.xcpro.orientation.OrientationStationaryHeadingPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OrientationBindingsModule {
    @Binds
    abstract fun bindOrientationSensorInputSource(
        impl: MapOrientationSensorInputSource
    ): OrientationSensorInputSource

    @Binds
    abstract fun bindOrientationStationaryHeadingPolicy(
        impl: MapOrientationHeadingPolicy
    ): OrientationStationaryHeadingPolicy
}
