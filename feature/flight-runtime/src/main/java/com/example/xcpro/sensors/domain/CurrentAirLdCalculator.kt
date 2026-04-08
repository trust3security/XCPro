package com.example.xcpro.sensors.domain

internal data class CurrentAirLdComputation(
    val value: Float = 0f,
    val valid: Boolean = false
)

internal fun calculateCurrentAirLd(
    isFlying: Boolean,
    tasValid: Boolean,
    airspeedSourceLabel: String,
    trueAirspeedMs: Double,
    teVario: Double?,
    isCircling: Boolean,
    isTurning: Boolean
): CurrentAirLdComputation {
    if (!isFlying || !tasValid || isCircling || isTurning) {
        return CurrentAirLdComputation()
    }
    if (airspeedSourceLabel == AirspeedSource.GPS_GROUND.label) {
        return CurrentAirLdComputation()
    }
    val sinkSource = teVario?.takeIf { it.isFinite() } ?: return CurrentAirLdComputation()
    if (!trueAirspeedMs.isFinite() || trueAirspeedMs <= 5.0) {
        return CurrentAirLdComputation()
    }

    val teSinkMs = -sinkSource
    if (!teSinkMs.isFinite() || teSinkMs <= FlightMetricsConstants.MIN_SINK_FOR_IAS_MS) {
        return CurrentAirLdComputation()
    }

    val currentLdAir = trueAirspeedMs / teSinkMs
    return if (currentLdAir.isFinite() && currentLdAir > 0.0) {
        CurrentAirLdComputation(value = currentLdAir.toFloat(), valid = true)
    } else {
        CurrentAirLdComputation()
    }
}
