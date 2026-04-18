package com.trust3.xcpro.profiles

import com.trust3.xcpro.audio.LEGACY_DEFAULT_DEADBAND_MAX
import com.trust3.xcpro.audio.LEGACY_DEFAULT_DEADBAND_MIN
import com.trust3.xcpro.audio.LEGACY_DEFAULT_LIFT_THRESHOLD
import com.trust3.xcpro.audio.LEGACY_DEFAULT_SINK_SILENCE_THRESHOLD
import com.trust3.xcpro.audio.legacyEffectiveLiftStartThreshold
import com.trust3.xcpro.audio.legacyEffectiveSinkStartThreshold
import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.trust3.xcpro.vario.LevoVarioPreferencesRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class LevoVarioProfileSettingsContributor @Inject constructor(
    private val levoVarioPreferencesRepository: LevoVarioPreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES) return null
        val config = levoVarioPreferencesRepository.config.first()
        return gson.toJsonTree(
            LevoVarioSectionSnapshot(
                macCready = config.macCready,
                macCreadyRisk = config.macCreadyRisk,
                autoMcEnabled = config.autoMcEnabled,
                teCompensationEnabled = config.teCompensationEnabled,
                showWindSpeedOnVario = config.showWindSpeedOnVario,
                showHawkCard = config.showHawkCard,
                enableHawkUi = config.enableHawkUi,
                audioEnabled = config.audioSettings.enabled,
                audioVolume = config.audioSettings.volume,
                audioLiftStartThreshold = config.audioSettings.liftStartThreshold,
                audioSinkStartThreshold = config.audioSettings.sinkStartThreshold,
                audioDutyCycle = config.audioSettings.dutyCycle,
                hawkNeedleOmegaMinHz = config.hawkNeedleOmegaMinHz,
                hawkNeedleOmegaMaxHz = config.hawkNeedleOmegaMaxHz,
                hawkNeedleTargetTauSec = config.hawkNeedleTargetTauSec,
                hawkNeedleDriftTauMinSec = config.hawkNeedleDriftTauMinSec,
                hawkNeedleDriftTauMaxSec = config.hawkNeedleDriftTauMaxSec
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.LEVO_VARIO_PREFERENCES) return
        val section = gson.fromJson(payload, SerializedLevoVarioSectionSnapshot::class.java)
            .toCanonicalSnapshot()
        levoVarioPreferencesRepository.setMacCready(section.macCready)
        levoVarioPreferencesRepository.setMacCreadyRisk(section.macCreadyRisk)
        levoVarioPreferencesRepository.setAutoMcEnabled(section.autoMcEnabled)
        levoVarioPreferencesRepository.setTeCompensationEnabled(section.teCompensationEnabled)
        levoVarioPreferencesRepository.setShowWindSpeedOnVario(section.showWindSpeedOnVario)
        levoVarioPreferencesRepository.setShowHawkCard(section.showHawkCard)
        levoVarioPreferencesRepository.setEnableHawkUi(section.enableHawkUi)
        levoVarioPreferencesRepository.updateAudioSettings { existing ->
            existing.copy(
                enabled = section.audioEnabled,
                volume = section.audioVolume,
                liftStartThreshold = section.audioLiftStartThreshold,
                sinkStartThreshold = section.audioSinkStartThreshold,
                dutyCycle = section.audioDutyCycle
            )
        }
        levoVarioPreferencesRepository.setHawkNeedleOmegaMinHz(section.hawkNeedleOmegaMinHz)
        levoVarioPreferencesRepository.setHawkNeedleOmegaMaxHz(section.hawkNeedleOmegaMaxHz)
        levoVarioPreferencesRepository.setHawkNeedleTargetTauSec(section.hawkNeedleTargetTauSec)
        levoVarioPreferencesRepository.setHawkNeedleDriftTauMinSec(section.hawkNeedleDriftTauMinSec)
        levoVarioPreferencesRepository.setHawkNeedleDriftTauMaxSec(section.hawkNeedleDriftTauMaxSec)
    }
}

private data class SerializedLevoVarioSectionSnapshot(
    val macCready: Double,
    val macCreadyRisk: Double,
    val autoMcEnabled: Boolean,
    val teCompensationEnabled: Boolean,
    val showWindSpeedOnVario: Boolean,
    val showHawkCard: Boolean,
    val enableHawkUi: Boolean,
    val audioEnabled: Boolean,
    val audioVolume: Float,
    val audioLiftStartThreshold: Double? = null,
    val audioSinkStartThreshold: Double? = null,
    val audioDutyCycle: Double,
    val audioLiftThreshold: Double? = null,
    val audioSinkSilenceThreshold: Double? = null,
    val audioDeadbandMin: Double? = null,
    val audioDeadbandMax: Double? = null,
    val hawkNeedleOmegaMinHz: Double,
    val hawkNeedleOmegaMaxHz: Double,
    val hawkNeedleTargetTauSec: Double,
    val hawkNeedleDriftTauMinSec: Double,
    val hawkNeedleDriftTauMaxSec: Double
) {
    fun toCanonicalSnapshot(): LevoVarioSectionSnapshot {
        val legacyDeadbandMin = audioDeadbandMin ?: LEGACY_DEFAULT_DEADBAND_MIN
        return LevoVarioSectionSnapshot(
            macCready = macCready,
            macCreadyRisk = macCreadyRisk,
            autoMcEnabled = autoMcEnabled,
            teCompensationEnabled = teCompensationEnabled,
            showWindSpeedOnVario = showWindSpeedOnVario,
            showHawkCard = showHawkCard,
            enableHawkUi = enableHawkUi,
            audioEnabled = audioEnabled,
            audioVolume = audioVolume,
            audioLiftStartThreshold = audioLiftStartThreshold
                ?: legacyEffectiveLiftStartThreshold(
                    liftThreshold = audioLiftThreshold ?: LEGACY_DEFAULT_LIFT_THRESHOLD,
                    deadbandMin = legacyDeadbandMin,
                    deadbandMax = audioDeadbandMax ?: LEGACY_DEFAULT_DEADBAND_MAX
                ),
            audioSinkStartThreshold = audioSinkStartThreshold
                ?: legacyEffectiveSinkStartThreshold(
                    sinkSilenceThreshold = audioSinkSilenceThreshold
                        ?: LEGACY_DEFAULT_SINK_SILENCE_THRESHOLD,
                    deadbandMin = legacyDeadbandMin
                ),
            audioDutyCycle = audioDutyCycle,
            hawkNeedleOmegaMinHz = hawkNeedleOmegaMinHz,
            hawkNeedleOmegaMaxHz = hawkNeedleOmegaMaxHz,
            hawkNeedleTargetTauSec = hawkNeedleTargetTauSec,
            hawkNeedleDriftTauMinSec = hawkNeedleDriftTauMinSec,
            hawkNeedleDriftTauMaxSec = hawkNeedleDriftTauMaxSec
        )
    }
}
