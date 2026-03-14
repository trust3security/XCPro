package com.example.xcpro.profiles

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.weather.wind.data.WindOverrideRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class WindOverrideProfileSettingsContributor @Inject constructor(
    private val windOverrideRepository: WindOverrideRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES) return null
        val manualOverride = windOverrideRepository.manualWind.first()
        return gson.toJsonTree(
            WindOverrideSectionSnapshot(
                manualOverride = manualOverride?.let { wind ->
                    ManualWindOverrideSnapshot(
                        speedMs = wind.vector.speed,
                        directionFromDeg = wind.vector.directionFromDeg,
                        timestampMillis = wind.timestampMillis,
                        source = wind.source.name
                    )
                }
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionIds.WIND_OVERRIDE_PREFERENCES) return
        val section = gson.fromJson(payload, WindOverrideSectionSnapshot::class.java)
        val override = section.manualOverride
        if (override == null) {
            windOverrideRepository.clearManualWind()
        } else {
            windOverrideRepository.setManualWind(
                speedMs = override.speedMs,
                directionFromDeg = override.directionFromDeg,
                timestampMillis = override.timestampMillis
            )
        }
    }
}
