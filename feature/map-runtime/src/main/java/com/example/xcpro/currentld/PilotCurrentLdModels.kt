package com.example.xcpro.currentld

/**
 * Debug/source tag for the fused pilot-facing Current L/D owner.
 */
enum class PilotCurrentLdSource {
    FUSED_WIND,
    FUSED_ZERO_WIND,
    POLAR_FILL,
    GROUND_FALLBACK,
    HELD,
    NONE
}

/**
 * Pilot-facing Current L/D snapshot joined on the map-runtime side.
 */
data class PilotCurrentLdSnapshot(
    val pilotCurrentLD: Float = 0f,
    val pilotCurrentLDValid: Boolean = false,
    val pilotCurrentLDSource: PilotCurrentLdSource = PilotCurrentLdSource.NONE
)
