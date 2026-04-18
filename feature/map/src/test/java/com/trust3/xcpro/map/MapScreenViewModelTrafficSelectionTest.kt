package com.trust3.xcpro.map

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.trust3.xcpro.core.flight.calculations.ConfidenceLevel
import com.trust3.xcpro.MapOrientationManagerFactory
import com.trust3.xcpro.MapOrientationSettingsRepository
import com.trust3.xcpro.OrientationDataSourceFactory
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.PressureHpa
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.glider.GliderConfig
import com.trust3.xcpro.common.glider.GliderModel
import com.trust3.xcpro.common.units.VerticalSpeedMs
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.common.waypoint.WaypointLoader
import com.trust3.xcpro.common.units.UnitsRepository
import com.trust3.xcpro.glider.GliderRepository
import com.trust3.xcpro.qnh.CalibrateQnhUseCase
import com.trust3.xcpro.qnh.QnhCalibrationState
import com.trust3.xcpro.qnh.QnhRepository
import com.trust3.xcpro.qnh.QnhValue
import com.trust3.xcpro.qnh.QnhSource
import com.trust3.xcpro.qnh.QnhConfidence
import com.trust3.xcpro.sensors.AttitudeData
import com.trust3.xcpro.sensors.CompassData
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.FlightStateSource
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.sensors.GpsStatus
import com.trust3.xcpro.sensors.SensorStatus
import com.trust3.xcpro.sensors.UnifiedSensorManager
import com.trust3.xcpro.sensors.domain.FlyingState
import com.trust3.xcpro.vario.VarioServiceManager
import com.trust3.xcpro.vario.LevoVarioPreferencesRepository
import com.trust3.xcpro.weather.wind.data.WindSensorFusionRepository
import com.trust3.xcpro.flightdata.FlightDataRepository
import com.trust3.xcpro.testing.MainDispatcherRule
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import com.trust3.xcpro.map.domain.MapWaypointError
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.replay.RacingReplayLogBuilder
import com.trust3.xcpro.airspace.AirspaceUseCase
import com.trust3.xcpro.flightdata.WaypointFilesUseCase
import com.trust3.xcpro.orientation.HeadingResolver
import com.trust3.xcpro.orientation.OrientationClock
import com.trust3.xcpro.replay.IgcReplayController
import com.trust3.xcpro.replay.ReplayEvent
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.map.trail.MapTrailPreferences
import com.trust3.xcpro.map.trail.MapTrailSettingsUseCase
import com.trust3.xcpro.tasks.TaskFeatureFlags
import com.trust3.xcpro.tasks.TaskNavigationController
import com.trust3.xcpro.tasks.aat.AATTaskManager
import com.trust3.xcpro.tasks.racing.RacingTaskManager
import com.trust3.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import com.trust3.xcpro.variometer.layout.VariometerLayoutUseCase
import com.trust3.xcpro.variometer.layout.VariometerWidgetRepository
import com.trust3.xcpro.map.ballast.BallastControllerFactory
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.ConfigurationRepository
import com.trust3.xcpro.map.AdsbProximityTier
import com.trust3.xcpro.hawk.HawkVarioUiState
import com.trust3.xcpro.hawk.HawkVarioUseCase
import com.trust3.xcpro.map.AdsbConnectionState
import com.trust3.xcpro.map.ADSB_ICON_SIZE_DEFAULT_PX
import com.trust3.xcpro.map.ADSB_ICON_SIZE_MAX_PX
import com.trust3.xcpro.map.ADSB_MAX_DISTANCE_DEFAULT_KM
import com.trust3.xcpro.map.ADSB_VERTICAL_FILTER_ABOVE_DEFAULT_METERS
import com.trust3.xcpro.map.ADSB_VERTICAL_FILTER_BELOW_DEFAULT_METERS
import com.trust3.xcpro.map.AdsbTrafficPreferencesRepository
import com.trust3.xcpro.map.AdsbTrafficRepository
import com.trust3.xcpro.map.AdsbTrafficSnapshot
import com.trust3.xcpro.map.AdsbTrafficUiModel
import com.trust3.xcpro.map.Icao24
import com.trust3.xcpro.map.AdsbMetadataEnrichmentUseCase
import com.trust3.xcpro.map.AircraftMetadata
import com.trust3.xcpro.map.AircraftMetadataRepository
import com.trust3.xcpro.map.AircraftMetadataSyncRepository
import com.trust3.xcpro.map.AircraftMetadataSyncScheduler
import com.trust3.xcpro.map.MetadataSyncRunResult
import com.trust3.xcpro.map.MetadataSyncState
import com.trust3.xcpro.map.OgnConnectionState
import com.trust3.xcpro.map.OgnDisplayUpdateMode
import com.trust3.xcpro.map.OGN_ICON_SIZE_DEFAULT_PX
import com.trust3.xcpro.map.OGN_ICON_SIZE_MAX_PX
import com.trust3.xcpro.map.OgnTrafficPreferencesRepository
import com.trust3.xcpro.map.OgnTrafficRepository
import com.trust3.xcpro.map.OgnTrafficSnapshot
import com.trust3.xcpro.map.OgnTrafficTarget
import com.trust3.xcpro.map.OgnThermalHotspot
import com.trust3.xcpro.map.OgnThermalHotspotState
import com.trust3.xcpro.map.OgnThermalRepository
import com.trust3.xcpro.map.OgnGliderTrailRepository
import com.trust3.xcpro.map.OgnGliderTrailSegment
import com.trust3.xcpro.map.OgnTrailSelectionPreferencesRepository
import com.trust3.xcpro.thermalling.ThermallingModeCoordinator
import com.trust3.xcpro.thermalling.ThermallingModePreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            target = MapPoint(latitude = -35.1234, longitude = 149.1234),
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
            target = MapPoint(latitude = -35.1234, longitude = 149.1234),
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
    fun setMapVisible_falseClearsSelectedTrafficDetails() = runBlocking {
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

        viewModel.setMapVisible(true)
        viewModel.onOgnTargetSelected(ognId)
        drainMain()
        assertEquals(ognId, viewModel.selectedOgnTarget.value?.id)

        viewModel.setMapVisible(false)
        drainMain()
        assertNull(viewModel.selectedOgnTarget.value)

        viewModel.setMapVisible(true)
        viewModel.onOgnThermalSelected(thermalId)
        drainMain()
        assertEquals(thermalId, viewModel.selectedOgnThermal.value?.id)
        assertTrue(viewModel.selectedOgnThermalDetailsVisible.value)

        viewModel.setMapVisible(false)
        drainMain()
        assertNull(viewModel.selectedOgnThermal.value)
        assertFalse(viewModel.selectedOgnThermalDetailsVisible.value)

        viewModel.setMapVisible(true)
        viewModel.onAdsbTargetSelected(adsbId)
        drainMain()
        assertEquals(adsbId, viewModel.selectedAdsbTarget.value?.id)

        viewModel.setMapVisible(false)
        drainMain()
        assertNull(viewModel.selectedAdsbTarget.value)
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
        assertTrue(viewModel.selectedOgnThermalDetailsVisible.value)
        assertNull(viewModel.selectedOgnTarget.value)
        assertNull(viewModel.selectedAdsbTarget.value)
    }

    @Test
    fun dismissSelectedOgnThermal_hidesDetailsWithoutClearingSelectedThermal() = runBlocking {
        val thermalRepository = FakeOgnThermalRepository()
        val viewModel = createViewModel(ognThermalRepositoryOverride = thermalRepository)
        val thermalId = "OGN123-thermal-1"

        thermalRepository.hotspots.value = listOf(sampleThermalHotspot(thermalId, "OGN123"))
        drainMain()

        viewModel.onOgnThermalSelected(thermalId)
        drainMain()
        assertEquals(thermalId, viewModel.selectedOgnThermal.value?.id)
        assertTrue(viewModel.selectedOgnThermalDetailsVisible.value)

        viewModel.dismissSelectedOgnThermal()
        drainMain()

        assertEquals(thermalId, viewModel.selectedOgnThermal.value?.id)
        assertFalse(viewModel.selectedOgnThermalDetailsVisible.value)
    }

    @Test
    fun selectingAnotherThermal_replacesSelectionAndReopensDetails() = runBlocking {
        val thermalRepository = FakeOgnThermalRepository()
        val viewModel = createViewModel(ognThermalRepositoryOverride = thermalRepository)
        val firstThermalId = "OGN123-thermal-1"
        val secondThermalId = "OGN123-thermal-2"

        thermalRepository.hotspots.value = listOf(
            sampleThermalHotspot(firstThermalId, "OGN123"),
            sampleThermalHotspot(secondThermalId, "OGN123")
        )
        drainMain()

        viewModel.onOgnThermalSelected(firstThermalId)
        drainMain()
        viewModel.dismissSelectedOgnThermal()
        drainMain()
        assertEquals(firstThermalId, viewModel.selectedOgnThermal.value?.id)
        assertFalse(viewModel.selectedOgnThermalDetailsVisible.value)

        viewModel.onOgnThermalSelected(secondThermalId)
        drainMain()

        assertEquals(secondThermalId, viewModel.selectedOgnThermal.value?.id)
        assertTrue(viewModel.selectedOgnThermalDetailsVisible.value)
    }

}
