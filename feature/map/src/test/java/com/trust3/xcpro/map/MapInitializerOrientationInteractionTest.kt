package com.trust3.xcpro.map

import android.content.Context
import com.trust3.xcpro.airspace.AirspaceUseCase
import com.trust3.xcpro.flightdata.WaypointFilesUseCase
import com.trust3.xcpro.map.trail.SnailTrailManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.RotateGestureDetector
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
class MapInitializerOrientationInteractionTest {

    @Test
    fun moveBegin_triggersOrientationInteractionCallbackOnce() = runTest {
        val fixture = createFixture(scope = this)
        val moveListeners = mutableListOf<MapLibreMap.OnMoveListener>()
        doAnswer { invocation ->
            moveListeners += invocation.getArgument<MapLibreMap.OnMoveListener>(0)
            null
        }.whenever(fixture.map).addOnMoveListener(any<MapLibreMap.OnMoveListener>())

        invokePrivateMethod(
            target = fixture.initializer,
            name = "setupListeners",
            parameterTypes = arrayOf(MapLibreMap::class.java),
            args = arrayOf(fixture.map)
        )

        moveListeners.single().onMoveBegin(mock<MoveGestureDetector>())

        verify(fixture.onOrientationUserInteraction, times(1)).invoke()
    }

    @Test
    fun rotateBegin_triggersOrientationInteractionCallbackOnce() = runTest {
        val fixture = createFixture(scope = this)
        val rotateListeners = mutableListOf<MapLibreMap.OnRotateListener>()
        doAnswer { invocation ->
            rotateListeners += invocation.getArgument<MapLibreMap.OnRotateListener>(0)
            null
        }.whenever(fixture.map).addOnRotateListener(any<MapLibreMap.OnRotateListener>())

        invokePrivateMethod(
            target = fixture.initializer,
            name = "setupListeners",
            parameterTypes = arrayOf(MapLibreMap::class.java),
            args = arrayOf(fixture.map)
        )

        rotateListeners.single().onRotateBegin(mock<RotateGestureDetector>())

        verify(fixture.onOrientationUserInteraction, times(1)).invoke()
    }

    private fun createFixture(scope: kotlinx.coroutines.test.TestScope): Fixture {
        val mapState = MapScreenState()
        val mapStateStore = MapStateStore(initialStyleName = "Terrain")
        val stateActions = MapStateActionsDelegate(mapStateStore)
        val overlayManager: MapOverlayManager = mock()
        val map: MapLibreMap = mock()
        val onOrientationUserInteraction: () -> Unit = mock()
        whenever(map.uiSettings).thenReturn(mock<UiSettings>())
        return Fixture(
            initializer = MapInitializer(
                context = mock<Context>(),
                mapState = mapState,
                mapStateReader = mapStateStore,
                stateActions = stateActions,
                overlayManager = overlayManager,
                onOrientationUserInteraction = onOrientationUserInteraction,
                taskRenderSyncCoordinator = mock<TaskRenderSyncCoordinator>(),
                snailTrailManager = mock<SnailTrailManager>(),
                coroutineScope = scope,
                airspaceUseCase = mock<AirspaceUseCase>(),
                waypointFilesUseCase = mock<WaypointFilesUseCase>(),
                localOwnshipRenderEnabledProvider = { false }
            ),
            initializerMap = map,
            onOrientationUserInteraction = onOrientationUserInteraction
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
        val initializerMap: MapLibreMap,
        val onOrientationUserInteraction: () -> Unit
    ) {
        val map: MapLibreMap
            get() = initializerMap
    }
}
