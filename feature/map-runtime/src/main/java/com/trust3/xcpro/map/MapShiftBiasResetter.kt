package com.trust3.xcpro.map

import com.trust3.xcpro.map.domain.MapShiftBiasCalculator

interface MapShiftBiasResetter {
    fun reset()
}

class MapShiftBiasResetterAdapter(
    private val calculator: MapShiftBiasCalculator
) : MapShiftBiasResetter {
    override fun reset() {
        calculator.reset()
    }
}
