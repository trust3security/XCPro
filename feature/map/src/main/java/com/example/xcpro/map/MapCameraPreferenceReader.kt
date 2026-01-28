package com.example.xcpro.map

import com.example.xcpro.MapOrientationPreferences
import com.example.xcpro.map.domain.MapShiftBiasMode

interface MapCameraPreferenceReader {
    fun getMapShiftBiasMode(): MapShiftBiasMode
    fun getMapShiftBiasStrength(): Double
    fun getGliderScreenPercent(): Int
}

class MapCameraPreferenceReaderAdapter(
    private val preferences: MapOrientationPreferences
) : MapCameraPreferenceReader {
    override fun getMapShiftBiasMode(): MapShiftBiasMode = preferences.getMapShiftBiasMode()

    override fun getMapShiftBiasStrength(): Double = preferences.getMapShiftBiasStrength()

    override fun getGliderScreenPercent(): Int = preferences.getGliderScreenPercent()
}
