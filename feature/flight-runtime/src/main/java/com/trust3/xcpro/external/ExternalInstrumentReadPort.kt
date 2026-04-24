package com.trust3.xcpro.external

import kotlinx.coroutines.flow.StateFlow

data class TimedExternalValue<T>(
    val value: T,
    val receivedMonoMs: Long
)

data class ExternalInstrumentFlightSnapshot(
    val pressureAltitudeM: TimedExternalValue<Double>? = null,
    val totalEnergyVarioMps: TimedExternalValue<Double>? = null,
    val externalVarioMps: TimedExternalValue<Double>? = null
)

interface ExternalInstrumentReadPort {
    val externalFlightSnapshot: StateFlow<ExternalInstrumentFlightSnapshot>
}
