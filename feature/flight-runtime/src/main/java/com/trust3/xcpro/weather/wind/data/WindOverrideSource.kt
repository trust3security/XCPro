package com.trust3.xcpro.weather.wind.data

import com.trust3.xcpro.weather.wind.model.WindOverride
import kotlinx.coroutines.flow.Flow

interface WindOverrideSource {
    val manualWind: Flow<WindOverride?>
    val externalWind: Flow<WindOverride?>
}
