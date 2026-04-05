package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.maplibre.android.maps.MapView
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RenderFrameSyncTest {

    @Test
    fun offMainRenderCallbacks_collapseToOnePostedDispatch() {
        val harness = RenderFrameSyncHarness()
        val sync = RenderFrameSync(
            isEnabled = { true },
            onRenderFrame = { harness.dispatchCount += 1 },
            diagnostics = harness.diagnostics,
            isMainThread = { false }
        )

        sync.bind(harness.mapView)
        harness.fireFrame()
        harness.fireFrame()

        assertEquals(1, harness.pendingPostCount())
        assertEquals(0, harness.dispatchCount)
        assertEquals(2L, harness.diagnostics.snapshot().renderFrameCallbackCount)
        assertEquals(1L, harness.diagnostics.snapshot().postedDispatchScheduledCount)
        assertEquals(1L, harness.diagnostics.snapshot().postedDispatchDroppedCount)

        harness.runNextPosted()

        assertEquals(1, harness.dispatchCount)
        assertEquals(0, harness.pendingPostCount())
    }

    @Test
    fun mainThreadRenderCallback_cancelsPendingPostedDispatch() {
        val harness = RenderFrameSyncHarness()
        var isMainThread = false
        val sync = RenderFrameSync(
            isEnabled = { true },
            onRenderFrame = { harness.dispatchCount += 1 },
            diagnostics = harness.diagnostics,
            isMainThread = { isMainThread }
        )

        sync.bind(harness.mapView)
        harness.fireFrame()
        assertEquals(1, harness.pendingPostCount())

        isMainThread = true
        harness.fireFrame()

        assertEquals(1, harness.dispatchCount)
        assertEquals(0, harness.pendingPostCount())
        assertEquals(1L, harness.diagnostics.snapshot().immediateDispatchCount)
        assertEquals(1L, harness.diagnostics.snapshot().pendingDispatchClearedCount)
    }

    @Test
    fun unbind_clearsPendingPostedDispatch() {
        val harness = RenderFrameSyncHarness()
        val sync = RenderFrameSync(
            isEnabled = { true },
            onRenderFrame = { harness.dispatchCount += 1 },
            diagnostics = harness.diagnostics,
            isMainThread = { false }
        )

        sync.bind(harness.mapView)
        harness.fireFrame()
        assertEquals(1, harness.pendingPostCount())

        sync.unbind()

        assertEquals(0, harness.pendingPostCount())
        verify(harness.mapView).removeCallbacks(any())
        assertEquals(1L, harness.diagnostics.snapshot().pendingDispatchClearedCount)
    }

    @Test
    fun bind_newMapView_rebindsListenerOnce() {
        val first = RenderFrameSyncHarness()
        val second = RenderFrameSyncHarness()
        val sync = RenderFrameSync(
            isEnabled = { true },
            onRenderFrame = {},
            diagnostics = first.diagnostics,
            isMainThread = { true }
        )

        sync.bind(first.mapView)
        val firstListener = first.listener
        sync.bind(second.mapView)
        val secondListener = second.listener

        verify(first.mapView).removeOnWillStartRenderingFrameListener(firstListener!!)
        verify(second.mapView).addOnWillStartRenderingFrameListener(secondListener!!)
    }

    private class RenderFrameSyncHarness {
        val mapView: MapView = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
        var listener: MapView.OnWillStartRenderingFrameListener? = null
        var dispatchCount: Int = 0
        private val postedCallbacks = ArrayDeque<Runnable>()

        init {
            whenever(mapView.addOnWillStartRenderingFrameListener(any())).thenAnswer { invocation ->
                listener = invocation.getArgument(0)
                Unit
            }
            whenever(mapView.removeOnWillStartRenderingFrameListener(any())).thenAnswer { invocation ->
                if (listener === invocation.getArgument(0)) {
                    listener = null
                }
                Unit
            }
            whenever(mapView.post(any())).thenAnswer { invocation ->
                postedCallbacks.addLast(invocation.getArgument(0))
                true
            }
            whenever(mapView.removeCallbacks(any())).thenAnswer { invocation ->
                postedCallbacks.remove(invocation.getArgument(0))
                true
            }
        }

        fun fireFrame() {
            val currentListener = listener
            assertNotNull(currentListener)
            currentListener!!.onWillStartRenderingFrame()
        }

        fun runNextPosted() {
            postedCallbacks.removeFirstOrNull()?.run()
        }

        fun pendingPostCount(): Int = postedCallbacks.size
    }
}
