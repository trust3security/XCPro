package com.example.xcpro

import android.content.Context
import android.content.SharedPreferences
import com.example.xcpro.common.orientation.MapOrientationMode
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
    val mapShiftBiasStrength: Double = 1.0
)

@Singleton
class MapOrientationSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = MapOrientationPreferences(context)
    private val _settingsFlow = MutableStateFlow(readSettings())
    val settingsFlow: StateFlow<MapOrientationSettings> = _settingsFlow.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        emitSettings()
    }

    init {
        preferences.registerListener(listener)
        emitSettings()
    }

    fun getSettings(): MapOrientationSettings = _settingsFlow.value

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

    private fun emitSettings() {
        val settings = readSettings()
        if (settings != _settingsFlow.value) {
            _settingsFlow.value = settings
        }
    }

    private fun readSettings(): MapOrientationSettings = MapOrientationSettings(
        cruiseMode = preferences.getCruiseOrientationMode(),
        circlingMode = preferences.getCirclingOrientationMode(),
        minSpeedThresholdMs = preferences.getMinSpeedThreshold(),
        gliderScreenPercent = preferences.getGliderScreenPercent(),
        mapShiftBiasMode = preferences.getMapShiftBiasMode(),
        mapShiftBiasStrength = preferences.getMapShiftBiasStrength()
    )
}