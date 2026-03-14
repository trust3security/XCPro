package com.example.xcpro.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.vario.LevoVarioPreferencesRepository
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
                audioLiftThreshold = config.audioSettings.liftThreshold,
                audioSinkSilenceThreshold = config.audioSettings.sinkSilenceThreshold,
                audioDutyCycle = config.audioSettings.dutyCycle,
                audioDeadbandMin = config.audioSettings.deadbandMin,
                audioDeadbandMax = config.audioSettings.deadbandMax,
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
        val section = gson.fromJson(payload, LevoVarioSectionSnapshot::class.java)
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
                liftThreshold = section.audioLiftThreshold,
                sinkSilenceThreshold = section.audioSinkSilenceThreshold,
                dutyCycle = section.audioDutyCycle,
                deadbandMin = section.audioDeadbandMin,
                deadbandMax = section.audioDeadbandMax
            )
        }
        levoVarioPreferencesRepository.setHawkNeedleOmegaMinHz(section.hawkNeedleOmegaMinHz)
        levoVarioPreferencesRepository.setHawkNeedleOmegaMaxHz(section.hawkNeedleOmegaMaxHz)
        levoVarioPreferencesRepository.setHawkNeedleTargetTauSec(section.hawkNeedleTargetTauSec)
        levoVarioPreferencesRepository.setHawkNeedleDriftTauMinSec(section.hawkNeedleDriftTauMinSec)
        levoVarioPreferencesRepository.setHawkNeedleDriftTauMaxSec(section.hawkNeedleDriftTauMaxSec)
    }
}
