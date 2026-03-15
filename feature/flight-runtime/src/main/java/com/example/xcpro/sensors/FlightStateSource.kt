package com.example.xcpro.sensors

import com.example.xcpro.sensors.domain.FlyingState
import kotlinx.coroutines.flow.StateFlow

interface FlightStateSource {
    val flightState: StateFlow<FlyingState>
}
