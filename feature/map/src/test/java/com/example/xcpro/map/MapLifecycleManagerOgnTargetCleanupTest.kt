package com.example.xcpro.map

import androidx.lifecycle.Lifecycle
import com.example.xcpro.MapOrientationManager
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
        mapState.ognTargetRingOverlay = targetRingOverlay
        mapState.ognTargetLineOverlay = targetLineOverlay
        val manager = createLifecycleManager(mapState)

        manager.cleanup()

        verify(targetRingOverlay, times(1)).cleanup()
        verify(targetLineOverlay, times(1)).cleanup()
        assertNull(mapState.ognTargetRingOverlay)
        assertNull(mapState.ognTargetLineOverlay)
    }

    @Test
    fun onDestroyEvent_clearsOgnTargetOverlays_andInvokesCleanup() {
        val mapState = MapScreenState()
        val targetRingOverlay: OgnTargetRingOverlay = mock()
        val targetLineOverlay: OgnTargetLineOverlay = mock()
        mapState.ognTargetRingOverlay = targetRingOverlay
        mapState.ognTargetLineOverlay = targetLineOverlay
        val manager = createLifecycleManager(mapState)

        manager.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        verify(targetRingOverlay, times(1)).cleanup()
        verify(targetLineOverlay, times(1)).cleanup()
        assertNull(mapState.ognTargetRingOverlay)
        assertNull(mapState.ognTargetLineOverlay)
    }

    private fun createLifecycleManager(mapState: MapScreenState): MapLifecycleManager {
        return MapLifecycleManager(
            mapState = mapState,
            orientationManager = mock<MapOrientationManager>(),
            locationManager = mock<LocationManager>(),
            replaySessionState = MutableStateFlow(SessionState()),
            stateActions = mock<MapStateActions>()
        )
    }
}
