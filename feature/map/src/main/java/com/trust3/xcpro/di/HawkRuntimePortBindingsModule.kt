package com.trust3.xcpro.di

import com.trust3.xcpro.hawk.HawkActiveSourcePort
import com.trust3.xcpro.hawk.HawkSensorStreamPort
import com.trust3.xcpro.hawk.MapHawkActiveSourceAdapter
import com.trust3.xcpro.hawk.MapHawkSensorStreamAdapter
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
