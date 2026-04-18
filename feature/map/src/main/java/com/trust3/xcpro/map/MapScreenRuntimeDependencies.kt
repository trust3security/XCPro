package com.trust3.xcpro.map

import com.trust3.xcpro.MapOrientationManager
import com.trust3.xcpro.airspace.AirspaceUseCase
import com.trust3.xcpro.flightdata.WaypointFilesUseCase
import com.trust3.xcpro.map.config.MapFeatureFlags

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
