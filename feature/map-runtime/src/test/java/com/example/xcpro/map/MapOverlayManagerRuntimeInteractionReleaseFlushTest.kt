package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.weather.rain.WeatherRadarRenderOptions
import com.example.xcpro.weather.rain.WeatherRadarStatusCode
import com.example.xcpro.weather.rain.WeatherRainFrameSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.Mockito.clearInvocations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class MapOverlayManagerRuntimeInteractionReleaseFlushTest {

    @Test
    fun interactionRelease_flushesDeferredTrafficAndWeatherAfterSettleWindow() = runTest {
        val fixture = createFixture(scope = this)

        fixture.manager.updateOgnTrafficTargets(
            targets = listOf(ognTarget("ogn-1")),
            ownshipAltitudeMeters = 1_000.0,
            altitudeUnit = AltitudeUnit.METERS,
            unitsPreferences = UnitsPreferences()
        )
        fixture.manager.updateAdsbTrafficTargets(
            targets = listOf(adsbTarget("abc123")),
            ownshipAltitudeMeters = 1_000.0,
            unitsPreferences = UnitsPreferences()
        )
        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frameSelection(1_000L),
            opacity = 0.60f,
            transitionDurationMs = 180L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        runCurrent()
        reset(fixture.ognTrafficOverlay, fixture.adsbTrafficOverlay, fixture.weatherOverlay)

        fixture.manager.setMapInteractionActive(true)
        clearInvocations(fixture.adsbTrafficOverlay)
        fixture.nowProbe.value = 10_100L
        fixture.manager.updateOgnTrafficTargets(
            targets = listOf(ognTarget("ogn-2")),
            ownshipAltitudeMeters = 1_000.0,
            altitudeUnit = AltitudeUnit.METERS,
            unitsPreferences = UnitsPreferences()
        )
        fixture.manager.updateAdsbTrafficTargets(
            targets = listOf(adsbTarget("def456")),
            ownshipAltitudeMeters = 1_000.0,
            unitsPreferences = UnitsPreferences()
        )
        fixture.manager.setWeatherRainOverlay(
            enabled = true,
            frameSelection = frameSelection(1_600L),
            opacity = 0.60f,
            transitionDurationMs = 180L,
            statusCode = WeatherRadarStatusCode.OK,
            stale = false
        )
        runCurrent()
        verifyNoInteractions(fixture.ognTrafficOverlay, fixture.weatherOverlay)
        verify(fixture.adsbTrafficOverlay, times(0)).render(
            targets = any(),
            selectedTargetId = anyOrNull(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )

        fixture.manager.setMapInteractionActive(false)
        runCurrent()
        clearInvocations(fixture.adsbTrafficOverlay)

        fixture.nowProbe.value = 10_600L
        advanceTimeBy(500L)
        runCurrent()
        verifyNoInteractions(fixture.ognTrafficOverlay, fixture.weatherOverlay)
        verify(fixture.adsbTrafficOverlay, times(0)).render(
            targets = any(),
            selectedTargetId = anyOrNull(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )

        fixture.nowProbe.value = 10_719L
        advanceTimeBy(119L)
        runCurrent()
        verifyNoInteractions(fixture.ognTrafficOverlay, fixture.weatherOverlay)
        verify(fixture.adsbTrafficOverlay, times(0)).render(
            targets = any(),
            selectedTargetId = anyOrNull(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )

        fixture.nowProbe.value = 10_720L
        advanceTimeBy(1L)
        runCurrent()

        verify(fixture.ognTrafficOverlay, times(1)).render(
            targets = any(),
            selectedTargetKey = anyOrNull(),
            ownshipAltitudeMeters = anyOrNull(),
            altitudeUnit = any(),
            unitsPreferences = any()
        )
        verify(fixture.adsbTrafficOverlay, times(1)).render(
            targets = any(),
            selectedTargetId = anyOrNull(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )
        verify(fixture.weatherOverlay, times(1)).render(
            frameSelection = any(),
            opacity = any(),
            transitionDurationMs = any()
        )
    }

    @Test
    fun interactionReactivation_beforeSettleWindow_cancelsDeferredFlush() = runTest {
        val fixture = createFixture(scope = this)

        fixture.manager.updateAdsbTrafficTargets(
            targets = listOf(adsbTarget("abc123")),
            ownshipAltitudeMeters = 1_000.0,
            unitsPreferences = UnitsPreferences()
        )
        runCurrent()
        reset(fixture.adsbTrafficOverlay)

        fixture.manager.setMapInteractionActive(true)
        clearInvocations(fixture.adsbTrafficOverlay)
        fixture.nowProbe.value = 10_100L
        fixture.manager.updateAdsbTrafficTargets(
            targets = listOf(adsbTarget("def456")),
            ownshipAltitudeMeters = 1_000.0,
            unitsPreferences = UnitsPreferences()
        )
        runCurrent()
        verify(fixture.adsbTrafficOverlay, times(0)).render(
            targets = any(),
            selectedTargetId = anyOrNull(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )

        fixture.manager.setMapInteractionActive(false)
        clearInvocations(fixture.adsbTrafficOverlay)
        fixture.nowProbe.value = 10_600L
        advanceTimeBy(500L)
        runCurrent()
        verify(fixture.adsbTrafficOverlay, times(0)).render(
            targets = any(),
            selectedTargetId = anyOrNull(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )

        fixture.manager.setMapInteractionActive(true)
        runCurrent()
        clearInvocations(fixture.adsbTrafficOverlay)

        fixture.nowProbe.value = 10_749L
        advanceTimeBy(149L)
        runCurrent()
        verify(fixture.adsbTrafficOverlay, times(0)).render(
            targets = any(),
            selectedTargetId = anyOrNull(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )
    }

    private fun createFixture(scope: TestScope): Fixture {
        val trafficRuntimeState = FakeTrafficOverlayRuntimeState()
        val forecastWeatherRuntimeState = FakeForecastWeatherOverlayRuntimeState()
        val map: MapLibreMap = mock()
        val ognTrafficOverlay: OgnTrafficOverlayHandle = mock()
        val adsbTrafficOverlay: AdsbTrafficOverlayHandle = mock()
        val weatherOverlay: WeatherRainOverlay = mock()
        val nowProbe = NowProbe()
        trafficRuntimeState.mapLibreMap = map
        forecastWeatherRuntimeState.mapLibreMap = map
        forecastWeatherRuntimeState.weatherRainOverlay = weatherOverlay
        return Fixture(
            manager = TestMapOverlayManagerRuntime(
                context = mock<Context>(),
                taskRenderSyncCoordinator = mock<TaskRenderSyncCoordinator>(),
                coroutineScope = scope,
                trafficRuntimeState = trafficRuntimeState,
                forecastWeatherRuntimeState = forecastWeatherRuntimeState,
                ognTrafficOverlay = ognTrafficOverlay,
                adsbTrafficOverlay = adsbTrafficOverlay,
                nowMonoMs = { nowProbe.value }
            ),
            nowProbe = nowProbe,
            ognTrafficOverlay = ognTrafficOverlay,
            adsbTrafficOverlay = adsbTrafficOverlay,
            weatherOverlay = weatherOverlay
        )
    }

    private fun frameSelection(epochSec: Long): WeatherRainFrameSelection = WeatherRainFrameSelection(
        hostUrl = "https://tilecache.rainviewer.com",
        framePath = "/v2/radar/$epochSec",
        frameTimeEpochSec = epochSec,
        renderOptions = WeatherRadarRenderOptions()
    )

    private fun ognTarget(id: String): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = 1_050.0,
        trackDegrees = 180.0,
        groundSpeedMps = 32.0,
        verticalSpeedMps = 0.5,
        deviceIdHex = "device$id",
        signalDb = null,
        displayLabel = "AB1",
        identity = null,
        rawComment = null,
        rawLine = "",
        timestampMillis = 1_000L,
        lastSeenMillis = 1_000L,
        distanceMeters = 1_000.0
    )

    private fun adsbTarget(icao24: String): AdsbTrafficUiModel = AdsbTrafficUiModel(
        id = Icao24.from(icao24) ?: error("invalid ICAO24"),
        callsign = "TEST",
        lat = -33.0,
        lon = 151.0,
        altitudeM = 900.0,
        speedMps = 45.0,
        trackDeg = 180.0,
        climbMps = 0.1,
        ageSec = 1,
        isStale = false,
        distanceMeters = 1_000.0,
        bearingDegFromUser = 180.0,
        positionSource = 0,
        category = 3,
        lastContactEpochSec = null
    )

    private data class Fixture(
        val manager: TestMapOverlayManagerRuntime,
        val nowProbe: NowProbe,
        val ognTrafficOverlay: OgnTrafficOverlayHandle,
        val adsbTrafficOverlay: AdsbTrafficOverlayHandle,
        val weatherOverlay: WeatherRainOverlay
    )

    private class TestMapOverlayManagerRuntime(
        context: Context,
        taskRenderSyncCoordinator: TaskRenderSyncCoordinator,
        coroutineScope: TestScope,
        trafficRuntimeState: TrafficOverlayRuntimeState,
        forecastWeatherRuntimeState: ForecastWeatherOverlayRuntimeState,
        ognTrafficOverlay: OgnTrafficOverlayHandle,
        adsbTrafficOverlay: AdsbTrafficOverlayHandle,
        nowMonoMs: () -> Long
    ) : MapOverlayManagerRuntime(
        context = context,
        taskRenderSyncCoordinator = taskRenderSyncCoordinator,
        coroutineScope = coroutineScope,
        trafficRuntimeState = trafficRuntimeState,
        forecastWeatherRuntimeState = forecastWeatherRuntimeState,
        adsbTrafficOverlayFactory = { _, _, _ -> adsbTrafficOverlay },
        ognTrafficOverlayFactory = { _, _, _, _ -> ognTrafficOverlay },
        ognTargetRingOverlayFactory = { _, _ -> mock() },
        ognTargetLineOverlayFactory = { _ -> mock() },
        ognOwnshipTargetBadgeOverlayFactory = { _ -> mock() },
        ognThermalOverlayFactory = { _ -> mock() },
        ognGliderTrailOverlayFactory = { _ -> mock() },
        ognSelectedThermalOverlayFactory = { _ -> mock() },
        nowMonoMs = nowMonoMs
    ) {
        init {
            attachShellPorts(
                lifecyclePort = object : MapOverlayRuntimeLifecyclePort {
                    override fun toggleDistanceCircles() = Unit
                    override fun refreshAirspace(map: MapLibreMap?) = Unit
                    override fun refreshWaypoints(map: MapLibreMap?) = Unit
                    override fun plotSavedTask(map: MapLibreMap?) = Unit
                    override fun clearTaskOverlays(map: MapLibreMap?) = Unit
                    override fun onMapStyleChanged(map: MapLibreMap?) = Unit
                    override fun initializeOverlays(map: MapLibreMap?) = Unit
                    override fun initializeTrafficOverlays(map: MapLibreMap?) = Unit
                    override fun onZoomChanged(map: MapLibreMap?) = Unit
                    override fun onMapDetached() = Unit
                },
                statusReporter = object : MapOverlayRuntimeStatusReporter {
                    override fun getOverlayStatus(): String = ""
                }
            )
        }
    }

    private class FakeForecastWeatherOverlayRuntimeState : ForecastWeatherOverlayRuntimeState {
        override var mapLibreMap: MapLibreMap? = null
        override var forecastOverlay: ForecastRasterOverlay? = null
        override var forecastWindOverlay: ForecastRasterOverlay? = null
        override var skySightSatelliteOverlay: SkySightSatelliteOverlay? = null
        override var weatherRainOverlay: WeatherRainOverlay? = null
    }

    private class FakeTrafficOverlayRuntimeState : TrafficOverlayRuntimeState {
        override var mapLibreMap: MapLibreMap? = null
        override val blueLocationLayerId: String = "blue"
        override fun bringBlueLocationOverlayToFront() = Unit
        override var ognTrafficOverlay: OgnTrafficOverlayHandle? = null
        override var ognTargetRingOverlay: OgnTargetRingOverlayHandle? = null
        override var ognTargetLineOverlay: OgnTargetLineOverlayHandle? = null
        override var ognOwnshipTargetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle? = null
        override var ognThermalOverlay: OgnThermalOverlayHandle? = null
        override var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle? = null
        override var ognSelectedThermalOverlay: OgnSelectedThermalOverlayHandle? = null
        override var adsbTrafficOverlay: AdsbTrafficOverlayHandle? = null
    }

    private class NowProbe(var value: Long = 10_000L)
}
