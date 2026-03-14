package com.example.xcpro.profiles

import com.example.dfcards.FlightModeSelection
import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.flightdata.FlightMgmtPreferencesRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlightMgmtProfileSettingsContributor @Inject constructor(
    private val flightMgmtPreferencesRepository: FlightMgmtPreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES) return null
        val profileLastModes = profileIds.associateWith { profileId ->
            flightMgmtPreferencesRepository.getLastFlightMode(profileId).name
        }
        return gson.toJsonTree(
            FlightMgmtSectionSnapshot(
                lastActiveTab = flightMgmtPreferencesRepository.getLastActiveTab(),
                profileLastFlightModes = profileLastModes
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.FLIGHT_MGMT_PREFERENCES) return
        val section = gson.fromJson(payload, FlightMgmtSectionSnapshot::class.java)
        flightMgmtPreferencesRepository.setLastActiveTab(section.lastActiveTab)
        section.profileLastFlightModes.forEach { (sourceProfileId, modeName) ->
            val mode = runCatching { FlightModeSelection.valueOf(modeName) }
                .getOrDefault(FlightModeSelection.CRUISE)
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            flightMgmtPreferencesRepository.setLastFlightMode(profileId, mode)
        }
    }
}
