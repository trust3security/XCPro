package com.trust3.xcpro.map

import com.trust3.xcpro.MapOrientationManager
import com.trust3.xcpro.MapOrientationManagerFactory
import com.trust3.xcpro.map.ballast.BallastController
import com.trust3.xcpro.map.ballast.BallastControllerFactory
import com.trust3.xcpro.map.config.MapFeatureFlags
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

data class MapUiControllers(
    val flightDataManager: FlightDataManager,
    val orientationManager: MapOrientationManager,
    val ballastController: BallastController,
    val featureFlags: MapFeatureFlags
)

class MapUiControllersUseCase @Inject constructor(
    private val flightDataManagerFactory: FlightDataManagerFactory,
    private val orientationManagerFactory: MapOrientationManagerFactory,
    private val ballastControllerFactory: BallastControllerFactory,
    private val featureFlags: MapFeatureFlags
) {
    fun create(scope: CoroutineScope): MapUiControllers =
        MapUiControllers(
            flightDataManager = flightDataManagerFactory.create(scope),
            orientationManager = orientationManagerFactory.create(scope),
            ballastController = ballastControllerFactory.create(scope),
            featureFlags = featureFlags
        )
}
