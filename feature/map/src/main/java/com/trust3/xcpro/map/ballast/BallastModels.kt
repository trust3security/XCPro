package com.trust3.xcpro.map.ballast

import kotlin.math.abs

private const val EPSILON_KG = 0.05

/**
 * Snapshot of the current ballast state shared across UI and domain layers.
 */
enum class BallastSource { INTERNAL, EXTERNAL }

data class BallastSnapshot(
    val currentKg: Double,
    val maxKg: Double,
    val ratio: Float,
    val hasBallast: Boolean,
    val source: BallastSource = BallastSource.INTERNAL,
    val externalFactor: Double? = null
) {
    val canFill: Boolean
        get() = source == BallastSource.INTERNAL && hasBallast && currentKg < maxKg - EPSILON_KG

    val canDrain: Boolean
        get() = source == BallastSource.INTERNAL && hasBallast && currentKg > EPSILON_KG

    val isExternalOverride: Boolean
        get() = source == BallastSource.EXTERNAL

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
                hasBallast = hasBallast,
                source = BallastSource.INTERNAL
            )
        }

        fun external(ballastOverloadFactor: Double): BallastSnapshot {
            val safeFactor = ballastOverloadFactor.coerceAtLeast(1.0)
            val ratio = ((safeFactor - 1.0) / EXTERNAL_FACTOR_RANGE)
                .toFloat()
                .coerceIn(0f, 1f)
            return BallastSnapshot(
                currentKg = 0.0,
                maxKg = 0.0,
                ratio = ratio,
                hasBallast = true,
                source = BallastSource.EXTERNAL,
                externalFactor = safeFactor
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
    val isReadOnlyExternal: Boolean
        get() = snapshot.isExternalOverride
    val isFillEnabled: Boolean
        get() = !isReadOnlyExternal && snapshot.canFill && mode != BallastMode.Filling
    val isDrainEnabled: Boolean
        get() = !isReadOnlyExternal && snapshot.canDrain && mode != BallastMode.Draining

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

private const val EXTERNAL_FACTOR_RANGE = 0.5
