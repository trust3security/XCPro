package com.trust3.xcpro.profiles

import com.trust3.xcpro.common.units.UnitsRepository
import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnitsProfileSettingsContributor @Inject constructor(
    private val unitsRepository: UnitsRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.UNITS_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.UNITS_PREFERENCES) return null
        val unitsByProfile = profileIds.associateWith { profileId ->
            unitsRepository.readProfileUnits(profileId)
        }
        return gson.toJsonTree(UnitsSectionSnapshot(unitsByProfile = unitsByProfile))
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.UNITS_PREFERENCES) return
        val section = gson.fromJson(payload, UnitsSectionSnapshot::class.java)
        section.unitsByProfile.forEach { (sourceProfileId, preferences) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            unitsRepository.writeProfileUnits(profileId, preferences)
        }
    }
}
