package com.example.dfcards

import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.UnitsConverter
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import kotlin.math.abs
import kotlin.math.roundToInt

internal object CardDataFormatter {

    private const val VARIO_NOISE_FLOOR = 1e-3
    private const val VARIO_ZERO_THRESHOLD = 0.05
    private const val VARIO_DECIMALS = 1
    private const val ALT_DECIMALS = 0

    fun mapLiveDataToCard(
        cardId: String,
        liveData: RealTimeFlightData?,
        units: UnitsPreferences = UnitsPreferences()
    ): Pair<String, String?> {
        fun placeholderFor(card: String): String = when (card) {
            "gps_alt", "baro_alt", "agl", "start_alt" ->
                "-- ${UnitsFormatter.altitude(AltitudeM(0.0), units).unitLabel}"
            "vario", "vario_optimized", "vario_legacy", "vario_raw", "vario_gps",
            "vario_complementary", "real_igc_vario",
            "thermal_avg", "thermal_tc_avg", "thermal_t_avg",
            "netto_avg30", "netto" ->
                "-- ${UnitsFormatter.verticalSpeed(VerticalSpeedMs(0.0), units).unitLabel}"
            "thermal_tc_gain" ->
                "-- ${UnitsFormatter.altitude(AltitudeM(0.0), units).unitLabel}"
            "ground_speed", "wind_spd", "wind_arrow", "task_spd", "ias" ->
                "-- ${UnitsFormatter.speed(SpeedMs(0.0), units).unitLabel}"
            "wind_dir" -> "--\u00B0"
            "wpt_dist", "task_dist" ->
                "-- ${UnitsFormatter.distance(DistanceM(0.0), units).unitLabel}"
            else -> "--"
        }

        if (liveData == null) {
            return Pair(placeholderFor(cardId), "NO DATA")
        }

        return when (cardId) {
            "gps_alt" -> when {
                liveData.isQNHCalibrated && liveData.currentPressureHPa > 0 -> {
                    val formatted = UnitsFormatter.altitude(AltitudeM(liveData.baroAltitude), units)
                    Pair(formatted.text, "QNH ${liveData.qnh.roundToInt()}")
                }
                liveData.gpsAltitude != 0.0 -> {
                    val formatted = UnitsFormatter.altitude(AltitudeM(liveData.gpsAltitude), units)
                    Pair(formatted.text, "QNH ${liveData.qnh.roundToInt()}")
                }
                else -> Pair(placeholderFor(cardId), "NO DATA")
            }

            "baro_alt" -> {
                if (liveData.baroAltitude > 0) {
                    val alt = UnitsFormatter.altitude(AltitudeM(liveData.baroAltitude), units)
                    val status = "QNH ${liveData.qnh.roundToInt()}"
                    Pair(alt.text, status)
                } else {
                    Pair(placeholderFor(cardId), "NO BARO")
                }
            }

            "agl" -> {
                val secondary = "QNH ${liveData.qnh.roundToInt()}"

                if (liveData.agl.isNaN()) {
                    Pair(placeholderFor(cardId), secondary)
                } else {
                    val formatted = UnitsFormatter.altitude(AltitudeM(liveData.agl), units)
                    Pair(formatted.text, secondary)
                }
            }

            "vario" -> {
                val varioValue = liveData.primaryVarioValue()
                val formatted = UnitsFormatter.verticalSpeed(VerticalSpeedMs(varioValue), units)
                val source = if (liveData.varioValid) {
                    liveData.varioSource.ifBlank { "TE" }
                } else {
                    "STALE"
                }
                Pair(formatted.text, source)
            }

            "vario_optimized" -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioOptimized), units).text,
                "R=0.5"
            )

            "vario_legacy" -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioLegacy), units).text,
                "R=2.0"
            )

            "vario_raw" -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioRaw), units).text,
                "RAW"
            )

            "vario_gps" -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioGPS), units).text,
                "GPS"
            )

            "vario_complementary" -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioComplementary), units).text,
                "COMP"
            )

            "real_igc_vario" -> {
                val sample = liveData.realIgcVario
                if (sample != null) {
                    Pair(
                        UnitsFormatter.verticalSpeed(VerticalSpeedMs(sample), units).text,
                        "REAL IGC"
                    )
                } else {
                    Pair(placeholderFor(cardId), "NO IGC")
                }
            }

            "ias" -> {
                val indicatedMs = liveData.indicatedAirspeed.takeIf { it.isFinite() && it > 0.1 }
                if (indicatedMs != null) {
                    val formatted = UnitsFormatter.speed(SpeedMs(indicatedMs), units)
                    return Pair(formatted.text, "LIVE")
                }
                Pair(placeholderFor(cardId), "NO DATA")
            }

            "ground_speed" -> Pair(
                UnitsFormatter.speed(SpeedMs(liveData.groundSpeed), units).text,
                "GPS"
            )

            "track" -> {
                val minSpeedMs = UnitsConverter.knotsToMs(2.0)
                if (liveData.groundSpeed > minSpeedMs) {
                    val trackDeg = liveData.track.roundToInt()
                    Pair("${trackDeg}?", "MAG")
                } else {
                    Pair("--?", "STATIC")
                }
            }

            "wpt_dist" -> Pair(placeholderFor(cardId), "NO WPT")
            "wpt_brg" -> Pair("---¦", "NO WPT")
            "final_gld" -> Pair("--:1", "NO WPT")
            "wpt_eta" -> Pair("--:--", "NO WPT")

            "ld_curr" -> {
                if (liveData.currentLD > 1f) {
                    Pair("${liveData.currentLD.roundToInt()}:1", "LIVE")
                } else {
                    Pair("--:1", "NO DATA")
                }
            }

            "thermal_avg" -> {
                // Parity with XCSoar TC 30s: primary shows the 30 s average, secondary shows current vario
                val avgValue: Double = liveData.thermalAverage.toDouble()
                val primary = if (avgValue.isFinite()) {
                    formatThermalVario(avgValue, units)
                } else {
                    placeholderFor(cardId)
                }

                val currentThermal = if (liveData.currentThermalValid) liveData.currentThermalLiftRate else Double.NaN
                val secondary = if (currentThermal.isFinite()) {
                    formatThermalVario(currentThermal, units)
                } else "---"
                Pair(primary, secondary)
            }

            "thermal_tc_avg" -> {
                val sample = liveData.thermalAverageCircle
                val isValid = liveData.currentThermalValid && sample.isFinite()
                if (!isValid) return Pair(placeholderFor(cardId), "NO DATA")
                val formatted = UnitsFormatter.verticalSpeed(
                    VerticalSpeedMs(sample.toDouble()),
                    units
                )
                Pair(formatted.text, "TC AVG")
            }

            "thermal_t_avg" -> {
                val sample = liveData.thermalAverageTotal
                if (abs(sample) <= 0.1f) return Pair(placeholderFor(cardId), "NO DATA")
                val formatted = UnitsFormatter.verticalSpeed(VerticalSpeedMs(sample.toDouble()), units)
                Pair(formatted.text, "T AVG")
            }

            "thermal_tc_gain" -> {
                val gain = liveData.thermalGain
                val isValid = liveData.thermalGainValid && gain.isFinite()
                val formatted = if (isValid) {
                    formatAltitudeValue(gain, units)
                } else "---"
                Pair(formatted, null)
            }

            "netto" -> {
                val formatted = UnitsFormatter.verticalSpeed(
                    VerticalSpeedMs(liveData.displayNetto),
                    units
                )
                val label = if (liveData.nettoValid) "NETTO" else "NO POLAR"
                Pair(formatted.text, label)
            }

            "netto_avg30" -> {
                val formatted = UnitsFormatter.verticalSpeed(
                    VerticalSpeedMs(liveData.nettoAverage30s),
                    units
                )
                Pair(formatted.text, "NETTO")
            }

            "mc_speed" -> Pair(placeholderFor(cardId), "NO MC")

            "flight_time" -> Pair(liveData.flightTime, "FLIGHT")

            "wind_spd" -> formatWindSpeed(liveData, units, placeholderFor(cardId))

            "wind_dir" -> formatWindDirection(liveData, units, placeholderFor(cardId))

            "wind_arrow" -> formatWindArrow(liveData, units, placeholderFor(cardId))

            "local_time" -> {
                val currentTime = System.currentTimeMillis()
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(currentTime))
                val seconds = java.text.SimpleDateFormat("ss", java.util.Locale.getDefault())
                    .format(java.util.Date(currentTime))
                Pair(time, seconds)
            }

            "task_spd" -> Pair(placeholderFor(cardId), "NO TASK")
            "task_dist" -> Pair(placeholderFor(cardId), "NO TASK")
            "start_alt" -> Pair(placeholderFor(cardId), "NO START")

            "g_force" -> Pair("-- G", "NO ACCEL")
            "flarm" -> Pair("NO FLARM", "---")

            "qnh" -> {
                if (liveData.currentPressureHPa > 0) {
                    val formatted = UnitsFormatter.pressure(
                        PressureHpa(liveData.qnh.toDouble()),
                        units
                    )
                    Pair(formatted.text, "CALC")
                } else {
                    val placeholder = UnitsFormatter.pressure(PressureHpa(0.0), units)
                    Pair("-- ${placeholder.unitLabel}", "NO BARO")
                }
            }

            "satelites" -> {
                if (liveData.satelliteCount > 0) {
                    val quality = when {
                        liveData.satelliteCount >= 8 -> "GOOD"
                        liveData.satelliteCount >= 6 -> "OK"
                        liveData.satelliteCount >= 4 -> "WEAK"
                        else -> "POOR"
                    }
                    Pair("${liveData.satelliteCount}", quality)
                } else {
                    Pair("--", "NO SATS")
                }
            }

            "gps_accuracy" -> {
                val accM = liveData.accuracy
                val quality = when {
                    accM < 3 -> "EXCELLENT"
                    accM < 10 -> "GOOD"
                    accM < 20 -> "OK"
                    else -> "POOR"
                }
                val formatted = UnitsFormatter.distance(DistanceM(accM), units)
                Pair(formatted.text, quality)
            }

            else -> Pair("--", "UNKNOWN")
        }
    }

    private fun formatWindSpeed(
        liveData: RealTimeFlightData,
        units: UnitsPreferences,
        placeholder: String
    ): Pair<String, String?> {
        val hasWind = liveData.windQuality > 0 && liveData.windSpeed > 0.5f
        if (!hasWind) {
            return Pair(placeholder, "NO WIND")
        }
        val formatted = UnitsFormatter.speed(SpeedMs(liveData.windSpeed.toDouble()), units)
        val arrow = arrowSymbol(liveData.windDirection.toDouble())
        return Pair(formatted.text, arrow)
    }

    private fun formatWindDirection(
        liveData: RealTimeFlightData,
        units: UnitsPreferences,
        placeholder: String
    ): Pair<String, String?> {
        val hasWind = liveData.windQuality > 0 && liveData.windSpeed > 0.5f
        if (!hasWind) {
            val placeholderWithDegree = if (placeholder.contains("?")) {
                placeholder.replace("?", "-\u00B0")
            } else {
                placeholder
            }
            return Pair(placeholderWithDegree, "NO WIND")
        }
        val windDir = ((liveData.windDirection.roundToInt() % 360) + 360) % 360
        val speed = UnitsFormatter.speed(SpeedMs(liveData.windSpeed.toDouble()), units).text
        return Pair("$windDir\u00B0", speed)
    }

    private fun formatWindArrow(
        liveData: RealTimeFlightData,
        units: UnitsPreferences,
        placeholder: String
    ): Pair<String, String?> {
        val hasWind = liveData.windQuality > 0 && liveData.windSpeed > 0.5f
        if (!hasWind) {
            return Pair(placeholder, "NO WIND")
        }
        val arrow = arrowSymbol(liveData.windDirection.toDouble())
        val speed = UnitsFormatter.speed(SpeedMs(liveData.windSpeed.toDouble()), units).text
        return Pair(arrow, speed)
    }

    private fun windBadge(liveData: RealTimeFlightData): String {
        val sourceLabel = when (liveData.windSource.uppercase()) {
            "CIRCLING" -> "AUTO CIRC"
            "EKF" -> "AUTO ZIGZAG"
            "EXTERNAL" -> "EXTERNAL"
            "MANUAL" -> "MANUAL"
            else -> "EST"
        }
        return if (liveData.windQuality > 0) {
            "$sourceLabel Q${liveData.windQuality}"
        } else {
            sourceLabel
        }
    }

    private fun headCrossSummary(
        liveData: RealTimeFlightData,
        units: UnitsPreferences
    ): String {
        val head = UnitsFormatter.speed(SpeedMs(abs(liveData.windHeadwind)), units).text
        val headSign = if (liveData.windHeadwind >= 0) "+" else "-"
        val cross = UnitsFormatter.speed(SpeedMs(abs(liveData.windCrosswind)), units).text
        val crossSide = if (liveData.windCrosswind >= 0) "R" else "L"
        return "Hd $headSign$head / X $crossSide $cross"
    }

    private fun arrowSymbol(directionFromDeg: Double): String {
        val arrows = listOf(
            "\u2191", // North
            "\u2197", // North-East
            "\u2192", // East
            "\u2198", // South-East
            "\u2193", // South
            "\u2199", // South-West
            "\u2190", // West
            "\u2196"  // North-West
        )
        val normalized = ((directionFromDeg % 360.0) + 360.0) % 360.0
        val index = ((normalized + 22.5) / 45.0).toInt() % arrows.size
        return arrows[index]
    }

    private fun windMeta(liveData: RealTimeFlightData): String {
        val parts = mutableListOf<String>()
        windAgeLabel(liveData.windAgeSeconds)?.let { parts += it }
        parts += windBadge(liveData)
        return parts.joinToString(" \u00B7 ")
    }

    private fun clampSmallVario(value: Double): Double {
        return if (value > -VARIO_ZERO_THRESHOLD && value < VARIO_ZERO_THRESHOLD) {
            if (value < 0) -0.0 else 0.0
        } else value
    }

    /**
     * Formats Thermal 30s primary/secondary so that true zero shows as "0.0" (no sign)
     * and inherits the card's zero-color rule (black).
     */
    private fun formatThermalVario(value: Double, units: UnitsPreferences): String {
        val clamped = clampSmallVario(value)
        val isZero = kotlin.math.abs(clamped) < VARIO_ZERO_THRESHOLD
        val formatted = UnitsFormatter.verticalSpeed(
            VerticalSpeedMs(clamped),
            units,
            decimals = VARIO_DECIMALS,
            showSign = !isZero
        ).text
        return if (isZero && (formatted.startsWith("+") || formatted.startsWith("-"))) {
            formatted.drop(1)
        } else {
            formatted
        }
    }

    private fun formatVarioValue(value: Double, units: UnitsPreferences): String {
        // Force 1 decimal like XCSoar, regardless of UnitsFormatter defaults
        val v = UnitsFormatter.verticalSpeed(
            VerticalSpeedMs(value),
            units,
            decimals = VARIO_DECIMALS
        )
        return v.text
    }

    private fun windAgeLabel(ageSeconds: Long): String? {
        if (ageSeconds < 0) return null
        return when {
            ageSeconds < 5 -> "LIVE"
            ageSeconds < 60 -> "${ageSeconds}s"
            ageSeconds < 3600 -> "${ageSeconds / 60}m"
            else -> "${ageSeconds / 3600}h"
        }
    }

    private fun formatAltitudeValue(value: Double, units: UnitsPreferences): String {
        val formatted = UnitsFormatter.altitude(
            AltitudeM(value),
            units,
            decimals = ALT_DECIMALS
        )
        return formatted.text
    }

    private fun combineSecondary(vararg labels: String?): String? {
        val text = labels.filterNot { it.isNullOrBlank() }.joinToString(" \u00B7 ")
        return text.ifBlank { null }
    }

    private fun RealTimeFlightData.primaryVarioValue(): Double {
        val finiteDisplay = displayVario.takeIf { it.isFinite() }
        if (varioValid && finiteDisplay != null) {
            return finiteDisplay
        }

        val fallback = listOfNotNull(
            finiteDisplay?.takeIf { abs(it) > VARIO_NOISE_FLOOR },
            verticalSpeed.takeIf { it.isFinite() && abs(it) > VARIO_NOISE_FLOOR },
            varioOptimized.takeIf { it.isFinite() && abs(it) > VARIO_NOISE_FLOOR },
            varioLegacy.takeIf { it.isFinite() && abs(it) > VARIO_NOISE_FLOOR },
            varioRaw.takeIf { it.isFinite() && abs(it) > VARIO_NOISE_FLOOR },
            varioGPS.takeIf { it.isFinite() && abs(it) > VARIO_NOISE_FLOOR },
            varioComplementary.takeIf { it.isFinite() && abs(it) > VARIO_NOISE_FLOOR }
        ).firstOrNull()

        return fallback ?: finiteDisplay ?: verticalSpeed.takeIf { it.isFinite() } ?: 0.0
    }
}
