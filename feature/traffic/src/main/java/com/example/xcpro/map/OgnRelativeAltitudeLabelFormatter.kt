package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.AltitudeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

object OgnRelativeAltitudeLabelFormatter {
    const val UNKNOWN_DELTA_TEXT = "--"

    /**
     * Formats a signed altitude delta string.
     *
     * Sign comes from raw delta to preserve strict +/- semantics around rounding.
     */
    fun formatDelta(deltaMeters: Double?, altitudeUnit: AltitudeUnit): String {
        val delta = deltaMeters?.takeIf { it.isFinite() } ?: return UNKNOWN_DELTA_TEXT
        val converted = altitudeUnit.fromSi(AltitudeM(delta))
        val rounded = abs(converted).roundToInt()
        return when {
            delta > 0.0 -> "+$rounded ${altitudeUnit.abbreviation}"
            delta < 0.0 -> "-$rounded ${altitudeUnit.abbreviation}"
            else -> "0 ${altitudeUnit.abbreviation}"
        }
    }
}
