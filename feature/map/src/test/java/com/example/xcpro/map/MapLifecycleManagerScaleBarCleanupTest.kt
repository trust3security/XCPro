package com.example.xcpro.map

import androidx.lifecycle.Lifecycle
import com.example.xcpro.replay.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapLifecycleManagerScaleBarCleanupTest {

    @Test
    fun cleanup_clearsScaleBarController_andInvokesClear() {
        val mapState = MapScreenState()
        val scaleBarController: MapScaleBarController = mock()
        mapState.scaleBarController = scaleBarController
        val manager = createLifecycleManager(mapState)

        manager.cleanup()

        verify(scaleBarController, times(1)).clear()
        assertNull(mapState.scaleBarController)
    }

    @Test
    fun onDestroyEvent_clearsScaleBarController_andInvokesClear() {
        val mapState = MapScreenState()
        val scaleBarController: MapScaleBarController = mock()
        mapState.scaleBarController = scaleBarController
        val manager = createLifecycleManager(mapState)

        manager.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        verify(scaleBarController, times(1)).clear()
        assertNull(mapState.scaleBarController)
    }

    private fun createLifecycleManager(mapState: MapScreenState): MapLifecycleManager {
        return MapLifecycleManager(
            lifecycleSurface = MapLifecycleSurfaceAdapter(mapState, mock<MapStateActions>()),
            orientationManager = mock<MapOrientationRuntimePort>(),
            locationManager = mock<MapLocationRuntimePort>(),
            locationRenderFrameCleanup = mock<MapRenderFrameCleanupPort>(),
            renderSurfaceDiagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L }),
            replaySessionState = MutableStateFlow(SessionState())
        )
    }
}
