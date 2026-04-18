package com.trust3.xcpro.map

import com.trust3.xcpro.map.domain.MapShiftBiasMode

interface MapCameraPreferenceReader {
    fun getMapShiftBiasMode(): MapShiftBiasMode
    fun getMapShiftBiasStrength(): Double
    fun getGliderScreenPercent(): Int
}
