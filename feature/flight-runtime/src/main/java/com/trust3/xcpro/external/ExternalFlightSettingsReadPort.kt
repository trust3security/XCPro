package com.trust3.xcpro.external

import kotlinx.coroutines.flow.StateFlow

/**
 * Live external-flight settings overrides from device sentences such as LXWP2/LXWP3/PLXVS.
 *
 * This seam is intentionally separate from [ExternalInstrumentReadPort]. It carries
 * session-scoped runtime overrides and display-only status values, not freshness-based
 * flight telemetry.
 */
data class ExternalFlightSettingsSnapshot(
    val macCreadyMps: Double? = null,
    val bugsPercent: Int? = null,
    val ballastOverloadFactor: Double? = null,
    val qnhHpa: Double? = null,
    val outsideAirTemperatureC: Double? = null
)

interface ExternalFlightSettingsReadPort {
    val externalFlightSettingsSnapshot: StateFlow<ExternalFlightSettingsSnapshot>
}
