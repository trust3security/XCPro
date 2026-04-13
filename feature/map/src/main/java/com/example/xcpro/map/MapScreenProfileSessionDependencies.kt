package com.example.xcpro.map

import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import javax.inject.Inject

data class MapScreenProfileSessionDependencies @Inject constructor(
    val mapStyleRepository: MapStyleRepository,
    val unitsRepository: UnitsRepository,
    val orientationSettingsRepository: MapOrientationSettingsRepository,
    val gliderConfigRepository: GliderConfigRepository,
    val variometerLayoutUseCase: VariometerLayoutUseCase,
    val trailSettingsUseCase: MapTrailSettingsUseCase,
    val qnhRepository: QnhRepository
)
