package com.example.xcpro.forecast

import com.example.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.example.xcpro.core.common.profiles.ProfileSettingsSectionContract
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ForecastProfileSettingsContributor @Inject constructor(
    private val forecastPreferencesRepository: ForecastPreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionContract.FORECAST_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionContract.FORECAST_PREFERENCES) return null
        val settings = forecastPreferencesRepository.preferencesFlow.first()
        return gson.toJsonTree(
            ForecastSectionPayload(
                overlayEnabled = settings.overlayEnabled,
                opacity = settings.opacity,
                windOverlayScale = settings.windOverlayScale,
                windOverlayEnabled = settings.windOverlayEnabled,
                windDisplayMode = settings.windDisplayMode.storageValue,
                skySightSatelliteOverlayEnabled = settings.skySightSatelliteOverlayEnabled,
                skySightSatelliteImageryEnabled = settings.skySightSatelliteImageryEnabled,
                skySightSatelliteRadarEnabled = settings.skySightSatelliteRadarEnabled,
                skySightSatelliteLightningEnabled = settings.skySightSatelliteLightningEnabled,
                skySightSatelliteAnimateEnabled = settings.skySightSatelliteAnimateEnabled,
                skySightSatelliteHistoryFrames = settings.skySightSatelliteHistoryFrames,
                selectedPrimaryParameterId = settings.selectedPrimaryParameterId.value,
                selectedWindParameterId = settings.selectedWindParameterId.value,
                selectedTimeUtcMs = settings.selectedTimeUtcMs,
                selectedRegion = settings.selectedRegion,
                followTimeOffsetMinutes = settings.followTimeOffsetMinutes,
                autoTimeEnabled = settings.autoTimeEnabled
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionContract.FORECAST_PREFERENCES) return
        val section = gson.fromJson(payload, ForecastSectionPayload::class.java)
        forecastPreferencesRepository.setOverlayEnabled(section.overlayEnabled)
        forecastPreferencesRepository.setOpacity(section.opacity)
        forecastPreferencesRepository.setWindOverlayScale(section.windOverlayScale)
        forecastPreferencesRepository.setWindOverlayEnabled(section.windOverlayEnabled)
        forecastPreferencesRepository.setWindDisplayMode(
            ForecastWindDisplayMode.fromStorageValue(section.windDisplayMode)
        )
        forecastPreferencesRepository.setSkySightSatelliteOverlayEnabled(
            section.skySightSatelliteOverlayEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteImageryEnabled(
            section.skySightSatelliteImageryEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteRadarEnabled(
            section.skySightSatelliteRadarEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteLightningEnabled(
            section.skySightSatelliteLightningEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteAnimateEnabled(
            section.skySightSatelliteAnimateEnabled
        )
        forecastPreferencesRepository.setSkySightSatelliteHistoryFrames(
            section.skySightSatelliteHistoryFrames
        )
        forecastPreferencesRepository.setSelectedPrimaryParameterId(
            ForecastParameterId(section.selectedPrimaryParameterId)
        )
        forecastPreferencesRepository.setSelectedWindParameterId(
            ForecastParameterId(section.selectedWindParameterId)
        )
        forecastPreferencesRepository.setSelectedTimeUtcMs(section.selectedTimeUtcMs)
        forecastPreferencesRepository.setSelectedRegion(section.selectedRegion)
        forecastPreferencesRepository.setFollowTimeOffsetMinutes(section.followTimeOffsetMinutes)
        forecastPreferencesRepository.setAutoTimeEnabled(section.autoTimeEnabled)
    }
}

private data class ForecastSectionPayload(
    val overlayEnabled: Boolean,
    val opacity: Float,
    val windOverlayScale: Float,
    val windOverlayEnabled: Boolean,
    val windDisplayMode: String,
    val skySightSatelliteOverlayEnabled: Boolean,
    val skySightSatelliteImageryEnabled: Boolean,
    val skySightSatelliteRadarEnabled: Boolean,
    val skySightSatelliteLightningEnabled: Boolean,
    val skySightSatelliteAnimateEnabled: Boolean,
    val skySightSatelliteHistoryFrames: Int,
    val selectedPrimaryParameterId: String,
    val selectedWindParameterId: String,
    val selectedTimeUtcMs: Long?,
    val selectedRegion: String,
    val followTimeOffsetMinutes: Int,
    val autoTimeEnabled: Boolean
)
