package com.example.xcpro.map

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.domain.MapShiftBiasCalculator
import com.example.xcpro.map.domain.MapShiftBiasConfig
import com.example.xcpro.map.domain.MapShiftBiasInput
import com.example.xcpro.map.domain.MapShiftBiasMode
import com.example.xcpro.map.domain.ScreenOffset
import com.example.xcpro.orientation.shortestDeltaDegrees
import kotlin.math.abs
import kotlin.math.roundToInt

class MapCameraPolicy(
    private val offsetAverager: OffsetAverager,
    private val biasCalculator: MapShiftBiasCalculator
) {

    interface OffsetAverager {
        fun remember(topPx: Float, bottomPx: Float)
        fun averaged(): AveragedOffset
    }

    data class AveragedOffset(
        val topPx: Float,
        val bottomPx: Float
    )

    data class BiasInput(
        val trackDeg: Double,
        val targetBearingDeg: Double?,
        val mapBearing: Double,
        val speedMs: Double,
        val orientationMode: MapOrientationMode,
        val flightMode: FlightMode,
        val biasMode: MapShiftBiasMode,
        val biasStrength: Double,
        val minSpeedMs: Double,
        val historySize: Int,
        val maxOffsetFraction: Double,
        val holdOnInvalid: Boolean,
        val screenWidthPx: Int,
        val screenHeightPx: Int,
        val gliderScreenPercent: Int
    )

    data class CameraUpdateInput(
        val timeBase: DisplayClock.TimeBase?,
        val useRenderFrameSync: Boolean,
        val cameraBearing: Double,
        val targetBearing: Double,
        val nowMs: Long,
        val lastCameraUpdateMs: Long,
        val minUpdateIntervalMs: Long,
        val bearingEpsDeg: Double
    )

    fun computeSmoothedPadding(rawPadding: IntArray, biasInput: BiasInput): IntArray {
        val basePadding = computeBasePadding(rawPadding)
        val biasOffset = computeBiasOffset(biasInput)
        return applyBiasToPadding(basePadding, biasOffset)
    }

    fun computeBasePadding(rawPadding: IntArray): IntArray {
        val top = rawPadding.getOrNull(1)?.toFloat() ?: 0f
        val bottom = rawPadding.getOrNull(3)?.toFloat() ?: 0f
        offsetAverager.remember(top, bottom)
        val averaged = offsetAverager.averaged()
        return intArrayOf(0, averaged.topPx.roundToInt(), 0, averaged.bottomPx.roundToInt())
    }

    fun computeBiasOffset(input: BiasInput): ScreenOffset {
        if (input.biasMode == MapShiftBiasMode.NONE) {
            biasCalculator.reset()
            return ScreenOffset.ZERO
        }
        if (input.orientationMode != MapOrientationMode.NORTH_UP) {
            biasCalculator.reset()
            return ScreenOffset.ZERO
        }
        if (input.flightMode == FlightMode.THERMAL) {
            biasCalculator.reset()
            return ScreenOffset.ZERO
        }

        val mapShiftInput = MapShiftBiasInput(
            trackBearingDeg = input.trackDeg.takeIf { it.isFinite() },
            targetBearingDeg = input.targetBearingDeg?.takeIf { it.isFinite() },
            mapBearingDeg = input.mapBearing,
            speedMs = input.speedMs.takeIf { it.isFinite() },
            screenWidthPx = input.screenWidthPx,
            screenHeightPx = input.screenHeightPx,
            gliderScreenPercent = input.gliderScreenPercent
        )
        val config = MapShiftBiasConfig(
            mode = input.biasMode,
            biasStrength = input.biasStrength,
            minSpeedMs = input.minSpeedMs,
            historySize = input.historySize,
            maxOffsetFraction = input.maxOffsetFraction,
            holdOnInvalid = input.holdOnInvalid
        )
        return biasCalculator.update(mapShiftInput, config).offset
    }

    fun applyBiasToPadding(basePadding: IntArray, biasOffset: ScreenOffset): IntArray {
        val left = if (biasOffset.dxPx > 0.0) {
            basePadding[0] + biasOffset.dxPx.roundToInt()
        } else {
            basePadding[0]
        }
        val right = if (biasOffset.dxPx < 0.0) {
            basePadding[2] + (-biasOffset.dxPx).roundToInt()
        } else {
            basePadding[2]
        }
        val top = if (biasOffset.dyPx > 0.0) {
            basePadding[1] + biasOffset.dyPx.roundToInt()
        } else {
            basePadding[1]
        }
        val bottom = if (biasOffset.dyPx < 0.0) {
            basePadding[3] + (-biasOffset.dyPx).roundToInt()
        } else {
            basePadding[3]
        }
        return intArrayOf(left, top, right, bottom)
    }

    fun shouldUpdateCamera(
        input: CameraUpdateInput,
        positionMovedProvider: () -> Boolean
    ): Boolean {
        if (input.useRenderFrameSync && input.timeBase == DisplayClock.TimeBase.REPLAY) {
            return true
        }
        val bearingDelta = abs(shortestDeltaDegrees(input.cameraBearing, input.targetBearing))
        val bearingMoved = bearingDelta >= input.bearingEpsDeg
        val timeDue = input.nowMs - input.lastCameraUpdateMs >= input.minUpdateIntervalMs
        if (!timeDue && !bearingMoved) return false

        val positionMoved = positionMovedProvider()
        return bearingMoved || (timeDue && positionMoved)
    }
}
