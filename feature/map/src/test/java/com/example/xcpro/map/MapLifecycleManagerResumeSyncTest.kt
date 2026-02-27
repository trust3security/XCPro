package com.example.xcpro.map

import androidx.lifecycle.Lifecycle
import com.example.xcpro.MapOrientationManager
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
        val locationManager: LocationManager = mock()
        val manager = createLifecycleManager(
            locationManager = locationManager,
            replayState = SessionState()
        )

        manager.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        verify(locationManager, times(1)).restartSensorsIfNeeded()
        verify(locationManager, times(1)).onDisplayFrame()
    }

    @Test
    fun syncCurrentOwnerState_resumed_forcesImmediateDisplayFrameSync() {
        val locationManager: LocationManager = mock()
        val manager = createLifecycleManager(
            locationManager = locationManager,
            replayState = SessionState()
        )

        manager.syncCurrentOwnerState(Lifecycle.State.RESUMED)

        verify(locationManager, times(1)).restartSensorsIfNeeded()
        verify(locationManager, times(1)).onDisplayFrame()
    }

    @Test
    fun onResume_withActiveReplay_skipsSensorRestart_butStillForcesDisplayFrameSync() {
        val locationManager: LocationManager = mock()
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
        locationManager: LocationManager,
        replayState: SessionState
    ): MapLifecycleManager {
        return MapLifecycleManager(
            mapState = MapScreenState(),
            orientationManager = mock<MapOrientationManager>(),
            locationManager = locationManager,
            replaySessionState = MutableStateFlow(replayState),
            stateActions = mock<MapStateActions>()
        )
    }
}

