package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.simulator.CondorLiveStatePort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CondorBridgeBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCondorTcpServerPort(
        impl: AndroidCondorTcpServer
    ): CondorTcpServerPort

    @Binds
    @Singleton
    abstract fun bindLocalNetworkInfoPort(
        impl: AndroidLocalNetworkInfoPort
    ): LocalNetworkInfoPort

    @Binds
    @Singleton
    abstract fun bindCondorBridgeControlPort(
        impl: CondorBridgeController
    ): CondorBridgeControlPort

    @Binds
    @Singleton
    abstract fun bindCondorLiveStatePort(
        impl: CondorSessionRepository
    ): CondorLiveStatePort

    @Binds
    @Singleton
    abstract fun bindCondorRuntimeSessionPort(
        impl: CondorBridgeController
    ): CondorRuntimeSessionPort
}
