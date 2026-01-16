package com.example.xcpro.vario

import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.domain.FlyingState

enum class GpsCadenceMode { FAST, SLOW }

object GpsCadencePolicy {
    fun select(source: FlightDataRepository.Source, state: FlyingState): GpsCadenceMode {
        if (source == FlightDataRepository.Source.REPLAY) return GpsCadenceMode.SLOW
        return if (state.isFlying) GpsCadenceMode.FAST else GpsCadenceMode.SLOW
    }
}
