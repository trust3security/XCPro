package com.trust3.xcpro.variometer.bluetooth.lxnav.control

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LxBluetoothControlBindingsModule {
    @Binds
    @Singleton
    abstract fun bindLxBluetoothControlPort(
        impl: LxBluetoothControlUseCase
    ): LxBluetoothControlPort
}
