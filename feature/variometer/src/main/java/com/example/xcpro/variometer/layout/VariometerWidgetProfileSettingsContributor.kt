package com.example.xcpro.variometer.layout

import com.example.xcpro.core.common.geometry.OffsetPx
import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsProfileIds
import com.example.xcpro.core.common.profiles.ProfileSettingsSectionContract
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VariometerWidgetProfileSettingsContributor @Inject constructor(
    private val variometerWidgetRepository: VariometerWidgetRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionContract.VARIOMETER_WIDGET_LAYOUT)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionContract.VARIOMETER_WIDGET_LAYOUT) return null
        val layoutsByProfile = profileIds.associateWith { profileId ->
            val layout = variometerWidgetRepository.load(
                profileId = profileId,
                defaultOffset = OffsetPx.Zero,
                defaultSizePx = 0f
            )
            VariometerLayoutProfilePayload(
                offset = OffsetPayload(x = layout.offset.x, y = layout.offset.y),
                sizePx = layout.sizePx,
                hasPersistedOffset = layout.hasPersistedOffset,
                hasPersistedSize = layout.hasPersistedSize
            )
        }
        return gson.toJsonTree(VariometerWidgetLayoutSectionPayload(layoutsByProfile = layoutsByProfile))
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionContract.VARIOMETER_WIDGET_LAYOUT) return
        val section = gson.fromJson(payload, VariometerWidgetLayoutSectionPayload::class.java)
        if (section.layoutsByProfile.isNotEmpty()) {
            section.layoutsByProfile.forEach { (sourceProfileId, snapshot) ->
                val profileId = ProfileSettingsProfileIds.resolveImportedProfileId(
                    sourceProfileId = sourceProfileId,
                    importedProfileIdMap = importedProfileIdMap
                ) ?: return@forEach
                variometerWidgetRepository.saveOffset(
                    profileId = profileId,
                    offset = OffsetPx(snapshot.offset.x, snapshot.offset.y)
                )
                variometerWidgetRepository.saveSize(
                    profileId = profileId,
                    sizePx = snapshot.sizePx
                )
            }
            return
        }

        val legacyOffset = section.offset ?: return
        val legacySize = section.sizePx ?: return
        val targetProfileIds = importedProfileIdMap.values.toSet().ifEmpty {
            setOf(ProfileSettingsProfileIds.CANONICAL_DEFAULT_PROFILE_ID)
        }
        targetProfileIds.forEach { profileId ->
            variometerWidgetRepository.saveOffset(
                profileId = profileId,
                offset = OffsetPx(legacyOffset.x, legacyOffset.y)
            )
            variometerWidgetRepository.saveSize(profileId = profileId, sizePx = legacySize)
        }
    }
}

private data class VariometerWidgetLayoutSectionPayload(
    val layoutsByProfile: Map<String, VariometerLayoutProfilePayload> = emptyMap(),
    val offset: OffsetPayload? = null,
    val sizePx: Float? = null,
    val hasPersistedOffset: Boolean? = null,
    val hasPersistedSize: Boolean? = null
)

private data class VariometerLayoutProfilePayload(
    val offset: OffsetPayload,
    val sizePx: Float,
    val hasPersistedOffset: Boolean,
    val hasPersistedSize: Boolean
)

private data class OffsetPayload(
    val x: Float,
    val y: Float
)
