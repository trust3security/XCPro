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
import com.example.xcpro.map.AdsbProximityTier
import com.example.xcpro.hawk.HawkVarioUiState
import com.example.xcpro.hawk.HawkVarioUseCase
import com.example.xcpro.map.AdsbConnectionState
import com.example.xcpro.map.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
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
import com.example.xcpro.ogn.OgnSciaStartupResetCoordinator
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
class MapScreenViewModelOverlayPreferenceTest : MapScreenViewModelTestBase() {

    @Test
    fun thermalSelection_clearsWhenHotspotDisappears() = runBlocking {
        val thermalRepository = FakeOgnThermalRepository()
        val viewModel = createViewModel(ognThermalRepositoryOverride = thermalRepository)
        val thermalId = "OGN123-thermal-1"

        thermalRepository.hotspots.value = listOf(sampleThermalHotspot(thermalId, "OGN123"))
        drainMain()
        viewModel.onOgnThermalSelected(thermalId)
        drainMain()
        assertEquals(thermalId, viewModel.selectedOgnThermal.value?.id)

        thermalRepository.hotspots.value = emptyList()
        drainMain()

        assertNull(viewModel.selectedOgnThermal.value)
    }

    @Test
    fun ognIconSize_defaultsToConfiguredDefaultPx() {
        val viewModel = createViewModel()

        assertEquals(OGN_ICON_SIZE_DEFAULT_PX, viewModel.ognIconSizePx.value)
    }

    @Test
    fun ognDisplayUpdateMode_defaultsToRealTime() {
        val viewModel = createViewModel()

        assertEquals(OgnDisplayUpdateMode.REAL_TIME, viewModel.ognDisplayUpdateMode.value)
    }

    @Test
    fun showOgnScia_defaultsToDisabled() {
        val viewModel = createViewModel()

        assertTrue(viewModel.showOgnSciaEnabled.value.not())
    }

    @Test
    fun onToggleOgnScia_enablingForcesOgnTrafficOn() = runBlocking {
        resetOgnTrafficTogglePreferences()
        val viewModel = createViewModel()
        awaitCondition { viewModel.showOgnSciaEnabled.value.not() }
        awaitCondition { viewModel.ognOverlayEnabled.value.not() }

        viewModel.onToggleOgnScia()
        awaitCondition { viewModel.showOgnSciaEnabled.value }
        awaitCondition { viewModel.ognOverlayEnabled.value }

        assertTrue(viewModel.showOgnSciaEnabled.value)
        assertTrue(viewModel.ognOverlayEnabled.value)
    }

    @Test
    fun onToggleOgnThermals_enablingForcesOgnTrafficOn() = runBlocking {
        resetOgnTrafficTogglePreferences()
        val viewModel = createViewModel()
        awaitCondition { viewModel.showOgnThermalsEnabled.value.not() }
        awaitCondition { viewModel.ognOverlayEnabled.value.not() }

        viewModel.onToggleOgnThermals()
        awaitCondition { viewModel.showOgnThermalsEnabled.value }
        awaitCondition { viewModel.ognOverlayEnabled.value }

        assertTrue(viewModel.showOgnThermalsEnabled.value)
        assertTrue(viewModel.ognOverlayEnabled.value)
    }

    @Test
    fun onToggleOgnTraffic_ignoredWhileSciaIsEnabled() = runBlocking {
        resetOgnTrafficTogglePreferences()
        val viewModel = createViewModel()
        awaitCondition { viewModel.showOgnSciaEnabled.value.not() }
        awaitCondition { viewModel.ognOverlayEnabled.value.not() }

        viewModel.onToggleOgnScia()
        awaitCondition { viewModel.showOgnSciaEnabled.value }
        awaitCondition { viewModel.ognOverlayEnabled.value }
        assertTrue(viewModel.showOgnSciaEnabled.value)
        assertTrue(viewModel.ognOverlayEnabled.value)

        viewModel.onToggleOgnTraffic()
        drainMain()

        assertTrue(viewModel.ognOverlayEnabled.value)
    }

    @Test
    fun ognIconSize_readsPersistedPreferenceOnInit() = runBlocking {
        val preferencesRepository = OgnTrafficPreferencesRepository(
            newTestPreferencesDataStore("ogn_traffic_seed"),
            OgnSciaStartupResetCoordinator.AlreadyReset
        )
        preferencesRepository.setIconSizePx(OGN_ICON_SIZE_MAX_PX)

        val viewModel = createViewModel(
            ognPreferencesRepositoryOverride = preferencesRepository
        )
        drainMain()

        assertEquals(OGN_ICON_SIZE_MAX_PX, viewModel.ognIconSizePx.value)
    }

    @Test
    fun ognDisplayUpdateMode_readsPersistedPreferenceOnInit() = runBlocking {
        val preferencesRepository = OgnTrafficPreferencesRepository(
            newTestPreferencesDataStore("ogn_traffic_seed"),
            OgnSciaStartupResetCoordinator.AlreadyReset
        )
        preferencesRepository.setDisplayUpdateMode(OgnDisplayUpdateMode.BATTERY)

        val viewModel = createViewModel(
            ognPreferencesRepositoryOverride = preferencesRepository
        )
        drainMain()

        assertEquals(OgnDisplayUpdateMode.BATTERY, viewModel.ognDisplayUpdateMode.value)
    }

    @Test
    fun adsbIconSize_defaultsToConfiguredDefaultPx() {
        val viewModel = createViewModel()

        assertEquals(ADSB_ICON_SIZE_DEFAULT_PX, viewModel.adsbIconSizePx.value)
    }

    @Test
    fun adsbIconSize_readsPersistedPreferenceOnInit() = runBlocking {
        val preferencesRepository = AdsbTrafficPreferencesRepository(
            newTestPreferencesDataStore("adsb_traffic_seed")
        )
        preferencesRepository.setIconSizePx(ADSB_ICON_SIZE_MAX_PX)

        val viewModel = createViewModel(
            adsbPreferencesRepositoryOverride = preferencesRepository
        )
        drainMain()

        assertEquals(ADSB_ICON_SIZE_MAX_PX, viewModel.adsbIconSizePx.value)
    }

    @Test
    fun adsbEmergencyFlash_defaultsToEnabled() {
        val viewModel = createViewModel()

        assertEquals(ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT, viewModel.adsbEmergencyFlashEnabled.value)
    }

    @Test
    fun adsbEmergencyFlash_readsPersistedPreferenceOnInit() = runBlocking {
        val preferencesRepository = AdsbTrafficPreferencesRepository(
            newTestPreferencesDataStore("adsb_traffic_seed_flash")
        )
        preferencesRepository.setEmergencyFlashEnabled(false)

        val viewModel = createViewModel(
            adsbPreferencesRepositoryOverride = preferencesRepository
        )
        drainMain()

        assertEquals(false, viewModel.adsbEmergencyFlashEnabled.value)
    }

}
