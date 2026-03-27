package com.example.xcpro.profiles

import com.example.xcpro.audio.VarioAudioSettings
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

class AppProfileSettingsRestoreApplierLocalGlobalSectionsTest {

    private val gson = Gson()

    @Test
    fun apply_localGlobalSections_applyViaExtractedContributors() = runTest {
        val harness = createAppProfileSettingsRestoreApplierHarness()
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
            audioLiftStartThreshold = 0.8,
            audioSinkStartThreshold = -1.5,
            audioDutyCycle = 0.55,
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
        val transformCaptor = argumentCaptor<(VarioAudioSettings) -> VarioAudioSettings>()
        verify(harness.levoVarioPreferencesRepository).updateAudioSettings(transformCaptor.capture())
        val normalizedAudio = transformCaptor.firstValue(VarioAudioSettings())
        assertEquals(levoSnapshot.audioLiftStartThreshold, normalizedAudio.liftStartThreshold, 0.0)
        assertEquals(levoSnapshot.audioSinkStartThreshold, normalizedAudio.sinkStartThreshold, 0.0)

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
