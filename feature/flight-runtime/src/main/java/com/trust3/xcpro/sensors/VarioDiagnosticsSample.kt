package com.trust3.xcpro.sensors

import com.trust3.xcpro.core.flight.filters.VarioFilterDiagnostics

data class VarioDiagnosticsSample(
    val timestamp: Long,
    val teVerticalSpeed: Double?,
    val rawVerticalSpeed: Double,
    val diagnostics: VarioFilterDiagnostics,
    val windAirspeedDecisionCounts: Map<String, Long> = emptyMap(),
    val windAirspeedTransitionCounts: Map<String, Long> = emptyMap()
)

