package com.trust3.xcpro.map.ui

import com.trust3.xcpro.map.MapLocationRenderFrameBinder
import com.trust3.xcpro.map.MapRenderSurfaceDiagnostics
import com.trust3.xcpro.map.MapScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.maplibre.android.maps.MapView
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class MapViewHostBindingControllerTest {

    @Test
    fun attach_sameMapView_bindsAndNotifiesOnce() {
        val mapState = MapScreenState()
        val binder = mock<MapLocationRenderFrameBinder>()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
        val controller = MapViewHostBindingController(mapState, binder, diagnostics)
        val mapView = mock<MapView>()
        var onMapViewBoundCount = 0

        controller.attach(mapView) { onMapViewBoundCount += 1 }
        controller.attach(mapView) { onMapViewBoundCount += 1 }

        assertSame(mapView, mapState.mapView)
        assertEquals(1, onMapViewBoundCount)
        verify(binder, times(1)).bindRenderFrameListener(mapView)
        assertEquals(1L, diagnostics.snapshot().mapViewAttachCount)
        assertEquals(0L, diagnostics.snapshot().mapViewSwapCount)
    }

    @Test
    fun attach_newMapView_rebindsAndUpdatesReference() {
        val mapState = MapScreenState()
        val binder = mock<MapLocationRenderFrameBinder>()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
        val controller = MapViewHostBindingController(mapState, binder, diagnostics)
        val firstView = mock<MapView>()
        val secondView = mock<MapView>()

        controller.attach(firstView) {}
        controller.attach(secondView) {}

        assertSame(secondView, mapState.mapView)
        verify(binder).bindRenderFrameListener(firstView)
        verify(binder).bindRenderFrameListener(secondView)
        assertEquals(2L, diagnostics.snapshot().mapViewAttachCount)
        assertEquals(1L, diagnostics.snapshot().mapViewSwapCount)
    }

    @Test
    fun clear_matchingView_keepsMapStateReferenceForHostReuse() {
        val mapState = MapScreenState()
        val binder = mock<MapLocationRenderFrameBinder>()
        val diagnostics = MapRenderSurfaceDiagnostics(nowMonoMs = { 123L })
        val controller = MapViewHostBindingController(mapState, binder, diagnostics)
        val mapView = mock<MapView>()

        controller.attach(mapView) {}
        controller.clear(mapView)

        assertSame(mapView, mapState.mapView)
        assertEquals(1L, diagnostics.snapshot().mapViewClearCount)
    }
}
