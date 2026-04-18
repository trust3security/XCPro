package com.trust3.xcpro.map

import androidx.lifecycle.Lifecycle
import com.trust3.xcpro.replay.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class MapLifecycleManagerForegroundRestoreTest {

    @Test
    fun backgroundThenForeground_replaysMapViewStartAndResume() {
        val lifecycleSurface = RecordingLifecycleSurface()
        val manager = MapLifecycleManager(
            lifecycleSurface = lifecycleSurface,
            orientationManager = mock<MapOrientationRuntimePort>(),
            locationManager = mock<MapLocationRuntimePort>(),
            locationRenderFrameCleanup = mock<MapRenderFrameCleanupPort>(),
            renderSurfaceDiagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L }),
            replaySessionState = MutableStateFlow(SessionState())
        )

        manager.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        manager.handleLifecycleEvent(Lifecycle.Event.ON_START)
        manager.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        manager.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        manager.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        manager.handleLifecycleEvent(Lifecycle.Event.ON_START)
        manager.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        assertEquals(1, lifecycleSurface.createCalls)
        assertEquals(2, lifecycleSurface.startCalls)
        assertEquals(2, lifecycleSurface.resumeCalls)
        assertEquals(1, lifecycleSurface.pauseCalls)
        assertEquals(1, lifecycleSurface.stopCalls)
    }

    private class RecordingLifecycleSurface : MapLifecycleSurfacePort {
        private val hostToken = Any()
        var createCalls = 0
        var startCalls = 0
        var resumeCalls = 0
        var pauseCalls = 0
        var stopCalls = 0

        override fun currentHostToken(): Any = hostToken

        override fun dispatchCreateIfPresent(): Boolean {
            createCalls += 1
            return true
        }

        override fun dispatchStartIfPresent(): Boolean {
            startCalls += 1
            return true
        }

        override fun dispatchResumeIfPresent(): Boolean {
            resumeCalls += 1
            return true
        }

        override fun dispatchPauseIfPresent(): Boolean {
            pauseCalls += 1
            return true
        }

        override fun dispatchStopIfPresent(): Boolean {
            stopCalls += 1
            return true
        }

        override fun dispatchDestroyIfPresent(): Boolean = true

        override fun captureCameraSnapshot() = Unit

        override fun clearRuntimeOverlays() = Unit

        override fun clearMapSurfaceReferences() = Unit

        override fun isMapViewReady(): Boolean = true

        override fun isMapLibreReady(): Boolean = true
    }
}
