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
        verify(orientationSettingsRepository).clearProfile("pilot-a")
    }
}
