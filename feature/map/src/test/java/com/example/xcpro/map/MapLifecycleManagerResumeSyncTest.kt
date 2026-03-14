package com.example.xcpro.map

import androidx.lifecycle.Lifecycle
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.replay.Selection
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapLifecycleManagerResumeSyncTest {

    @Test
    fun onResume_restartsSensors_andForcesImmediateDisplayFrameSync() {
        val locationManager: MapLocationRuntimePort = mock()
        val manager = createLifecycleManager(
            locationManager = locationManager,
            replayState = SessionState()
        )

        manager.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        verify(locationManager, times(1)).restartSensorsIfNeeded()
        verify(locationManager, times(1)).onDisplayFrame()
    }

    @Test
    fun syncCurrentOwnerState_resumed_restartsSensors_withoutDisplayFrameSideEffect() {
        val locationManager: MapLocationRuntimePort = mock()
        val manager = createLifecycleManager(
            locationManager = locationManager,
            replayState = SessionState()
        )

        manager.syncCurrentOwnerState(Lifecycle.State.RESUMED)

        verify(locationManager, times(1)).restartSensorsIfNeeded()
        verify(locationManager, never()).onDisplayFrame()
    }

    @Test
    fun syncCurrentOwnerState_sameState_isIdempotent() {
        val locationManager: MapLocationRuntimePort = mock()
        val manager = createLifecycleManager(
            locationManager = locationManager,
            replayState = SessionState()
        )

        manager.syncCurrentOwnerState(Lifecycle.State.RESUMED)
        manager.syncCurrentOwnerState(Lifecycle.State.RESUMED)

        verify(locationManager, times(1)).restartSensorsIfNeeded()
        verify(locationManager, never()).onDisplayFrame()
    }

    @Test
    fun onResume_withActiveReplay_skipsSensorRestart_butStillForcesDisplayFrameSync() {
        val locationManager: MapLocationRuntimePort = mock()
        val replayState = SessionState(
            selection = Selection(
                document = DocumentRef(uri = "content://replay/test.igc")
            ),
            status = SessionStatus.PLAYING
        )
        val manager = createLifecycleManager(
            locationManager = locationManager,
            replayState = replayState
        )

        manager.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        verify(locationManager, never()).restartSensorsIfNeeded()
        verify(locationManager, times(1)).onDisplayFrame()
    }

    private fun createLifecycleManager(
        locationManager: MapLocationRuntimePort,
        replayState: SessionState
    ): MapLifecycleManager {
        return MapLifecycleManager(
            lifecycleSurface = mock<MapLifecycleSurfacePort>(),
            orientationManager = mock<MapOrientationRuntimePort>(),
            locationManager = locationManager,
            locationRenderFrameCleanup = mock<MapRenderFrameCleanupPort>(),
            replaySessionState = MutableStateFlow(replayState)
        )
    }
}
