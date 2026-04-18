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
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AppProfileScopedDataCleanerTest {

    @Test
    fun clearProfileData_clearsAllSupportedProfileScopedStores() = runTest {
        val cardPreferences = mock<CardPreferences>()
        val flightMgmtPreferencesRepository = mock<FlightMgmtPreferencesRepository>()
        val lookAndFeelPreferences = mock<LookAndFeelPreferences>()
        val themePreferencesRepository = mock<ThemePreferencesRepository>()
        val mapWidgetLayoutRepository = mock<MapWidgetLayoutRepository>()
        val variometerWidgetRepository = mock<VariometerWidgetRepository>()
        val gliderRepository = mock<GliderRepository>()
        val unitsRepository = mock<UnitsRepository>()
        val mapStyleRepository = mock<MapStyleRepository>()
        val mapTrailPreferences = mock<MapTrailPreferences>()
        val qnhPreferencesRepository = mock<QnhPreferencesRepository>()
        val orientationSettingsRepository = mock<MapOrientationSettingsRepository>()
        val cleaner = AppProfileScopedDataCleaner(
            cardPreferences = cardPreferences,
            flightMgmtPreferencesRepository = flightMgmtPreferencesRepository,
            lookAndFeelPreferences = lookAndFeelPreferences,
            themePreferencesRepository = themePreferencesRepository,
            mapWidgetLayoutRepository = mapWidgetLayoutRepository,
            variometerWidgetRepository = variometerWidgetRepository,
            gliderRepository = gliderRepository,
            unitsRepository = unitsRepository,
            mapStyleRepository = mapStyleRepository,
            mapTrailPreferences = mapTrailPreferences,
            qnhPreferencesRepository = qnhPreferencesRepository,
            orientationSettingsRepository = orientationSettingsRepository
        )

        cleaner.clearProfileData("pilot-a")

        verify(cardPreferences).clearProfile("pilot-a")
        verify(flightMgmtPreferencesRepository).clearProfile("pilot-a")
        verify(lookAndFeelPreferences).clearProfile("pilot-a")
        verify(themePreferencesRepository).clearProfile("pilot-a")
        verify(mapWidgetLayoutRepository).deleteProfileLayout("pilot-a")
        verify(variometerWidgetRepository).deleteProfileLayout("pilot-a")
        verify(gliderRepository).clearProfile("pilot-a")
        verify(unitsRepository).clearProfile("pilot-a")
        verify(mapStyleRepository).clearProfile("pilot-a")
        verify(mapTrailPreferences).clearProfile("pilot-a")
        verify(qnhPreferencesRepository).clearProfile("pilot-a")
        verify(orientationSettingsRepository).clearProfile("pilot-a")
    }
}
