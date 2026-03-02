package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.trail.SnailTrailManager
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MapOverlayManagerSkySightSatelliteErrorTest {

    @Test
    fun setSkySightSatelliteOverlay_renderFailureSurfacesErrorAndRetriesSameConfig() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val satelliteOverlay: SkySightSatelliteOverlay = mock()
        val blueOverlay: BlueLocationOverlay = mock()
        fixture.mapState.mapLibreMap = map
        fixture.mapState.skySightSatelliteOverlay = satelliteOverlay
        fixture.mapState.blueLocationOverlay = blueOverlay

        var renderCalls = 0
        doAnswer {
            renderCalls += 1
            if (renderCalls == 1) {
                throw IllegalStateException("satellite render failed")
            }
            Unit
        }.whenever(satelliteOverlay).render(any())

        applyEnabledSatelliteConfig(fixture.manager)
        assertEquals(
            "satellite render failed",
            fixture.manager.skySightSatelliteRuntimeErrorMessage.value
        )

        applyEnabledSatelliteConfig(fixture.manager)

        verify(satelliteOverlay, times(2)).render(any())
        verify(blueOverlay, times(1)).bringToFront()
        assertNull(fixture.manager.skySightSatelliteRuntimeErrorMessage.value)
    }

    @Test
    fun clearSkySightSatelliteOverlay_clearsRuntimeError() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val satelliteOverlay: SkySightSatelliteOverlay = mock()
        fixture.mapState.mapLibreMap = map
        fixture.mapState.skySightSatelliteOverlay = satelliteOverlay

        doAnswer {
            throw IllegalStateException("satellite render failed")
        }.whenever(satelliteOverlay).render(any())

        applyEnabledSatelliteConfig(fixture.manager)
        fixture.manager.clearSkySightSatelliteOverlay()

        assertNull(fixture.manager.skySightSatelliteRuntimeErrorMessage.value)
    }

    @Test
    fun setSkySightSatelliteOverlay_disabled_clearsErrorWhenMapUnavailable() {
        val fixture = createFixture()
        val map: MapLibreMap = mock()
        val satelliteOverlay: SkySightSatelliteOverlay = mock()
        fixture.mapState.mapLibreMap = map
        fixture.mapState.skySightSatelliteOverlay = satelliteOverlay

        doAnswer {
            throw IllegalStateException("satellite render failed")
        }.whenever(satelliteOverlay).render(any())

        applyEnabledSatelliteConfig(fixture.manager)
        assertEquals(
            "satellite render failed",
            fixture.manager.skySightSatelliteRuntimeErrorMessage.value
        )

        fixture.mapState.mapLibreMap = null
        fixture.manager.setSkySightSatelliteOverlay(
            enabled = false,
            showSatelliteImagery = false,
            showRadar = false,
            showLightning = false,
            animate = false,
            historyFrameCount = 3,
            referenceTimeUtcMs = null
        )

        assertNull(fixture.manager.skySightSatelliteRuntimeErrorMessage.value)
    }

    private fun applyEnabledSatelliteConfig(manager: MapOverlayManager) {
        manager.setSkySightSatelliteOverlay(
            enabled = true,
            showSatelliteImagery = true,
            showRadar = false,
            showLightning = false,
            animate = false,
            historyFrameCount = 3,
            referenceTimeUtcMs = null
        )
    }

    private fun createFixture(): Fixture {
        val mapState = MapScreenState()
        val mapStateStore = MapStateStore(initialStyleName = "Terrain")
        val manager = MapOverlayManager(
            context = mock<Context>(),
            mapState = mapState,
            mapStateReader = mapStateStore,
            taskRenderSyncCoordinator = mock<TaskRenderSyncCoordinator>(),
            taskWaypointCountProvider = { 0 },
            stateActions = MapStateActionsDelegate(mapStateStore),
            snailTrailManager = mock<SnailTrailManager>(),
            coroutineScope = TestScope(),
            airspaceUseCase = mock<AirspaceUseCase>(),
            waypointFilesUseCase = mock<WaypointFilesUseCase>(),
            ognTrafficOverlayFactory = { _, _, _ -> mock<OgnTrafficOverlay>() }
        )
        return Fixture(manager = manager, mapState = mapState)
    }

    private data class Fixture(
        val manager: MapOverlayManager,
        val mapState: MapScreenState
    )
}
