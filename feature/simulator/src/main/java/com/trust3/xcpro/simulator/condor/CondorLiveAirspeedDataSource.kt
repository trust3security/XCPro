package com.trust3.xcpro.simulator.condor

import com.trust3.xcpro.weather.wind.data.AirspeedDataSource
import com.trust3.xcpro.weather.wind.model.AirspeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class CondorLiveAirspeedDataSource @Inject constructor(
    sampleRepository: CondorLiveSampleRepository
) : AirspeedDataSource {
    override val airspeedFlow: StateFlow<AirspeedSample?> = sampleRepository.airspeedFlow
}
