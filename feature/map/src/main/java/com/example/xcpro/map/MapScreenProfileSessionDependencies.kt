package com.example.xcpro.map

import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import javax.inject.Inject

data class MapScreenProfileSessionDependencies @Inject constructor(
    val mapStyleUseCase: MapStyleUseCase,
    val unitsUseCase: UnitsPreferencesUseCase,
    val orientationSettingsUseCase: MapOrientationSettingsUseCase,
    val gliderConfigUseCase: GliderConfigUseCase,
    val variometerLayoutUseCase: VariometerLayoutUseCase,
    val trailSettingsUseCase: MapTrailSettingsUseCase,
    val qnhUseCase: QnhUseCase
)
