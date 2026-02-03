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
import com.example.xcpro.common.units.VerticalSpeedUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val VARIO_NOISE_FLOOR = 1e-3
private const val VARIO_ZERO_THRESHOLD = 0.05
private const val VARIO_DECIMALS = 1
private const val ALT_DECIMALS = 0

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

internal fun placeholderFor(
    cardId: KnownCardId?,
    units: UnitsPreferences,
    strings: CardStrings = DefaultCardStrings()
): String {
    return when (cardId) {
        KnownCardId.GPS_ALT,
        KnownCardId.BARO_ALT,
        KnownCardId.AGL,
        KnownCardId.START_ALT ->
            "-- ${UnitsFormatter.altitude(AltitudeM(0.0), units).unitLabel}"
        KnownCardId.VARIO,
        KnownCardId.VARIO_OPTIMIZED,
        KnownCardId.VARIO_LEGACY,
        KnownCardId.VARIO_RAW,
        KnownCardId.VARIO_GPS,
        KnownCardId.VARIO_COMPLEMENTARY,
        KnownCardId.REAL_IGC_VARIO,
        KnownCardId.THERMAL_AVG,
        KnownCardId.THERMAL_TC_AVG,
        KnownCardId.THERMAL_T_AVG,
        KnownCardId.NETTO_AVG30,
        KnownCardId.NETTO,
        KnownCardId.LEVO_NETTO ->
            "-- ${UnitsFormatter.verticalSpeed(VerticalSpeedMs(0.0), units).unitLabel}"
        KnownCardId.THERMAL_TC_GAIN ->
            "-- ${UnitsFormatter.altitude(AltitudeM(0.0), units).unitLabel}"
        KnownCardId.GROUND_SPEED,
        KnownCardId.WIND_SPD,
        KnownCardId.WIND_ARROW,
        KnownCardId.TASK_SPD,
        KnownCardId.IAS,
        KnownCardId.TAS ->
            "-- ${UnitsFormatter.speed(SpeedMs(0.0), units).unitLabel}"
        KnownCardId.WIND_DIR -> "-- ${strings.degUnit}"
        KnownCardId.WPT_DIST,
        KnownCardId.TASK_DIST ->
            "-- ${UnitsFormatter.distance(DistanceM(0.0), units).unitLabel}"
        else -> "--"
    }
}

internal fun formatWindSpeed(
    liveData: RealTimeFlightData,
    units: UnitsPreferences,
    strings: CardStrings,
    placeholder: String
): Pair<String, String?> {
    val hasWind = liveData.windQuality > 0 && liveData.windSpeed > 0.5f
    if (!hasWind) {
        return Pair(placeholder, strings.noWind)
    }
    val formatted = UnitsFormatter.speed(SpeedMs(liveData.windSpeed.toDouble()), units)
    val windDir = ((liveData.windDirection.roundToInt() % 360) + 360) % 360
    return Pair(formatted.text, "$windDir ${strings.degUnit}")
}

internal fun formatWindDirection(
    liveData: RealTimeFlightData,
    units: UnitsPreferences,
    strings: CardStrings,
    placeholder: String
): Pair<String, String?> {
    val hasWind = liveData.windQuality > 0 && liveData.windSpeed > 0.5f
    if (!hasWind) {
        return Pair(placeholder, strings.noWind)
    }
    val windDir = ((liveData.windDirection.roundToInt() % 360) + 360) % 360
    val speed = UnitsFormatter.speed(SpeedMs(liveData.windSpeed.toDouble()), units).text
    return Pair("$windDir ${strings.degUnit}", speed)
}

internal fun formatWindArrow(
    liveData: RealTimeFlightData,
    units: UnitsPreferences,
    strings: CardStrings,
    placeholder: String
): Pair<String, String?> {
    val hasWind = liveData.windQuality > 0 && liveData.windSpeed > 0.5f
    if (!hasWind) {
        return Pair(placeholder, strings.noWind)
    }
    val windFrom = liveData.windDirection.toDouble()
    val relativeFrom = if (liveData.headingValid) windFrom - liveData.headingDeg else windFrom
    val arrow = arrowSymbol(relativeFrom)
    val speed = UnitsFormatter.speed(SpeedMs(liveData.windSpeed.toDouble()), units).text
    return Pair(arrow, speed)
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

private fun clampSmallVario(value: Double): Double {
    return if (value > -VARIO_ZERO_THRESHOLD && value < VARIO_ZERO_THRESHOLD) {
        if (value < 0) -0.0 else 0.0
    } else {
        value
    }
}

/**
 * Formats Thermal 30s primary/secondary so that true zero shows as "0.0" (no sign)
 * and inherits the card's zero-color rule (black).
 */
internal fun formatThermalVario(value: Double, units: UnitsPreferences): String {
    val clamped = clampSmallVario(value)
    val isZero = kotlin.math.abs(clamped) < VARIO_ZERO_THRESHOLD
    val decimals = varioDecimals(units)
    val formatted = UnitsFormatter.verticalSpeed(
        VerticalSpeedMs(clamped),
        units,
        decimals = decimals,
        showSign = !isZero
    ).text
    return if (isZero && (formatted.startsWith("+") || formatted.startsWith("-"))) {
        formatted.drop(1)
    } else {
        formatted
    }
}

private fun varioDecimals(units: UnitsPreferences): Int =
    if (units.verticalSpeed == VerticalSpeedUnit.FEET_PER_MINUTE) 0 else VARIO_DECIMALS

private fun formatAltitudeValue(value: Double, units: UnitsPreferences): String {
    val formatted = UnitsFormatter.altitude(
        AltitudeM(value),
        units,
        decimals = ALT_DECIMALS
    )
    return formatted.text
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
