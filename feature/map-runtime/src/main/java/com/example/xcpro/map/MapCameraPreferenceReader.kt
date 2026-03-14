package com.example.xcpro.map

import com.example.xcpro.map.domain.MapShiftBiasMode

interface MapCameraPreferenceReader {
    fun getMapShiftBiasMode(): MapShiftBiasMode
    fun getMapShiftBiasStrength(): Double
    fun getGliderScreenPercent(): Int
}
