package com.example.xcpro.weather.wind.data

import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import kotlinx.coroutines.flow.Flow

data class WindSensorInputs(
    val gps: Flow<GpsSample?>,
    val pressure: Flow<PressureSample?>,
    val airspeed: Flow<AirspeedSample?>,
    val heading: Flow<HeadingSample?>,
    val gLoad: Flow<GLoadSample?>
)
