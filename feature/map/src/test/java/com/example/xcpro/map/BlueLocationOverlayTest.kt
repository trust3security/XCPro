package com.example.xcpro.map

import android.graphics.PointF
import android.content.Context
import android.graphics.Bitmap
import com.example.xcpro.common.orientation.MapOrientationMode
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Projection
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.mockito.Mockito.clearInvocations
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BlueLocationOverlayTest {

    @Test
    fun setViewportMetrics_knownLocation_keepsReducedScaleWithoutRewritingLayerOrSource() {
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

        fixture.overlay.setViewportMetrics(
            MapCameraViewportMetrics(
                widthPx = 1_000,
                heightPx = 1_000,
                pixelRatio = 1f
            ),
            distancePerPixelMeters = 100.0
        )

        verifyNoInteractions(fixture.source, fixture.layer)
        assertCurrentIconScale(fixture.overlay, 0.75f)
    }

    @Test
    fun updateLocation_recomputesScaleWhenOwnshipMovesAcrossScreen() {
        val fixture = createFixture()
        val closeLocation = LatLng(-35.4, 149.0)
        val wideLocation = LatLng(-35.0, 149.0)

        fixture.overlay.initialize()
        fixture.overlay.setViewportMetrics(
            MapCameraViewportMetrics(
                widthPx = 1_000,
                heightPx = 1_000,
                pixelRatio = 1f
            ),
            distancePerPixelMeters = 100.0
        )

        fixture.overlay.updateLocation(
            location = closeLocation,
            gpsTrack = 90.0,
            headingDeg = 100.0,
            mapBearing = 10.0,
            orientationMode = MapOrientationMode.NORTH_UP
        )
        assertCurrentIconScale(fixture.overlay, 0.75f)

        fixture.overlay.updateLocation(
            location = wideLocation,
            gpsTrack = 90.0,
            headingDeg = 100.0,
            mapBearing = 10.0,
            orientationMode = MapOrientationMode.NORTH_UP
        )

        assertCurrentIconScale(fixture.overlay, 0.75f)
    }

    @Test
    fun initialize_afterStyleSwap_reappliesLastKnownLocationAndScale() {
        val fixture = createFixture()
        val location = LatLng(-35.0, 149.0)

        fixture.overlay.initialize()
        fixture.overlay.setViewportMetrics(
            MapCameraViewportMetrics(
                widthPx = 1_000,
                heightPx = 1_000,
                pixelRatio = 1f
            ),
            distancePerPixelMeters = 100.0
        )
        fixture.overlay.updateLocation(
            location = location,
            gpsTrack = 90.0,
            headingDeg = 100.0,
            mapBearing = 10.0,
            orientationMode = MapOrientationMode.NORTH_UP
        )
        clearInvocations(fixture.secondSource, fixture.secondLayer)

        fixture.currentStyle = fixture.secondStyle
        fixture.overlay.initialize()

        verify(fixture.secondSource).setGeoJson(any<org.maplibre.geojson.Feature>())
        verify(fixture.secondLayer, atLeastOnce()).setProperties(any())
        assertCurrentIconScale(fixture.overlay, 0.75f)
    }

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
        val projection: Projection = mock()
        val style: Style = mock()
        val secondStyle: Style = mock()
        val source: GeoJsonSource = mock()
        val secondSource: GeoJsonSource = mock()
        val layer: SymbolLayer = mock()
        val secondLayer: SymbolLayer = mock()
        val bitmap: Bitmap = mock()
        var currentStyle: Style = style
        whenever(map.style).thenAnswer { currentStyle }
        whenever(map.projection).thenReturn(projection)
        whenever(style.getImage(any<String>())).thenReturn(bitmap)
        whenever(secondStyle.getImage(any<String>())).thenReturn(bitmap)
        whenever(style.getSourceAs<GeoJsonSource>(any())).thenReturn(source)
        whenever(secondStyle.getSourceAs<GeoJsonSource>(any())).thenReturn(secondSource)
        whenever(style.getLayerAs<SymbolLayer>(any())).thenReturn(layer)
        whenever(secondStyle.getLayerAs<SymbolLayer>(any())).thenReturn(secondLayer)
        doAnswer { 100.0 }.whenever(projection).getMetersPerPixelAtLatitude(any())
        doAnswer { invocation ->
            val location = invocation.getArgument<LatLng>(0)
            when (location.latitude) {
                -35.4 -> PointF(40f, 500f)
                -35.0 -> PointF(100f, 500f)
                else -> PointF(100f, 500f)
            }
        }.whenever(projection).toScreenLocation(any<LatLng>())
        return OverlayFixture(
            overlay = BlueLocationOverlay(context, map),
            source = source,
            layer = layer,
            secondStyle = secondStyle,
            secondSource = secondSource,
            secondLayer = secondLayer,
            currentStyleSetter = { styleToUse -> currentStyle = styleToUse },
            currentStyleGetter = { currentStyle }
        )
    }

    private data class OverlayFixture(
        val overlay: BlueLocationOverlay,
        val source: GeoJsonSource,
        val layer: SymbolLayer,
        val secondStyle: Style,
        val secondSource: GeoJsonSource,
        val secondLayer: SymbolLayer,
        val currentStyleSetter: (Style) -> Unit,
        val currentStyleGetter: () -> Style
    ) {
        var currentStyle: Style
            get() = currentStyleGetter()
            set(value) {
                currentStyleSetter(value)
            }
    }

    private fun assertCurrentIconScale(
        overlay: BlueLocationOverlay,
        expected: Float
    ) {
        val field = BlueLocationOverlay::class.java.getDeclaredField("currentIconScale")
        field.isAccessible = true
        val actual = field.getFloat(overlay)
        org.junit.Assert.assertEquals(expected, actual, 0f)
    }
}
