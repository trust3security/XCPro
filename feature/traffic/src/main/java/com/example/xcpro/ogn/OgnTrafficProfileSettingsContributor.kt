package com.example.xcpro.ogn

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsSectionContract
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class OgnTrafficProfileSettingsContributor @Inject constructor(
    private val ognTrafficPreferencesRepository: OgnTrafficPreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionContract.OGN_TRAFFIC_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionContract.OGN_TRAFFIC_PREFERENCES) return null
        return gson.toJsonTree(
            OgnTrafficSectionPayload(
                enabled = ognTrafficPreferencesRepository.enabledFlow.first(),
                iconSizePx = ognTrafficPreferencesRepository.iconSizePxFlow.first(),
                receiveRadiusKm = ognTrafficPreferencesRepository.receiveRadiusKmFlow.first(),
                autoReceiveRadiusEnabled = ognTrafficPreferencesRepository.autoReceiveRadiusEnabledFlow.first(),
                displayUpdateMode = ognTrafficPreferencesRepository.displayUpdateModeFlow.first().storageValue,
                showSciaEnabled = ognTrafficPreferencesRepository.showSciaEnabledFlow.first(),
                showThermalsEnabled = ognTrafficPreferencesRepository.showThermalsEnabledFlow.first(),
                thermalRetentionHours = ognTrafficPreferencesRepository.thermalRetentionHoursFlow.first(),
                hotspotsDisplayPercent = ognTrafficPreferencesRepository.hotspotsDisplayPercentFlow.first(),
                targetEnabled = ognTrafficPreferencesRepository.targetEnabledFlow.first(),
                targetAircraftKey = ognTrafficPreferencesRepository.targetAircraftKeyFlow.first(),
                ownFlarmHex = ognTrafficPreferencesRepository.ownFlarmHexFlow.first(),
                ownIcaoHex = ognTrafficPreferencesRepository.ownIcaoHexFlow.first(),
                clientCallsign = ognTrafficPreferencesRepository.clientCallsignFlow.first()
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionContract.OGN_TRAFFIC_PREFERENCES) return
        val section = gson.fromJson(payload, OgnTrafficSectionPayload::class.java)
        ognTrafficPreferencesRepository.setEnabled(section.enabled)
        ognTrafficPreferencesRepository.setIconSizePx(section.iconSizePx)
        ognTrafficPreferencesRepository.setReceiveRadiusKm(section.receiveRadiusKm)
        ognTrafficPreferencesRepository.setAutoReceiveRadiusEnabled(section.autoReceiveRadiusEnabled)
        ognTrafficPreferencesRepository.setDisplayUpdateMode(
            OgnDisplayUpdateMode.fromStorage(section.displayUpdateMode)
        )
        ognTrafficPreferencesRepository.setShowSciaEnabled(section.showSciaEnabled)
        ognTrafficPreferencesRepository.setShowThermalsEnabled(section.showThermalsEnabled)
        ognTrafficPreferencesRepository.setThermalRetentionHours(section.thermalRetentionHours)
        ognTrafficPreferencesRepository.setHotspotsDisplayPercent(section.hotspotsDisplayPercent)
        ognTrafficPreferencesRepository.setTargetSelection(
            enabled = section.targetEnabled,
            aircraftKey = section.targetAircraftKey
        )
        ognTrafficPreferencesRepository.setOwnFlarmHex(section.ownFlarmHex)
        ognTrafficPreferencesRepository.setOwnIcaoHex(section.ownIcaoHex)
        section.clientCallsign?.let { ognTrafficPreferencesRepository.setClientCallsign(it) }
    }
}

private data class OgnTrafficSectionPayload(
    val enabled: Boolean,
    val iconSizePx: Int,
    val receiveRadiusKm: Int,
    val autoReceiveRadiusEnabled: Boolean,
    val displayUpdateMode: String,
    val showSciaEnabled: Boolean,
    val showThermalsEnabled: Boolean,
    val thermalRetentionHours: Int,
    val hotspotsDisplayPercent: Int,
    val targetEnabled: Boolean,
    val targetAircraftKey: String?,
    val ownFlarmHex: String?,
    val ownIcaoHex: String?,
    val clientCallsign: String?
)
