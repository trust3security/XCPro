package com.example.xcpro.map

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapOverlayManagerRuntimeOgnDelegateTargetTapTest {

    @Test
    fun findTargetAt_prefersRingTargetBeforeTrafficOverlay() = runTest {
        val ringOverlay: OgnTargetRingOverlayHandle = mock()
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        whenever(ringOverlay.findTargetAt(any())).thenReturn("ring")

        val fixture = createFixture(
            scope = this,
            ognTargetRingOverlay = ringOverlay,
            ognTrafficOverlay = trafficOverlay
        )

        val targetKey = fixture.delegate.findTargetAt(LatLng(-35.0, 149.0))

        assertEquals("ring", targetKey)
        verify(ringOverlay).findTargetAt(any())
        verifyNoInteractions(trafficOverlay)
    }

    @Test
    fun findTargetAt_fallsBackToTrafficOverlayWhenRingMisses() = runTest {
        val ringOverlay: OgnTargetRingOverlayHandle = mock()
        val trafficOverlay: OgnTrafficOverlayHandle = mock()
        whenever(ringOverlay.findTargetAt(any())).thenReturn(null)
        whenever(trafficOverlay.findTargetAt(any())).thenReturn("traffic")

        val fixture = createFixture(
            scope = this,
            ognTargetRingOverlay = ringOverlay,
            ognTrafficOverlay = trafficOverlay
        )

        val targetKey = fixture.delegate.findTargetAt(LatLng(-35.0, 149.0))

        assertEquals("traffic", targetKey)
        verify(ringOverlay).findTargetAt(any())
        verify(trafficOverlay).findTargetAt(any())
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
