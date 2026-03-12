package com.example.xcpro.weather.wind.data

import com.example.xcpro.weather.wind.model.WindOverride
import kotlinx.coroutines.flow.StateFlow

interface WindOverrideSource {
    val manualWind: StateFlow<WindOverride?>
    val externalWind: StateFlow<WindOverride?>
}
