package com.example.xcpro.map

import androidx.lifecycle.Lifecycle
import com.example.xcpro.replay.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapLifecycleManagerOgnTargetCleanupTest {

    @Test
    fun cleanup_clearsOgnTargetOverlays_andInvokesCleanup() {
        val mapState = MapScreenState()
        val targetRingOverlay: OgnTargetRingOverlay = mock()
        val targetLineOverlay: OgnTargetLineOverlay = mock()
        val targetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle = mock()
        mapState.ognTargetRingOverlay = targetRingOverlay
        mapState.ognTargetLineOverlay = targetLineOverlay
        mapState.ognOwnshipTargetBadgeOverlay = targetBadgeOverlay
        val manager = createLifecycleManager(mapState)

        manager.cleanup()

        verify(targetRingOverlay, times(1)).cleanup()
        verify(targetLineOverlay, times(1)).cleanup()
        verify(targetBadgeOverlay, times(1)).cleanup()
        assertNull(mapState.ognTargetRingOverlay)
        assertNull(mapState.ognTargetLineOverlay)
        assertNull(mapState.ognOwnshipTargetBadgeOverlay)
    }

    @Test
    fun onDestroyEvent_clearsOgnTargetOverlays_andInvokesCleanup() {
        val mapState = MapScreenState()
        val targetRingOverlay: OgnTargetRingOverlay = mock()
        val targetLineOverlay: OgnTargetLineOverlay = mock()
        val targetBadgeOverlay: OgnOwnshipTargetBadgeOverlayHandle = mock()
        mapState.ognTargetRingOverlay = targetRingOverlay
        mapState.ognTargetLineOverlay = targetLineOverlay
        mapState.ognOwnshipTargetBadgeOverlay = targetBadgeOverlay
        val manager = createLifecycleManager(mapState)

        manager.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        verify(targetRingOverlay, times(1)).cleanup()
        verify(targetLineOverlay, times(1)).cleanup()
        verify(targetBadgeOverlay, times(1)).cleanup()
        assertNull(mapState.ognTargetRingOverlay)
        assertNull(mapState.ognTargetLineOverlay)
        assertNull(mapState.ognOwnshipTargetBadgeOverlay)
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
