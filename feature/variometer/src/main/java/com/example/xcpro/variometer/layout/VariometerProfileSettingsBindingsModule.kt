package com.example.xcpro.variometer.layout

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class VariometerProfileSettingsBindingsModule {

    @Binds
    @IntoSet
    abstract fun bindVariometerWidgetProfileSettingsCaptureContributor(
        contributor: VariometerWidgetProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindVariometerWidgetProfileSettingsApplyContributor(
        contributor: VariometerWidgetProfileSettingsContributor
    ): ProfileSettingsApplyContributor
}
