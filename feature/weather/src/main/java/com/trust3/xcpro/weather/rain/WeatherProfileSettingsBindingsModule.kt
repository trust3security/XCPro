package com.trust3.xcpro.weather.rain

import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
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
