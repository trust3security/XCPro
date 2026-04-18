package com.trust3.xcpro.profiles

import com.trust3.xcpro.MapOrientationSettings
import com.trust3.xcpro.MapOrientationSettingsRepository
import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.trust3.xcpro.map.domain.MapShiftBiasMode
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrientationProfileSettingsContributor @Inject constructor(
    private val orientationSettingsRepository: MapOrientationSettingsRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> = setOf(ProfileSettingsSectionIds.ORIENTATION_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.ORIENTATION_PREFERENCES) return null
        val settingsByProfile = profileIds.associateWith { profileId ->
            val settings = orientationSettingsRepository.readProfileSettings(profileId)
            OrientationProfileSectionSnapshot(
                cruiseMode = settings.cruiseMode.name,
                circlingMode = settings.circlingMode.name,
                minSpeedThresholdMs = settings.minSpeedThresholdMs,
                gliderScreenPercent = settings.gliderScreenPercent,
                mapShiftBiasMode = settings.mapShiftBiasMode.name,
                mapShiftBiasStrength = settings.mapShiftBiasStrength,
                autoResetEnabled = settings.autoResetEnabled,
                autoResetTimeoutSeconds = settings.autoResetTimeoutSeconds,
                bearingSmoothingEnabled = settings.bearingSmoothingEnabled
            )
        }
        return gson.toJsonTree(OrientationSectionSnapshot(settingsByProfile = settingsByProfile))
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.ORIENTATION_PREFERENCES) return
        val section = gson.fromJson(payload, OrientationSectionSnapshot::class.java)
        val defaults = MapOrientationSettings()
        section.settingsByProfile.forEach { (sourceProfileId, snapshot) ->
            val profileId = resolveImportedProfileId(sourceProfileId, importedProfileIdMap)
                ?: return@forEach
            val settings = MapOrientationSettings(
                cruiseMode = parseOrientationMode(snapshot.cruiseMode),
                circlingMode = parseOrientationMode(snapshot.circlingMode),
                minSpeedThresholdMs = snapshot.minSpeedThresholdMs,
                gliderScreenPercent = snapshot.gliderScreenPercent,
                mapShiftBiasMode = parseMapShiftBiasMode(snapshot.mapShiftBiasMode),
                mapShiftBiasStrength = snapshot.mapShiftBiasStrength,
                autoResetEnabled = snapshot.autoResetEnabled ?: defaults.autoResetEnabled,
                autoResetTimeoutSeconds = snapshot.autoResetTimeoutSeconds
                    ?: defaults.autoResetTimeoutSeconds,
                bearingSmoothingEnabled = snapshot.bearingSmoothingEnabled
                    ?: defaults.bearingSmoothingEnabled
            )
            orientationSettingsRepository.writeProfileSettings(profileId, settings)
        }
    }

    private fun parseOrientationMode(raw: String): MapOrientationMode =
        runCatching { MapOrientationMode.valueOf(raw) }
            .getOrDefault(MapOrientationMode.TRACK_UP)

    private fun parseMapShiftBiasMode(raw: String): MapShiftBiasMode =
        runCatching { MapShiftBiasMode.valueOf(raw) }
            .getOrDefault(MapShiftBiasMode.NONE)
}
