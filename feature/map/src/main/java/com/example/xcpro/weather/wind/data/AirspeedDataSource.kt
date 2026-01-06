package com.example.xcpro.weather.wind.data

import com.example.xcpro.weather.wind.model.AirspeedSample
import kotlinx.coroutines.flow.StateFlow

interface AirspeedDataSource {
    val airspeedFlow: StateFlow<AirspeedSample?>
}
