package com.example.xcpro

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.map.domain.MapShiftBiasMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


data class MapOrientationSettings(
    val cruiseMode: MapOrientationMode = MapOrientationMode.TRACK_UP,
    val circlingMode: MapOrientationMode = MapOrientationMode.TRACK_UP,
    val minSpeedThresholdMs: Double = 2.0,
    val gliderScreenPercent: Int = 35,
    val mapShiftBiasMode: MapShiftBiasMode = MapShiftBiasMode.NONE,
    val mapShiftBiasStrength: Double = 1.0,
    val autoResetEnabled: Boolean = true,
    val autoResetTimeoutSeconds: Int = 10,
    val bearingSmoothingEnabled: Boolean = true
)

@Singleton
class MapOrientationSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = MapOrientationPreferences(context)
    private var activeProfileId: String = MapOrientationPreferences.DEFAULT_PROFILE_ID
    private val _settingsFlow = MutableStateFlow(readSettings())
    val settingsFlow: StateFlow<MapOrientationSettings> = _settingsFlow.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        emitSettings()
    }

    init {
        preferences.registerListener(listener)
        preferences.setActiveProfileId(activeProfileId)
        emitSettings()
    }

    fun getSettings(): MapOrientationSettings = _settingsFlow.value

    fun setActiveProfileId(profileId: String) {
        val resolved = MapOrientationPreferences.resolveProfileId(profileId)
        if (activeProfileId == resolved) return
        activeProfileId = resolved
        preferences.setActiveProfileId(resolved)
        emitSettings()
    }

    fun setCruiseOrientationMode(mode: MapOrientationMode) {
        preferences.setCruiseOrientationMode(mode)
        emitSettings()
    }

    fun setCirclingOrientationMode(mode: MapOrientationMode) {
        preferences.setCirclingOrientationMode(mode)
        emitSettings()
    }

    fun setMinSpeedThresholdKnots(speedKnots: Double) {
        preferences.setMinSpeedThreshold(speedKnots)
        emitSettings()
    }

    fun setGliderScreenPercent(percentFromBottom: Int) {
        preferences.setGliderScreenPercent(percentFromBottom)
        emitSettings()
    }

    fun setMapShiftBiasMode(mode: MapShiftBiasMode) {
        preferences.setMapShiftBiasMode(mode)
        emitSettings()
    }

    fun setMapShiftBiasStrength(strength: Double) {
        preferences.setMapShiftBiasStrength(strength)
        emitSettings()
    }

    fun readProfileSettings(profileId: String): MapOrientationSettings =
        readSettings(profileId)

    fun writeProfileSettings(profileId: String, settings: MapOrientationSettings) {
        val resolved = MapOrientationPreferences.resolveProfileId(profileId)
        preferences.setCruiseOrientationMode(settings.cruiseMode, resolved)
        preferences.setCirclingOrientationMode(settings.circlingMode, resolved)
        preferences.setMinSpeedThreshold(
            UnitsConverter.msToKnots(settings.minSpeedThresholdMs),
            resolved
        )
        preferences.setGliderScreenPercent(settings.gliderScreenPercent, resolved)
        preferences.setMapShiftBiasMode(settings.mapShiftBiasMode, resolved)
        preferences.setMapShiftBiasStrength(settings.mapShiftBiasStrength, resolved)
        preferences.setAutoResetEnabled(settings.autoResetEnabled, resolved)
        preferences.setAutoResetTimeoutSeconds(settings.autoResetTimeoutSeconds, resolved)
        preferences.setBearingSmoothingEnabled(settings.bearingSmoothingEnabled, resolved)
        if (activeProfileId == resolved) {
            emitSettings()
        }
    }

    fun clearProfile(profileId: String) {
        val resolved = MapOrientationPreferences.resolveProfileId(profileId)
        preferences.clearProfile(resolved)
        if (activeProfileId == resolved) {
            emitSettings()
        }
    }

    private fun emitSettings() {
        val settings = readSettings()
        if (settings != _settingsFlow.value) {
            _settingsFlow.value = settings
        }
    }

    private fun readSettings(profileId: String = activeProfileId): MapOrientationSettings =
        MapOrientationSettings(
            cruiseMode = preferences.getCruiseOrientationMode(profileId),
            circlingMode = preferences.getCirclingOrientationMode(profileId),
            minSpeedThresholdMs = preferences.getMinSpeedThreshold(profileId),
            gliderScreenPercent = preferences.getGliderScreenPercent(profileId),
            mapShiftBiasMode = preferences.getMapShiftBiasMode(profileId),
            mapShiftBiasStrength = preferences.getMapShiftBiasStrength(profileId),
            autoResetEnabled = preferences.isAutoResetEnabled(profileId),
            autoResetTimeoutSeconds = preferences.getAutoResetTimeoutSeconds(profileId),
            bearingSmoothingEnabled = preferences.isBearingSmoothingEnabled(profileId)
        )
}
