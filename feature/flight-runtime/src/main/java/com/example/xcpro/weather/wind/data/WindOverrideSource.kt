package com.example.xcpro.weather.wind.data

import com.example.xcpro.weather.wind.model.WindOverride
import kotlinx.coroutines.flow.Flow

interface WindOverrideSource {
    val manualWind: Flow<WindOverride?>
    val externalWind: Flow<WindOverride?>
}
