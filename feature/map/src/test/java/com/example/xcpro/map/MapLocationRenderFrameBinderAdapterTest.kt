package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapLocationRenderFrameBinderAdapterTest {

    @Test
    fun bindRenderFrameListener_readsUseRenderFrameSyncLiveWithoutRebinding() {
        val harness = MapLocationRenderFrameBinderHarness()
        var useRenderFrameSync = false
        val binder = MapLocationRenderFrameBinderAdapter(
            useRenderFrameSync = { useRenderFrameSync },
            onRenderFrame = { harness.dispatchCount += 1 },
            renderSurfaceDiagnostics = harness.diagnostics
        )

        binder.bindRenderFrameListener(harness.mapView)
        harness.fireFrame()
        assertEquals(0, harness.dispatchCount)

        useRenderFrameSync = true
        harness.fireFrame()
        assertEquals(1, harness.dispatchCount)

        useRenderFrameSync = false
        harness.fireFrame()
        assertEquals(1, harness.dispatchCount)
    }

    private class MapLocationRenderFrameBinderHarness {
        val mapView: MapView = mock()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
        var listener: MapView.OnWillStartRenderingFrameListener? = null
        var dispatchCount: Int = 0

        init {
            whenever(mapView.addOnWillStartRenderingFrameListener(any())).thenAnswer { invocation ->
                listener = invocation.getArgument(0)
                Unit
            }
        }

        fun fireFrame() {
            val currentListener = listener
            assertNotNull(currentListener)
            currentListener!!.onWillStartRenderingFrame()
        }
    }
}
