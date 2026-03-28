package com.example.xcpro.map

import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapOverlayManagerRuntimeTrafficDelegateTest {

    @Test
    fun interactionThrottle_defersRender_untilExplicitFlush() = runTest {
        var nowMonoMs = 0L
        var interactionActive = true
        val overlay: AdsbTrafficOverlayHandle = mock()
        val map: MapLibreMap = mock()
        val fixture = createFixture(
            scope = this,
            nowMonoMsProvider = { nowMonoMs },
            interactionActiveProvider = { interactionActive },
            overlay = overlay
        )
        fixture.mapState.mapLibreMap = map

        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target(icao24 = "abc123", category = 0)),
            ownshipAltitudeMeters = 1200.0,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()
        verify(overlay, times(1)).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )

        nowMonoMs = 100L
        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target(icao24 = "def456", category = 0)),
            ownshipAltitudeMeters = 1200.0,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()
        verify(overlay, times(1)).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )

        fixture.delegate.flushDeferredAdsbRenderIfNeeded()
        runCurrent()
        verify(overlay, times(2)).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )

        advanceTimeBy(1_000L)
        runCurrent()
        verify(overlay, times(2)).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = any()
        )
    }

    @Test
    fun stickyProjection_appliesPriorStrongFixedWingStyleWithinTtl() = runTest {
        var nowMonoMs = 1_000L
        val overlay: AdsbTrafficOverlayHandle = mock()
        val map: MapLibreMap = mock()
        val fixture = createFixture(
            scope = this,
            nowMonoMsProvider = { nowMonoMs },
            interactionActiveProvider = { false },
            overlay = overlay
        )
        fixture.mapState.mapLibreMap = map

        val strongTarget = target(
            icao24 = "abc123",
            category = 0,
            metadataTypecode = "B738"
        )
        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(strongTarget),
            ownshipAltitudeMeters = 800.0,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()

        nowMonoMs = 1_500L
        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target(icao24 = "abc123", category = 0, metadataTypecode = null)),
            ownshipAltitudeMeters = 800.0,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()

        val styleCaptor = argumentCaptor<Map<String, String>>()
        verify(overlay, atLeast(2)).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = styleCaptor.capture()
        )
        val latestOverrides = styleCaptor.allValues.last()
        assertEquals(
            strongTarget.aircraftIcon().styleImageId,
            latestOverrides["abc123"]
        )
    }

    @Test
    fun rolloutSwitchToLegacyUnknown_reRendersWithLegacyUnknownStyleAndCounters() = runTest {
        var nowMonoMs = 2_000L
        val overlay: AdsbTrafficOverlayHandle = mock()
        val map: MapLibreMap = mock()
        val fixture = createFixture(
            scope = this,
            nowMonoMsProvider = { nowMonoMs },
            interactionActiveProvider = { false },
            overlay = overlay
        )
        fixture.mapState.mapLibreMap = map

        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target(icao24 = "abc123", category = 0)),
            ownshipAltitudeMeters = null,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()

        nowMonoMs = 2_050L
        fixture.delegate.setAdsbDefaultMediumUnknownIconEnabled(false)
        runCurrent()

        val styleCaptor = argumentCaptor<Map<String, String>>()
        verify(overlay, atLeast(2)).render(
            targets = any(),
            ownshipAltitudeMeters = anyOrNull(),
            unitsPreferences = any(),
            iconStyleIdOverrides = styleCaptor.capture()
        )
        val latestOverrides = styleCaptor.allValues.last()
        assertEquals(ADSB_ICON_STYLE_UNKNOWN_LEGACY, latestOverrides["abc123"])

        val counters = fixture.delegate.runtimeCounters()
        assertEquals(false, counters.adsbDefaultMediumUnknownIconEnabled)
        assertTrue(counters.adsbIconLegacyUnknownRenderCount >= 1L)
    }

    @Test
    fun runtimeCounters_trackUnknownRenderAndResolveLatencyFromProjectedIcons() = runTest {
        var nowMonoMs = 3_000L
        val overlay: AdsbTrafficOverlayHandle = mock()
        val map: MapLibreMap = mock()
        val fixture = createFixture(
            scope = this,
            nowMonoMsProvider = { nowMonoMs },
            interactionActiveProvider = { false },
            overlay = overlay
        )
        fixture.mapState.mapLibreMap = map

        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target(icao24 = "abc123", category = 0)),
            ownshipAltitudeMeters = null,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()

        nowMonoMs = 3_400L
        fixture.delegate.updateAdsbTrafficTargets(
            targets = listOf(target(icao24 = "abc123", category = 0, metadataTypecode = "B738")),
            ownshipAltitudeMeters = null,
            unitsPreferences = UnitsPreferences(),
            normalizeOwnshipAltitudeForRender = { it }
        )
        runCurrent()

        val counters = fixture.delegate.runtimeCounters()
        assertEquals(1L, counters.adsbIconUnknownRenderCount)
        assertEquals(0L, counters.adsbIconLegacyUnknownRenderCount)
        assertEquals(1L, counters.adsbIconResolveLatencySampleCount)
        assertEquals(400L, counters.adsbIconResolveLatencyLastMs)
        assertEquals(400L, counters.adsbIconResolveLatencyMaxMs)
        assertEquals(400L, counters.adsbIconResolveLatencyAverageMs)
        assertEquals(true, counters.adsbDefaultMediumUnknownIconEnabled)
        assertTrue(counters.overlayFrontOrderApplyCount >= 1L)
    }

    private fun createFixture(
        scope: TestScope,
        nowMonoMsProvider: () -> Long,
        interactionActiveProvider: () -> Boolean,
        overlay: AdsbTrafficOverlayHandle
    ): Fixture {
        val mapState = MapScreenState()
        val delegate = MapOverlayManagerRuntimeTrafficDelegate(
            runtimeState = MapOverlayTestRuntimeStateAdapter(mapState),
            coroutineScope = scope,
            adsbTrafficOverlayFactory = { _, _, _ -> overlay },
            context = mock(),
            interactionActiveProvider = interactionActiveProvider,
            bringOgnOverlaysToFront = {},
            nowMonoMs = nowMonoMsProvider
        )
        return Fixture(
            mapState = mapState,
            delegate = delegate
        )
    }

    private class MapOverlayTestRuntimeStateAdapter(
        private val state: MapScreenState
    ) : TrafficOverlayRuntimeState {
        override val mapLibreMap: MapLibreMap?
            get() = state.mapLibreMap

        override val blueLocationLayerId: String
            get() = "aircraft-location-layer"

        override fun bringBlueLocationOverlayToFront() {
            state.blueLocationOverlay?.bringToFront()
        }

        override var ognTrafficOverlay: OgnTrafficOverlayHandle?
            get() = state.ognTrafficOverlay
            set(value) {
                state.ognTrafficOverlay = value
            }

        override var ognTargetRingOverlay: OgnTargetRingOverlayHandle?
            get() = state.ognTargetRingOverlay
            set(value) {
                state.ognTargetRingOverlay = value
            }

        override var ognTargetLineOverlay: OgnTargetLineOverlayHandle?
            get() = state.ognTargetLineOverlay
            set(value) {
                state.ognTargetLineOverlay = value
            }

        override var ognOwnshipTargetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle?
            get() = state.ognOwnshipTargetBadgeOverlay
            set(value) {
                state.ognOwnshipTargetBadgeOverlay = value
            }

        override var ognThermalOverlay: OgnThermalOverlayHandle?
            get() = state.ognThermalOverlay
            set(value) {
                state.ognThermalOverlay = value
            }

        override var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle?
            get() = state.ognGliderTrailOverlay
            set(value) {
                state.ognGliderTrailOverlay = value
            }

        override var adsbTrafficOverlay: AdsbTrafficOverlayHandle?
            get() = state.adsbTrafficOverlay
            set(value) {
                state.adsbTrafficOverlay = value
            }
    }

    private fun target(
        icao24: String,
        category: Int?,
        metadataTypecode: String? = null
    ): AdsbTrafficUiModel = AdsbTrafficUiModel(
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
        category = category,
        lastContactEpochSec = null,
        metadataTypecode = metadataTypecode,
        metadataIcaoAircraftType = null
    )

    private data class Fixture(
        val mapState: MapScreenState,
        val delegate: MapOverlayManagerRuntimeTrafficDelegate
    )
}
