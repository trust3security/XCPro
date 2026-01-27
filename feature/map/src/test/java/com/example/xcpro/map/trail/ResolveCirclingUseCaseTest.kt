package com.example.xcpro.map.trail

import com.example.xcpro.map.trail.domain.CirclingDetectorAdapter
import com.example.xcpro.map.trail.domain.ResolveCirclingUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCirclingUseCaseTest {

    @Test
    fun replay_returns_true_when_already_circling() {
        val useCase = ResolveCirclingUseCase(FakeDetector())
        val data = buildCompleteFlightData(isCircling = true)

        assertTrue(useCase.resolve(data, isReplay = true))
    }

    @Test
    fun replay_returns_true_when_thermal_flags_valid() {
        val useCase = ResolveCirclingUseCase(FakeDetector())
        val data = buildCompleteFlightData(
            currentThermalValid = true,
            thermalAverageValid = false
        )

        assertTrue(useCase.resolve(data, isReplay = true))
    }

    @Test
    fun replay_uses_detector_when_needed() {
        val detector = FakeDetector(result = true)
        val useCase = ResolveCirclingUseCase(detector)
        val data = buildCompleteFlightData(
            isCircling = false,
            currentThermalValid = false,
            thermalAverageValid = false,
            timestampMillis = 1000L
        )

        val result = useCase.resolve(data, isReplay = true)

        assertTrue(result)
        assertEquals(1, detector.updateCalls)
    }

    @Test
    fun replay_resets_detector_on_time_backstep() {
        val detector = FakeDetector(result = false)
        val useCase = ResolveCirclingUseCase(detector)
        val first = buildCompleteFlightData(timestampMillis = 2000L)
        val second = buildCompleteFlightData(timestampMillis = 1000L)

        assertFalse(useCase.resolve(first, isReplay = true))
        assertFalse(useCase.resolve(second, isReplay = true))
        assertEquals(1, detector.resetCalls)
    }

    private class FakeDetector(
        private val result: Boolean = false
    ) : CirclingDetectorAdapter {
        var resetCalls: Int = 0
            private set
        var updateCalls: Int = 0
            private set

        override fun reset() {
            resetCalls++
        }

        override fun update(trackDegrees: Double, timestampMillis: Long, isFlying: Boolean): Boolean {
            updateCalls++
            return result
        }
    }
}

