package com.example.xcpro.weather.wind.data

import com.example.xcpro.weather.wind.model.AirspeedSample
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ReplayAirspeedRepository @Inject constructor() : AirspeedDataSource {

    private val _airspeedFlow = MutableStateFlow<AirspeedSample?>(null)
    override val airspeedFlow: StateFlow<AirspeedSample?> = _airspeedFlow.asStateFlow()

    fun emitAirspeed(
        trueMs: Double,
        indicatedMs: Double,
        timestampMillis: Long,
        clockMillis: Long = timestampMillis,
        valid: Boolean = true
    ) {
        _airspeedFlow.value = AirspeedSample(
            trueMs = trueMs,
            indicatedMs = indicatedMs,
            timestampMillis = timestampMillis,
            clockMillis = clockMillis,
            valid = valid
        )
    }

    fun reset() {
        _airspeedFlow.value = null
    }
}
