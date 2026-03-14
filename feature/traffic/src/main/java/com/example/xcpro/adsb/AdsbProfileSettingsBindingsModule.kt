package com.example.xcpro.adsb

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsbProfileSettingsBindingsModule {

    @Binds
    @IntoSet
    abstract fun bindAdsbTrafficProfileSettingsCaptureContributor(
        contributor: AdsbTrafficProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindAdsbTrafficProfileSettingsApplyContributor(
        contributor: AdsbTrafficProfileSettingsContributor
    ): ProfileSettingsApplyContributor
}
