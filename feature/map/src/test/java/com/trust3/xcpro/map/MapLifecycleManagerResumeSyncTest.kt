package com.trust3.xcpro.map

import androidx.lifecycle.Lifecycle
import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.replay.Selection
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapLifecycleManagerResumeSyncTest {

    @Test
    fun onResume_restartsSensors_andForcesImmediateDisplayFrameSync() {
        val locationManager: MapLocationRuntimePort = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
        val manager = createLifecycleManager(
            locationManager = locationManager,
            replayState = SessionState(),
            renderSurfaceDiagnostics = diagnostics
        )

        manager.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        verify(locationManager, times(1)).restartSensorsIfNeeded()
        verify(locationManager, times(1)).onDisplayFrame()
        assertEquals(1L, diagnostics.snapshot().lifecycleResumeForcedFrameCount)
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
    fun syncCurrentOwnerState_resumed_retriesLifecycleAfterHostAttaches() {
        val locationManager: MapLocationRuntimePort = mock()
        val lifecycleSurface = FakeLifecycleSurface()
        val manager = createLifecycleManager(
            locationManager = locationManager,
            replayState = SessionState(),
            lifecycleSurface = lifecycleSurface
        )

        manager.syncCurrentOwnerState(Lifecycle.State.RESUMED)

        assertEquals(0, lifecycleSurface.createCalls)
        assertEquals(0, lifecycleSurface.startCalls)
        assertEquals(0, lifecycleSurface.resumeCalls)

        lifecycleSurface.hostToken = Any()
        manager.syncCurrentOwnerState(Lifecycle.State.RESUMED)

        assertEquals(1, lifecycleSurface.createCalls)
        assertEquals(1, lifecycleSurface.startCalls)
        assertEquals(1, lifecycleSurface.resumeCalls)
        verify(locationManager, times(2)).restartSensorsIfNeeded()
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
        replayState: SessionState,
        lifecycleSurface: MapLifecycleSurfacePort = mock<MapLifecycleSurfacePort>(),
        renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics =
            MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
    ): MapLifecycleManager {
        return MapLifecycleManager(
            lifecycleSurface = lifecycleSurface,
            orientationManager = mock<MapOrientationRuntimePort>(),
            locationManager = locationManager,
            locationRenderFrameCleanup = mock<MapRenderFrameCleanupPort>(),
            renderSurfaceDiagnostics = renderSurfaceDiagnostics,
            replaySessionState = MutableStateFlow(replayState)
        )
    }

    private class FakeLifecycleSurface : MapLifecycleSurfacePort {
        var hostToken: Any? = null
        var createCalls: Int = 0
        var startCalls: Int = 0
        var resumeCalls: Int = 0

        override fun currentHostToken(): Any? = hostToken

        override fun dispatchCreateIfPresent(): Boolean {
            if (hostToken == null) return false
            createCalls += 1
            return true
        }

        override fun dispatchStartIfPresent(): Boolean {
            if (hostToken == null) return false
            startCalls += 1
            return true
        }

        override fun dispatchResumeIfPresent(): Boolean {
            if (hostToken == null) return false
            resumeCalls += 1
            return true
        }

        override fun dispatchPauseIfPresent(): Boolean = hostToken != null

        override fun dispatchStopIfPresent(): Boolean = hostToken != null

        override fun dispatchDestroyIfPresent(): Boolean = hostToken != null

        override fun captureCameraSnapshot() = Unit

        override fun clearRuntimeOverlays() = Unit

        override fun clearMapSurfaceReferences() = Unit

        override fun isMapViewReady(): Boolean = hostToken != null

        override fun isMapLibreReady(): Boolean = false
    }
}
