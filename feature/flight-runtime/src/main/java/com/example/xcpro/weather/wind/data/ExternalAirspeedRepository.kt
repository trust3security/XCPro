package com.example.xcpro.weather.wind.data

import com.example.xcpro.weather.wind.model.AirspeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ExternalAirspeedRepository @Inject constructor() : AirspeedDataSource, ExternalAirspeedWritePort {

    private val _airspeedFlow = MutableStateFlow<AirspeedSample?>(null)
    override val airspeedFlow: StateFlow<AirspeedSample?> = _airspeedFlow.asStateFlow()

    override fun updateAirspeed(sample: AirspeedSample?) {
        _airspeedFlow.value = sample
    }

    override fun clear() {
        _airspeedFlow.value = null
    }
}
