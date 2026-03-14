package com.example.xcpro.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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
    @IntoSet
    abstract fun bindFlightMgmtProfileSettingsCaptureContributor(
        contributor: FlightMgmtProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindLookAndFeelProfileSettingsCaptureContributor(
        contributor: LookAndFeelProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindThemeProfileSettingsCaptureContributor(
        contributor: ThemeProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindQnhProfileSettingsCaptureContributor(
        contributor: QnhProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindMapStyleProfileSettingsCaptureContributor(
        contributor: MapStyleProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindSnailTrailProfileSettingsCaptureContributor(
        contributor: SnailTrailProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindMapWidgetLayoutProfileSettingsCaptureContributor(
        contributor: MapWidgetLayoutProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindUnitsProfileSettingsCaptureContributor(
        contributor: UnitsProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindOrientationProfileSettingsCaptureContributor(
        contributor: OrientationProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindGliderConfigProfileSettingsCaptureContributor(
        contributor: GliderConfigProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindLevoVarioProfileSettingsCaptureContributor(
        contributor: LevoVarioProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindThermallingModeProfileSettingsCaptureContributor(
        contributor: ThermallingModeProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @IntoSet
    abstract fun bindWindOverrideProfileSettingsCaptureContributor(
        contributor: WindOverrideProfileSettingsContributor
    ): ProfileSettingsCaptureContributor

    @Binds
    @Singleton
    abstract fun bindProfileSettingsRestoreApplier(
        applier: AppProfileSettingsRestoreApplier
    ): ProfileSettingsRestoreApplier

    @Binds
    @IntoSet
    abstract fun bindFlightMgmtProfileSettingsApplyContributor(
        contributor: FlightMgmtProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindLookAndFeelProfileSettingsApplyContributor(
        contributor: LookAndFeelProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindThemeProfileSettingsApplyContributor(
        contributor: ThemeProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindQnhProfileSettingsApplyContributor(
        contributor: QnhProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindMapStyleProfileSettingsApplyContributor(
        contributor: MapStyleProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindSnailTrailProfileSettingsApplyContributor(
        contributor: SnailTrailProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindMapWidgetLayoutProfileSettingsApplyContributor(
        contributor: MapWidgetLayoutProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindUnitsProfileSettingsApplyContributor(
        contributor: UnitsProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindOrientationProfileSettingsApplyContributor(
        contributor: OrientationProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindGliderConfigProfileSettingsApplyContributor(
        contributor: GliderConfigProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindLevoVarioProfileSettingsApplyContributor(
        contributor: LevoVarioProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindThermallingModeProfileSettingsApplyContributor(
        contributor: ThermallingModeProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @IntoSet
    abstract fun bindWindOverrideProfileSettingsApplyContributor(
        contributor: WindOverrideProfileSettingsContributor
    ): ProfileSettingsApplyContributor

    @Binds
    @Singleton
    abstract fun bindProfileScopedDataCleaner(
        cleaner: AppProfileScopedDataCleaner
    ): ProfileScopedDataCleaner
}
