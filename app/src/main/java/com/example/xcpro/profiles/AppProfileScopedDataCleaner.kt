package com.example.xcpro.profiles

import com.example.dfcards.CardPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.map.widgets.MapWidgetLayoutRepository
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.example.xcpro.ui.theme.ThemePreferencesRepository
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppProfileScopedDataCleaner @Inject constructor(
    private val cardPreferences: CardPreferences,
    private val flightMgmtPreferencesRepository: FlightMgmtPreferencesRepository,
    private val lookAndFeelPreferences: LookAndFeelPreferences,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val mapWidgetLayoutRepository: MapWidgetLayoutRepository,
    private val variometerWidgetRepository: VariometerWidgetRepository,
    private val gliderRepository: GliderRepository,
    private val unitsRepository: UnitsRepository,
    private val orientationSettingsRepository: MapOrientationSettingsRepository
) : ProfileScopedDataCleaner {

    override suspend fun clearProfileData(profileId: String) {
        cardPreferences.clearProfile(profileId)
        flightMgmtPreferencesRepository.clearProfile(profileId)
        lookAndFeelPreferences.clearProfile(profileId)
        themePreferencesRepository.clearProfile(profileId)
        mapWidgetLayoutRepository.deleteProfileLayout(profileId)
        variometerWidgetRepository.deleteProfileLayout(profileId)
        gliderRepository.clearProfile(profileId)
        unitsRepository.clearProfile(profileId)
        orientationSettingsRepository.clearProfile(profileId)
    }
}
