package com.example.xcpro.di

import com.example.xcpro.MapOrientationHeadingPolicy
import com.example.xcpro.MapOrientationSensorInputSource
import com.example.xcpro.orientation.OrientationSensorInputSource
import com.example.xcpro.orientation.OrientationStationaryHeadingPolicy
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
