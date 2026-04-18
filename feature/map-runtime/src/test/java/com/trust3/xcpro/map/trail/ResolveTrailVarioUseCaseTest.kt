package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.trail.domain.ResolveTrailVarioUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveTrailVarioUseCaseTest {

    private val useCase = ResolveTrailVarioUseCase()

    @Test
    fun replay_prefers_real_igc_vario() {
        val data = buildCompleteFlightData(
            realIgcVarioMs = 5.0,
            baselineDisplayVarioMs = 2.0,
            baselineVarioValid = true,
            displayNettoMs = 1.0,
            nettoValid = true
        )

        val result = useCase.resolve(data, isReplay = true)

        assertEquals(5.0, result, 1e-6)
    }

    @Test
    fun replay_falls_back_to_baseline_display_vario() {
        val data = buildCompleteFlightData(
            realIgcVarioMs = null,
            baselineDisplayVarioMs = 2.5,
            baselineVarioValid = true,
            displayNettoMs = 1.0,
            nettoValid = true
        )

        val result = useCase.resolve(data, isReplay = true)

        assertEquals(2.5, result, 1e-6)
    }

    @Test
    fun replay_falls_back_to_display_netto() {
        val data = buildCompleteFlightData(
            realIgcVarioMs = null,
            baselineDisplayVarioMs = 2.5,
            baselineVarioValid = false,
            displayNettoMs = 1.2,
            nettoValid = true
        )

        val result = useCase.resolve(data, isReplay = true)

        assertEquals(1.2, result, 1e-6)
    }

    @Test
    fun live_uses_netto_when_valid() {
        val data = buildCompleteFlightData(
            nettoMs = 0.8,
            nettoValid = true,
            displayVarioMs = 2.0,
            verticalSpeedMs = 3.0
        )

        val result = useCase.resolve(data, isReplay = false)

        assertEquals(0.8, result, 1e-6)
    }

    @Test
    fun live_falls_back_to_display_vario() {
        val data = buildCompleteFlightData(
            nettoValid = false,
            displayVarioMs = 1.7,
            verticalSpeedMs = 3.0
        )

        val result = useCase.resolve(data, isReplay = false)

        assertEquals(1.7, result, 1e-6)
    }

    @Test
    fun live_falls_back_to_vertical_speed() {
        val data = buildCompleteFlightData(
            nettoValid = false,
            displayVarioMs = Double.NaN,
            verticalSpeedMs = 0.4
        )

        val result = useCase.resolve(data, isReplay = false)

        assertEquals(0.4, result, 1e-6)
    }
}
