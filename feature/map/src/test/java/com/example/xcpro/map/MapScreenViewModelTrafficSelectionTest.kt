package com.example.xcpro.map

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.MapOrientationManagerFactory
import com.example.xcpro.MapOrientationSettingsRepository
import com.example.xcpro.OrientationDataSourceFactory
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.glider.GliderConfig
import com.example.xcpro.common.glider.GliderModel
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
import com.example.xcpro.adsb.AdsbProximityTier
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.hawk.HawkVarioUseCase
import com.example.xcpro.adsb.AdsbConnectionState
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.ADSB_ICON_SIZE_MAX_PX
import com.example.xcpro.adsb.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.example.xcpro.adsb.AdsbTrafficPreferencesRepository
import com.example.xcpro.adsb.AdsbTrafficRepository
import com.example.xcpro.adsb.AdsbTrafficSnapshot
import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.metadata.domain.AdsbMetadataEnrichmentUseCase
import com.example.xcpro.adsb.metadata.domain.AircraftMetadata
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncScheduler
import com.example.xcpro.adsb.metadata.domain.MetadataSyncRunResult
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import com.example.xcpro.ogn.OgnConnectionState
import com.example.xcpro.ogn.OgnDisplayUpdateMode
import com.example.xcpro.ogn.OGN_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.ogn.OGN_ICON_SIZE_MAX_PX
import com.example.xcpro.ogn.OgnTrafficPreferencesRepository
import com.example.xcpro.ogn.OgnTrafficRepository
import com.example.xcpro.ogn.OgnTrafficSnapshot
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnThermalHotspot
import com.example.xcpro.ogn.OgnThermalHotspotState
import com.example.xcpro.ogn.OgnThermalRepository
import com.example.xcpro.ogn.OgnGliderTrailRepository
import com.example.xcpro.ogn.OgnGliderTrailSegment
import com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepository
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
class MapScreenViewModelTrafficSelectionTest : MapScreenViewModelTestBase() {

    @Test
    fun onToggleAdsbTraffic_seedsCenterFromCameraSnapshotWhenGpsUnavailable() {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        ensureAdsbOverlayDisabled(viewModel)
        awaitCondition { viewModel.adsbOverlayEnabled.value.not() }

        viewModel.mapStateActions.updateCameraSnapshot(
            target = MapStateStore.MapPoint(latitude = -35.1234, longitude = 149.1234),
            zoom = 11.0,
            bearing = 0.0
        )
        drainMain()

        viewModel.onToggleAdsbTraffic()
        drainMain()

        assertEquals(-35.1234, adsbRepository.lastCenterLat ?: Double.NaN, 1e-6)
        assertEquals(149.1234, adsbRepository.lastCenterLon ?: Double.NaN, 1e-6)
    }

    @Test
    fun adsbCenter_updatesFromOwnshipGpsLocation() {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        ensureAdsbOverlayEnabled(viewModel)
        drainMain()

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(
                    latitude = -34.5000,
                    longitude = 150.5000,
                    speedMs = 12.0,
                    bearingDeg = 87.0
                )
            )
        )
        drainMain()

        assertEquals(-34.5000, adsbRepository.lastCenterLat ?: Double.NaN, 1e-6)
        assertEquals(150.5000, adsbRepository.lastCenterLon ?: Double.NaN, 1e-6)
        assertEquals(-34.5000, adsbRepository.lastOwnshipLat ?: Double.NaN, 1e-6)
        assertEquals(150.5000, adsbRepository.lastOwnshipLon ?: Double.NaN, 1e-6)
        assertEquals(87.0, adsbRepository.lastOwnshipTrackDeg ?: Double.NaN, 1e-6)
        assertEquals(12.0, adsbRepository.lastOwnshipSpeedMps ?: Double.NaN, 1e-6)
    }

    @Test
    fun adsbOwnshipReference_refreshesWhenStationaryFixRepeatsSameCoordinates() {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        ensureAdsbOverlayEnabled(viewModel)
        drainMain()

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(
                    latitude = -34.5000,
                    longitude = 150.5000,
                    timestampMillis = 1_000L
                )
            )
        )
        drainMain()
        val callsAfterFirstFix = adsbRepository.updateOwnshipOriginCalls

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(
                    latitude = -34.5000,
                    longitude = 150.5000,
                    timestampMillis = 2_000L
                )
            )
        )
        drainMain()

        assertEquals(callsAfterFirstFix + 1, adsbRepository.updateOwnshipOriginCalls)
    }

    @Test
    fun adsbOwnshipReference_clearsWhenGpsBecomesUnavailable() {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        ensureAdsbOverlayEnabled(viewModel)
        drainMain()
        val initialClearCalls = adsbRepository.clearOwnshipOriginCalls

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(latitude = -34.5000, longitude = 150.5000)
            )
        )
        drainMain()
        assertEquals(-34.5000, adsbRepository.lastOwnshipLat ?: Double.NaN, 1e-6)
        assertEquals(150.5000, adsbRepository.lastOwnshipLon ?: Double.NaN, 1e-6)

        flightDataRepository.update(buildCompleteFlightData(gps = null))
        drainMain()

        assertEquals(initialClearCalls + 1, adsbRepository.clearOwnshipOriginCalls)
        assertNull(adsbRepository.lastOwnshipLat)
        assertNull(adsbRepository.lastOwnshipLon)
        assertNull(adsbRepository.lastOwnshipTrackDeg)
        assertNull(adsbRepository.lastOwnshipSpeedMps)
    }

    @Test
    fun adsbOwnshipMotion_dropsTrackWhenGroundSpeedBelowTrackThreshold() {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        ensureAdsbOverlayEnabled(viewModel)
        drainMain()

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(
                    latitude = -34.5000,
                    longitude = 150.5000,
                    speedMs = 1.2,
                    bearingDeg = 132.0
                )
            )
        )
        drainMain()

        assertNull(adsbRepository.lastOwnshipTrackDeg)
        assertEquals(1.2, adsbRepository.lastOwnshipSpeedMps ?: Double.NaN, 1e-6)
    }

    @Test
    fun adsbOwnshipMotion_dropsMotionWhenSpeedAccuracyIsPoor() {
        val adsbRepository = FakeAdsbTrafficRepository()
        val viewModel = createViewModel(adsbRepositoryOverride = adsbRepository)
        ensureAdsbOverlayEnabled(viewModel)
        drainMain()

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(
                    latitude = -34.5000,
                    longitude = 150.5000,
                    speedMs = 15.0,
                    bearingDeg = 145.0
                ).copy(speedAccuracyMs = 20.0)
            )
        )
        drainMain()

        assertNull(adsbRepository.lastOwnshipTrackDeg)
        assertNull(adsbRepository.lastOwnshipSpeedMps)
    }

    @Test
    fun ognCenter_updatesFromOwnshipGpsLocation() {
        val ognRepository = FakeOgnTrafficRepository()
        createViewModel(ognRepositoryOverride = ognRepository)
        drainMain()

        flightDataRepository.update(
            buildCompleteFlightData(
                gps = defaultGps(latitude = -34.5000, longitude = 150.5000)
            )
        )
        drainMain()

        assertEquals(-34.5000, ognRepository.lastCenterLat ?: Double.NaN, 1e-6)
        assertEquals(150.5000, ognRepository.lastCenterLon ?: Double.NaN, 1e-6)
    }

    @Test
    fun ognCenter_doesNotUpdateFromCameraSnapshotWithoutGps() {
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(ognRepositoryOverride = ognRepository)

        viewModel.mapStateActions.updateCameraSnapshot(
            target = MapStateStore.MapPoint(latitude = -35.1234, longitude = 149.1234),
            zoom = 11.0,
            bearing = 0.0
        )
        drainMain()

        assertNull(ognRepository.lastCenterLat)
        assertNull(ognRepository.lastCenterLon)
    }

    @Test
    fun ognSelection_tracksSelectedTargetFromCurrentTargetList() = runBlocking {
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(ognRepositoryOverride = ognRepository)
        val id = "OGN123"

        ognRepository.targets.value = listOf(sampleOgnTarget(id))
        drainMain()

        viewModel.onOgnTargetSelected(id)
        drainMain()

        assertEquals(id, viewModel.selectedOgnTarget.value?.id)
    }

    @Test
    fun ognSelection_clearsWhenSelectedTargetDisappears() = runBlocking {
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(ognRepositoryOverride = ognRepository)
        val id = "OGN123"

        ognRepository.targets.value = listOf(sampleOgnTarget(id))
        drainMain()
        viewModel.onOgnTargetSelected(id)
        drainMain()
        assertEquals(id, viewModel.selectedOgnTarget.value?.id)

        ognRepository.targets.value = emptyList()
        drainMain()

        assertNull(viewModel.selectedOgnTarget.value)
    }

    @Test
    fun selectingOgnTarget_clearsSelectedAdsbTarget() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(
            adsbRepositoryOverride = adsbRepository,
            ognRepositoryOverride = ognRepository
        )
        val adsbId = Icao24.from("abc123") ?: error("invalid adsb id")
        val ognId = "OGN123"

        adsbRepository.targets.value = listOf(sampleAdsbTarget(adsbId))
        ognRepository.targets.value = listOf(sampleOgnTarget(ognId))
        drainMain()
        viewModel.onAdsbTargetSelected(adsbId)
        drainMain()
        assertEquals(adsbId, viewModel.selectedAdsbTarget.value?.id)

        viewModel.onOgnTargetSelected(ognId)
        drainMain()

        assertEquals(ognId, viewModel.selectedOgnTarget.value?.id)
        assertNull(viewModel.selectedAdsbTarget.value)
    }

    @Test
    fun selectingAdsbTarget_clearsSelectedOgnTarget() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(
            adsbRepositoryOverride = adsbRepository,
            ognRepositoryOverride = ognRepository
        )
        val adsbId = Icao24.from("abc123") ?: error("invalid adsb id")
        val ognId = "OGN123"

        adsbRepository.targets.value = listOf(sampleAdsbTarget(adsbId))
        ognRepository.targets.value = listOf(sampleOgnTarget(ognId))
        drainMain()
        viewModel.onOgnTargetSelected(ognId)
        drainMain()
        assertEquals(ognId, viewModel.selectedOgnTarget.value?.id)

        viewModel.onAdsbTargetSelected(adsbId)
        drainMain()

        assertEquals(adsbId, viewModel.selectedAdsbTarget.value?.id)
        assertNull(viewModel.selectedOgnTarget.value)
    }

    @Test
    fun dismissSelectedOgnTarget_clearsSelectionState() = runBlocking {
        val ognRepository = FakeOgnTrafficRepository()
        val viewModel = createViewModel(ognRepositoryOverride = ognRepository)
        val id = "OGN123"

        ognRepository.targets.value = listOf(sampleOgnTarget(id))
        drainMain()
        viewModel.onOgnTargetSelected(id)
        drainMain()
        assertEquals(id, viewModel.selectedOgnTarget.value?.id)

        viewModel.dismissSelectedOgnTarget()
        drainMain()

        assertNull(viewModel.selectedOgnTarget.value)
    }

    @Test
    fun selectingThermal_clearsSelectedOgnAndAdsbTargets() = runBlocking {
        val adsbRepository = FakeAdsbTrafficRepository()
        val ognRepository = FakeOgnTrafficRepository()
        val thermalRepository = FakeOgnThermalRepository()
        val viewModel = createViewModel(
            adsbRepositoryOverride = adsbRepository,
            ognRepositoryOverride = ognRepository,
            ognThermalRepositoryOverride = thermalRepository
        )
        val adsbId = Icao24.from("abc123") ?: error("invalid adsb id")
        val ognId = "OGN123"
        val thermalId = "OGN123-thermal-1"

        adsbRepository.targets.value = listOf(sampleAdsbTarget(adsbId))
        ognRepository.targets.value = listOf(sampleOgnTarget(ognId))
        thermalRepository.hotspots.value = listOf(sampleThermalHotspot(thermalId, ognId))
        drainMain()

        viewModel.onAdsbTargetSelected(adsbId)
        drainMain()
        assertEquals(adsbId, viewModel.selectedAdsbTarget.value?.id)

        viewModel.onOgnTargetSelected(ognId)
        drainMain()
        assertEquals(ognId, viewModel.selectedOgnTarget.value?.id)
        assertNull(viewModel.selectedAdsbTarget.value)

        viewModel.onOgnThermalSelected(thermalId)
        drainMain()

        assertEquals(thermalId, viewModel.selectedOgnThermal.value?.id)
        assertNull(viewModel.selectedOgnTarget.value)
        assertNull(viewModel.selectedAdsbTarget.value)
    }

}
