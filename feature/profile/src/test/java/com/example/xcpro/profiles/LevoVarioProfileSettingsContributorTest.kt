package com.example.xcpro.profiles

import com.example.xcpro.audio.VarioAudioSettings
import com.example.xcpro.vario.LevoVarioConfig
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LevoVarioProfileSettingsContributorTest {

    private val gson = Gson()

    @Test
    fun capture_section_writes_canonical_audio_threshold_fields() = runTest {
        val repository = mock<LevoVarioPreferencesRepository>()
        whenever(repository.config).thenReturn(
            flowOf(
                LevoVarioConfig(
                    audioSettings = VarioAudioSettings(
                        liftStartThreshold = 0.8,
                        sinkStartThreshold = -1.5
                    )
                )
            )
        )
        val contributor = LevoVarioProfileSettingsContributor(repository)

        val payload = contributor.captureSection(
            sectionId = ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES,
            profileIds = emptySet()
        )
        val snapshot = gson.fromJson(payload, LevoVarioSectionSnapshot::class.java)

        assertEquals(0.8, snapshot.audioLiftStartThreshold, 0.0)
        assertEquals(-1.5, snapshot.audioSinkStartThreshold, 0.0)
    }

    @Test
    fun apply_section_writes_canonical_audio_thresholds_in_update_transform() = runTest {
        val repository = mock<LevoVarioPreferencesRepository>()
        val contributor = LevoVarioProfileSettingsContributor(repository)
        val snapshot = LevoVarioSectionSnapshot(
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

        contributor.applySection(
            sectionId = ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES,
            payload = gson.toJsonTree(snapshot),
            importedProfileIdMap = emptyMap()
        )

        val transformCaptor = argumentCaptor<(VarioAudioSettings) -> VarioAudioSettings>()
        verify(repository).updateAudioSettings(transformCaptor.capture())

        val updated = transformCaptor.firstValue(
            VarioAudioSettings(
                enabled = true,
                volume = 0.8f,
                liftStartThreshold = 0.1,
                sinkStartThreshold = -0.3,
                dutyCycle = 2.0 / 3.0,
            )
        )

        assertEquals(snapshot.audioEnabled, updated.enabled)
        assertEquals(snapshot.audioVolume, updated.volume)
        assertEquals(snapshot.audioLiftStartThreshold, updated.liftStartThreshold, 0.0)
        assertEquals(snapshot.audioSinkStartThreshold, updated.sinkStartThreshold, 0.0)
        assertEquals(snapshot.audioDutyCycle, updated.dutyCycle, 0.0)
    }

    @Test
    fun apply_section_accepts_legacy_raw_audio_fields_and_migrates_to_canonical_thresholds() = runTest {
        val repository = mock<LevoVarioPreferencesRepository>()
        val contributor = LevoVarioProfileSettingsContributor(repository)
        val legacyPayload = gson.fromJson(
            """
            {
              "macCready": 1.6,
              "macCreadyRisk": 1.2,
              "autoMcEnabled": false,
              "teCompensationEnabled": true,
              "showWindSpeedOnVario": false,
              "showHawkCard": true,
              "enableHawkUi": true,
              "audioEnabled": false,
              "audioVolume": 0.33,
              "audioLiftThreshold": 0.8,
              "audioSinkSilenceThreshold": -0.5,
              "audioDutyCycle": 0.55,
              "audioDeadbandMin": -1.5,
              "audioDeadbandMax": 0.2,
              "hawkNeedleOmegaMinHz": 1.1,
              "hawkNeedleOmegaMaxHz": 2.4,
              "hawkNeedleTargetTauSec": 0.9,
              "hawkNeedleDriftTauMinSec": 4.0,
              "hawkNeedleDriftTauMaxSec": 9.0
            }
            """.trimIndent(),
            JsonElement::class.java
        )

        contributor.applySection(
            sectionId = ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES,
            payload = legacyPayload,
            importedProfileIdMap = emptyMap()
        )

        val transformCaptor = argumentCaptor<(VarioAudioSettings) -> VarioAudioSettings>()
        verify(repository).updateAudioSettings(transformCaptor.capture())

        val updated = transformCaptor.firstValue(VarioAudioSettings())
        assertEquals(0.8, updated.liftStartThreshold, 0.0)
        assertEquals(-1.5, updated.sinkStartThreshold, 0.0)
    }
}
