package com.trust3.xcpro.map

import com.trust3.xcpro.MapOrientationManager
import com.trust3.xcpro.MapOrientationManagerFactory
import com.trust3.xcpro.map.ballast.BallastController
import com.trust3.xcpro.map.ballast.BallastControllerFactory
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

data class MapUiControllers(
    val flightDataManager: FlightDataManager,
    val orientationManager: MapOrientationManager,
    val ballastController: BallastController
)

class MapUiControllersUseCase @Inject constructor(
    private val flightDataManagerFactory: FlightDataManagerFactory,
    private val orientationManagerFactory: MapOrientationManagerFactory,
    private val ballastControllerFactory: BallastControllerFactory
) {
    fun create(scope: CoroutineScope): MapUiControllers =
        MapUiControllers(
            flightDataManager = flightDataManagerFactory.create(scope),
            orientationManager = orientationManagerFactory.create(scope),
            ballastController = ballastControllerFactory.create(scope)
        )
}
