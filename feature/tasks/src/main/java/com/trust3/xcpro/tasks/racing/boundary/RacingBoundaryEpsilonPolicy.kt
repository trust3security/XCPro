package com.trust3.xcpro.tasks.racing.boundary

import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationFix
import kotlin.math.max

class RacingBoundaryEpsilonPolicy(
    private val baseMeters: Double = DEFAULT_BASE_METERS,
    private val accuracyMultiplier: Double = DEFAULT_ACCURACY_MULTIPLIER,
    private val maxMeters: Double = DEFAULT_MAX_METERS
) {

    fun epsilonMeters(): Double = baseMeters

    fun epsilonMeters(fix: RacingNavigationFix): Double {
        val accuracy = fix.accuracyMeters ?: 0.0
        val computed = max(baseMeters, accuracy * accuracyMultiplier)
        return computed.coerceAtMost(maxMeters)
    }

    companion object {
        const val DEFAULT_BASE_METERS = 30.0
        const val DEFAULT_ACCURACY_MULTIPLIER = 2.0
        const val DEFAULT_MAX_METERS = 150.0
    }
}
