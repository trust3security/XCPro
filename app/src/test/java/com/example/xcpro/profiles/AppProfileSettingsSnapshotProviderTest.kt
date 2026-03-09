package com.example.xcpro.profiles

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.example.xcpro.forecast.ForecastPreferences
import com.example.xcpro.forecast.ForecastPreferencesRepository
import com.example.xcpro.glider.GliderProfileSnapshot
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.map.widgets.MapWidgetLayoutRepository
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.thermalling.ThermallingModeSettings
import com.example.xcpro.ui.theme.ThemePreferencesRepository
import com.example.xcpro.vario.LevoVarioConfig
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.variometer.layout.VariometerLayout
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import com.example.xcpro.weather.rain.WeatherOverlayPreferences
import com.example.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.example.xcpro.weather.wind.data.WindOverrideRepository
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AppProfileSettingsSnapshotProviderTest {

    private val gson = Gson()

    @Test
    fun buildSnapshot_includesAllTierASections_andNormalizesProfileAliases() = runTest {
        val cardPreferences = mock<CardPreferences>()
        whenever(cardPreferences.getAllTemplates()).thenReturn(
            flowOf(
                listOf(
                    FlightTemplate(
                        id = "id01",
                        name = "Cruise",
                        description = "Cruise cards",
                        cardIds = listOf("agl")
                    )
                )
            )
        )
        whenever(cardPreferences.getAllProfileTemplateCards()).thenReturn(
            flowOf(
                mapOf(
                    "default" to mapOf("id01" to listOf("agl")),
                    "pilot-1" to mapOf("id01" to listOf("gps_alt"))
                )
            )
        )
        whenever(cardPreferences.getAllProfileFlightModeTemplates()).thenReturn(
            flowOf(
                mapOf(
                    "default" to mapOf("CRUISE" to "id01"),
                    "pilot-1" to mapOf("CRUISE" to "id01")
                )
            )
        )
        whenever(cardPreferences.getCardsAcrossPortrait()).thenReturn(flowOf(3))
        whenever(cardPreferences.getCardsAnchorPortrait()).thenReturn(
            flowOf(CardPreferences.CardAnchor.TOP)
        )
        whenever(cardPreferences.getLastActiveTemplate()).thenReturn(flowOf("id01"))
        whenever(cardPreferences.getVarioSmoothingAlpha()).thenReturn(flowOf(0.25f))
        whenever(cardPreferences.getProfileAllFlightModeVisibilities(any())).thenReturn(
            flowOf(mapOf("CRUISE" to true, "THERMAL" to true, "FINAL_GLIDE" to false))
        )
        whenever(cardPreferences.getProfileCardPositions(any(), any())).thenReturn(
            flowOf(
                mapOf(
                    "agl" to CardPreferences.CardPosition(
                        x = 1f,
                        y = 2f,
                        width = 100f,
                        height = 50f
                    )
                )
            )
        )

        val flightMgmtPreferences = mock<FlightMgmtPreferencesRepository>()
        whenever(flightMgmtPreferences.getLastActiveTab()).thenReturn("screens")
        whenever(flightMgmtPreferences.getLastFlightMode(any())).thenReturn(FlightModeSelection.CRUISE)

        val lookAndFeelPreferences = mock<LookAndFeelPreferences>()
        whenever(lookAndFeelPreferences.getStatusBarStyleId(any())).thenReturn("transparent")
        whenever(lookAndFeelPreferences.getCardStyleId(any())).thenReturn("standard")
        whenever(lookAndFeelPreferences.getColorThemeId(any())).thenReturn("default")

        val themePreferences = mock<ThemePreferencesRepository>()
        whenever(themePreferences.getThemeId(any())).thenReturn("default")
        whenever(themePreferences.getCustomColorsJson(any(), any())).thenReturn(null)

        val mapWidgetLayout = mock<MapWidgetLayoutRepository>()
        whenever(mapWidgetLayout.readOffset(any(), any())).thenReturn(null)
        whenever(mapWidgetLayout.readSizePx(any(), any())).thenReturn(null)

        val variometerWidget = mock<VariometerWidgetRepository>()
        whenever(variometerWidget.load(any(), any(), any())).thenReturn(
            VariometerLayout(
                offset = OffsetPx.Zero,
                sizePx = 0f,
                hasPersistedOffset = false,
                hasPersistedSize = false
            )
        )

        val gliderRepository = mock<GliderRepository>()
        whenever(gliderRepository.loadProfileSnapshot(any())).thenReturn(
            GliderProfileSnapshot(
                selectedModelId = null,
                effectiveModelId = "club-default-fallback",
                isFallbackPolarActive = true,
                config = GliderConfig()
            )
        )
        val unitsRepository = mock<UnitsRepository>()
        whenever(unitsRepository.readProfileUnits(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID))
            .thenReturn(UnitsPreferences(altitude = AltitudeUnit.FEET))
        whenever(unitsRepository.readProfileUnits("pilot-1"))
            .thenReturn(UnitsPreferences(altitude = AltitudeUnit.METERS))

        val levoVario = mock<LevoVarioPreferencesRepository>()
        whenever(levoVario.config).thenReturn(flowOf(LevoVarioConfig()))

        val thermalling = mock<ThermallingModePreferencesRepository>()
        whenever(thermalling.settingsFlow).thenReturn(flowOf(ThermallingModeSettings()))

        val ognTraffic = mock<OgnTrafficPreferencesRepository>()
        whenever(ognTraffic.enabledFlow).thenReturn(flowOf(false))
        whenever(ognTraffic.iconSizePxFlow).thenReturn(flowOf(124))
        whenever(ognTraffic.receiveRadiusKmFlow).thenReturn(flowOf(150))
        whenever(ognTraffic.autoReceiveRadiusEnabledFlow).thenReturn(flowOf(false))
        whenever(ognTraffic.displayUpdateModeFlow).thenReturn(flowOf(OgnDisplayUpdateMode.REAL_TIME))
        whenever(ognTraffic.showSciaEnabledFlow).thenReturn(flowOf(false))
        whenever(ognTraffic.showThermalsEnabledFlow).thenReturn(flowOf(false))
        whenever(ognTraffic.thermalRetentionHoursFlow).thenReturn(flowOf(24))
        whenever(ognTraffic.hotspotsDisplayPercentFlow).thenReturn(flowOf(100))
        whenever(ognTraffic.targetEnabledFlow).thenReturn(flowOf(false))
        whenever(ognTraffic.targetAircraftKeyFlow).thenReturn(flowOf(null))
        whenever(ognTraffic.ownFlarmHexFlow).thenReturn(flowOf(null))
        whenever(ognTraffic.ownIcaoHexFlow).thenReturn(flowOf(null))
        whenever(ognTraffic.clientCallsignFlow).thenReturn(flowOf(null))

        val ognTrailSelection = mock<OgnTrailSelectionPreferencesRepository>()
        whenever(ognTrailSelection.selectedAircraftKeysFlow).thenReturn(flowOf(emptySet()))

        val adsbTraffic = mock<AdsbTrafficPreferencesRepository>()
        whenever(adsbTraffic.enabledFlow).thenReturn(flowOf(false))
        whenever(adsbTraffic.iconSizePxFlow).thenReturn(flowOf(124))
        whenever(adsbTraffic.maxDistanceKmFlow).thenReturn(flowOf(10))
        whenever(adsbTraffic.verticalAboveMetersFlow).thenReturn(flowOf(1000.0))
        whenever(adsbTraffic.verticalBelowMetersFlow).thenReturn(flowOf(800.0))
        whenever(adsbTraffic.emergencyFlashEnabledFlow).thenReturn(flowOf(true))
        whenever(adsbTraffic.emergencyAudioEnabledFlow).thenReturn(flowOf(false))
        whenever(adsbTraffic.emergencyAudioCooldownMsFlow).thenReturn(flowOf(45_000L))
        whenever(adsbTraffic.emergencyAudioMasterEnabledFlow).thenReturn(flowOf(true))
        whenever(adsbTraffic.emergencyAudioShadowModeFlow).thenReturn(flowOf(false))
        whenever(adsbTraffic.emergencyAudioCohortPercentFlow).thenReturn(flowOf(100))
        whenever(adsbTraffic.emergencyAudioCohortBucketFlow).thenReturn(flowOf(1))
        whenever(adsbTraffic.emergencyAudioRollbackLatchedFlow).thenReturn(flowOf(false))
        whenever(adsbTraffic.emergencyAudioRollbackReasonFlow).thenReturn(flowOf(null))

        val weatherOverlay = mock<WeatherOverlayPreferencesRepository>()
        whenever(weatherOverlay.preferencesFlow).thenReturn(flowOf(WeatherOverlayPreferences()))

        val forecast = mock<ForecastPreferencesRepository>()
        whenever(forecast.preferencesFlow).thenReturn(flowOf(ForecastPreferences()))

        val windOverride = mock<WindOverrideRepository>()
        whenever(windOverride.manualWind).thenReturn(MutableStateFlow(null))

        val provider = AppProfileSettingsSnapshotProvider(
            cardPreferences = cardPreferences,
            flightMgmtPreferencesRepository = flightMgmtPreferences,
            lookAndFeelPreferences = lookAndFeelPreferences,
            themePreferencesRepository = themePreferences,
            mapWidgetLayoutRepository = mapWidgetLayout,
            variometerWidgetRepository = variometerWidget,
            gliderRepository = gliderRepository,
            unitsRepository = unitsRepository,
            levoVarioPreferencesRepository = levoVario,
            thermallingModePreferencesRepository = thermalling,
            ognTrafficPreferencesRepository = ognTraffic,
            ognTrailSelectionPreferencesRepository = ognTrailSelection,
            adsbTrafficPreferencesRepository = adsbTraffic,
            weatherOverlayPreferencesRepository = weatherOverlay,
            forecastPreferencesRepository = forecast,
            windOverrideRepository = windOverride
        )

        val snapshot = provider.buildSnapshot(setOf("default", "pilot-1"))

        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.CARD_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.THEME_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.VARIOMETER_WIDGET_LAYOUT))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.GLIDER_CONFIG))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.UNITS_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.OGN_TRAFFIC_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.OGN_TRAIL_SELECTION_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.ADSB_TRAFFIC_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.WEATHER_OVERLAY_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.FORECAST_PREFERENCES))
        assertTrue(snapshot.sections.keys.contains(ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES))

        val flightMgmtSection = gson.fromJson(
            snapshot.sections.getValue(ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES),
            FlightMgmtSectionSnapshot::class.java
        )
        assertEquals(
            setOf(ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID, "pilot-1"),
            flightMgmtSection.profileLastFlightModes.keys
        )
        val unitsSection = gson.fromJson(
            snapshot.sections.getValue(ProfileSettingsSectionIds.UNITS_PREFERENCES),
            UnitsSectionSnapshot::class.java
        )
        assertEquals(
            AltitudeUnit.FEET,
            unitsSection.unitsByProfile[ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID]?.altitude
        )
        assertEquals(
            AltitudeUnit.METERS,
            unitsSection.unitsByProfile["pilot-1"]?.altitude
        )
    }
}
