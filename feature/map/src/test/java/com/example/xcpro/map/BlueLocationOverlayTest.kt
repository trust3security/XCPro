package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import com.example.xcpro.common.orientation.MapOrientationMode
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.mockito.Mockito.clearInvocations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class BlueLocationOverlayTest {

    @Test
    fun updateLocation_samePose_isNoOpAfterFirstRender() {
        val fixture = createFixture()
        val location = LatLng(-35.0, 149.0)

        fixture.overlay.initialize()
        fixture.overlay.updateLocation(
            location = location,
            gpsTrack = 90.0,
            headingDeg = 100.0,
            mapBearing = 10.0,
            orientationMode = MapOrientationMode.NORTH_UP
        )

        clearInvocations(fixture.source, fixture.layer)

        fixture.overlay.updateLocation(
            location = location,
            gpsTrack = 90.0,
            headingDeg = 100.0,
            mapBearing = 10.0,
            orientationMode = MapOrientationMode.NORTH_UP
        )

        verifyNoInteractions(fixture.source, fixture.layer)
    }

    @Test
    fun setVisible_sameValue_isNoOp() {
        val fixture = createFixture()

        fixture.overlay.initialize()
        fixture.overlay.setVisible(true)
        clearInvocations(fixture.layer)

        fixture.overlay.setVisible(true)

        verifyNoInteractions(fixture.layer)
    }

    @Test
    fun cleanup_clearsCachedPoseSoSamePoseRendersAfterReinitialize() {
        val fixture = createFixture()
        val location = LatLng(-35.0, 149.0)

        fixture.overlay.initialize()
        fixture.overlay.updateLocation(
            location = location,
            gpsTrack = 90.0,
            headingDeg = 100.0,
            mapBearing = 10.0,
            orientationMode = MapOrientationMode.NORTH_UP
        )
        fixture.overlay.cleanup()

        clearInvocations(fixture.source, fixture.layer)

        fixture.overlay.initialize()
        fixture.overlay.updateLocation(
            location = location,
            gpsTrack = 90.0,
            headingDeg = 100.0,
            mapBearing = 10.0,
            orientationMode = MapOrientationMode.NORTH_UP
        )

        verify(fixture.source).setGeoJson(any<org.maplibre.geojson.Feature>())
    }

    private fun createFixture(): OverlayFixture {
        val context: Context = mock()
        val map: MapLibreMap = mock()
        val style: Style = mock()
        val source: GeoJsonSource = mock()
        val layer: SymbolLayer = mock()
        val bitmap: Bitmap = mock()
        whenever(map.style).thenReturn(style)
        whenever(style.getImage(any<String>())).thenReturn(bitmap)
        whenever(style.getSourceAs<GeoJsonSource>(any())).thenReturn(source)
        whenever(style.getLayerAs<SymbolLayer>(any())).thenReturn(layer)
        return OverlayFixture(
            overlay = BlueLocationOverlay(context, map),
            source = source,
            layer = layer
        )
    }

    private data class OverlayFixture(
        val overlay: BlueLocationOverlay,
        val source: GeoJsonSource,
        val layer: SymbolLayer
    )
}
