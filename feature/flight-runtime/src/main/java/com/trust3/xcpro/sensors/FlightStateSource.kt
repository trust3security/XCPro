package com.trust3.xcpro.sensors

import com.trust3.xcpro.sensors.domain.FlyingState
import kotlinx.coroutines.flow.StateFlow

interface FlightStateSource {
    val flightState: StateFlow<FlyingState>
}
