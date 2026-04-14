package com.example.xcpro.map

import com.example.xcpro.MapOrientationManager
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.ballast.BallastControllerFactory
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
