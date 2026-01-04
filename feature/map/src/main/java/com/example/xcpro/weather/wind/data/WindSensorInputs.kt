package com.example.xcpro.weather.wind.data

import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import kotlinx.coroutines.flow.StateFlow

data class WindSensorInputs(
    val gps: StateFlow<GpsSample?>,
    val pressure: StateFlow<PressureSample?>,
    val airspeed: StateFlow<AirspeedSample?>,
    val heading: StateFlow<HeadingSample?>,
    val gLoad: StateFlow<GLoadSample?>
)
