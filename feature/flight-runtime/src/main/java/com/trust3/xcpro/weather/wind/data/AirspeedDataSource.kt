package com.trust3.xcpro.weather.wind.data

import com.trust3.xcpro.weather.wind.model.AirspeedSample
import kotlinx.coroutines.flow.StateFlow

interface AirspeedDataSource {
    val airspeedFlow: StateFlow<AirspeedSample?>
}
