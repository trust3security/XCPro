package com.example.xcpro.map

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.xcpro.core.flight.calculations.ConfidenceLevel
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.OrientationDataSourceFactory
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.WaypointLoader
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.glider.GliderRepository
import com.example.xcpro.qnh.CalibrateQnhUseCase
import com.example.xcpro.qnh.QnhCalibrationState
import com.example.xcpro.qnh.QnhRepository
import com.example.xcpro.qnh.QnhValue
import com.example.xcpro.qnh.QnhSource
import com.example.xcpro.qnh.QnhConfidence
import com.example.xcpro.sensors.AttitudeData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.sensors.GpsStatus
import com.example.xcpro.sensors.SensorStatus
import com.example.xcpro.sensors.UnifiedSensorManager
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.vario.VarioServiceManager
import com.example.xcpro.vario.LevoVarioPreferencesRepository
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.testing.MainDispatcherRule
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import com.example.xcpro.map.domain.MapWaypointError
import com.example.xcpro.map.config.MapFeatureFlags
import com.example.xcpro.map.replay.RacingReplayLogBuilder
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.orientation.HeadingResolver
import com.example.xcpro.orientation.OrientationClock
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.ReplayEvent
import com.example.xcpro.replay.SessionState
import com.example.xcpro.map.trail.MapTrailPreferences
import com.example.xcpro.map.trail.MapTrailSettingsUseCase
import com.example.xcpro.map.trail.TrailLength
import com.example.xcpro.map.trail.TrailSettings
import com.example.xcpro.map.trail.TrailType
import com.example.xcpro.tasks.TaskFeatureFlags
import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.racing.RacingTaskManager
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import com.example.xcpro.variometer.layout.VariometerLayoutUseCase
import com.example.xcpro.variometer.layout.VariometerWidgetRepository
import com.example.xcpro.map.ballast.BallastControllerFactory
import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.ConfigurationRepository
import com.example.xcpro.map.AdsbProximityTier
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.hawk.HawkVarioUseCase
import com.example.xcpro.map.AdsbConnectionState
import com.example.xcpro.map.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.map.ADSB_ICON_SIZE_MAX_PX
import com.example.xcpro.map.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.example.xcpro.map.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.example.xcpro.map.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.example.xcpro.map.AdsbTrafficPreferencesRepository
import com.example.xcpro.map.AdsbTrafficRepository
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.map.AdsbMetadataEnrichmentUseCase
import com.example.xcpro.map.AircraftMetadata
import com.example.xcpro.map.AircraftMetadataRepository
import com.example.xcpro.map.AircraftMetadataSyncRepository
import com.example.xcpro.map.AircraftMetadataSyncScheduler
import com.example.xcpro.map.MetadataSyncRunResult
import com.example.xcpro.map.MetadataSyncState
import com.example.xcpro.map.OgnConnectionState
import com.example.xcpro.map.OgnDisplayUpdateMode
import com.example.xcpro.map.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.map.OGN_ICON_SIZE_MAX_PX
import com.example.xcpro.map.OgnTrafficPreferencesRepository
import com.example.xcpro.map.OgnTrafficRepository
import com.example.xcpro.map.OgnTrafficSnapshot
import com.example.xcpro.map.OgnTrafficTarget
import com.example.xcpro.map.OgnThermalHotspot
import com.example.xcpro.map.OgnThermalHotspotState
import com.example.xcpro.map.OgnThermalRepository
import com.example.xcpro.map.OgnGliderTrailRepository
import com.example.xcpro.map.OgnGliderTrailSegment
import com.example.xcpro.map.OgnTrailSelectionPreferencesRepository
import com.example.xcpro.thermalling.ThermallingModeCoordinator
import com.example.xcpro.thermalling.ThermallingModePreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.mockito.Mockito
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenViewModelCoreStateTest : MapScreenViewModelTestBase() {

    @Test
    fun refreshWaypoints_success_updatesState() = runBlocking {
        val expected = listOf(
            WaypointData(
                name = "Test Field",
                code = "TEST",
                country = "NZ",
                latitude = -45.0,
                longitude = 170.0,
                elevation = "500m",
                style = 4,
                runwayDirection = "09/27",
                runwayLength = "1200m",
                frequency = "118.700",
                description = "Glider base"
            )
        )
        val loader = SuccessfulWaypointLoader(expected)

        val viewModel = createViewModel(waypointLoader = loader)

        drainMain()

        val state = viewModel.uiState.value
        assertEquals(expected, state.waypoints)
        assertTrue(state.isLoadingWaypoints.not())
        assertNull(state.waypointError)
    }

    @Test
    fun refreshWaypoints_failure_setsError() = runBlocking {
        val loader = FailingWaypointLoader(IllegalStateException("Failed to read waypoints"))

        val viewModel = createViewModel(waypointLoader = loader)

        drainMain()

        val state = viewModel.uiState.value
        assertTrue(state.waypoints.isEmpty())
        assertTrue(state.isLoadingWaypoints.not())
        val error = state.waypointError
        assertTrue(error is MapWaypointError.LoadFailed)
        assertEquals("Failed to read waypoints", (error as MapWaypointError.LoadFailed).recoveryHint)
    }

    @Test
    fun setMapStyle_emitsCommandAndUpdatesStore() = runBlocking {
        val viewModel = createViewModel()

        val nextStyle = if (viewModel.mapState.mapStyleName.value == "Satellite") "Topo" else "Satellite"
        val commandDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.mapCommands.first() }
        viewModel.setMapStyle(nextStyle)

        assertEquals(MapCommand.SetStyle(nextStyle), commandDeferred.await())
        assertEquals(nextStyle, viewModel.mapState.mapStyleName.value)
    }

    @Test
    fun setFlightMode_updatesStore() {
        val viewModel = createViewModel()

        viewModel.setFlightMode(com.example.xcpro.common.flight.FlightMode.THERMAL)

        assertEquals(com.example.xcpro.common.flight.FlightMode.THERMAL, viewModel.mapState.currentMode.value)
    }

    @Test
    fun toggleDistanceCircles_updatesStore() {
        val viewModel = createViewModel()

        assertTrue(viewModel.mapState.showDistanceCircles.value.not())

        viewModel.mapStateActions.toggleDistanceCircles()

        assertTrue(viewModel.mapState.showDistanceCircles.value)
    }

    @Test
    fun updateCurrentZoom_updatesStore() {
        val viewModel = createViewModel()

        viewModel.mapStateActions.updateCurrentZoom(14.5f)

        assertEquals(14.5f, viewModel.mapState.currentZoom.value)
    }

    @Test
    fun setTarget_updatesStore() {
        val viewModel = createViewModel()

        val target = MapPoint(1.23, 4.56)

        viewModel.mapStateActions.setTarget(target, 12.0f)

        assertEquals(target, viewModel.mapState.targetLatLng.value)
        assertEquals(12.0f, viewModel.mapState.targetZoom.value)
    }

    @Test
    fun saveLocation_updatesStore() {
        val viewModel = createViewModel()

        val location = MapPoint(10.0, -20.0)

        viewModel.mapStateActions.saveLocation(location, 9.0, 180.0)

        assertEquals(location, viewModel.mapState.savedLocation.value)
        assertEquals(9.0, viewModel.mapState.savedZoom.value)
        assertEquals(180.0, viewModel.mapState.savedBearing.value)
    }

    @Test
    fun adsbSelection_tracksSelectedIdFromCurrentTargetList() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(sampleAdsbTarget(id))
        drainMain()

        viewModel.onAdsbTargetSelected(id)
        drainMain()

        assertEquals(id, viewModel.selectedAdsbId.value)
        assertEquals(id, viewModel.selectedAdsbTarget.value?.id)
    }

    @Test
    fun adsbSelection_clearsWhenSelectedTargetDisappears() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(sampleAdsbTarget(id))
        drainMain()
        viewModel.onAdsbTargetSelected(id)
        drainMain()
        assertEquals(id, viewModel.selectedAdsbTarget.value?.id)

        adsbRepository.targets.value = emptyList()
        drainMain()

        assertNull(viewModel.selectedAdsbId.value)
        assertNull(viewModel.selectedAdsbTarget.value)
    }

    @Test
    fun selectedAdsbDetails_distanceRemainsOwnshipRelativeAndIndependentOfOgnTargets() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(
            adsbRepositoryOverride = adsbRepository,
            ognRepositoryOverride = ognRepository
        )
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(sampleAdsbTarget(id, distanceMeters = 4_321.0))
        ognRepository.targets.value = listOf(sampleOgnTarget("OGN123"))
        drainMain()

        viewModel.onAdsbTargetSelected(id)
        drainMain()
        assertEquals(4_321.0, viewModel.selectedAdsbTarget.value?.distanceMeters ?: Double.NaN, 1e-6)
        assertEquals(220.0, viewModel.selectedAdsbTarget.value?.bearingDegFromUser ?: Double.NaN, 1e-6)

        ognRepository.targets.value = listOf(
            sampleOgnTarget("OGN999").copy(
                latitude = -34.0,
                longitude = 151.0,
                altitudeMeters = 2_600.0
            )
        )
        drainMain()

        assertEquals(4_321.0, viewModel.selectedAdsbTarget.value?.distanceMeters ?: Double.NaN, 1e-6)
        assertEquals(220.0, viewModel.selectedAdsbTarget.value?.bearingDegFromUser ?: Double.NaN, 1e-6)
    }

    @Test
    fun setActiveProfileId_updatesUnitsPreferencesForProfileScope() = runBlocking {
        val viewModel = createViewModel()
        unitsRepository.writeProfileUnits(
            profileId = "pilot-a",
            preferences = UnitsPreferences(altitude = AltitudeUnit.FEET)
        )
        unitsRepository.writeProfileUnits(
            profileId = "pilot-b",
            preferences = UnitsPreferences(altitude = AltitudeUnit.METERS)
        )

        viewModel.setActiveProfileId("pilot-a")
        drainMain()
        assertEquals(AltitudeUnit.FEET, viewModel.unitsPreferencesFlow.value.altitude)

        viewModel.setActiveProfileId("pilot-b")
        drainMain()
        assertEquals(AltitudeUnit.METERS, viewModel.unitsPreferencesFlow.value.altitude)
    }

    @Test
    fun setActiveProfileId_routesMapStylePerProfileAndAppliesProfileStyle() = runBlocking {
        val viewModel = createViewModel()

        viewModel.setActiveProfileId("pilot-a")
        viewModel.persistMapStyle("Satellite")
        drainMain()

        viewModel.setActiveProfileId("pilot-b")
        viewModel.persistMapStyle("Terrain")
        drainMain()

        viewModel.setActiveProfileId("pilot-a")
        drainMain()
        assertEquals("Satellite", mapStyleRepository.readProfileStyle("pilot-a"))
        assertEquals("Satellite", viewModel.mapState.mapStyleName.value)

        viewModel.setActiveProfileId("pilot-b")
        drainMain()
        assertEquals("Terrain", mapStyleRepository.readProfileStyle("pilot-b"))
        assertEquals("Terrain", viewModel.mapState.mapStyleName.value)
    }

    @Test
    fun persistMapStyle_normalizesUnknownStyleToDefaultBaseStyle() = runBlocking {
        val viewModel = createViewModel()

        viewModel.setActiveProfileId("pilot-a")
        viewModel.persistMapStyle("Map4")
        drainMain()

        assertEquals(MapStyleCatalog.defaultSelectableKey(), mapStyleRepository.readProfileStyle("pilot-a"))
    }

    @Test
    fun setActiveProfileId_updatesTrailSettingsForProfileScope() = runBlocking {
        val viewModel = createViewModel()
        trailSettingsUseCase.writeProfileSettings(
            "pilot-a",
            TrailSettings(
                length = TrailLength.SHORT,
                type = TrailType.ALTITUDE,
                windDriftEnabled = false,
                scalingEnabled = false
            )
        )
        trailSettingsUseCase.writeProfileSettings(
            "pilot-b",
            TrailSettings(
                length = TrailLength.FULL,
                type = TrailType.VARIO_2,
                windDriftEnabled = true,
                scalingEnabled = true
            )
        )

        viewModel.setActiveProfileId("pilot-a")
        drainMain()
        assertEquals(TrailLength.SHORT, viewModel.trailSettings.value.length)
        assertEquals(TrailType.ALTITUDE, viewModel.trailSettings.value.type)

        viewModel.setActiveProfileId("pilot-b")
        drainMain()
        assertEquals(TrailLength.FULL, viewModel.trailSettings.value.length)
        assertEquals(TrailType.VARIO_2, viewModel.trailSettings.value.type)
    }

    @Test
    fun setActiveProfileId_routesQnhProfileScope() = runBlocking {
        val viewModel = createViewModel()

        viewModel.setActiveProfileId("pilot-a")
        drainMain()
        viewModel.setActiveProfileId("pilot-b")
        drainMain()

        assertEquals(listOf("pilot-a", "pilot-b"), qnhRepository.activeProfileIds)
        assertEquals("pilot-b", qnhRepository.activeProfileId)
    }

    @Test
    fun selectedAdsbDetails_carriesProximityTierTrendAndOwnshipReferenceSemantics() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        val id = Icao24.from("abc123") ?: error("invalid test id")

        adsbRepository.targets.value = listOf(
            sampleAdsbTarget(
                id = id,
                distanceMeters = 1_750.0,
                usesOwnshipReference = false,
                proximityTier = AdsbProximityTier.NEUTRAL,
                isClosing = false,
                closingRateMps = -0.4,
                isEmergencyCollisionRisk = false
            )
        )
        drainMain()

        viewModel.onAdsbTargetSelected(id)
        drainMain()

        val selected = viewModel.selectedAdsbTarget.value ?: error("selected details missing")
        assertEquals(false, selected.usesOwnshipReference)
        assertEquals(AdsbProximityTier.NEUTRAL, selected.proximityTier)
        assertEquals(false, selected.isClosing)
        assertEquals(-0.4, selected.closingRateMps ?: Double.NaN, 1e-6)
        assertEquals(false, selected.isEmergencyCollisionRisk)
    }

}
