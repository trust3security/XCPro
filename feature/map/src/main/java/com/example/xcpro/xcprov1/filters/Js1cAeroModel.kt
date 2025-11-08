package com.example.xcpro.xcprov1.filters

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Hard coded aerodynamic model for the Jonker JS1-C 18 m glider.
 *
 * This provides the sink polar, circle polar, angle of attack estimation and
 * characteristic constants used by the XCPro V1 filter. The coefficients are
 * derived from publicly available manufacturer performance data and tuned for
 * 18 m span operation at typical competition wing loadings.
 */
object Js1cAeroModel {

    // --- Reference constants -------------------------------------------------
    private const val WING_AREA_M2 = 10.93
    private const val ASPECT_RATIO = 18.0 * 18.0 / WING_AREA_M2
    private const val MASS_LIGHT_KG = 450.0
    private const val MASS_HEAVY_KG = 600.0
    private const val G = 9.80665

    // Polynomial sink curve coefficients (m/s) for TAS in m/s.
    // Derived from manufacturer polar (converted from km/h).
    private data class PolarPoint(val speedKmh: Double, val sinkRate: Double)

    private val polarPoints = listOf(
        PolarPoint(60.0, 0.65),
        PolarPoint(70.0, 0.53),
        PolarPoint(75.0, 0.51),
        PolarPoint(80.0, 0.52),
        PolarPoint(90.0, 0.56),
        PolarPoint(100.0, 0.65),
        PolarPoint(105.0, 0.68),
        PolarPoint(110.0, 0.72),
        PolarPoint(120.0, 0.84),
        PolarPoint(130.0, 0.99),
        PolarPoint(140.0, 1.16),
        PolarPoint(150.0, 1.36),
        PolarPoint(160.0, 1.59),
        PolarPoint(170.0, 1.85),
        PolarPoint(180.0, 2.14)
    )

    /**
     * Returns the still-air sink rate for a given true airspeed (m/s) and wing loading (kg/m^2).
     */
    fun sinkRate(tas: Double, wingLoading: Double = defaultWingLoading()): Double {
        val speed = max(tas, 0.0)
        val base = polarSink(speed)
        val referenceLoading = MASS_LIGHT_KG / WING_AREA_M2
        val loadingFactor = sqrt(max(wingLoading, 1.0) / referenceLoading)
        return base * loadingFactor
    }

    /**
     * Circle polar: additional sink introduced by bank angle (degrees).
     */
    fun circleSink(tas: Double, bankDeg: Double, wingLoading: Double = defaultWingLoading()): Double {
        val bankRad = Math.toRadians(bankDeg)
        val loadFactor = 1.0 / cos(bankRad)
        val inducedIncrease = (loadFactor.pow(1.5) - 1.0)
        return inducedIncrease * sinkRate(tas, wingLoading)
    }

    /**
     * Estimate angle of attack (deg) based on true airspeed and lift requirement.
     */
    fun angleOfAttack(tas: Double, liftNewton: Double, wingLoading: Double = defaultWingLoading()): Double {
        val rho = 1.225  // Sea level approximation for now.
        val dynamicPressure = 0.5 * rho * tas * tas
        val liftCoefficient = liftNewton / (dynamicPressure * WING_AREA_M2)
        val clAlpha = 0.085  // Approximate slope per degree.
        return (liftCoefficient / clAlpha).coerceIn(-8.0, 18.0)
    }

    /**
     * Approximate wing loading for default configuration in kg/m^2.
     */
    fun defaultWingLoading(): Double = MASS_HEAVY_KG / WING_AREA_M2

    /**
     * Reference wing loading (manufacturer polar) in kg/m^2.
     */
    fun referenceWingLoading(): Double = MASS_LIGHT_KG / WING_AREA_M2

    private fun polarSink(tas: Double): Double {
        val points = polarPoints
        if (points.isEmpty()) return 0.0

        val speedKmh = tas * 3.6
        if (speedKmh <= points.first().speedKmh) return points.first().sinkRate
        if (speedKmh >= points.last().speedKmh) return points.last().sinkRate

        val (lower, upper) = points.zipWithNext().first { (a, b) ->
            speedKmh >= a.speedKmh && speedKmh <= b.speedKmh
        }

        val fraction = (speedKmh - lower.speedKmh) / (upper.speedKmh - lower.speedKmh)
        val interpolated = lower.sinkRate + fraction * (upper.sinkRate - lower.sinkRate)
        return max(interpolated, 0.0)
    }

    /**
     * Convert vertical speed and TAS into netto (airmass) figure.
     */
    fun nettoFromVerticals(actualClimb: Double, bankDeg: Double, tas: Double, wingLoading: Double = defaultWingLoading()): Double {
        val sink = sinkRate(tas, wingLoading)
        val circleLoss = circleSink(tas, bankDeg, wingLoading)
        return actualClimb + sink + circleLoss
    }

    /**
     * Compute potential climb by removing sink from the airmass climb.
     */
    fun potentialClimb(airmassClimb: Double, tas: Double, bankDeg: Double, wingLoading: Double = defaultWingLoading()): Double {
        val sink = sinkRate(tas, wingLoading)
        val circleLoss = circleSink(tas, bankDeg, wingLoading)
        return airmassClimb - (sink + circleLoss)
    }

    /**
     * Estimate load factor from turn rate (rad/s) and TAS (m/s).
     */
    fun loadFactor(turnRateRad: Double, tas: Double): Double {
        if (tas <= 0.1) return 1.0
        val bank = atanFromTurnRate(turnRateRad, tas)
        return 1.0 / cos(bank)
    }

    private fun atanFromTurnRate(turnRateRad: Double, tas: Double): Double {
        val g = G
        return kotlin.math.atan((tas * turnRateRad) / g)
    }
}
