package com.example.xcpro.vario

import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.domain.FlyingState
import org.junit.Assert.assertEquals
import org.junit.Test

class GpsCadencePolicyTest {

    @Test
    fun replayAlwaysUsesSlowCadence() {
        val state = FlyingState(isFlying = true, onGround = false)
        val mode = GpsCadencePolicy.select(FlightDataRepository.Source.REPLAY, state)
        assertEquals(GpsCadenceMode.SLOW, mode)
    }

    @Test
    fun liveUsesFastCadenceWhenFlying() {
        val state = FlyingState(isFlying = true, onGround = false)
        val mode = GpsCadencePolicy.select(FlightDataRepository.Source.LIVE, state)
        assertEquals(GpsCadenceMode.FAST, mode)
    }

    @Test
    fun liveUsesSlowCadenceWhenNotFlying() {
        val state = FlyingState(isFlying = false, onGround = true)
        val mode = GpsCadencePolicy.select(FlightDataRepository.Source.LIVE, state)
        assertEquals(GpsCadenceMode.SLOW, mode)
    }
}
