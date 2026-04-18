package com.trust3.xcpro.vario

import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.sensors.domain.FlyingState

enum class GpsCadenceMode { FAST, SLOW }

object GpsCadencePolicy {
    fun select(source: FlightDataRepository.Source, state: FlyingState): GpsCadenceMode {
        if (source == FlightDataRepository.Source.REPLAY) return GpsCadenceMode.SLOW
        return if (state.isFlying) GpsCadenceMode.FAST else GpsCadenceMode.SLOW
    }
}
