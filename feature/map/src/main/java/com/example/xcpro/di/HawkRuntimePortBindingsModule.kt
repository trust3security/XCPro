package com.example.xcpro.di

import com.example.xcpro.hawk.HawkActiveSourcePort
import com.example.xcpro.hawk.HawkSensorStreamPort
import com.example.xcpro.hawk.MapHawkActiveSourceAdapter
import com.example.xcpro.hawk.MapHawkSensorStreamAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HawkRuntimePortBindingsModule {
    @Binds
    @Singleton
    abstract fun bindHawkSensorStreamPort(
        impl: MapHawkSensorStreamAdapter
    ): HawkSensorStreamPort

    @Binds
    @Singleton
    abstract fun bindHawkActiveSourcePort(
        impl: MapHawkActiveSourceAdapter
    ): HawkActiveSourcePort
}
