package com.example.xcpro.flightdata

import com.example.xcpro.sensors.CompleteFlightData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-source-of-truth holder for the latest CompleteFlightData sample.
 *
 * VarioForegroundService updates this repository, and UI layers observe the exposed StateFlow
 * instead of attaching directly to the FlightDataCalculator.
 */
@Singleton
class FlightDataRepository @Inject constructor() {

    private val _flightData = MutableStateFlow<CompleteFlightData?>(null)
    val flightData: StateFlow<CompleteFlightData?> = _flightData.asStateFlow()

    fun update(data: CompleteFlightData?) {
        _flightData.value = data
    }
}

