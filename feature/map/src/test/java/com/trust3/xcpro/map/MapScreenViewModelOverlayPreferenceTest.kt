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
import com.trust3.xcpro.map.ADSB_EMERGENCY_FLASH_ENABLED_DEFAULT
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
import com.trust3.xcpro.ogn.OgnSciaStartupResetCoordinator
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
        assertTrue(viewModel.selectedOgnThermalDetailsVisible.value)

        thermalRepository.hotspots.value = emptyList()
        drainMain()

        assertNull(viewModel.selectedOgnThermal.value)
        assertFalse(viewModel.selectedOgnThermalDetailsVisible.value)
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
