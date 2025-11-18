package com.example.xcpro.sensors

import com.example.dfcards.filters.VarioFilterDiagnostics

data class VarioDiagnosticsSample(
    val timestamp: Long,
    val teVerticalSpeed: Double?,
    val rawVerticalSpeed: Double,
    val diagnostics: VarioFilterDiagnostics
)

