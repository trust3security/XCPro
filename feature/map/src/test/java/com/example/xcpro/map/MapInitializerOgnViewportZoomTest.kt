package com.example.xcpro.map

import android.content.Context
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.map.trail.SnailTrailManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.UiSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapInitializerOgnViewportZoomTest {

    @Test
    fun setupInitialPosition_forwardsViewportZoomToOgnRuntime() = runTest {
        val fixture = createFixture(scope = this)

        invokePrivateMethod(
            target = fixture.initializer,
            name = "setupInitialPosition",
            parameterTypes = arrayOf(MapLibreMap::class.java),
            args = arrayOf(fixture.map)
        )

        verify(fixture.overlayManager, times(1)).setOgnViewportZoom(8.0f)
    }

    @Test
    fun cameraIdleListener_forwardsViewportZoomToOgnRuntime() = runTest {
        val fixture = createFixture(scope = this)
        val cameraIdleListeners = mutableListOf<MapLibreMap.OnCameraIdleListener>()
        doAnswer { invocation ->
            cameraIdleListeners += invocation.getArgument<MapLibreMap.OnCameraIdleListener>(0)
            null
        }.whenever(fixture.map).addOnCameraIdleListener(any<MapLibreMap.OnCameraIdleListener>())
        whenever(fixture.map.cameraPosition).thenReturn(
            CameraPosition.Builder()
                .target(LatLng(-35.2, 149.2))
                .zoom(9.4)
                .bearing(12.0)
                .build()
        )

        invokePrivateMethod(
            target = fixture.initializer,
            name = "setupListeners",
            parameterTypes = arrayOf(MapLibreMap::class.java),
            args = arrayOf(fixture.map)
        )
        cameraIdleListeners.single().onCameraIdle()

        verify(fixture.overlayManager, times(1)).setOgnViewportZoom(9.4f)
    }

    private fun createFixture(scope: kotlinx.coroutines.test.TestScope): Fixture {
        val mapState = MapScreenState()
        val mapStateStore = MapStateStore(initialStyleName = "Terrain")
        val stateActions = MapStateActionsDelegate(mapStateStore)
        val overlayManager: MapOverlayManager = mock()
        val map: MapLibreMap = mock()
        whenever(map.uiSettings).thenReturn(mock<UiSettings>())
        return Fixture(
            initializer = MapInitializer(
                context = mock<Context>(),
                mapState = mapState,
                mapStateReader = mapStateStore,
                stateActions = stateActions,
                overlayManager = overlayManager,
                orientationManager = mock<MapOrientationManager>(),
                taskRenderSyncCoordinator = mock<TaskRenderSyncCoordinator>(),
                snailTrailManager = mock<SnailTrailManager>(),
                coroutineScope = scope,
                airspaceUseCase = mock<AirspaceUseCase>(),
                waypointFilesUseCase = mock<WaypointFilesUseCase>(),
                localOwnshipRenderEnabledProvider = { false }
            ),
            overlayManager = overlayManager,
            map = map
        )
    }

    private fun invokePrivateMethod(
        target: Any,
        name: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any>
    ) {
        val method = target::class.java.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        method.invoke(target, *args)
    }

    private data class Fixture(
        val initializer: MapInitializer,
        val overlayManager: MapOverlayManager,
        val map: MapLibreMap
    )
}
