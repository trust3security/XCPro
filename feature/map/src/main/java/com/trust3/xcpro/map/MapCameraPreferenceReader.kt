package com.trust3.xcpro.map

import com.trust3.xcpro.MapOrientationPreferences
import com.trust3.xcpro.map.domain.MapShiftBiasMode

class MapCameraPreferenceReaderAdapter(
    private val preferences: MapOrientationPreferences
) : MapCameraPreferenceReader {
    override fun getMapShiftBiasMode(): MapShiftBiasMode = preferences.getMapShiftBiasMode()

    override fun getMapShiftBiasStrength(): Double = preferences.getMapShiftBiasStrength()

    override fun getGliderScreenPercent(): Int = preferences.getGliderScreenPercent()
}
