package com.trust3.xcpro.map

import com.trust3.xcpro.core.flight.calculations.ConfidenceLevel
import com.trust3.xcpro.common.flight.FlightMode
import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.PressureHpa
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.units.VerticalSpeedMs
import com.trust3.xcpro.core.time.FakeClock
import com.trust3.xcpro.sensors.CompleteFlightData
import com.trust3.xcpro.sensors.GPSData
import com.trust3.xcpro.thermalling.ThermallingModeAction
import com.trust3.xcpro.thermalling.ThermallingModeCoordinator
import com.trust3.xcpro.thermalling.ThermallingModeInput
import com.trust3.xcpro.thermalling.ThermallingModeSettings
import com.trust3.xcpro.thermalling.ThermallingModeState
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThermallingModeRuntimeWiringTest {

    @Test
    fun enterAndExit_applyModeAndZoomActions() = runTest {
        val controller = TestRuntimeController()
        val settings = MutableStateFlow(
            ThermallingModeSettings(
                enabled = true,
                enterDelaySeconds = 0,
                exitDelaySeconds = 0,
                thermalZoomLevel = 13.0f
            )
        )
        val flightData = MutableStateFlow<CompleteFlightData?>(flightSample(isCircling = false))
        val thermalModeVisible = MutableStateFlow(true)
        val replayActive = MutableStateFlow(false)
        val currentMode = MutableStateFlow(FlightMode.CRUISE)
        val currentZoom = MutableStateFlow(10f)
        val currentBaseStyle = MutableStateFlow(MapStyleCatalog.TOPO)
        val runtimeOverrideActions = mutableListOf<FlightMode>()
        var clearRuntimeOverrideCalls = 0
        val zoomActions = mutableListOf<Float>()
        val contrastMapActions = mutableListOf<Boolean>()

        val wiring = ThermallingModeRuntimeWiring(
            scope = this,
            controller = controller,
            settings = settings,
            flightData = flightData,
            thermalModeVisible = thermalModeVisible,
            replayActive = replayActive,
            currentMode = currentMode,
            currentZoom = currentZoom,
            currentBaseStyle = currentBaseStyle,
            applyRuntimeFlightMode = { mode ->
                runtimeOverrideActions += mode
                currentMode.value = mode
            },
            clearRuntimeFlightModeOverride = {
                clearRuntimeOverrideCalls += 1
                currentMode.value = FlightMode.CRUISE
            },
            applyZoom = { zoom ->
                zoomActions += zoom
                currentZoom.value = zoom
            },
            applyContrastMap = { enabled -> contrastMapActions += enabled }
        )
        try {
            wiring.bind()
            advanceUntilIdle()

            flightData.value = flightSample(isCircling = true)
            advanceUntilIdle()

            assertEquals(listOf(FlightMode.THERMAL), runtimeOverrideActions)
            assertEquals(listOf(13.0f), zoomActions)

            flightData.value = flightSample(isCircling = false)
            advanceUntilIdle()

            assertEquals(listOf(FlightMode.THERMAL), runtimeOverrideActions)
            assertEquals(1, clearRuntimeOverrideCalls)
            assertEquals(listOf(13.0f, 10.0f), zoomActions)
            assertTrue(contrastMapActions.isEmpty())
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun manualZoom_isForwardedOnlyWhenSessionActive() = runTest {
        val controller = TestRuntimeController()
        val settings = MutableStateFlow(
            ThermallingModeSettings(
                enabled = true,
                enterDelaySeconds = 0,
                exitDelaySeconds = 8,
                rememberManualThermalZoomInSession = true
            )
        )
        val flightData = MutableStateFlow<CompleteFlightData?>(flightSample(isCircling = false))
        val thermalModeVisible = MutableStateFlow(true)
        val replayActive = MutableStateFlow(false)
        val currentMode = MutableStateFlow(FlightMode.CRUISE)
        val currentZoom = MutableStateFlow(10f)
        val currentBaseStyle = MutableStateFlow(MapStyleCatalog.TOPO)

        val wiring = ThermallingModeRuntimeWiring(
            scope = this,
            controller = controller,
            settings = settings,
            flightData = flightData,
            thermalModeVisible = thermalModeVisible,
            replayActive = replayActive,
            currentMode = currentMode,
            currentZoom = currentZoom,
            currentBaseStyle = currentBaseStyle,
            applyRuntimeFlightMode = { mode -> currentMode.value = mode },
            clearRuntimeFlightModeOverride = { currentMode.value = FlightMode.CRUISE },
            applyZoom = { zoom -> currentZoom.value = zoom },
            applyContrastMap = {}
        )
        try {
            wiring.bind()
            advanceUntilIdle()

            currentZoom.value = 11.0f
            advanceUntilIdle()
            assertTrue(controller.userZoomEvents.isEmpty())

            flightData.value = flightSample(isCircling = true)
            advanceUntilIdle()
            controller.userZoomEvents.clear()

            currentZoom.value = 14.6f
            advanceUntilIdle()

            assertEquals(listOf(14.6f), controller.userZoomEvents)
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun bind_isIdempotent_actionsNotDuplicated() = runTest {
        val controller = TestRuntimeController()
        val settings = MutableStateFlow(
            ThermallingModeSettings(
                enabled = true,
                enterDelaySeconds = 0,
                exitDelaySeconds = 8
            )
        )
        val flightData = MutableStateFlow<CompleteFlightData?>(flightSample(isCircling = false))
        val thermalModeVisible = MutableStateFlow(true)
        val replayActive = MutableStateFlow(false)
        val currentMode = MutableStateFlow(FlightMode.CRUISE)
        val currentZoom = MutableStateFlow(10f)
        val currentBaseStyle = MutableStateFlow(MapStyleCatalog.TOPO)
        val runtimeOverrideActions = mutableListOf<FlightMode>()
        var clearRuntimeOverrideCalls = 0

        val wiring = ThermallingModeRuntimeWiring(
            scope = this,
            controller = controller,
            settings = settings,
            flightData = flightData,
            thermalModeVisible = thermalModeVisible,
            replayActive = replayActive,
            currentMode = currentMode,
            currentZoom = currentZoom,
            currentBaseStyle = currentBaseStyle,
            applyRuntimeFlightMode = { mode ->
                runtimeOverrideActions += mode
                currentMode.value = mode
            },
            clearRuntimeFlightModeOverride = {
                clearRuntimeOverrideCalls += 1
                currentMode.value = FlightMode.CRUISE
            },
            applyZoom = { zoom -> currentZoom.value = zoom },
            applyContrastMap = {}
        )
        try {
            wiring.bind()
            wiring.bind()
            advanceUntilIdle()

            flightData.value = flightSample(isCircling = true)
            advanceUntilIdle()

            assertEquals(1, runtimeOverrideActions.size)
            assertEquals(FlightMode.THERMAL, runtimeOverrideActions.first())
            assertEquals(0, clearRuntimeOverrideCalls)
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun replayActive_resetsThermallingAndClearsContrastOverride() = runTest {
        val controller = TestRuntimeController()
        val settings = MutableStateFlow(
            ThermallingModeSettings(
                enabled = true,
                enterDelaySeconds = 0,
                exitDelaySeconds = 0,
                applyContrastMapOnEnter = true
            )
        )
        val flightData = MutableStateFlow<CompleteFlightData?>(flightSample(isCircling = false))
        val thermalModeVisible = MutableStateFlow(true)
        val replayActive = MutableStateFlow(false)
        val currentMode = MutableStateFlow(FlightMode.CRUISE)
        val currentZoom = MutableStateFlow(10f)
        val currentBaseStyle = MutableStateFlow(MapStyleCatalog.TOPO)
        val contrastMapActions = mutableListOf<Boolean>()
        var clearRuntimeOverrideCalls = 0

        val wiring = ThermallingModeRuntimeWiring(
            scope = this,
            controller = controller,
            settings = settings,
            flightData = flightData,
            thermalModeVisible = thermalModeVisible,
            replayActive = replayActive,
            currentMode = currentMode,
            currentZoom = currentZoom,
            currentBaseStyle = currentBaseStyle,
            applyRuntimeFlightMode = { mode -> currentMode.value = mode },
            clearRuntimeFlightModeOverride = {
                clearRuntimeOverrideCalls += 1
                currentMode.value = FlightMode.CRUISE
            },
            applyZoom = { zoom -> currentZoom.value = zoom },
            applyContrastMap = { enabled -> contrastMapActions += enabled }
        )
        try {
            wiring.bind()
            advanceUntilIdle()

            flightData.value = flightSample(isCircling = true)
            advanceUntilIdle()
            assertEquals(listOf(true), contrastMapActions)

            replayActive.value = true
            advanceUntilIdle()

            assertEquals(listOf(true, false), contrastMapActions)
            assertEquals(1, clearRuntimeOverrideCalls)
            assertEquals(ThermallingModeState(), controller.state())
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun satelliteBaseStyle_blocksContrastEnableAction() = runTest {
        val controller = TestRuntimeController()
        val settings = MutableStateFlow(
            ThermallingModeSettings(
                enabled = true,
                enterDelaySeconds = 0,
                exitDelaySeconds = 0,
                applyContrastMapOnEnter = true
            )
        )
        val flightData = MutableStateFlow<CompleteFlightData?>(flightSample(isCircling = false))
        val thermalModeVisible = MutableStateFlow(true)
        val replayActive = MutableStateFlow(false)
        val currentMode = MutableStateFlow(FlightMode.CRUISE)
        val currentZoom = MutableStateFlow(10f)
        val currentBaseStyle = MutableStateFlow(MapStyleCatalog.SATELLITE)
        val contrastMapActions = mutableListOf<Boolean>()

        val wiring = ThermallingModeRuntimeWiring(
            scope = this,
            controller = controller,
            settings = settings,
            flightData = flightData,
            thermalModeVisible = thermalModeVisible,
            replayActive = replayActive,
            currentMode = currentMode,
            currentZoom = currentZoom,
            currentBaseStyle = currentBaseStyle,
            applyRuntimeFlightMode = { mode -> currentMode.value = mode },
            clearRuntimeFlightModeOverride = { currentMode.value = FlightMode.CRUISE },
            applyZoom = { zoom -> currentZoom.value = zoom },
            applyContrastMap = { enabled -> contrastMapActions += enabled }
        )
        try {
            wiring.bind()
            advanceUntilIdle()

            flightData.value = flightSample(isCircling = true)
            advanceUntilIdle()

            assertTrue(contrastMapActions.isEmpty())
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    @Test
    fun hiddenThermalMode_clearsRuntimeOverrideWithoutMutatingRequestedMode() = runTest {
        val controller = TestRuntimeController()
        val settings = MutableStateFlow(
            ThermallingModeSettings(
                enabled = true,
                enterDelaySeconds = 0,
                exitDelaySeconds = 0
            )
        )
        val flightData = MutableStateFlow<CompleteFlightData?>(flightSample(isCircling = true))
        val thermalModeVisible = MutableStateFlow(true)
        val replayActive = MutableStateFlow(false)
        val currentMode = MutableStateFlow(FlightMode.CRUISE)
        val currentZoom = MutableStateFlow(10f)
        val currentBaseStyle = MutableStateFlow(MapStyleCatalog.TOPO)
        val runtimeOverrideActions = mutableListOf<FlightMode>()
        var clearRuntimeOverrideCalls = 0

        val wiring = ThermallingModeRuntimeWiring(
            scope = this,
            controller = controller,
            settings = settings,
            flightData = flightData,
            thermalModeVisible = thermalModeVisible,
            replayActive = replayActive,
            currentMode = currentMode,
            currentZoom = currentZoom,
            currentBaseStyle = currentBaseStyle,
            applyRuntimeFlightMode = { mode ->
                runtimeOverrideActions += mode
                currentMode.value = mode
            },
            clearRuntimeFlightModeOverride = {
                clearRuntimeOverrideCalls += 1
                currentMode.value = FlightMode.CRUISE
            },
            applyZoom = { zoom -> currentZoom.value = zoom },
            applyContrastMap = {}
        )
        try {
            wiring.bind()
            advanceUntilIdle()
            assertEquals(listOf(FlightMode.THERMAL), runtimeOverrideActions)

            thermalModeVisible.value = false
            advanceUntilIdle()

            assertEquals(1, clearRuntimeOverrideCalls)
            assertEquals(listOf(FlightMode.THERMAL), runtimeOverrideActions)
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    private class TestRuntimeController : ThermallingModeRuntimeController {
        private val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        private val coordinator = ThermallingModeCoordinator(clock)
        val userZoomEvents = mutableListOf<Float>()

        override fun update(input: ThermallingModeInput): List<ThermallingModeAction> =
            coordinator.update(input)

        override fun onUserZoomChanged(currentZoom: Float, settings: ThermallingModeSettings) {
            userZoomEvents += currentZoom
            coordinator.onUserZoomChanged(currentZoom, settings)
        }

        override fun state(): ThermallingModeState = coordinator.state()

        override fun reset() {
            coordinator.reset()
        }
    }

    private fun flightSample(isCircling: Boolean): CompleteFlightData =
        CompleteFlightData(
            gps = GPSData(
                position = GeoPoint(46.0, 7.0),
                altitude = AltitudeM(1200.0),
                speed = SpeedMs(22.0),
                bearing = 180.0,
                accuracy = 5f,
                timestamp = 1_000L,
                monotonicTimestampMillis = 1_000L
            ),
            baro = null,
            compass = null,
            baroAltitude = AltitudeM(1200.0),
            qnh = PressureHpa(1_013.25),
            isQNHCalibrated = false,
            verticalSpeed = VerticalSpeedMs(0.0),
            pressureAltitude = AltitudeM(1200.0),
            baroGpsDelta = null,
            baroConfidence = ConfidenceLevel.LOW,
            qnhCalibrationAgeSeconds = 0L,
            agl = AltitudeM(400.0),
            thermalAverage = VerticalSpeedMs(0.0),
            currentLD = 0f,
            netto = VerticalSpeedMs(0.0),
            isCircling = isCircling,
            timestamp = 1_000L,
            dataQuality = "TEST"
        )
}
