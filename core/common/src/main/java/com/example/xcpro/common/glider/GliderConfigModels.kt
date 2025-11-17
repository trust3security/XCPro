package com.example.xcpro.common.glider

data class ThreePointPolar(
    val lowKmh: Double = 80.0,
    val lowSinkMs: Double = 0.5,
    val midKmh: Double = 120.0,
    val midSinkMs: Double = 0.8,
    val highKmh: Double = 180.0,
    val highSinkMs: Double = 2.0
)

data class UserPolarCoefficients(
    val a: Double? = null,
    val b: Double? = null,
    val c: Double? = null
)

data class GliderConfig(
    val pilotAndGearKg: Double = 90.0,
    val waterBallastKg: Double = 0.0,
    val bugsPercent: Int = 0,
    val referenceWeightKg: Double? = null,
    val threePointPolar: ThreePointPolar? = null,
    val userCoefficients: UserPolarCoefficients? = null,
    val ballastDrainMinutes: Double = 5.0,
    val hideBallastPill: Boolean = false
)
