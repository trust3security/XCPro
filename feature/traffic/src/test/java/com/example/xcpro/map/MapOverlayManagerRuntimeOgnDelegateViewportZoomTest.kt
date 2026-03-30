package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapOverlayManagerRuntimeOgnDelegateViewportZoomTest {

    @Test
    fun cachedViewportZoom_isAppliedWhenTrafficOverlayIsCreatedFromLazyRenderPath() = runTest {
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        val fixture = createFixture(
            scope = this,
            trafficOverlays = listOf(trafficOverlay)
        )

        fixture.delegate.setViewportZoom(8.6f)
        fixture.delegate.updateTrafficTargets(
            targets = listOf(target("T1")),
            ownshipAltitudeMeters = 1_200.0,
            altitudeUnit = AltitudeUnit.METERS,
            unitsPreferences = UnitsPreferences()
        )
        runCurrent()

        assertEquals(
            listOf(resolveOgnTrafficViewportSizing(OGN_ICON_SIZE_DEFAULT_PX, 8.6f).renderedIconSizePx),
            fixture.createdTrafficOverlayIconSizes
        )
        verify(trafficOverlay).initialize()
        verify(trafficOverlay).setViewportZoom(8.6f)
    }

    @Test
    fun cachedViewportZoom_isAppliedWhenTargetRingIsCreatedFromLazyRenderPath() = runTest {
        val targetRingOverlay: OgnTargetRingOverlayHandle = mock()
        val fixture = createFixture(
            scope = this,
            targetRingOverlays = listOf(targetRingOverlay)
        )

        fixture.delegate.setViewportZoom(8.6f)
        fixture.delegate.updateTargetVisuals(
            enabled = true,
            resolvedTarget = target("T2"),
            ownshipLocation = OverlayCoordinate(-35.2, 149.2),
            ownshipAltitudeMeters = 900.0,
            altitudeUnit = AltitudeUnit.FEET,
            unitsPreferences = UnitsPreferences(),
            forceImmediate = true
        )
        runCurrent()

        assertEquals(
            listOf(resolveOgnTrafficViewportSizing(OGN_ICON_SIZE_DEFAULT_PX, 8.6f).renderedIconSizePx),
            fixture.createdTargetRingOverlayIconSizes
        )
        verify(targetRingOverlay).initialize()
    }

    @Test
    fun baseIconSizeChange_afterCachedZoom_reappliesDerivedRenderedSize() = runTest {
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        val targetRingOverlay: OgnTargetRingOverlayHandle = mock()
        val fixture = createFixture(
            scope = this,
            trafficOverlays = listOf(trafficOverlay),
            targetRingOverlays = listOf(targetRingOverlay)
        )

        fixture.delegate.setViewportZoom(8.6f)
        fixture.delegate.initializeTrafficOverlays(fixture.runtimeState.map)
        reset(trafficOverlay, targetRingOverlay)

        fixture.delegate.setIconSizePx(OGN_ICON_SIZE_MAX_PX)

        val expectedRenderedSizePx = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_MAX_PX,
            zoomLevel = 8.6f
        ).renderedIconSizePx
        verify(trafficOverlay).setIconSizePx(expectedRenderedSizePx)
        verify(targetRingOverlay).setIconSizePx(expectedRenderedSizePx)
    }

    @Test
    fun sameEffectiveRenderedSize_doesNotTriggerUnnecessaryWork() = runTest {
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        val targetRingOverlay: OgnTargetRingOverlayHandle = mock()
        val fixture = createFixture(
            scope = this,
            trafficOverlays = listOf(trafficOverlay),
            targetRingOverlays = listOf(targetRingOverlay)
        )

        fixture.delegate.initializeTrafficOverlays(fixture.runtimeState.map)
        reset(trafficOverlay, targetRingOverlay)

        fixture.delegate.setViewportZoom(7.0f)
        val expectedRenderedSizePx = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = 7.0f
        ).renderedIconSizePx
        verify(trafficOverlay).setIconSizePx(expectedRenderedSizePx)
        verify(targetRingOverlay).setIconSizePx(expectedRenderedSizePx)

        reset(trafficOverlay, targetRingOverlay)
        fixture.delegate.setViewportZoom(6.0f)

        verify(trafficOverlay).setViewportZoom(6.0f)
        verifyNoInteractions(targetRingOverlay)
    }

    @Test
    fun liveTargetRing_resizesWhenViewportZoomChangesWithoutTrafficOverlay() = runTest {
        val targetRingOverlay: OgnTargetRingOverlayHandle = mock()
        val fixture = createFixture(
            scope = this,
            targetRingOverlays = listOf(targetRingOverlay)
        )

        fixture.delegate.setViewportZoom(8.6f)
        fixture.delegate.updateTargetVisuals(
            enabled = true,
            resolvedTarget = target("T2"),
            ownshipLocation = OverlayCoordinate(-35.2, 149.2),
            ownshipAltitudeMeters = 900.0,
            altitudeUnit = AltitudeUnit.METERS,
            unitsPreferences = UnitsPreferences(),
            forceImmediate = true
        )
        runCurrent()
        reset(targetRingOverlay)

        fixture.delegate.setViewportZoom(11.0f)

        val expectedRenderedSizePx = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = 11.0f
        ).renderedIconSizePx
        verify(targetRingOverlay).setIconSizePx(expectedRenderedSizePx)
    }

    @Test
    fun cachedViewportZoom_isReusedWhenOverlaysAreRecreated() = runTest {
        val firstTrafficOverlay: OgnTrafficOverlayHandle = mock()
        val secondTrafficOverlay: OgnTrafficOverlayHandle = mock()
        val firstTargetRingOverlay: OgnTargetRingOverlayHandle = mock()
        val secondTargetRingOverlay: OgnTargetRingOverlayHandle = mock()
        val fixture = createFixture(
            scope = this,
            trafficOverlays = listOf(firstTrafficOverlay, secondTrafficOverlay),
            targetRingOverlays = listOf(firstTargetRingOverlay, secondTargetRingOverlay)
        )

        fixture.delegate.setViewportZoom(8.6f)
        fixture.delegate.initializeTrafficOverlays(fixture.runtimeState.map)
        fixture.delegate.initializeTrafficOverlays(fixture.runtimeState.map)

        val expectedRenderedSizePx = resolveOgnTrafficViewportSizing(
            baseIconSizePx = OGN_ICON_SIZE_DEFAULT_PX,
            zoomLevel = 8.6f
        ).renderedIconSizePx
        assertEquals(
            listOf(expectedRenderedSizePx, expectedRenderedSizePx),
            fixture.createdTrafficOverlayIconSizes
        )
        assertEquals(
            listOf(expectedRenderedSizePx, expectedRenderedSizePx),
            fixture.createdTargetRingOverlayIconSizes
        )
        verify(firstTrafficOverlay, times(1)).cleanup()
        verify(firstTargetRingOverlay, times(1)).cleanup()
        verify(secondTrafficOverlay, times(1)).initialize()
        verify(secondTrafficOverlay, times(1)).setViewportZoom(8.6f)
        verify(secondTargetRingOverlay, times(1)).initialize()
    }

    @Test
    fun selectedTargetChange_doesNotRerenderTrafficOverlay() = runTest {
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        val fixture = createFixture(
            scope = this,
            trafficOverlays = listOf(trafficOverlay)
        )

        fixture.delegate.updateTrafficTargets(
            targets = listOf(target("T1")),
            ownshipAltitudeMeters = 1_200.0,
            altitudeUnit = AltitudeUnit.METERS,
            unitsPreferences = UnitsPreferences()
        )
        runCurrent()
        reset(trafficOverlay)

        fixture.delegate.updateTargetVisuals(
            enabled = true,
            resolvedTarget = target("T1"),
            ownshipLocation = OverlayCoordinate(-35.2, 149.2),
            ownshipAltitudeMeters = 900.0,
            altitudeUnit = AltitudeUnit.METERS,
            unitsPreferences = UnitsPreferences()
        )
        runCurrent()

        verifyNoInteractions(trafficOverlay)
    }

    @Test
    fun projectionInvalidation_doesNotRerenderTrafficForOgn() = runTest {
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        val fixture = createFixture(
            scope = this,
            trafficOverlays = listOf(trafficOverlay)
        )

        fixture.delegate.updateTrafficTargets(
            targets = listOf(target("T1")),
            ownshipAltitudeMeters = 1_200.0,
            altitudeUnit = AltitudeUnit.METERS,
            unitsPreferences = UnitsPreferences()
        )
        runCurrent()
        reset(trafficOverlay)

        fixture.delegate.invalidateProjection()
        runCurrent()

        verifyNoInteractions(trafficOverlay)
    }

    private fun createFixture(
        scope: TestScope,
        trafficOverlays: List<OgnTrafficOverlayHandle> = listOf(mock()),
        targetRingOverlays: List<OgnTargetRingOverlayHandle> = listOf(mock()),
        nowMonoMsProvider: () -> Long = { 0L }
    ): Fixture {
        val runtimeState = TestTrafficOverlayRuntimeState(map = mock())
        val createdTrafficOverlayIconSizes = mutableListOf<Int>()
        val createdTargetRingOverlayIconSizes = mutableListOf<Int>()
        var trafficOverlayIndex = 0
        var targetRingOverlayIndex = 0
        val delegate = MapOverlayManagerRuntimeOgnDelegate(
            runtimeState = runtimeState,
            coroutineScope = scope,
            context = mock(),
            ognTrafficOverlayFactory = { _, _, iconSizePx, _ ->
                createdTrafficOverlayIconSizes += iconSizePx
                trafficOverlays[trafficOverlayIndex++]
            },
            ognTargetRingOverlayFactory = { _, iconSizePx ->
                createdTargetRingOverlayIconSizes += iconSizePx
                targetRingOverlays[targetRingOverlayIndex++]
            },
            ognTargetLineOverlayFactory = { mock() },
            ognOwnshipTargetBadgeOverlayFactory = { mock() },
            ognThermalOverlayFactory = { mock() },
            ognGliderTrailOverlayFactory = { mock() },
            bringTrafficOverlaysToFront = {},
            satelliteContrastIconsEnabled = { false },
            normalizeOwnshipAltitudeForRender = { it },
            nowMonoMs = nowMonoMsProvider
        )
        return Fixture(
            runtimeState = runtimeState,
            delegate = delegate,
            createdTrafficOverlayIconSizes = createdTrafficOverlayIconSizes,
            createdTargetRingOverlayIconSizes = createdTargetRingOverlayIconSizes
        )
    }

    private fun target(id: String): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = 850.0,
        trackDegrees = 90.0,
        groundSpeedMps = 35.0,
        verticalSpeedMps = 1.2,
        deviceIdHex = id,
        signalDb = -12.0,
        displayLabel = id,
        identity = null,
        rawComment = null,
        rawLine = "raw-$id",
        timestampMillis = 1_000L,
        lastSeenMillis = 1_000L,
        addressType = OgnAddressType.FLARM,
        addressHex = id,
        canonicalKey = "FLARM:$id"
    )

    private data class Fixture(
        val runtimeState: TestTrafficOverlayRuntimeState,
        val delegate: MapOverlayManagerRuntimeOgnDelegate,
        val createdTrafficOverlayIconSizes: List<Int>,
        val createdTargetRingOverlayIconSizes: List<Int>
    )

    private class TestTrafficOverlayRuntimeState(
        var map: MapLibreMap
    ) : TrafficOverlayRuntimeState {
        override val mapLibreMap: MapLibreMap?
            get() = map

        override val blueLocationLayerId: String
            get() = "aircraft-location-layer"

        override fun bringBlueLocationOverlayToFront() = Unit

        override var ognTrafficOverlay: OgnTrafficOverlayHandle? = null
        override var ognTargetRingOverlay: OgnTargetRingOverlayHandle? = null
        override var ognTargetLineOverlay: OgnTargetLineOverlayHandle? = null
        override var ognOwnshipTargetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle? = null
        override var ognThermalOverlay: OgnThermalOverlayHandle? = null
        override var ognGliderTrailOverlay: OgnGliderTrailOverlayHandle? = null
        override var adsbTrafficOverlay: AdsbTrafficOverlayHandle? = null
    }
}
