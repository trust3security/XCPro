package com.trust3.xcpro.map

import com.trust3.xcpro.MapOrientationManager
import com.trust3.xcpro.airspace.AirspaceUseCase
import com.trust3.xcpro.flightdata.WaypointFilesUseCase
import com.trust3.xcpro.map.ballast.BallastController
import com.trust3.xcpro.map.config.MapFeatureFlags
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Map-owned runtime session for screen-scoped controllers and UI runtime inputs.
 */
internal class MapScreenRuntimeSession(
    val flightDataManager: FlightDataManager,
    val orientationManager: MapOrientationManager,
    val ballastController: BallastController,
    val runtimeInputs: MapScreenRuntimeInputs
)

internal data class MapScreenRuntimeInputs(
    val flightDataManager: FlightDataManager,
    val orientationManager: MapOrientationManager,
    val sensorsUseCase: MapSensorsUseCase,
    val phoneHealthUseCase: MapPhoneHealthUseCase,
    val airspaceUseCase: AirspaceUseCase,
    val waypointFilesUseCase: WaypointFilesUseCase,
    val featureFlags: MapFeatureFlags
)

class MapScreenRuntimeSessionFactory @Inject constructor(
    private val mapUiControllersUseCase: MapUiControllersUseCase,
    private val featureFlags: MapFeatureFlags
) {
    internal fun create(
        scope: CoroutineScope,
        sensorsUseCase: MapSensorsUseCase,
        phoneHealthUseCase: MapPhoneHealthUseCase,
        airspaceUseCase: AirspaceUseCase,
        waypointFilesUseCase: WaypointFilesUseCase
    ): MapScreenRuntimeSession {
        val controllers = mapUiControllersUseCase.create(scope)
        val runtimeInputs = MapScreenRuntimeInputs(
            flightDataManager = controllers.flightDataManager,
            orientationManager = controllers.orientationManager,
            sensorsUseCase = sensorsUseCase,
            phoneHealthUseCase = phoneHealthUseCase,
            airspaceUseCase = airspaceUseCase,
            waypointFilesUseCase = waypointFilesUseCase,
            featureFlags = featureFlags
        )
        return MapScreenRuntimeSession(
            flightDataManager = controllers.flightDataManager,
            orientationManager = controllers.orientationManager,
            ballastController = controllers.ballastController,
            runtimeInputs = runtimeInputs
        )
    }
}
