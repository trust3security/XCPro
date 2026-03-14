package com.example.xcpro.adsb

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsSectionContract
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class AdsbTrafficProfileSettingsContributor @Inject constructor(
    private val adsbTrafficPreferencesRepository: AdsbTrafficPreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionContract.ADSB_TRAFFIC_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionContract.ADSB_TRAFFIC_PREFERENCES) return null
        return gson.toJsonTree(
            AdsbTrafficSectionPayload(
                enabled = adsbTrafficPreferencesRepository.enabledFlow.first(),
                iconSizePx = adsbTrafficPreferencesRepository.iconSizePxFlow.first(),
                maxDistanceKm = adsbTrafficPreferencesRepository.maxDistanceKmFlow.first(),
                verticalAboveMeters = adsbTrafficPreferencesRepository.verticalAboveMetersFlow.first(),
                verticalBelowMeters = adsbTrafficPreferencesRepository.verticalBelowMetersFlow.first(),
                emergencyFlashEnabled = adsbTrafficPreferencesRepository.emergencyFlashEnabledFlow.first(),
                emergencyAudioEnabled = adsbTrafficPreferencesRepository.emergencyAudioEnabledFlow.first(),
                emergencyAudioCooldownMs = adsbTrafficPreferencesRepository.emergencyAudioCooldownMsFlow.first(),
                emergencyAudioMasterEnabled = adsbTrafficPreferencesRepository.emergencyAudioMasterEnabledFlow.first(),
                emergencyAudioShadowMode = adsbTrafficPreferencesRepository.emergencyAudioShadowModeFlow.first(),
                emergencyAudioRollbackLatched = adsbTrafficPreferencesRepository.emergencyAudioRollbackLatchedFlow.first(),
                emergencyAudioRollbackReason = adsbTrafficPreferencesRepository.emergencyAudioRollbackReasonFlow.first()
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionContract.ADSB_TRAFFIC_PREFERENCES) return
        val section = gson.fromJson(payload, AdsbTrafficSectionPayload::class.java)
        adsbTrafficPreferencesRepository.setEnabled(section.enabled)
        adsbTrafficPreferencesRepository.setIconSizePx(section.iconSizePx)
        adsbTrafficPreferencesRepository.setMaxDistanceKm(section.maxDistanceKm)
        adsbTrafficPreferencesRepository.setVerticalAboveMeters(section.verticalAboveMeters)
        adsbTrafficPreferencesRepository.setVerticalBelowMeters(section.verticalBelowMeters)
        adsbTrafficPreferencesRepository.setEmergencyFlashEnabled(section.emergencyFlashEnabled)
        adsbTrafficPreferencesRepository.setEmergencyAudioEnabled(section.emergencyAudioEnabled)
        adsbTrafficPreferencesRepository.setEmergencyAudioCooldownMs(section.emergencyAudioCooldownMs)
        adsbTrafficPreferencesRepository.setEmergencyAudioMasterEnabled(
            section.emergencyAudioMasterEnabled
        )
        adsbTrafficPreferencesRepository.setEmergencyAudioShadowMode(section.emergencyAudioShadowMode)
        if (section.emergencyAudioRollbackLatched) {
            adsbTrafficPreferencesRepository.latchEmergencyAudioRollback(
                section.emergencyAudioRollbackReason ?: "imported"
            )
        } else {
            adsbTrafficPreferencesRepository.clearEmergencyAudioRollback()
        }
    }
}

private data class AdsbTrafficSectionPayload(
    val enabled: Boolean,
    val iconSizePx: Int,
    val maxDistanceKm: Int,
    val verticalAboveMeters: Double,
    val verticalBelowMeters: Double,
    val emergencyFlashEnabled: Boolean,
    val emergencyAudioEnabled: Boolean,
    val emergencyAudioCooldownMs: Long,
    val emergencyAudioMasterEnabled: Boolean,
    val emergencyAudioShadowMode: Boolean,
    val emergencyAudioRollbackLatched: Boolean,
    val emergencyAudioRollbackReason: String?
)
