package com.example.xcpro.map

import com.example.xcpro.MapOrientationManager
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.config.MapFeatureFlags

/**
 * Runtime-only collaborators needed by map UI orchestration.
 * Internal to the map shell; not a cross-module route contract.
 */
internal data class MapScreenRuntimeDependencies(
    val flightDataManager: FlightDataManager,
    val orientationManager: MapOrientationManager,
    val sensorsUseCase: MapSensorsUseCase,
    val tasksUseCase: MapTasksUseCase,
    val airspaceUseCase: AirspaceUseCase,
    val waypointFilesUseCase: WaypointFilesUseCase,
    val featureFlags: MapFeatureFlags
)
