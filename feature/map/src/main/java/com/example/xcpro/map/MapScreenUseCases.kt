package com.example.xcpro.map

import com.example.xcpro.common.glider.GliderConfigRepository
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.model.WindState
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.map.ballast.BallastController
import com.example.xcpro.map.ballast.BallastControllerFactory
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.domain.FlyingState
import com.example.dfcards.CardPreferences
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.MapOrientationManagerFactory
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

class MapStyleUseCase @Inject constructor(
    private val repository: MapStyleRepository
) {
    fun initialStyle(): String = repository.initialStyle()

    suspend fun saveStyle(style: String) {
        repository.saveStyle(style)
    }
}

class UnitsPreferencesUseCase @Inject constructor(
    private val repository: UnitsRepository
) {
    val unitsFlow: Flow<UnitsPreferences> = repository.unitsFlow
}

class GliderConfigUseCase @Inject constructor(
    private val repository: GliderConfigRepository
) {
    val config = repository.config
}

class FlightDataUseCase @Inject constructor(
    private val repository: FlightDataRepository
) {
    val flightData: StateFlow<CompleteFlightData?> = repository.flightData
    val activeSource: StateFlow<FlightDataRepository.Source> = repository.activeSource
}

class WindStateUseCase @Inject constructor(
    private val repository: WindSensorFusionRepository
) {
    val windState: StateFlow<WindState> = repository.windState
}

class QnhUseCase @Inject constructor(
    private val repository: QnhRepository
) {
    val calibrationState = repository.calibrationState

    suspend fun setManualQnh(hpa: Double) {
        repository.setManualQnh(hpa)
    }
}

class MapWaypointsUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val waypointLoader: WaypointLoader
) {
    suspend fun loadWaypoints(): List<WaypointData> = waypointLoader.load(appContext)
}

class MapSensorsUseCase @Inject constructor(
    private val varioServiceManager: VarioServiceManager
) {
    val serviceManager: VarioServiceManager = varioServiceManager
    val gpsStatusFlow: StateFlow<GpsStatus> = varioServiceManager.unifiedSensorManager.gpsStatusFlow
    val flightStateFlow: StateFlow<FlyingState> = varioServiceManager.flightStateSource.flightState

    fun setFlightMode(mode: com.example.xcpro.common.flight.FlightMode) {
        varioServiceManager.setFlightMode(mode)
    }
}

class MapVarioPreferencesUseCase @Inject constructor(
    private val repository: LevoVarioPreferencesRepository
) {
    val showWindSpeedOnVario: Flow<Boolean> = repository.config.map { it.showWindSpeedOnVario }
}

class MapTasksUseCase @Inject constructor(
    val taskManager: TaskManagerCoordinator,
    val taskNavigationController: TaskNavigationController
) {
    fun loadSavedTasks() {
        taskManager.loadSavedTasks()
    }
}

class MapReplayUseCase @Inject constructor(
    val controller: IgcReplayController,
    val racingReplayLogBuilder: RacingReplayLogBuilder
)

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

class MapCardPreferencesUseCase @Inject constructor(
    val cardPreferences: CardPreferences
)

class MapFeatureFlagsUseCase @Inject constructor(
    val featureFlags: MapFeatureFlags
)
