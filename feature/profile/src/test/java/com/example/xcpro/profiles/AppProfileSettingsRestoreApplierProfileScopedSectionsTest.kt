package com.example.xcpro.profiles

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.MapOrientationSettings
import com.example.xcpro.map.domain.MapShiftBiasMode
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType
import com.example.xcpro.map.widgets.MapWidgetId
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class AppProfileSettingsRestoreApplierProfileScopedSectionsTest {

    private val gson = Gson()

    @Test
    fun apply_flightMgmtSection_appliesOnlyMappedProfileIds() = runTest {
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
    fun apply_profileScopedLayoutAndGliderSections_mapToImportedProfileIds() = runTest {
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
    fun apply_lookAndFeelAndThemeSections_keepThemeApplyCanonical() = runTest {
        val harness = createAppProfileSettingsRestoreApplierHarness()
        val snapshot = ProfileSettingsSnapshot(
            sections = mapOf(
                ProfileSettingsSectionIds.LOOK_AND_FEEL_PREFERENCES to gson.toJsonTree(
                    LookAndFeelSectionSnapshot(
                        statusBarStyleByProfile = mapOf("source-a" to "glass"),
                        cardStyleByProfile = mapOf("source-a" to "compact")
                    )
                ),
                ProfileSettingsSectionIds.THEME_PREFERENCES to gson.toJsonTree(
                    ThemeSectionSnapshot(
                        themeIdByProfile = mapOf("source-a" to "sunset"),
                        customColorsByProfileAndTheme = emptyMap()
                    )
                )
            )
        )

        val result = harness.applier.apply(
            settingsSnapshot = snapshot,
            importedProfileIdMap = mapOf("source-a" to "target-a")
        )

        assertTrue(result.failedSections.isEmpty())
        verify(harness.lookAndFeelPreferences).setStatusBarStyleId("target-a", "glass")
        verify(harness.lookAndFeelPreferences).setCardStyleId("target-a", "compact")
        verify(harness.themePreferencesRepository).setThemeId("target-a", "sunset")
    }

    @Test
    fun apply_mapWidgetLayout_usesDefaultProfileLayoutWhenSourceHasNoPersistedData() = runTest {
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
    fun apply_mapStyleTrailAndQnhSections_applyMappedProfiles() = runTest {
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
}
