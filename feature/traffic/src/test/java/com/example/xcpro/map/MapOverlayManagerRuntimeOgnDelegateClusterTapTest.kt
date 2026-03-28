package com.example.xcpro.map

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapOverlayManagerRuntimeOgnDelegateClusterTapTest {

    @Test
    fun findHitAt_prefersRingHitBeforeTrafficOverlay() = runTest {
        val ringHit = OgnTrafficHitResult.Target(targetKey = "ring")
        val ringOverlay: OgnTargetRingOverlayHandle = mock()
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        whenever(ringOverlay.findHitAt(any())).thenReturn(ringHit)

        val fixture = createFixture(
            scope = this,
            ognTargetRingOverlay = ringOverlay,
            ognTrafficOverlay = trafficOverlay
        )

        val hit = fixture.delegate.findHitAt(LatLng(-35.0, 149.0))

        assertEquals(ringHit, hit)
        verify(ringOverlay).findHitAt(any())
        verifyNoInteractions(trafficOverlay)
    }

    @Test
    fun findHitAt_fallsBackToTrafficOverlayWhenRingMisses() = runTest {
        val ringOverlay: OgnTargetRingOverlayHandle = mock()
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        val trafficHit = OgnTrafficHitResult.Target(targetKey = "traffic")
        whenever(ringOverlay.findHitAt(any())).thenReturn(null)
        whenever(trafficOverlay.findHitAt(any())).thenReturn(trafficHit)

        val fixture = createFixture(
            scope = this,
            ognTargetRingOverlay = ringOverlay,
            ognTrafficOverlay = trafficOverlay
        )

        val hit = fixture.delegate.findHitAt(LatLng(-35.0, 149.0))

        assertEquals(trafficHit, hit)
        verify(ringOverlay).findHitAt(any())
        verify(trafficOverlay).findHitAt(any())
    }

    @Test
    fun expandCluster_animatesCameraUpdate() = runTest {
        val map: MapLibreMap = mock()
        whenever(map.cameraPosition).thenReturn(
            CameraPosition.Builder()
                .target(LatLng(-35.0, 149.0))
                .zoom(9.0)
                .bearing(14.0)
                .tilt(8.0)
                .build()
        )
        val fixture = createFixture(
            scope = this,
            map = map
        )

        fixture.delegate.expandCluster(
            OgnTrafficHitResult.Cluster(
                clusterKey = "cluster:FLARM:A|FLARM:B",
                centerLatitude = -35.2,
                centerLongitude = 149.3,
                memberCount = 2
            )
        )

        verify(map).animateCamera(any(), eq(320))
    }

    private fun createFixture(
        scope: TestScope,
        map: MapLibreMap? = mock(),
        ognTargetRingOverlay: OgnTargetRingOverlayHandle? = null,
        ognTrafficOverlay: OgnTrafficOverlayHandle? = null
    ): Fixture {
        val runtimeState = TestTrafficOverlayRuntimeState(map = map).apply {
            this.ognTargetRingOverlay = ognTargetRingOverlay
            this.ognTrafficOverlay = ognTrafficOverlay
        }
        return Fixture(
            delegate = MapOverlayManagerRuntimeOgnDelegate(
                runtimeState = runtimeState,
                coroutineScope = scope,
                context = mock<Context>(),
                ognTrafficOverlayFactory = { _, _, _, _ -> mock() },
                ognTargetRingOverlayFactory = { _, _ -> mock() },
                ognTargetLineOverlayFactory = { mock() },
                ognOwnshipTargetBadgeOverlayFactory = { mock() },
                ognThermalOverlayFactory = { mock() },
                ognGliderTrailOverlayFactory = { mock() },
                bringTrafficOverlaysToFront = {},
                satelliteContrastIconsEnabled = { false },
                normalizeOwnshipAltitudeForRender = { it },
                nowMonoMs = { 0L }
            )
        )
    }

    private data class Fixture(
        val delegate: MapOverlayManagerRuntimeOgnDelegate
    )

    private class TestTrafficOverlayRuntimeState(
        private val map: MapLibreMap?
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
