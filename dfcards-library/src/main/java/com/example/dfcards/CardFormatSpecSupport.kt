package com.example.dfcards

import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.common.units.VerticalSpeedUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val VARIO_NOISE_FLOOR = 1e-3
private const val VARIO_ZERO_THRESHOLD = 0.05
private const val VARIO_DECIMALS = 1
private const val ALT_DECIMALS = 0

internal fun placeholderFor(
    cardId: KnownCardId?,
    units: UnitsPreferences,
    strings: CardStrings = DefaultCardStrings()
): String {
    return when (cardId) {
        KnownCardId.HAWK_VARIO -> "--.- m/s"
        KnownCardId.GPS_ALT,
        KnownCardId.BARO_ALT,
        KnownCardId.AGL,
        KnownCardId.ARR_ALT,
        KnownCardId.REQ_ALT,
        KnownCardId.ARR_MC0,
        KnownCardId.START_ALT ->
            "-- ${UnitsFormatter.altitude(AltitudeM(0.0), units).unitLabel}"
        KnownCardId.FINAL_GLD,
        KnownCardId.LD_CURR,
        KnownCardId.POLAR_LD,
        KnownCardId.BEST_LD ->
            "--:1"
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
        KnownCardId.TASK_DIST,
        KnownCardId.TASK_REMAIN_DIST ->
            "-- ${UnitsFormatter.distance(DistanceM(0.0), units).unitLabel}"
        KnownCardId.TASK_REMAIN_TIME -> "--:--"
        else -> "--"
    }
}

internal fun formatWindSpeed(
    liveData: RealTimeFlightData,
    units: UnitsPreferences,
    strings: CardStrings,
    placeholder: String
): Pair<String, String?> {
    if (!liveData.windValid) return Pair(placeholder, strings.noWind)
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
    if (!liveData.windValid) return Pair(placeholder, strings.noWind)
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
    if (!liveData.windValid) return Pair(placeholder, strings.noWind)
    val windFrom = liveData.windDirection.toDouble()
    val relativeFrom = if (liveData.headingValid) windFrom - liveData.headingDeg else windFrom
    val arrow = arrowSymbol(relativeFrom)
    val speed = UnitsFormatter.speed(SpeedMs(liveData.windSpeed.toDouble()), units).text
    return Pair(arrow, speed)
}

private fun arrowSymbol(directionFromDeg: Double): String {
    val arrows = listOf(
        "\u2191",
        "\u2197",
        "\u2192",
        "\u2198",
        "\u2193",
        "\u2199",
        "\u2190",
        "\u2196"
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

internal fun formatAltitudeValue(value: Double, units: UnitsPreferences): String {
    val formatted = UnitsFormatter.altitude(
        AltitudeM(value),
        units,
        decimals = ALT_DECIMALS
    )
    return formatted.text
}

internal fun formatSignedAltitudeValue(value: Double, units: UnitsPreferences): String {
    val formatted = formatAltitudeValue(value, units)
    return if (value > 0.0) "+$formatted" else formatted
}

internal fun glideInvalidLabel(reason: String, strings: CardStrings): String {
    return when (reason) {
        "PRESTART" -> strings.prestart
        "NO_FINISH_ALTITUDE",
        "NO_ALTITUDE" -> strings.noAlt
        "NO_POLAR" -> strings.noPolar
        "NO_POSITION" -> strings.noData
        "INVALID_ROUTE",
        "INVALID_SPEED",
        "FINISHED",
        "INVALID" -> strings.invalid
        else -> strings.noTask
    }
}

internal fun glideDegradedLabel(reason: String, strings: CardStrings): String {
    return when (reason) {
        "STILL_AIR_ASSUMED" -> strings.noWind
        else -> strings.est
    }
}

internal fun glideStatusLabel(
    liveData: RealTimeFlightData,
    strings: CardStrings,
    base: String? = null
): String {
    val validLabel = base ?: strings.calc
    if (!liveData.glideDegraded) return validLabel

    val degradedLabel = glideDegradedLabel(liveData.glideDegradedReason, strings)
    return if (base.isNullOrBlank()) {
        degradedLabel
    } else {
        "$base $degradedLabel"
    }
}

internal fun waypointInvalidLabel(reason: String, strings: CardStrings): String {
    return when (reason) {
        "PRESTART" -> strings.prestart
        "NO_POSITION",
        "NO_TIME" -> strings.noData
        "STATIC" -> strings.static
        "FINISHED",
        "INVALID_ROUTE",
        "INVALID" -> strings.invalid
        else -> strings.noWpt
    }
}

internal fun taskPerformanceInvalidLabel(reason: String, strings: CardStrings): String {
    return when (reason) {
        "PRESTART" -> strings.prestart
        "NO_POSITION" -> strings.noData
        "NO_START" -> strings.noStart
        "NO_ALTITUDE" -> strings.noAlt
        "STATIC" -> strings.static
        "INVALID_ROUTE",
        "INVALID" -> strings.invalid
        else -> strings.noTask
    }
}

internal fun taskRemainingTimeBasisLabel(basis: String, strings: CardStrings): String {
    return when (basis) {
        "ACHIEVED_TASK_SPEED" -> strings.calc
        else -> strings.calc
    }
}

internal fun formatDurationClock(durationMillis: Long): String {
    val totalMinutes = durationMillis.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return String.format(Locale.US, "%d:%02d", hours, minutes)
}

internal fun RealTimeFlightData.primaryVarioValue(): Double {
    val finiteDisplay = displayVario.takeIf { it.isFinite() }
    if (varioValid && finiteDisplay != null) return finiteDisplay

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

internal fun formatHawkVario(value: Double?): String {
    if (value == null || !value.isFinite()) return "--.- m/s"
    val clamped = if (abs(value) < VARIO_ZERO_THRESHOLD) 0.0 else value
    return String.format(Locale.US, "%+.1f m/s", clamped)
}

internal fun hawkStatusText(liveData: RealTimeFlightData): String {
    val accelText = if (liveData.hawkAccelOk) "ACCEL OK" else "ACCEL UNREL"
    val baroText = if (liveData.hawkBaroOk) "BARO OK" else "BARO DEG"
    val confText = when (liveData.hawkConfidenceCode) {
        6 -> "CONF 6"
        5 -> "CONF 5"
        4 -> "CONF 4"
        3 -> "CONF 3"
        2 -> "CONF 2"
        1 -> "CONF 1"
        else -> "CONF --"
    }
    return "$accelText $baroText $confText"
}
