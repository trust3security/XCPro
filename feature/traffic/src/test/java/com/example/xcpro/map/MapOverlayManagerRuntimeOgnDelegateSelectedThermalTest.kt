package com.example.xcpro.map

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapOverlayManagerRuntimeOgnDelegateSelectedThermalTest {

    @Test
    fun updateSelectedThermalContext_createsOverlayAndRendersContext() = runTest {
        val selectedOverlay: OgnSelectedThermalOverlayHandle = mock()
        val context = selectedContext()
        val fixture = createFixture(
            scope = this,
            selectedOverlayFactory = { selectedOverlay }
        )

        fixture.delegate.updateSelectedThermalContext(context, forceImmediate = true)

        verify(selectedOverlay, times(1)).initialize()
        verify(selectedOverlay, times(1)).render(eq(context))
    }

    @Test
    fun updateSelectedThermalContext_nullContextClearsExistingOverlay() = runTest {
        val selectedOverlay: OgnSelectedThermalOverlayHandle = mock()
        val context = selectedContext()
        val fixture = createFixture(
            scope = this,
            selectedOverlayFactory = { selectedOverlay }
        )
        fixture.delegate.updateSelectedThermalContext(context, forceImmediate = true)

        fixture.delegate.updateSelectedThermalContext(null, forceImmediate = true)

        verify(selectedOverlay, times(1)).render(eq(context))
        verify(selectedOverlay, times(1)).render(eq(null))
    }

    private fun createFixture(
        scope: TestScope,
        selectedOverlayFactory: OgnSelectedThermalOverlayFactory
    ): Fixture {
        val runtimeState = TestTrafficOverlayRuntimeState(map = mock())
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
                ognSelectedThermalOverlayFactory = selectedOverlayFactory,
                bringTrafficOverlaysToFront = {},
                satelliteContrastIconsEnabled = { false },
                normalizeOwnshipAltitudeForRender = { it },
                nowMonoMs = { 0L }
            )
        )
    }

    private fun selectedContext(): SelectedOgnThermalOverlayContext =
        SelectedOgnThermalOverlayContext(
            hotspotId = "thermal-1",
            snailColorIndex = 11,
            hotspotPoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0000),
            highlightedSegments = listOf(
                OgnGliderTrailSegment(
                    id = "seg-1",
                    sourceTargetId = "pilot-1",
                    sourceLabel = "pilot-1",
                    startLatitude = -35.0000,
                    startLongitude = 149.0000,
                    endLatitude = -34.9950,
                    endLongitude = 149.0050,
                    colorIndex = 11,
                    widthPx = 2f,
                    timestampMonoMs = 100L
                )
            ),
            occupancyHullPoints = listOf(
                OgnThermalPoint(latitude = -35.0000, longitude = 149.0000),
                OgnThermalPoint(latitude = -34.9950, longitude = 149.0050),
                OgnThermalPoint(latitude = -35.0000, longitude = 149.0100)
            ),
            startPoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0000),
            latestPoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0100)
        )

    private data class Fixture(
        val delegate: MapOverlayManagerRuntimeOgnDelegate
    )

    private class TestTrafficOverlayRuntimeState(
        private val map: MapLibreMap
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
        override var ognSelectedThermalOverlay: OgnSelectedThermalOverlayHandle? = null
        override var adsbTrafficOverlay: AdsbTrafficOverlayHandle? = null
    }
}
