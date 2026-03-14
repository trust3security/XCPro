package com.example.dfcards.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class CardProfileSettingsBindingsModule {

    @Binds
    @IntoSet
    abstract fun bindCardProfileSettingsCaptureContributor(
        contributor: CardProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindCardProfileSettingsApplyContributor(
        contributor: CardProfileSettingsContributor
    ): ProfileSettingsApplyContributor
}
