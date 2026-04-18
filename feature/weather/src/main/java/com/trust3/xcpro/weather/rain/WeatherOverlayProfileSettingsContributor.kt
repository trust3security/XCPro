package com.trust3.xcpro.weather.rain

import com.trust3.xcpro.core.common.profiles.ProfileSettingsApplyContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsCaptureContributor
import com.trust3.xcpro.core.common.profiles.ProfileSettingsSectionContract
import com.google.gson.Gson
import com.google.gson.JsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class WeatherOverlayProfileSettingsContributor @Inject constructor(
    private val weatherOverlayPreferencesRepository: WeatherOverlayPreferencesRepository
) : ProfileSettingsCaptureContributor, ProfileSettingsApplyContributor {

    private val gson = Gson()

    override val sectionIds: Set<String> =
        setOf(ProfileSettingsSectionContract.WEATHER_OVERLAY_PREFERENCES)

    override suspend fun captureSection(
        sectionId: String,
        profileIds: Set<String>
    ): JsonElement? {
        if (sectionId != ProfileSettingsSectionContract.WEATHER_OVERLAY_PREFERENCES) return null
        val settings = weatherOverlayPreferencesRepository.preferencesFlow.first()
        return gson.toJsonTree(
            WeatherOverlaySectionPayload(
                enabled = settings.enabled,
                opacity = settings.opacity,
                animatePastWindow = settings.animatePastWindow,
                animationWindow = settings.animationWindow.storageKey,
                animationSpeed = settings.animationSpeed.storageKey,
                transitionQuality = settings.transitionQuality.storageKey,
                frameMode = settings.frameMode.storageKey,
                manualFrameIndex = settings.manualFrameIndex,
                smooth = settings.renderOptions.smooth,
                snow = settings.renderOptions.snow
            )
        )
    }

    override suspend fun applySection(
        sectionId: String,
        payload: JsonElement,
        importedProfileIdMap: Map<String, String>
    ) {
        if (sectionId != ProfileSettingsSectionContract.WEATHER_OVERLAY_PREFERENCES) return
        val section = gson.fromJson(payload, WeatherOverlaySectionPayload::class.java)
        weatherOverlayPreferencesRepository.setEnabled(section.enabled)
        weatherOverlayPreferencesRepository.setOpacity(section.opacity)
        weatherOverlayPreferencesRepository.setAnimatePastWindow(section.animatePastWindow)
        weatherOverlayPreferencesRepository.setAnimationWindow(
            WeatherRainAnimationWindow.fromStorage(section.animationWindow)
        )
        weatherOverlayPreferencesRepository.setAnimationSpeed(
            WeatherRainAnimationSpeed.fromStorage(section.animationSpeed)
        )
        weatherOverlayPreferencesRepository.setTransitionQuality(
            WeatherRainTransitionQuality.fromStorage(section.transitionQuality)
        )
        weatherOverlayPreferencesRepository.setFrameMode(
            WeatherRadarFrameMode.fromStorage(section.frameMode)
        )
        weatherOverlayPreferencesRepository.setManualFrameIndex(section.manualFrameIndex)
        weatherOverlayPreferencesRepository.setSmoothEnabled(section.smooth)
        weatherOverlayPreferencesRepository.setSnowEnabled(section.snow)
    }
}

private data class WeatherOverlaySectionPayload(
    val enabled: Boolean,
    val opacity: Float,
    val animatePastWindow: Boolean,
    val animationWindow: String,
    val animationSpeed: String,
    val transitionQuality: String,
    val frameMode: String,
    val manualFrameIndex: Int,
    val smooth: Boolean,
    val snow: Boolean
)
