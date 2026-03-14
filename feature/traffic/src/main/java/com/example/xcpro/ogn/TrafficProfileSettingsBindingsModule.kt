package com.example.xcpro.ogn

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class TrafficProfileSettingsBindingsModule {

    @Binds
    @IntoSet
    abstract fun bindOgnTrafficProfileSettingsCaptureContributor(
        contributor: OgnTrafficProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindOgnTrafficProfileSettingsApplyContributor(
        contributor: OgnTrafficProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindOgnTrailSelectionProfileSettingsCaptureContributor(
        contributor: OgnTrailSelectionProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindOgnTrailSelectionProfileSettingsApplyContributor(
        contributor: OgnTrailSelectionProfileSettingsContributor
    ): ProfileSettingsApplyContributor
}
