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
class OgnTrailSelectionProfileSettingsContributor @Inject constructor(
    private val ognTrailSelectionPreferencesRepository: OgnTrailSelectionPreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionContract.OGN_TRAIL_SELECTION_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionContract.OGN_TRAIL_SELECTION_PREFERENCES) return null
        return gson.toJsonTree(
            OgnTrailSelectionSectionPayload(
                selectedAircraftKeys = ognTrailSelectionPreferencesRepository
                    .selectedAircraftKeysFlow
                    .first()
                    .toSortedSet()
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionContract.OGN_TRAIL_SELECTION_PREFERENCES) return
        val section = gson.fromJson(payload, OgnTrailSelectionSectionPayload::class.java)
        ognTrailSelectionPreferencesRepository.clearSelectedAircraft()
        section.selectedAircraftKeys.forEach { key ->
            ognTrailSelectionPreferencesRepository.setAircraftSelected(key, selected = true)
        }
    }
}

private data class OgnTrailSelectionSectionPayload(
    val selectedAircraftKeys: Set<String>
)
