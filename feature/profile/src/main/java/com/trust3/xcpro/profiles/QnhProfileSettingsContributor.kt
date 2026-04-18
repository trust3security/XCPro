package com.trust3.xcpro.profiles

import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.trust3.xcpro.map.QnhPreferencesRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QnhProfileSettingsContributor @Inject constructor(
    private val qnhPreferencesRepository: QnhPreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.QNH_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.QNH_PREFERENCES) return null
        val valuesByProfile = profileIds.associateWith { profileId ->
            val manual = qnhPreferencesRepository.readProfileManualQnh(profileId)
            QnhProfileSectionSnapshot(
                manualQnhHpa = manual?.qnhHpa,
                capturedAtWallMs = manual?.capturedAtWallMs,
                source = manual?.source
            )
        }
        return gson.toJsonTree(QnhSectionSnapshot(valuesByProfile = valuesByProfile))
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.QNH_PREFERENCES) return
        val section = gson.fromJson(payload, QnhSectionSnapshot::class.java)
        section.valuesByProfile.forEach { (sourceProfileId, snapshot) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            if (snapshot.manualQnhHpa == null) {
                qnhPreferencesRepository.clearProfile(profileId)
            } else {
                qnhPreferencesRepository.writeProfileManualQnh(
                    profileId = profileId,
                    qnhHpa = snapshot.manualQnhHpa,
                    capturedAtWallMs = snapshot.capturedAtWallMs,
                    source = snapshot.source
                )
            }
        }
    }
}
