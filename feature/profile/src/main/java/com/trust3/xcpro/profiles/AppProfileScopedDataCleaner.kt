package com.trust3.xcpro.profiles

import com.example.dfcards.CardPreferences
import com.trust3.xcpro.common.units.UnitsRepository
import com.trust3.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.trust3.xcpro.glider.GliderRepository
import com.trust3.xcpro.MapOrientationSettingsRepository
import com.trust3.xcpro.map.MapStyleRepository
import com.trust3.xcpro.map.QnhPreferencesRepository
import com.trust3.xcpro.map.trail.MapTrailPreferences
import com.trust3.xcpro.map.widgets.MapWidgetLayoutRepository
import com.trust3.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.trust3.xcpro.ui.theme.ThemePreferencesRepository
import com.trust3.xcpro.variometer.layout.VariometerWidgetRepository
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
    private val mapStyleRepository: MapStyleRepository,
    private val mapTrailPreferences: MapTrailPreferences,
    private val qnhPreferencesRepository: QnhPreferencesRepository,
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
        mapStyleRepository.clearProfile(profileId)
        mapTrailPreferences.clearProfile(profileId)
        qnhPreferencesRepository.clearProfile(profileId)
        orientationSettingsRepository.clearProfile(profileId)
    }
}
