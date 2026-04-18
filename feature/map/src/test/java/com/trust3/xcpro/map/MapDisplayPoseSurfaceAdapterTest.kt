package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Projection
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MapDisplayPoseSurfaceAdapterTest {

    @Test
    fun distancePerPixelMetersAt_returnsNormalizedMetersPerPixel() {
        val mapState = MapScreenState()
        val map = mock<MapLibreMap>()
        val mapView = mock<MapView>()
        val projection = mock<Projection>()
        mapState.mapLibreMap = map
        mapState.mapView = mapView
        whenever(map.projection).thenReturn(projection)
        whenever(mapView.pixelRatio).thenReturn(2f)
        whenever(projection.getMetersPerPixelAtLatitude(46.0)).thenReturn(8.0)

        val adapter = MapDisplayPoseSurfaceAdapter(mapState)

        assertEquals(4.0, requireNotNull(adapter.distancePerPixelMetersAt(46.0)), 0.0)
    }

    @Test
    fun distancePerPixelMetersAt_returnsNull_whenPixelRatioIsInvalid() {
        val mapState = MapScreenState()
        val map = mock<MapLibreMap>()
        val mapView = mock<MapView>()
        mapState.mapLibreMap = map
        mapState.mapView = mapView
        whenever(mapView.pixelRatio).thenReturn(0f)

        val adapter = MapDisplayPoseSurfaceAdapter(mapState)

        assertNull(adapter.distancePerPixelMetersAt(46.0))
    }

    @Test
    fun distancePerPixelMetersAt_returnsNull_whenMapHandlesAreMissing() {
        val adapter = MapDisplayPoseSurfaceAdapter(MapScreenState())

        assertNull(adapter.distancePerPixelMetersAt(46.0))
    }
}
