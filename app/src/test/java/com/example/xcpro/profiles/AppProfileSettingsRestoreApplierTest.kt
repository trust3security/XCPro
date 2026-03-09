package com.example.xcpro.profiles

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.example.xcpro.forecast.ForecastPreferencesRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.map.widgets.MapWidgetId
import com.example.xcpro.map.widgets.MapWidgetLayoutRepository
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.ui.theme.ThemePreferencesRepository
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import com.example.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.example.xcpro.weather.wind.data.WindOverrideRepository
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AppProfileSettingsRestoreApplierTest {

    private data class Harness(
        val applier: AppProfileSettingsRestoreApplier,
        val flightMgmtPreferencesRepository: FlightMgmtPreferencesRepository,
        val themePreferencesRepository: ThemePreferencesRepository,
        val mapWidgetLayoutRepository: MapWidgetLayoutRepository,
        val variometerWidgetRepository: VariometerWidgetRepository,
        val gliderRepository: GliderRepository,
        val unitsRepository: UnitsRepository
    )

    private val gson = Gson()

    private fun createHarness(): Harness {
        val cardPreferences = mock<CardPreferences>()
        val flightMgmtPreferencesRepository = mock<FlightMgmtPreferencesRepository>()
        val lookAndFeelPreferences = mock<LookAndFeelPreferences>()
        val themePreferencesRepository = mock<ThemePreferencesRepository>()
        val mapWidgetLayoutRepository = mock<MapWidgetLayoutRepository>()
        val variometerWidgetRepository = mock<VariometerWidgetRepository>()
        val gliderRepository = mock<GliderRepository>()
        val unitsRepository = mock<UnitsRepository>()
        val levoVarioPreferencesRepository = mock<LevoVarioPreferencesRepository>()
        val thermallingModePreferencesRepository = mock<ThermallingModePreferencesRepository>()
        val ognTrafficPreferencesRepository = mock<OgnTrafficPreferencesRepository>()
        val ognTrailSelectionPreferencesRepository = mock<OgnTrailSelectionPreferencesRepository>()
        val adsbTrafficPreferencesRepository = mock<AdsbTrafficPreferencesRepository>()
        val weatherOverlayPreferencesRepository = mock<WeatherOverlayPreferencesRepository>()
        val forecastPreferencesRepository = mock<ForecastPreferencesRepository>()
        val windOverrideRepository = mock<WindOverrideRepository>()

        return Harness(
            applier = AppProfileSettingsRestoreApplier(
                cardPreferences = cardPreferences,
                flightMgmtPreferencesRepository = flightMgmtPreferencesRepository,
                lookAndFeelPreferences = lookAndFeelPreferences,
                themePreferencesRepository = themePreferencesRepository,
                mapWidgetLayoutRepository = mapWidgetLayoutRepository,
                variometerWidgetRepository = variometerWidgetRepository,
                gliderRepository = gliderRepository,
                unitsRepository = unitsRepository,
                levoVarioPreferencesRepository = levoVarioPreferencesRepository,
                thermallingModePreferencesRepository = thermallingModePreferencesRepository,
                ognTrafficPreferencesRepository = ognTrafficPreferencesRepository,
                ognTrailSelectionPreferencesRepository = ognTrailSelectionPreferencesRepository,
                adsbTrafficPreferencesRepository = adsbTrafficPreferencesRepository,
                weatherOverlayPreferencesRepository = weatherOverlayPreferencesRepository,
                forecastPreferencesRepository = forecastPreferencesRepository,
                windOverrideRepository = windOverrideRepository
            ),
            flightMgmtPreferencesRepository = flightMgmtPreferencesRepository,
            themePreferencesRepository = themePreferencesRepository,
            mapWidgetLayoutRepository = mapWidgetLayoutRepository,
            variometerWidgetRepository = variometerWidgetRepository,
            gliderRepository = gliderRepository,
            unitsRepository = unitsRepository
        )
    }

    @Test
    fun apply_flightMgmtSection_appliesOnlyMappedProfileIds() = runTest {
        val harness = createHarness()
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES to gson.toJsonTree(
                    FlightMgmtSectionSnapshot(
                        lastActiveTab = "screens",
                        profileLastFlightModes = mapOf(
                            "source-a" to "CRUISE",
                            "source-unmapped" to "THERMAL"
                        )
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf("source-a" to "target-a")
        )

        assertTrue(result.failedSections.isEmpty())
        assertTrue(result.appliedSections.contains(ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES))
        verify(harness.flightMgmtPreferencesRepository).setLastActiveTab("screens")
        verify(harness.flightMgmtPreferencesRepository).setLastFlightMode(
            "target-a",
            FlightModeSelection.CRUISE
        )
        verify(harness.flightMgmtPreferencesRepository, times(1)).setLastFlightMode(any(), any())
    }

    @Test
    fun apply_whenThemeSectionFails_recordsFailureAndContinues() = runTest {
        val harness = createHarness()
        doThrow(IllegalStateException("theme failure"))
            .whenever(harness.themePreferencesRepository)
            .setThemeId(any(), any())
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.THEME_PREFERENCES to gson.toJsonTree(
                    ThemeSectionSnapshot(
                        themeIdByProfile = mapOf("source-a" to "dark"),
                        customColorsByProfileAndTheme = emptyMap()
                    )
                ),
                ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES to gson.toJsonTree(
                    FlightMgmtSectionSnapshot(
                        lastActiveTab = "instruments",
                        profileLastFlightModes = mapOf("source-a" to "THERMAL")
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf("source-a" to "target-a")
        )

        assertTrue(result.appliedSections.contains(ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES))
        assertTrue(result.failedSections.containsKey(ProfileSettingsSectionIds.THEME_PREFERENCES))
        verify(harness.flightMgmtPreferencesRepository).setLastActiveTab("instruments")
        verify(harness.flightMgmtPreferencesRepository).setLastFlightMode(
            "target-a",
            FlightModeSelection.THERMAL
        )
    }

    @Test
    fun apply_profileScopedLayoutAndGliderSections_mapToImportedProfileIds() = runTest {
        val harness = createHarness()
        val gliderConfig = GliderConfig(waterBallastKg = 7.0)
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT to gson.toJsonTree(
                    MapWidgetLayoutSectionSnapshot(
                        widgetsByProfile = mapOf(
                            "source-a" to mapOf(
                                MapWidgetId.SIDE_HAMBURGER.name to MapWidgetPlacementSnapshot(
                                    offset = OffsetSnapshot(10f, 20f),
                                    sizePx = 180f
                                )
                            )
                        )
                    )
                ),
                ProfileSettingsSectionIds.VARIOMETER_WIDGET_LAYOUT to gson.toJsonTree(
                    VariometerWidgetLayoutSectionSnapshot(
                        layoutsByProfile = mapOf(
                            "source-a" to VariometerLayoutProfileSnapshot(
                                offset = OffsetSnapshot(40f, 50f),
                                sizePx = 160f,
                                hasPersistedOffset = true,
                                hasPersistedSize = true
                            )
                        )
                    )
                ),
                ProfileSettingsSectionIds.GLIDER_CONFIG to gson.toJsonTree(
                    GliderSectionSnapshot(
                        profiles = mapOf(
                            "source-a" to GliderProfileSectionSnapshot(
                                selectedModelId = "js1c-18",
                                effectiveModelId = "js1c-18",
                                isFallbackPolarActive = false,
                                config = gliderConfig
                            )
                        )
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf("source-a" to "target-a")
        )

        assertTrue(result.failedSections.isEmpty())
        verify(harness.mapWidgetLayoutRepository).saveOffset(
            "target-a",
            MapWidgetId.SIDE_HAMBURGER,
            OffsetPx(10f, 20f)
        )
        verify(harness.mapWidgetLayoutRepository).saveSizePx(
            "target-a",
            MapWidgetId.SIDE_HAMBURGER,
            180f
        )
        verify(harness.variometerWidgetRepository).saveOffset(
            profileId = "target-a",
            offset = OffsetPx(40f, 50f)
        )
        verify(harness.variometerWidgetRepository).saveSize(
            profileId = "target-a",
            sizePx = 160f
        )
        verify(harness.gliderRepository).saveProfileSnapshot(
            profileId = "target-a",
            selectedModelId = "js1c-18",
            config = gliderConfig
        )
    }

    @Test
    fun apply_mapWidgetLayout_usesDefaultProfileLayoutWhenSourceHasNoPersistedData() = runTest {
        val harness = createHarness()
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.MAP_WIDGET_LAYOUT to gson.toJsonTree(
                    MapWidgetLayoutSectionSnapshot(
                        widgetsByProfile = mapOf(
                            "source-a" to mapOf(
                                MapWidgetId.SIDE_HAMBURGER.name to MapWidgetPlacementSnapshot(
                                    offset = null,
                                    sizePx = null
                                ),
                                MapWidgetId.SETTINGS_SHORTCUT.name to MapWidgetPlacementSnapshot(
                                    offset = null,
                                    sizePx = null
                                )
                            ),
                            ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID to mapOf(
                                MapWidgetId.SIDE_HAMBURGER.name to MapWidgetPlacementSnapshot(
                                    offset = OffsetSnapshot(11f, 22f),
                                    sizePx = 181f
                                ),
                                MapWidgetId.SETTINGS_SHORTCUT.name to MapWidgetPlacementSnapshot(
                                    offset = OffsetSnapshot(33f, 44f),
                                    sizePx = 121f
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf("source-a" to "target-a")
        )

        assertTrue(result.failedSections.isEmpty())
        verify(harness.mapWidgetLayoutRepository).saveOffset(
            "target-a",
            MapWidgetId.SIDE_HAMBURGER,
            OffsetPx(11f, 22f)
        )
        verify(harness.mapWidgetLayoutRepository).saveSizePx(
            "target-a",
            MapWidgetId.SIDE_HAMBURGER,
            181f
        )
        verify(harness.mapWidgetLayoutRepository).saveOffset(
            "target-a",
            MapWidgetId.SETTINGS_SHORTCUT,
            OffsetPx(33f, 44f)
        )
        verify(harness.mapWidgetLayoutRepository).saveSizePx(
            "target-a",
            MapWidgetId.SETTINGS_SHORTCUT,
            121f
        )
        verify(harness.mapWidgetLayoutRepository, times(2)).saveOffset(eq("target-a"), any(), any())
        verify(harness.mapWidgetLayoutRepository, times(2)).saveSizePx(eq("target-a"), any(), any())
    }

    @Test
    fun apply_unitsSection_appliesOnlyMappedProfileIds() = runTest {
        val harness = createHarness()
        val feetUnits = UnitsPreferences(altitude = AltitudeUnit.FEET)
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.UNITS_PREFERENCES to gson.toJsonTree(
                    UnitsSectionSnapshot(
                        unitsByProfile = mapOf(
                            "source-a" to feetUnits,
                            "source-unmapped" to UnitsPreferences()
                        )
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf("source-a" to "target-a")
        )

        assertTrue(result.failedSections.isEmpty())
        assertTrue(result.appliedSections.contains(ProfileSettingsSectionIds.UNITS_PREFERENCES))
        verify(harness.unitsRepository).writeProfileUnits("target-a", feetUnits)
        verify(harness.unitsRepository, times(1)).writeProfileUnits(any(), any())
    }

    @Test
    fun apply_unitsSection_resolvesLegacyDefaultAliasToCanonicalMapEntry() = runTest {
        val harness = createHarness()
        val feetUnits = UnitsPreferences(altitude = AltitudeUnit.FEET)
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.UNITS_PREFERENCES to gson.toJsonTree(
                    UnitsSectionSnapshot(
                        unitsByProfile = mapOf(
                            "default" to feetUnits
                        )
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf(
                ProfileIdResolver.CANONICAL_DEFAULT_PROFILE_ID to "target-default"
            )
        )

        assertTrue(result.failedSections.isEmpty())
        verify(harness.unitsRepository).writeProfileUnits(eq("target-default"), eq(feetUnits))
    }
}
