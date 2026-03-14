package com.example.xcpro.weather.rain

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherProfileSettingsBindingsModule {

    @Binds
    @IntoSet
    abstract fun bindWeatherOverlayProfileSettingsCaptureContributor(
        contributor: WeatherOverlayProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindWeatherOverlayProfileSettingsApplyContributor(
        contributor: WeatherOverlayProfileSettingsContributor
    ): ProfileSettingsApplyContributor
}
