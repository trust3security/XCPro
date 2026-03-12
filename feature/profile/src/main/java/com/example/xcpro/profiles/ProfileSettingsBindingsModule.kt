package com.example.xcpro.profiles

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileSettingsBindingsModule {

    @Binds
    @Singleton
    abstract fun bindProfileSettingsSnapshotProvider(
        provider: AppProfileSettingsSnapshotProvider
    ): ProfileSettingsSnapshotProvider

    @Binds
    @Singleton
    abstract fun bindProfileSettingsRestoreApplier(
        applier: AppProfileSettingsRestoreApplier
    ): ProfileSettingsRestoreApplier

    @Binds
    @Singleton
    abstract fun bindProfileScopedDataCleaner(
        cleaner: AppProfileScopedDataCleaner
    ): ProfileScopedDataCleaner
}
