package com.example.xcpro.map.ballast

import kotlin.math.abs

private const val EPSILON_KG = 0.05

/**
 * Snapshot of the current ballast state shared across UI and domain layers.
 */
data class BallastSnapshot(
    val currentKg: Double,
    val maxKg: Double,
    val ratio: Float,
    val hasBallast: Boolean
) {
    val canFill: Boolean
        get() = hasBallast && currentKg < maxKg - EPSILON_KG

    val canDrain: Boolean
        get() = hasBallast && currentKg > EPSILON_KG

    companion object {
        fun create(currentKg: Double, maxKg: Double): BallastSnapshot {
            val boundedMax = maxKg.coerceAtLeast(0.0)
            val boundedCurrent = currentKg.coerceAtLeast(0.0)
            val hasBallast = boundedMax > 0.0
            val ratio = if (hasBallast) {
                (boundedCurrent / boundedMax).toFloat().coerceIn(0f, 1f)
            } else {
                0f
            }
            val clampedCurrent = if (hasBallast) {
                boundedCurrent.coerceIn(0.0, boundedMax)
            } else {
                boundedCurrent
            }
            return BallastSnapshot(
                currentKg = clampedCurrent,
                maxKg = boundedMax,
                ratio = ratio,
                hasBallast = hasBallast
            )
        }
    }
}

enum class BallastMode { Idle, Filling, Draining }

sealed interface BallastCommand {
    data object StartFill : BallastCommand
    data object StartDrain : BallastCommand
    data object Cancel : BallastCommand
    data class ImmediateSet(val kilograms: Double) : BallastCommand
}

data class BallastUiState(
    val snapshot: BallastSnapshot,
    val mode: BallastMode = BallastMode.Idle,
    val remainingMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val targetKg: Double? = null
) {
    val isAnimating: Boolean get() = mode != BallastMode.Idle
    val isFillEnabled: Boolean
        get() = snapshot.canFill && mode != BallastMode.Filling
    val isDrainEnabled: Boolean
        get() = snapshot.canDrain && mode != BallastMode.Draining

    fun withSnapshot(snapshot: BallastSnapshot): BallastUiState =
        copy(snapshot = snapshot)

    fun withMode(
        mode: BallastMode,
        durationMillis: Long,
        targetKg: Double?
    ): BallastUiState = copy(
        mode = mode,
        durationMillis = durationMillis,
        remainingMillis = durationMillis,
        targetKg = targetKg
    )

    fun withRemaining(remainingMillis: Long): BallastUiState =
        copy(remainingMillis = remainingMillis)

    fun resetAnimation(): BallastUiState = copy(
        mode = BallastMode.Idle,
        remainingMillis = 0L,
        durationMillis = 0L,
        targetKg = null
    )
}

internal fun Double.isCloseTo(other: Double, tolerance: Double = EPSILON_KG): Boolean =
    abs(this - other) <= tolerance
