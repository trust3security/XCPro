package com.example.xcpro.map

import com.example.xcpro.MapOrientationManager
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.config.MapFeatureFlags

/**
 * Runtime-only collaborators needed by map UI orchestration.
 * Keeps the ViewModel boundary narrow by exposing one grouped contract.
 */
data class MapScreenRuntimeDependencies(
    val flightDataManager: FlightDataManager,
    val orientationManager: MapOrientationManager,
    val sensorsUseCase: MapSensorsUseCase,
    val tasksUseCase: MapTasksUseCase,
    val airspaceUseCase: AirspaceUseCase,
    val waypointFilesUseCase: WaypointFilesUseCase,
    val featureFlags: MapFeatureFlags
)
