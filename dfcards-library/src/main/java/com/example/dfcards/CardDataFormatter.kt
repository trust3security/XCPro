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

    fun mapLiveDataToCard(
        cardId: String,
        liveData: RealTimeFlightData?,
        units: UnitsPreferences = UnitsPreferences()
    ): Pair<String, String?> {
        fun placeholderFor(card: String): String = when (card) {
            "gps_alt", "baro_alt", "agl", "start_alt" ->
                "-- ${UnitsFormatter.altitude(AltitudeM(0.0), units).unitLabel}"
            "vario", "vario_optimized", "vario_legacy", "vario_raw", "vario_gps",
            "vario_complementary", "thermal_avg", "netto" ->
                "-- ${UnitsFormatter.verticalSpeed(VerticalSpeedMs(0.0), units).unitLabel}"
            "ground_speed", "wind_spd", "task_spd", "ias" ->
                "-- ${UnitsFormatter.speed(SpeedMs(0.0), units).unitLabel}"
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
                    Pair(formatted.text, "BARO")
                }
                liveData.gpsAltitude != 0.0 -> {
                    val formatted = UnitsFormatter.altitude(AltitudeM(liveData.gpsAltitude), units)
                    Pair(formatted.text, "GPS")
                }
                else -> Pair(placeholderFor(cardId), "NO DATA")
            }

            "baro_alt" -> {
                if (liveData.baroAltitude > 0) {
                    val alt = UnitsFormatter.altitude(AltitudeM(liveData.baroAltitude), units)
                    val qnh = liveData.qnh.roundToInt()
                    val status = if (liveData.isQNHCalibrated) "QNH $qnh" else "STD"
                    Pair(alt.text, status)
                } else {
                    Pair(placeholderFor(cardId), "NO BARO")
                }
            }

            "agl" -> {
                val hasManualQnh = liveData.qnh in 900.0..1100.0 && !liveData.qnh.isNaN()
                val secondary = when {
                    liveData.isQNHCalibrated -> "QNH ${liveData.qnh.roundToInt()}"
                    hasManualQnh -> "QNH ${liveData.qnh.roundToInt()}"
                    else -> "STD"
                }

                if (liveData.agl.isNaN()) {
                    Pair(placeholderFor(cardId), secondary)
                } else {
                    val formatted = UnitsFormatter.altitude(AltitudeM(liveData.agl), units)
                    Pair(formatted.text, secondary)
                }
            }

            "vario" -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.verticalSpeed), units).text,
                "OPT"
            )

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

            "ias" -> {
                val indicatedMs = liveData.indicatedAirspeed.takeIf { it.isFinite() && it > 0.1 }
                if (indicatedMs != null) {
                    val formatted = UnitsFormatter.speed(SpeedMs(indicatedMs), units)
                    return Pair(formatted.text, "LIVE")
                }

                val groundSpeedMs = liveData.groundSpeed
                if (!groundSpeedMs.isFinite()) {
                    return Pair(placeholderFor(cardId), "NO DATA")
                }

                val altitudeMeters = when {
                    liveData.baroAltitude.isFinite() && liveData.baroAltitude != 0.0 -> liveData.baroAltitude
                    liveData.gpsAltitude.isFinite() && liveData.gpsAltitude != 0.0 -> liveData.gpsAltitude
                    else -> 0.0
                }
                val altitudeFeet = UnitsConverter.metersToFeet(altitudeMeters)
                val approxIasKt = AirspeedCalculator.calculateApproximateIAS(
                    groundSpeedKt = UnitsConverter.msToKnots(groundSpeedMs.coerceAtLeast(0.0)),
                    altitudeFt = altitudeFeet
                )
                val approxIasMs = UnitsConverter.knotsToMs(approxIasKt).coerceAtLeast(0.0)
                val formatted = UnitsFormatter.speed(SpeedMs(approxIasMs), units)
                Pair(formatted.text, "EST")
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
            "wpt_brg" -> Pair("--°", "NO WPT")
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
                if (abs(liveData.thermalAverage) > 0.1f) {
                    val formatted = UnitsFormatter.verticalSpeed(
                        VerticalSpeedMs(liveData.thermalAverage.toDouble()),
                        units
                    )
                    Pair(formatted.text, "AVG")
                } else {
                    Pair(placeholderFor(cardId), "NO THERMAL")
                }
            }

            "netto" -> {
                val minSpeedMs = UnitsConverter.knotsToMs(15.0)
                if (liveData.groundSpeed > minSpeedMs) {
                    val formatted = UnitsFormatter.verticalSpeed(
                        VerticalSpeedMs(liveData.netto.toDouble()),
                        units
                    )
                    Pair(formatted.text, "NETTO")
                } else {
                    Pair(placeholderFor(cardId), "TOO SLOW")
                }
            }

            "mc_speed" -> Pair(placeholderFor(cardId), "NO MC")

            "flight_time" -> Pair(liveData.flightTime, "FLIGHT")

            "wind_spd" -> {
                if (liveData.windSpeed > 0.5f) {
                    val formatted = UnitsFormatter.speed(SpeedMs(liveData.windSpeed.toDouble()), units)
                    val gsKnots = UnitsConverter.msToKnots(liveData.groundSpeed)
                    val confidence = if (gsKnots > 20) "CALC" else "EST"
                    Pair(formatted.text, confidence)
                } else {
                    Pair(placeholderFor(cardId), "NO WIND")
                }
            }

            "wind_dir" -> {
                if (liveData.windSpeed > 1.0f) {
                    val windDir = liveData.windDirection.roundToInt()
                    Pair("${windDir}?", "FROM")
                } else {
                    Pair("--?", "NO WIND")
                }
            }

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
}
