package com.example.xcpro.map.domain

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class MapShiftBiasMode {
    NONE,
    TRACK,
    TARGET
}

enum class MapShiftBiasState {
    OFF,
    ACTIVE_TRACK,
    ACTIVE_TARGET,
    HOLD
}

data class MapShiftBiasConfig(
    val mode: MapShiftBiasMode = MapShiftBiasMode.NONE,
    val biasStrength: Double = 1.0,
    val minSpeedMs: Double = 8.0,
    val historySize: Int = DEFAULT_HISTORY_SIZE,
    val maxOffsetFraction: Double = DEFAULT_MAX_OFFSET_FRACTION,
    val holdOnInvalid: Boolean = true
) {
    companion object {
        const val DEFAULT_HISTORY_SIZE = 30
        const val DEFAULT_MAX_OFFSET_FRACTION = 0.35
    }
}

data class MapShiftBiasInput(
    val trackBearingDeg: Double?,
    val targetBearingDeg: Double?,
    val mapBearingDeg: Double,
    val speedMs: Double?,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val gliderScreenPercent: Int
)

data class ScreenOffset(
    val dxPx: Double,
    val dyPx: Double
) {
    companion object {
        val ZERO = ScreenOffset(0.0, 0.0)
    }
}

data class MapShiftBiasResult(
    val offset: ScreenOffset,
    val state: MapShiftBiasState
)

class MapShiftBiasCalculator {

    private val history = ArrayDeque<ScreenOffset>()
    private var sumDx = 0.0
    private var sumDy = 0.0
    private var lastOffset = ScreenOffset.ZERO
    private var lastState = MapShiftBiasState.OFF

    fun reset() {
        history.clear()
        sumDx = 0.0
        sumDy = 0.0
        lastOffset = ScreenOffset.ZERO
        lastState = MapShiftBiasState.OFF
    }

    fun update(input: MapShiftBiasInput, config: MapShiftBiasConfig): MapShiftBiasResult {
        val historyLimit = config.historySize.coerceIn(MIN_HISTORY_SIZE, MAX_HISTORY_SIZE)
        val strength = config.biasStrength.coerceIn(0.0, 1.0)
        val maxFraction = config.maxOffsetFraction.coerceIn(0.0, 1.0)
        val gliderPercent = input.gliderScreenPercent.coerceIn(MIN_GLIDER_PERCENT, MAX_GLIDER_PERCENT)

        val screenValid = input.screenWidthPx > 0 && input.screenHeightPx > 0
        val biasAllowed = config.mode != MapShiftBiasMode.NONE &&
            gliderPercent < BIAS_DISABLED_PERCENT &&
            screenValid &&
            strength > 0.0 &&
            maxFraction > 0.0

        if (!biasAllowed) {
            reset()
            return record(ScreenOffset.ZERO, MapShiftBiasState.OFF)
        }

        val speedOk = input.speedMs?.let { it.isFinite() && it >= config.minSpeedMs } ?: false
        val bearingDeg = when (config.mode) {
            MapShiftBiasMode.TRACK -> input.trackBearingDeg
            MapShiftBiasMode.TARGET -> input.targetBearingDeg
            MapShiftBiasMode.NONE -> null
        }
        val bearingOk = bearingDeg?.isFinite() == true

        if (!speedOk || !bearingOk) {
            return holdOrOff(config)
        }

        val mapBearing = if (input.mapBearingDeg.isFinite()) input.mapBearingDeg else 0.0
        val offset = computeOffset(
            bearingDeg = bearingDeg!!,
            mapBearingDeg = mapBearing,
            screenWidthPx = input.screenWidthPx,
            screenHeightPx = input.screenHeightPx,
            gliderScreenPercent = gliderPercent,
            biasStrength = strength,
            maxOffsetFraction = maxFraction
        )

        pushHistory(offset, historyLimit)
        val averaged = ScreenOffset(
            dxPx = sumDx / history.size.toDouble(),
            dyPx = sumDy / history.size.toDouble()
        )

        val state = when (config.mode) {
            MapShiftBiasMode.TRACK -> MapShiftBiasState.ACTIVE_TRACK
            MapShiftBiasMode.TARGET -> MapShiftBiasState.ACTIVE_TARGET
            MapShiftBiasMode.NONE -> MapShiftBiasState.OFF
        }
        return record(averaged, state)
    }

    private fun holdOrOff(config: MapShiftBiasConfig): MapShiftBiasResult {
        if (config.holdOnInvalid && history.isNotEmpty()) {
            return record(lastOffset, MapShiftBiasState.HOLD)
        }
        reset()
        return record(ScreenOffset.ZERO, MapShiftBiasState.OFF)
    }

    private fun pushHistory(offset: ScreenOffset, historyLimit: Int) {
        if (history.size >= historyLimit) {
            val removed = history.removeFirst()
            sumDx -= removed.dxPx
            sumDy -= removed.dyPx
        }
        history.addLast(offset)
        sumDx += offset.dxPx
        sumDy += offset.dyPx
    }

    private fun computeOffset(
        bearingDeg: Double,
        mapBearingDeg: Double,
        screenWidthPx: Int,
        screenHeightPx: Int,
        gliderScreenPercent: Int,
        biasStrength: Double,
        maxOffsetFraction: Double
    ): ScreenOffset {
        val positionFactor = (BIAS_DISABLED_PERCENT - gliderScreenPercent).toDouble() / 100.0
        val width = screenWidthPx.toDouble()
        val height = screenHeightPx.toDouble()
        val minDimension = min(width, height)

        if (positionFactor <= 0.0 || minDimension <= 0.0) {
            return ScreenOffset.ZERO
        }

        val thetaDeg = normalizeDegrees(bearingDeg + 180.0 - mapBearingDeg)
        val thetaRad = Math.toRadians(thetaDeg)

        val baseDx = sin(thetaRad) * width * positionFactor
        val baseDy = -cos(thetaRad) * height * positionFactor

        val scaledDx = baseDx * biasStrength
        val scaledDy = baseDy * biasStrength

        val maxOffset = minDimension * maxOffsetFraction
        val clampedDx = scaledDx.coerceIn(-maxOffset, maxOffset)
        val clampedDy = scaledDy.coerceIn(-maxOffset, maxOffset)

        return ScreenOffset(clampedDx, clampedDy)
    }

    private fun normalizeDegrees(deg: Double): Double {
        var value = deg % 360.0
        if (value < 0.0) value += 360.0
        return value
    }

    private fun record(offset: ScreenOffset, state: MapShiftBiasState): MapShiftBiasResult {
        lastOffset = offset
        lastState = state
        return MapShiftBiasResult(offset = offset, state = state)
    }

    companion object {
        private const val MIN_GLIDER_PERCENT = 10
        private const val MAX_GLIDER_PERCENT = 50
        private const val BIAS_DISABLED_PERCENT = 50
        private const val MIN_HISTORY_SIZE = 1
        private const val MAX_HISTORY_SIZE = 120
    }
}
