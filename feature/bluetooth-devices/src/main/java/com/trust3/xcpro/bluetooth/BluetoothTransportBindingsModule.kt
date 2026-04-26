package com.trust3.xcpro.bluetooth

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothTransportBindingsModule {

    @Binds
    @Singleton
    abstract fun bindBluetoothTransport(
        impl: AndroidBluetoothTransport
    ): BluetoothTransport
}
