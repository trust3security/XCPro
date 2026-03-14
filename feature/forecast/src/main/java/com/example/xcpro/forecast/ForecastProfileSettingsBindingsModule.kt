package com.example.xcpro.forecast

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ForecastProfileSettingsBindingsModule {

    @Binds
    @IntoSet
    abstract fun bindForecastProfileSettingsCaptureContributor(
        contributor: ForecastProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindForecastProfileSettingsApplyContributor(
        contributor: ForecastProfileSettingsContributor
    ): ProfileSettingsApplyContributor
}
