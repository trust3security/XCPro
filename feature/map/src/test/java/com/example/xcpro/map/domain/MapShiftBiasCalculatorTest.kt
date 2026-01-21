package com.example.xcpro.map.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapShiftBiasCalculatorTest {

    @Test
    fun returnsOffWhenModeNone() {
        val calculator = MapShiftBiasCalculator()
        val config = MapShiftBiasConfig(mode = MapShiftBiasMode.NONE)
        val result = calculator.update(baseInput(), config)

        assertEquals(MapShiftBiasState.OFF, result.state)
        assertEquals(0.0, result.offset.dxPx, EPS)
        assertEquals(0.0, result.offset.dyPx, EPS)
    }

    @Test
    fun returnsOffWhenCentered() {
        val calculator = MapShiftBiasCalculator()
        val config = MapShiftBiasConfig(mode = MapShiftBiasMode.TRACK)
        val result = calculator.update(baseInput(gliderScreenPercent = 50), config)

        assertEquals(MapShiftBiasState.OFF, result.state)
        assertEquals(0.0, result.offset.dxPx, EPS)
        assertEquals(0.0, result.offset.dyPx, EPS)
    }

    @Test
    fun computesForwardOffsetForNorthUp() {
        val calculator = MapShiftBiasCalculator()
        val config = MapShiftBiasConfig(
            mode = MapShiftBiasMode.TRACK,
            historySize = 1,
            maxOffsetFraction = 1.0
        )
        val result = calculator.update(
            baseInput(trackBearingDeg = 0.0, mapBearingDeg = 0.0, gliderScreenPercent = 20),
            config
        )

        assertEquals(MapShiftBiasState.ACTIVE_TRACK, result.state)
        assertEquals(0.0, result.offset.dxPx, EPS)
        assertEquals(300.0, result.offset.dyPx, EPS)
    }

    @Test
    fun clampsOffsetToMaxFraction() {
        val calculator = MapShiftBiasCalculator()
        val config = MapShiftBiasConfig(
            mode = MapShiftBiasMode.TRACK,
            historySize = 1,
            maxOffsetFraction = 0.1
        )
        val result = calculator.update(
            baseInput(trackBearingDeg = 0.0, gliderScreenPercent = 10),
            config
        )

        assertEquals(100.0, result.offset.dyPx, EPS)
    }

    @Test
    fun holdsLastOffsetWhenInvalid() {
        val calculator = MapShiftBiasCalculator()
        val config = MapShiftBiasConfig(
            mode = MapShiftBiasMode.TRACK,
            historySize = 2,
            minSpeedMs = 8.0
        )
        val first = calculator.update(
            baseInput(trackBearingDeg = 0.0, speedMs = 20.0),
            config
        )
        val second = calculator.update(
            baseInput(trackBearingDeg = 0.0, speedMs = 2.0),
            config
        )

        assertEquals(MapShiftBiasState.HOLD, second.state)
        assertEquals(first.offset.dxPx, second.offset.dxPx, EPS)
        assertEquals(first.offset.dyPx, second.offset.dyPx, EPS)
    }

    @Test
    fun averagesOffsetsAcrossHistory() {
        val calculator = MapShiftBiasCalculator()
        val config = MapShiftBiasConfig(
            mode = MapShiftBiasMode.TRACK,
            historySize = 2,
            maxOffsetFraction = 1.0
        )
        calculator.update(baseInput(trackBearingDeg = 0.0), config)
        val second = calculator.update(baseInput(trackBearingDeg = 180.0), config)

        assertEquals(0.0, second.offset.dxPx, EPS)
        assertTrue(kotlin.math.abs(second.offset.dyPx) < 1e-6)
    }

    private fun baseInput(
        trackBearingDeg: Double? = 0.0,
        targetBearingDeg: Double? = null,
        mapBearingDeg: Double = 0.0,
        speedMs: Double? = 20.0,
        screenWidthPx: Int = 1000,
        screenHeightPx: Int = 1000,
        gliderScreenPercent: Int = 20
    ): MapShiftBiasInput {
        return MapShiftBiasInput(
            trackBearingDeg = trackBearingDeg,
            targetBearingDeg = targetBearingDeg,
            mapBearingDeg = mapBearingDeg,
            speedMs = speedMs,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            gliderScreenPercent = gliderScreenPercent
        )
    }

    companion object {
        private const val EPS = 1e-6
    }
}
