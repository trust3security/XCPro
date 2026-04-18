package com.trust3.xcpro.profiles

import com.example.dfcards.FlightModeSelection
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AppProfileSettingsRestoreApplierOrderingAndFailureTest {

    private val gson = Gson()

    @Test
    fun apply_whenThemeSectionFails_recordsFailureAndContinues() = runTest {
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
    fun apply_usesCanonicalSectionOrder_notSnapshotMapOrder() = runTest {
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
}
