package com.example.xcpro.profiles

import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.profiles.CardProfileSettingsContributor
import com.example.xcpro.adsb.AdsbTrafficProfileSettingsContributor
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.example.xcpro.forecast.ForecastProfileSettingsContributor
import com.example.xcpro.forecast.ForecastPreferencesRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.MapOrientationSettings
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.map.MapStyleRepository
import com.example.xcpro.map.QnhPreferencesRepository
import com.example.xcpro.map.trail.MapTrailPreferences
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType
import com.example.xcpro.map.widgets.MapWidgetId
import com.example.xcpro.map.widgets.MapWidgetLayoutRepository
import com.example.xcpro.map.domain.MapShiftBiasMode
import com.example.xcpro.ogn.OgnTrailSelectionProfileSettingsContributor
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficProfileSettingsContributor
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelPreferences
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.example.xcpro.ui.theme.ThemePreferencesRepository
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.variometer.layout.VariometerWidgetProfileSettingsContributor
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import com.example.xcpro.weather.rain.WeatherOverlayProfileSettingsContributor
import com.example.xcpro.weather.rain.WeatherOverlayPreferencesRepository
import com.example.xcpro.weather.wind.data.WindOverrideRepository
import com.google.gson.Gson
import dagger.Lazy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        val unitsRepository: UnitsRepository,
        val mapStyleRepository: MapStyleRepository,
        val mapTrailPreferences: MapTrailPreferences,
        val qnhPreferencesRepository: QnhPreferencesRepository,
        val orientationSettingsRepository: MapOrientationSettingsRepository,
        val levoVarioPreferencesRepository: LevoVarioPreferencesRepository,
        val thermallingModePreferencesRepository: ThermallingModePreferencesRepository,
        val windOverrideRepository: WindOverrideRepository
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
        val mapStyleRepository = mock<MapStyleRepository>()
        val mapTrailPreferences = mock<MapTrailPreferences>()
        val qnhPreferencesRepository = mock<QnhPreferencesRepository>()
        val orientationSettingsRepository = mock<MapOrientationSettingsRepository>()
        val levoVarioPreferencesRepository = mock<LevoVarioPreferencesRepository>()
        val thermallingModePreferencesRepository = mock<ThermallingModePreferencesRepository>()
        val ognTrafficPreferencesRepository = mock<OgnTrafficPreferencesRepository>()
        val ognTrailSelectionPreferencesRepository = mock<OgnTrailSelectionPreferencesRepository>()
        val adsbTrafficPreferencesRepository = mock<AdsbTrafficPreferencesRepository>()
        val weatherOverlayPreferencesRepository = mock<WeatherOverlayPreferencesRepository>()
        val forecastPreferencesRepository = mock<ForecastPreferencesRepository>()
        val windOverrideRepository = mock<WindOverrideRepository>()
        lateinit var contributorRegistry: ProfileSettingsContributorRegistry
        val contributorRegistryLazy = object : Lazy<ProfileSettingsContributorRegistry> {
            override fun get(): ProfileSettingsContributorRegistry = contributorRegistry
        }

        val applier = AppProfileSettingsRestoreApplier(
            contributorRegistry = contributorRegistryLazy
        )
        val cardContributor = CardProfileSettingsContributor(cardPreferences)
        val flightMgmtContributor = FlightMgmtProfileSettingsContributor(
            flightMgmtPreferencesRepository
        )
        val lookAndFeelContributor = LookAndFeelProfileSettingsContributor(
            lookAndFeelPreferences
        )
        val themeContributor = ThemeProfileSettingsContributor(themePreferencesRepository)
        val qnhContributor = QnhProfileSettingsContributor(qnhPreferencesRepository)
        val mapStyleContributor = MapStyleProfileSettingsContributor(mapStyleRepository)
        val snailTrailContributor = SnailTrailProfileSettingsContributor(mapTrailPreferences)
        val mapWidgetLayoutContributor = MapWidgetLayoutProfileSettingsContributor(
            mapWidgetLayoutRepository
        )
        val variometerWidgetContributor = VariometerWidgetProfileSettingsContributor(
            variometerWidgetRepository
        )
        val ognTrafficContributor = OgnTrafficProfileSettingsContributor(
            ognTrafficPreferencesRepository
        )
        val ognTrailSelectionContributor = OgnTrailSelectionProfileSettingsContributor(
            ognTrailSelectionPreferencesRepository
        )
        val adsbTrafficContributor = AdsbTrafficProfileSettingsContributor(
            adsbTrafficPreferencesRepository
        )
        val weatherOverlayContributor = WeatherOverlayProfileSettingsContributor(
            weatherOverlayPreferencesRepository
        )
        val forecastContributor = ForecastProfileSettingsContributor(
            forecastPreferencesRepository
        )
        val unitsContributor = UnitsProfileSettingsContributor(unitsRepository)
        val orientationContributor = OrientationProfileSettingsContributor(
            orientationSettingsRepository
        )
        val gliderContributor = GliderConfigProfileSettingsContributor(gliderRepository)
        val levoVarioContributor = LevoVarioProfileSettingsContributor(
            levoVarioPreferencesRepository
        )
        val thermallingContributor = ThermallingModeProfileSettingsContributor(
            thermallingModePreferencesRepository
        )
        val windOverrideContributor = WindOverrideProfileSettingsContributor(windOverrideRepository)
        contributorRegistry = ProfileSettingsContributorRegistry(
            captureContributors = setOf(
                cardContributor,
                flightMgmtContributor,
                lookAndFeelContributor,
                themeContributor,
                qnhContributor,
                mapStyleContributor,
                snailTrailContributor,
                mapWidgetLayoutContributor,
                variometerWidgetContributor,
                ognTrafficContributor,
                ognTrailSelectionContributor,
                adsbTrafficContributor,
                weatherOverlayContributor,
                forecastContributor,
                unitsContributor,
                orientationContributor,
                gliderContributor,
                levoVarioContributor,
                thermallingContributor,
                windOverrideContributor
            ),
            applyContributors = setOf(
                cardContributor,
                flightMgmtContributor,
                lookAndFeelContributor,
                themeContributor,
                qnhContributor,
                mapStyleContributor,
                snailTrailContributor,
                mapWidgetLayoutContributor,
                variometerWidgetContributor,
                ognTrafficContributor,
                ognTrailSelectionContributor,
                adsbTrafficContributor,
                weatherOverlayContributor,
                forecastContributor,
                unitsContributor,
                orientationContributor,
                gliderContributor,
                levoVarioContributor,
                thermallingContributor,
                windOverrideContributor
            )
        )

        return Harness(
            applier = applier,
            flightMgmtPreferencesRepository = flightMgmtPreferencesRepository,
            themePreferencesRepository = themePreferencesRepository,
            mapWidgetLayoutRepository = mapWidgetLayoutRepository,
            variometerWidgetRepository = variometerWidgetRepository,
            gliderRepository = gliderRepository,
            unitsRepository = unitsRepository,
            mapStyleRepository = mapStyleRepository,
            mapTrailPreferences = mapTrailPreferences,
            qnhPreferencesRepository = qnhPreferencesRepository,
            orientationSettingsRepository = orientationSettingsRepository,
            levoVarioPreferencesRepository = levoVarioPreferencesRepository,
            thermallingModePreferencesRepository = thermallingModePreferencesRepository,
            windOverrideRepository = windOverrideRepository
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

        assertEquals(
            listOf(ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES),
            result.appliedSections.toList()
        )
        assertEquals(
            listOf(ProfileSettingsSectionIds.THEME_PREFERENCES),
            result.failedSections.keys.toList()
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
                    mapOf(
                        "layoutsByProfile" to mapOf(
                            "source-a" to mapOf(
                                "offset" to mapOf("x" to 40f, "y" to 50f),
                                "sizePx" to 160f,
                                "hasPersistedOffset" to true,
                                "hasPersistedSize" to true
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

    @Test
    fun apply_orientationSection_appliesOnlyMappedProfileIds() = runTest {
        val harness = createHarness()
        val expected = MapOrientationSettings(
            cruiseMode = com.example.xcpro.common.orientation.MapOrientationMode.NORTH_UP,
            circlingMode = com.example.xcpro.common.orientation.MapOrientationMode.HEADING_UP,
            minSpeedThresholdMs = 2.5,
            gliderScreenPercent = 22,
            mapShiftBiasMode = MapShiftBiasMode.TRACK,
            mapShiftBiasStrength = 0.35,
            autoResetEnabled = false,
            autoResetTimeoutSeconds = 25,
            bearingSmoothingEnabled = false
        )
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.ORIENTATION_PREFERENCES to gson.toJsonTree(
                    OrientationSectionSnapshot(
                        settingsByProfile = mapOf(
                            "source-a" to OrientationProfileSectionSnapshot(
                                cruiseMode = expected.cruiseMode.name,
                                circlingMode = expected.circlingMode.name,
                                minSpeedThresholdMs = expected.minSpeedThresholdMs,
                                gliderScreenPercent = expected.gliderScreenPercent,
                                mapShiftBiasMode = expected.mapShiftBiasMode.name,
                                mapShiftBiasStrength = expected.mapShiftBiasStrength,
                                autoResetEnabled = expected.autoResetEnabled,
                                autoResetTimeoutSeconds = expected.autoResetTimeoutSeconds,
                                bearingSmoothingEnabled = expected.bearingSmoothingEnabled
                            ),
                            "source-unmapped" to OrientationProfileSectionSnapshot(
                                cruiseMode = "TRACK_UP",
                                circlingMode = "TRACK_UP",
                                minSpeedThresholdMs = 2.0,
                                gliderScreenPercent = 35,
                                mapShiftBiasMode = "NONE",
                                mapShiftBiasStrength = 0.0,
                                autoResetEnabled = true,
                                autoResetTimeoutSeconds = 10,
                                bearingSmoothingEnabled = true
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
        verify(harness.orientationSettingsRepository).writeProfileSettings("target-a", expected)
        verify(harness.orientationSettingsRepository, times(1)).writeProfileSettings(any(), any())
    }

    @Test
    fun apply_usesCanonicalSectionOrder_notSnapshotMapOrder() = runTest {
        val harness = createHarness()
        val snapshot = ProfileSettingsSnapshot(
            sections = linkedMapOf(
                ProfileSettingsSectionIds.QNH_PREFERENCES to gson.toJsonTree(
                    QnhSectionSnapshot(
                        valuesByProfile = mapOf(
                            "source-qnh" to QnhProfileSectionSnapshot(
                                manualQnhHpa = 1007.1,
                                capturedAtWallMs = 1_000L,
                                source = "MANUAL"
                            )
                        )
                    )
                ),
                ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES to gson.toJsonTree(
                    FlightMgmtSectionSnapshot(
                        lastActiveTab = "screens",
                        profileLastFlightModes = mapOf("source-fmg" to "CRUISE")
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf(
                "source-fmg" to "target-fmg",
                "source-qnh" to "target-qnh"
            )
        )

        assertTrue(result.failedSections.isEmpty())
        assertEquals(
            listOf(
                ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES,
                ProfileSettingsSectionIds.QNH_PREFERENCES
            ),
            result.appliedSections.toList()
        )
        verify(harness.flightMgmtPreferencesRepository).setLastActiveTab("screens")
        verify(harness.flightMgmtPreferencesRepository).setLastFlightMode(
            "target-fmg",
            FlightModeSelection.CRUISE
        )
        verify(harness.qnhPreferencesRepository).writeProfileManualQnh(
            profileId = "target-qnh",
            qnhHpa = 1007.1,
            capturedAtWallMs = 1_000L,
            source = "MANUAL"
        )
    }

    @Test
    fun apply_mapStyleTrailAndQnhSections_applyMappedProfiles() = runTest {
        val harness = createHarness()
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.MAP_STYLE_PREFERENCES to gson.toJsonTree(
                    MapStyleSectionSnapshot(
                        stylesByProfile = mapOf(
                            "source-a" to "Satellite",
                            "source-unmapped" to "Topo"
                        )
                    )
                ),
                ProfileSettingsSectionIds.SNAIL_TRAIL_PREFERENCES to gson.toJsonTree(
                    SnailTrailSectionSnapshot(
                        settingsByProfile = mapOf(
                            "source-a" to SnailTrailProfileSectionSnapshot(
                                length = TrailLength.SHORT.name,
                                type = TrailType.ALTITUDE.name,
                                windDriftEnabled = false,
                                scalingEnabled = true
                            )
                        )
                    )
                ),
                ProfileSettingsSectionIds.QNH_PREFERENCES to gson.toJsonTree(
                    QnhSectionSnapshot(
                        valuesByProfile = mapOf(
                            "source-a" to QnhProfileSectionSnapshot(
                                manualQnhHpa = 1009.2,
                                capturedAtWallMs = 5_000L,
                                source = "MANUAL"
                            ),
                            "source-clear" to QnhProfileSectionSnapshot(
                                manualQnhHpa = null,
                                capturedAtWallMs = null,
                                source = null
                            )
                        )
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf(
                "source-a" to "target-a",
                "source-clear" to "target-clear"
            )
        )

        assertTrue(result.failedSections.isEmpty())
        verify(harness.mapStyleRepository).writeProfileStyle("target-a", "Satellite")
        verify(harness.mapTrailPreferences).writeProfileSettings(
            "target-a",
            TrailSettings(
                length = TrailLength.SHORT,
                type = TrailType.ALTITUDE,
                windDriftEnabled = false,
                scalingEnabled = true
            )
        )
        verify(harness.qnhPreferencesRepository).writeProfileManualQnh(
            profileId = "target-a",
            qnhHpa = 1009.2,
            capturedAtWallMs = 5_000L,
            source = "MANUAL"
        )
        verify(harness.qnhPreferencesRepository).clearProfile("target-clear")
    }

    @Test
    fun apply_localGlobalSections_applyViaExtractedContributors() = runTest {
        val harness = createHarness()
        val levoSnapshot = LevoVarioSectionSnapshot(
            macCready = 1.6,
            macCreadyRisk = 1.2,
            autoMcEnabled = false,
            teCompensationEnabled = true,
            showWindSpeedOnVario = false,
            showHawkCard = true,
            enableHawkUi = true,
            audioEnabled = false,
            audioVolume = 0.33f,
            audioLiftThreshold = 0.8,
            audioSinkSilenceThreshold = -1.5,
            audioDutyCycle = 0.55,
            audioDeadbandMin = -0.1,
            audioDeadbandMax = 0.2,
            hawkNeedleOmegaMinHz = 1.1,
            hawkNeedleOmegaMaxHz = 2.4,
            hawkNeedleTargetTauSec = 0.9,
            hawkNeedleDriftTauMinSec = 4.0,
            hawkNeedleDriftTauMaxSec = 9.0
        )
        val thermallingSnapshot = ThermallingModeSectionSnapshot(
            enabled = false,
            switchToThermalMode = true,
            zoomOnlyFallbackWhenThermalHidden = true,
            enterDelaySeconds = 9,
            exitDelaySeconds = 14,
            applyZoomOnEnter = false,
            thermalZoomLevel = 12.5f,
            rememberManualThermalZoomInSession = false,
            restorePreviousModeOnExit = true,
            restorePreviousZoomOnExit = false
        )
        val windSnapshot = WindOverrideSectionSnapshot(
            manualOverride = ManualWindOverrideSnapshot(
                speedMs = 8.5,
                directionFromDeg = 210.0,
                timestampMillis = 55_000L,
                source = "MANUAL"
            )
        )
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES to gson.toJsonTree(levoSnapshot),
                ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES to
                    gson.toJsonTree(thermallingSnapshot),
                ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES to gson.toJsonTree(windSnapshot)
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = emptyMap()
        )

        assertTrue(result.failedSections.isEmpty())
        assertEquals(
            listOf(
                ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES,
                ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES,
                ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES
            ),
            result.appliedSections.toList()
        )
        verify(harness.levoVarioPreferencesRepository).setMacCready(levoSnapshot.macCready)
        verify(harness.levoVarioPreferencesRepository).setMacCreadyRisk(levoSnapshot.macCreadyRisk)
        verify(harness.levoVarioPreferencesRepository).setAutoMcEnabled(levoSnapshot.autoMcEnabled)
        verify(harness.levoVarioPreferencesRepository)
            .setTeCompensationEnabled(levoSnapshot.teCompensationEnabled)
        verify(harness.levoVarioPreferencesRepository)
            .setShowWindSpeedOnVario(levoSnapshot.showWindSpeedOnVario)
        verify(harness.levoVarioPreferencesRepository).setShowHawkCard(levoSnapshot.showHawkCard)
        verify(harness.levoVarioPreferencesRepository).setEnableHawkUi(levoSnapshot.enableHawkUi)
        verify(harness.levoVarioPreferencesRepository)
            .setHawkNeedleOmegaMinHz(levoSnapshot.hawkNeedleOmegaMinHz)
        verify(harness.levoVarioPreferencesRepository)
            .setHawkNeedleOmegaMaxHz(levoSnapshot.hawkNeedleOmegaMaxHz)
        verify(harness.levoVarioPreferencesRepository)
            .setHawkNeedleTargetTauSec(levoSnapshot.hawkNeedleTargetTauSec)
        verify(harness.levoVarioPreferencesRepository)
            .setHawkNeedleDriftTauMinSec(levoSnapshot.hawkNeedleDriftTauMinSec)
        verify(harness.levoVarioPreferencesRepository)
            .setHawkNeedleDriftTauMaxSec(levoSnapshot.hawkNeedleDriftTauMaxSec)
        verify(harness.levoVarioPreferencesRepository).updateAudioSettings(any())

        verify(harness.thermallingModePreferencesRepository).setEnabled(thermallingSnapshot.enabled)
        verify(harness.thermallingModePreferencesRepository)
            .setSwitchToThermalMode(thermallingSnapshot.switchToThermalMode)
        verify(harness.thermallingModePreferencesRepository).setZoomOnlyFallbackWhenThermalHidden(
            thermallingSnapshot.zoomOnlyFallbackWhenThermalHidden
        )
        verify(harness.thermallingModePreferencesRepository)
            .setEnterDelaySeconds(thermallingSnapshot.enterDelaySeconds)
        verify(harness.thermallingModePreferencesRepository)
            .setExitDelaySeconds(thermallingSnapshot.exitDelaySeconds)
        verify(harness.thermallingModePreferencesRepository)
            .setApplyZoomOnEnter(thermallingSnapshot.applyZoomOnEnter)
        verify(harness.thermallingModePreferencesRepository)
            .setThermalZoomLevel(thermallingSnapshot.thermalZoomLevel)
        verify(harness.thermallingModePreferencesRepository)
            .setRememberManualThermalZoomInSession(
                thermallingSnapshot.rememberManualThermalZoomInSession
            )
        verify(harness.thermallingModePreferencesRepository)
            .setRestorePreviousModeOnExit(thermallingSnapshot.restorePreviousModeOnExit)
        verify(harness.thermallingModePreferencesRepository)
            .setRestorePreviousZoomOnExit(thermallingSnapshot.restorePreviousZoomOnExit)

        verify(harness.windOverrideRepository).setManualWind(
            speedMs = windSnapshot.manualOverride!!.speedMs,
            directionFromDeg = windSnapshot.manualOverride.directionFromDeg,
            timestampMillis = windSnapshot.manualOverride.timestampMillis
        )
    }
}
