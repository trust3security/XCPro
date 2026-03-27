package com.example.xcpro.common.glider

import com.example.xcpro.common.units.UnitsConverter

data class ThreePointPolar(
    val lowMs: Double = UnitsConverter.kmhToMs(80.0),
    val lowSinkMs: Double = 0.5,
    val midMs: Double = UnitsConverter.kmhToMs(120.0),
    val midSinkMs: Double = 0.8,
    val highMs: Double = UnitsConverter.kmhToMs(180.0),
    val highSinkMs: Double = 2.0
) {
    val lowKmh: Double
        get() = UnitsConverter.msToKmh(lowMs)
    val midKmh: Double
        get() = UnitsConverter.msToKmh(midMs)
    val highKmh: Double
        get() = UnitsConverter.msToKmh(highMs)

    companion object {
        fun fromKmh(
            lowKmh: Double,
            lowSinkMs: Double,
            midKmh: Double,
            midSinkMs: Double,
            highKmh: Double,
            highSinkMs: Double
        ): ThreePointPolar = ThreePointPolar(
            lowMs = UnitsConverter.kmhToMs(lowKmh),
            lowSinkMs = lowSinkMs,
            midMs = UnitsConverter.kmhToMs(midKmh),
            midSinkMs = midSinkMs,
            highMs = UnitsConverter.kmhToMs(highKmh),
            highSinkMs = highSinkMs
        )
    }
}

data class UserPolarCoefficients(
    val a: Double? = null,
    val b: Double? = null,
    val c: Double? = null
)

data class GliderConfig(
    val pilotAndGearKg: Double = 90.0,
    val waterBallastKg: Double = 0.0,
    val bugsPercent: Int = 0,
    // Stored for future work; not part of the Phase 4 authoritative runtime polar path.
    val referenceWeightKg: Double? = null,
    val iasMinMs: Double? = null,
    val iasMaxMs: Double? = null,
    // Phase 4 release contract: this remains the authoritative manual polar input.
    val threePointPolar: ThreePointPolar? = null,
    // Stored for future work; not part of the Phase 4 authoritative runtime polar path.
    val userCoefficients: UserPolarCoefficients? = null,
    val ballastDrainMinutes: Double = 5.0,
    val hideBallastPill: Boolean = false
) {
    val iasMinKmh: Double?
        get() = iasMinMs?.let(UnitsConverter::msToKmh)
    val iasMaxKmh: Double?
        get() = iasMaxMs?.let(UnitsConverter::msToKmh)
}
