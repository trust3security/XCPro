package com.example.dfcards

// Formatting spec table for card values.
// Invariants: no Android or Compose types; pure functions only.

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

/**
 * Defines formatting behavior for a card's primary and secondary values.
 */
internal data class CardFormatSpec(
    val format: (RealTimeFlightData, UnitsPreferences, CardStrings, CardTimeFormatter) -> Pair<String, String?>
)

/**
 * Registry of formatting specs keyed by known card IDs.
 */
internal object CardFormatSpecs {
    val specs: Map<KnownCardId, CardFormatSpec> =
        KnownCardId.values().associateWith { cardId ->
            CardFormatSpec { liveData, units, strings, timeFormatter ->
                formatKnownCard(cardId, liveData, units, strings, timeFormatter)
            }
        }

    private fun formatKnownCard(
        cardId: KnownCardId,
        liveData: RealTimeFlightData,
        units: UnitsPreferences,
        strings: CardStrings,
        timeFormatter: CardTimeFormatter
    ): Pair<String, String?> {
        return when (cardId) {
            KnownCardId.GPS_ALT -> when {
                liveData.isQNHCalibrated && liveData.currentPressureHPa > 0 -> {
                    val formatted = UnitsFormatter.altitude(AltitudeM(liveData.baroAltitude), units)
                    Pair(formatted.text, "${strings.qnhPrefix} ${liveData.qnh.roundToInt()}")
                }
                liveData.gpsAltitude != 0.0 -> {
                    val formatted = UnitsFormatter.altitude(AltitudeM(liveData.gpsAltitude), units)
                    Pair(formatted.text, "${strings.qnhPrefix} ${liveData.qnh.roundToInt()}")
                }
                else -> Pair(placeholderFor(cardId, units, strings), strings.noData)
            }

            KnownCardId.BARO_ALT -> {
                if (liveData.baroAltitude > 0) {
                    val alt = UnitsFormatter.altitude(AltitudeM(liveData.baroAltitude), units)
                    val status = "${strings.qnhPrefix} ${liveData.qnh.roundToInt()}"
                    Pair(alt.text, status)
                } else {
                    Pair(placeholderFor(cardId, units, strings), strings.noBaro)
                }
            }

            KnownCardId.AGL -> {
                val secondary = "${strings.qnhPrefix} ${liveData.qnh.roundToInt()}"
                if (liveData.agl.isNaN()) {
                    Pair(placeholderFor(cardId, units, strings), secondary)
                } else {
                    val formatted = UnitsFormatter.altitude(AltitudeM(liveData.agl), units)
                    Pair(formatted.text, secondary)
                }
            }

            KnownCardId.VARIO -> {
                val varioValue = liveData.primaryVarioValue()
                val formatted = UnitsFormatter.verticalSpeed(VerticalSpeedMs(varioValue), units)
                val source = if (liveData.varioValid) {
                    liveData.varioSource.ifBlank { strings.te }
                } else {
                    strings.stale
                }
                Pair(formatted.text, source)
            }

            KnownCardId.VARIO_OPTIMIZED -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioOptimized), units).text,
                strings.rOptimized
            )

            KnownCardId.VARIO_LEGACY -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioLegacy), units).text,
                strings.rLegacy
            )

            KnownCardId.VARIO_RAW -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioRaw), units).text,
                strings.raw
            )

            KnownCardId.VARIO_GPS -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioGPS), units).text,
                strings.gps
            )

            KnownCardId.VARIO_COMPLEMENTARY -> Pair(
                UnitsFormatter.verticalSpeed(VerticalSpeedMs(liveData.varioComplementary), units).text,
                strings.comp
            )

            KnownCardId.HAWK_VARIO -> Pair(
                formatHawkVario(liveData.hawkVarioSmoothedMps),
                hawkStatusText(liveData)
            )

            KnownCardId.REAL_IGC_VARIO -> {
                val sample = liveData.realIgcVario
                if (sample != null) {
                    Pair(
                        UnitsFormatter.verticalSpeed(VerticalSpeedMs(sample), units).text,
                        strings.realIgc
                    )
                } else {
                    Pair(placeholderFor(cardId, units, strings), strings.noIgc)
                }
            }

            KnownCardId.IAS -> {
                val indicatedMs = liveData.indicatedAirspeed.takeIf { it.isFinite() && it > 0.1 }
                if (indicatedMs != null) {
                    val formatted = UnitsFormatter.speed(SpeedMs(indicatedMs), units)
                    val label = if (liveData.tasValid) strings.est else strings.gps
                    Pair(formatted.text, label)
                } else {
                    Pair(placeholderFor(cardId, units, strings), strings.noData)
                }
            }

            KnownCardId.TAS -> {
                val tasMs = liveData.trueAirspeed.takeIf { it.isFinite() && it > 0.1 }
                if (tasMs != null) {
                    val formatted = UnitsFormatter.speed(SpeedMs(tasMs), units)
                    val label = if (liveData.tasValid) strings.est else strings.gps
                    Pair(formatted.text, label)
                } else {
                    Pair(placeholderFor(cardId, units, strings), strings.noData)
                }
            }

            KnownCardId.GROUND_SPEED -> Pair(
                UnitsFormatter.speed(SpeedMs(liveData.groundSpeed), units).text,
                strings.gps
            )

            KnownCardId.TRACK -> {
                val minSpeedMs = UnitsConverter.knotsToMs(2.0)
                if (liveData.groundSpeed > minSpeedMs) {
                    val trackDeg = liveData.track.roundToInt()
                    Pair("${trackDeg}${strings.degUnit}", strings.mag)
                } else {
                    Pair("--${strings.degUnit}", strings.static)
                }
            }

            KnownCardId.WPT_DIST -> Pair(placeholderFor(cardId, units, strings), strings.noWpt)
            KnownCardId.WPT_BRG -> Pair("---|", strings.noWpt)
            KnownCardId.FINAL_GLD -> Pair("--:1", strings.noWpt)
            KnownCardId.WPT_ETA -> Pair("--:--", strings.noWpt)

            KnownCardId.LD_CURR -> {
                if (liveData.currentLD > 1f) {
                    Pair("${liveData.currentLD.roundToInt()}:1", strings.live)
                } else {
                    Pair("--:1", strings.noData)
                }
            }

            KnownCardId.THERMAL_AVG -> {
                // Match 30 s TC: primary shows the 30 s average, secondary shows current vario
                val avgValue: Double = liveData.thermalAverage.toDouble()
                val primary = if (avgValue.isFinite()) {
                    formatThermalVario(avgValue, units)
                } else {
                    placeholderFor(cardId, units, strings)
                }

                val currentThermal = if (liveData.currentThermalValid) liveData.currentThermalLiftRate else Double.NaN
                val secondary = if (currentThermal.isFinite()) {
                    formatThermalVario(currentThermal, units)
                } else {
                    "---"
                }
                Pair(primary, secondary)
            }

            KnownCardId.THERMAL_TC_AVG -> {
                val sample = liveData.thermalAverageCircle
                val isValid = liveData.currentThermalValid && sample.isFinite()
                if (!isValid) {
                    Pair(placeholderFor(cardId, units, strings), strings.noData)
                } else {
                    val formatted = UnitsFormatter.verticalSpeed(
                        VerticalSpeedMs(sample.toDouble()),
                        units
                    )
                    val tc30 = liveData.thermalAverage.toDouble()
                    val tc30Valid = liveData.thermalAverageValid && tc30.isFinite()
                    val secondary = if (tc30Valid) {
                        formatThermalVario(tc30, units)
                    } else {
                        strings.noData
                    }
                    Pair(formatted.text, secondary)
                }
            }

            KnownCardId.THERMAL_T_AVG -> {
                val sample = liveData.thermalAverageTotal
                if (abs(sample) <= 0.1f) {
                    Pair(placeholderFor(cardId, units, strings), strings.noData)
                } else {
                    val formatted = UnitsFormatter.verticalSpeed(VerticalSpeedMs(sample.toDouble()), units)
                    val tc30 = liveData.thermalAverage.toDouble()
                    val tc30Valid = liveData.thermalAverageValid && tc30.isFinite()
                    val secondary = if (tc30Valid) {
                        formatThermalVario(tc30, units)
                    } else {
                        strings.noData
                    }
                    Pair(formatted.text, secondary)
                }
            }

            KnownCardId.THERMAL_TC_GAIN -> {
                val gain = liveData.thermalGain
                val isValid = liveData.thermalGainValid && gain.isFinite()
                val formatted = if (isValid) {
                    formatAltitudeValue(gain, units)
                } else {
                    "---"
                }
                Pair(formatted, null)
            }

            KnownCardId.NETTO -> {
                val formatted = UnitsFormatter.verticalSpeed(
                    VerticalSpeedMs(liveData.displayNetto),
                    units
                )
                val label = if (liveData.nettoValid) strings.netto else strings.noPolar
                Pair(formatted.text, label)
            }

            KnownCardId.LEVO_NETTO -> {
                val hasWind = liveData.levoNettoHasWind
                val hasPolar = liveData.levoNettoHasPolar
                when {
                    !hasWind -> Pair(placeholderFor(cardId, units, strings), strings.noWind)
                    !hasPolar -> Pair(placeholderFor(cardId, units, strings), strings.noPolar)
                    else -> {
                        val formatted = UnitsFormatter.verticalSpeed(
                            VerticalSpeedMs(liveData.levoNetto),
                            units
                        )
                        Pair(formatted.text, strings.netto)
                    }
                }
            }

            KnownCardId.NETTO_AVG30 -> {
                val formatted = UnitsFormatter.verticalSpeed(
                    VerticalSpeedMs(liveData.nettoAverage30s),
                    units
                )
                Pair(formatted.text, strings.netto)
            }

            KnownCardId.MC_SPEED -> {
                if (!liveData.speedToFlyHasPolar) {
                    Pair(placeholderFor(cardId, units, strings), strings.noPolar)
                } else if (liveData.speedToFlyValid) {
                    val formatted = UnitsFormatter.speed(SpeedMs(liveData.speedToFlyIas), units)
                    val deltaKt = UnitsConverter.msToKnots(liveData.speedToFlyDelta)
                    val deltaRounded = deltaKt.roundToInt()
                    val deltaLabel = if (abs(deltaKt) < 0.5) {
                        "0 kt"
                    } else {
                        val sign = if (deltaRounded > 0) "+" else ""
                        "$sign$deltaRounded kt"
                    }
                    val modeLabel = if (liveData.speedToFlyMcSourceAuto) "AUTO" else "MAN"
                    Pair(formatted.text, "$modeLabel $deltaLabel")
                } else {
                    Pair(placeholderFor(cardId, units, strings), strings.noMc)
                }
            }

            KnownCardId.FLIGHT_TIME -> Pair(liveData.flightTime, strings.flight)

            KnownCardId.WIND_SPD -> formatWindSpeed(liveData, units, strings, placeholderFor(cardId, units, strings))
            KnownCardId.WIND_DIR -> formatWindDirection(liveData, units, strings, placeholderFor(cardId, units, strings))
            KnownCardId.WIND_ARROW -> formatWindArrow(liveData, units, strings, placeholderFor(cardId, units, strings))

            KnownCardId.LOCAL_TIME -> {
                val timeMillis = liveData.lastUpdateTime.takeIf { it > 0L } ?: liveData.timestamp
                val (time, seconds) = timeFormatter.formatLocalTime(timeMillis)
                Pair(time, seconds)
            }

            KnownCardId.TASK_SPD -> Pair(placeholderFor(cardId, units, strings), strings.noTask)
            KnownCardId.TASK_DIST -> Pair(placeholderFor(cardId, units, strings), strings.noTask)
            KnownCardId.START_ALT -> Pair(placeholderFor(cardId, units, strings), strings.noStart)

            KnownCardId.G_FORCE -> Pair("-- G", strings.noAccel)
            KnownCardId.FLARM -> Pair(strings.noFlarm, "---")

            KnownCardId.QNH -> {
                if (liveData.currentPressureHPa > 0) {
                    val formatted = UnitsFormatter.pressure(
                        PressureHpa(liveData.qnh.toDouble()),
                        units
                    )
                    Pair(formatted.text, strings.calc)
                } else {
                    val placeholder = UnitsFormatter.pressure(PressureHpa(0.0), units)
                    Pair("-- ${placeholder.unitLabel}", strings.noBaro)
                }
            }

            KnownCardId.SATELITES -> {
                if (liveData.satelliteCount > 0) {
                    val quality = when {
                        liveData.satelliteCount >= 8 -> strings.good
                        liveData.satelliteCount >= 6 -> strings.ok
                        liveData.satelliteCount >= 4 -> strings.weak
                        else -> strings.poor
                    }
                    Pair("${liveData.satelliteCount}", quality)
                } else {
                    Pair("--", strings.noSats)
                }
            }

            KnownCardId.GPS_ACCURACY -> {
                val accM = liveData.accuracy
                val quality = when {
                    accM < 3 -> strings.excellent
                    accM < 10 -> strings.good
                    accM < 20 -> strings.ok
                    else -> strings.poor
                }
                val formatted = UnitsFormatter.distance(DistanceM(accM), units)
                Pair(formatted.text, quality)
            }
        }
    }
}
