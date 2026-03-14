package com.example.xcpro.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ThermallingModeProfileSettingsContributor @Inject constructor(
    private val thermallingModePreferencesRepository: ThermallingModePreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES) return null
        val settings = thermallingModePreferencesRepository.settingsFlow.first()
        return gson.toJsonTree(
            ThermallingModeSectionSnapshot(
                enabled = settings.enabled,
                switchToThermalMode = settings.switchToThermalMode,
                zoomOnlyFallbackWhenThermalHidden = settings.zoomOnlyFallbackWhenThermalHidden,
                enterDelaySeconds = settings.enterDelaySeconds,
                exitDelaySeconds = settings.exitDelaySeconds,
                applyZoomOnEnter = settings.applyZoomOnEnter,
                thermalZoomLevel = settings.thermalZoomLevel,
                rememberManualThermalZoomInSession = settings.rememberManualThermalZoomInSession,
                restorePreviousModeOnExit = settings.restorePreviousModeOnExit,
                restorePreviousZoomOnExit = settings.restorePreviousZoomOnExit
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.THERMALLING_MODE_PREFERENCES) return
        val section = gson.fromJson(payload, ThermallingModeSectionSnapshot::class.java)
        thermallingModePreferencesRepository.setEnabled(section.enabled)
        thermallingModePreferencesRepository.setSwitchToThermalMode(section.switchToThermalMode)
        thermallingModePreferencesRepository.setZoomOnlyFallbackWhenThermalHidden(
            section.zoomOnlyFallbackWhenThermalHidden
        )
        thermallingModePreferencesRepository.setEnterDelaySeconds(section.enterDelaySeconds)
        thermallingModePreferencesRepository.setExitDelaySeconds(section.exitDelaySeconds)
        thermallingModePreferencesRepository.setApplyZoomOnEnter(section.applyZoomOnEnter)
        thermallingModePreferencesRepository.setThermalZoomLevel(section.thermalZoomLevel)
        thermallingModePreferencesRepository.setRememberManualThermalZoomInSession(
            section.rememberManualThermalZoomInSession
        )
        thermallingModePreferencesRepository.setRestorePreviousModeOnExit(
            section.restorePreviousModeOnExit
        )
        thermallingModePreferencesRepository.setRestorePreviousZoomOnExit(
            section.restorePreviousZoomOnExit
        )
    }
}
