package com.example.xcpro.map

import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapOverlayManagerRuntimeTrafficDelegateViewportZoomTest {

    @Test
    fun cachedViewportZoom_isAppliedWhenOverlayIsCreatedFromLazyRenderPath() = runTest {
        val overlay: AdsbTrafficOverlayHandle = mock()
        val fixture = createFixture(scope = this, overlay = overlay)

        fixture.delegate.setAdsbViewportZoom(8.0f)
        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target("abc123")),
            ownshipAltitudeMeters = 1_200.0,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()

        verify(overlay).initialize()
        verify(overlay).setViewportZoom(8.0f)
        verify(overlay, times(1)).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )
    }

    @Test
    fun viewportZoomChange_rerendersExistingTargetsImmediately() = runTest {
        val overlay: AdsbTrafficOverlayHandle = mock()
        val fixture = createFixture(scope = this, overlay = overlay)

        fixture.delegate.setAdsbViewportZoom(10.5f)
        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target("abc123")),
            ownshipAltitudeMeters = 1_200.0,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()

        fixture.delegate.setAdsbViewportZoom(8.0f)
        runCurrent()

        verify(overlay).setViewportZoom(8.0f)
        verify(overlay, times(2)).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )
    }

    @Test
    fun sameViewportZoom_refreshesOverlayPolicyWithoutTriggeringRerender() = runTest {
        val overlay: AdsbTrafficOverlayHandle = mock()
        val fixture = createFixture(scope = this, overlay = overlay)

        fixture.delegate.setAdsbViewportZoom(10.5f)
        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target("abc123")),
            ownshipAltitudeMeters = 1_200.0,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()
        reset(overlay)

        fixture.delegate.setAdsbViewportZoom(10.5f)
        runCurrent()

        verify(overlay).setViewportZoom(10.5f)
        verify(overlay, never()).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )
    }

    private fun createFixture(
        scope: TestScope,
        overlay: AdsbTrafficOverlayHandle
    ): Fixture {
        val runtimeState = TestTrafficOverlayRuntimeState(map = mock())
        val delegate = MapOverlayManagerRuntimeTrafficDelegate(
            runtimeState = runtimeState,
            coroutineScope = scope,
            adsbTrafficOverlayFactory = { _, _, _ -> overlay },
            context = mock(),
            interactionActiveProvider = { false },
            bringOgnOverlaysToFront = {},
            nowMonoMs = { 0L }
        )
        return Fixture(runtimeState = runtimeState, delegate = delegate)
    }

    private fun target(icao24: String): AdsbTrafficUiModel = AdsbTrafficUiModel(
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
        val runtimeState: TestTrafficOverlayRuntimeState,
        val delegate: MapOverlayManagerRuntimeTrafficDelegate
    )

    private class TestTrafficOverlayRuntimeState(
        val map: MapLibreMap
    ) : TrafficOverlayRuntimeState {
        override val mapLibreMap: MapLibreMap?
            get() = map

        override val blueLocationLayerId: String
            get() = "aircraft-location-layer"

        override fun bringBlueLocationOverlayToFront() = Unit

        override var ognTrafficOverlay: OgnTrafficOverlayHandle? = null
        override var ognTargetRingOverlay: OgnTargetRingOverlayHandle? = null
        override var ognTargetLineOverlay: OgnTargetLineOverlayHandle? = null
        override var ognThermalOverlay: OgnThermalOverlayHandle? = null
        override var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle? = null
        override var adsbTrafficOverlay: AdsbTrafficOverlayHandle? = null
    }
}
