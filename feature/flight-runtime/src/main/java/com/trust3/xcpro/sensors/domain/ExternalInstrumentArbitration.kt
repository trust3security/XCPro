package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.external.ExternalInstrumentFlightSnapshot
import com.trust3.xcpro.external.TimedExternalValue

internal const val EXTERNAL_INSTRUMENT_FRESHNESS_MS: Long = 2_000L

internal data class ResolvedExternalInstrumentInputs(
    val pressureAltitudeM: TimedExternalValue<Double>? = null,
    val totalEnergyVarioMps: TimedExternalValue<Double>? = null
)

internal fun resolveExternalInstrumentInputs(
    snapshot: ExternalInstrumentFlightSnapshot,
    currentMonoMs: Long,
    isReplayMode: Boolean
): ResolvedExternalInstrumentInputs {
    if (isReplayMode) {
        return ResolvedExternalInstrumentInputs()
    }

    return ResolvedExternalInstrumentInputs(
        pressureAltitudeM = snapshot.pressureAltitudeM?.takeIf { it.isFreshAt(currentMonoMs) },
        totalEnergyVarioMps = snapshot.totalEnergyVarioMps?.takeIf { it.isFreshAt(currentMonoMs) }
    )
}

private fun TimedExternalValue<Double>.isFreshAt(currentMonoMs: Long): Boolean {
    val ageMs = currentMonoMs - receivedMonoMs
    return ageMs in 0L..EXTERNAL_INSTRUMENT_FRESHNESS_MS
}
