package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveFollowWatchAircraftOverlayTest {

    @Test
    fun createLiveFollowWatchAircraftBitmap_usesAppGliderDrawable() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val actual = createLiveFollowWatchAircraftBitmap(context)
        val expected = renderDrawableBitmap(
            context = context,
            drawableId = LIVE_FOLLOW_WATCH_AIRCRAFT_DRAWABLE_RES_ID,
            iconSizePx = LIVE_FOLLOW_WATCH_AIRCRAFT_ICON_SIZE_PX
        )

        assertTrue(actual.sameAs(expected))
    }

    @Test
    fun resolveLiveFollowWatchAircraftRotation_normalizesTrackForMarkerRotation() {
        assertEquals(185f, resolveLiveFollowWatchAircraftRotation(185.0), 0f)
        assertEquals(350f, resolveLiveFollowWatchAircraftRotation(-10.0), 0f)
        assertEquals(5f, resolveLiveFollowWatchAircraftRotation(725.0), 0f)
        assertEquals(0f, resolveLiveFollowWatchAircraftRotation(null), 0f)
    }

    @Test
    fun resolveLiveFollowWatchAircraftScale_usesRequestedZoomBands() {
        assertEquals(1.60f, resolveLiveFollowWatchAircraftScale(10.5f), 0f)
        assertEquals(1.30f, resolveLiveFollowWatchAircraftScale(9.25f), 0f)
        assertEquals(1.00f, resolveLiveFollowWatchAircraftScale(8.25f), 0f)
        assertEquals(0.80f, resolveLiveFollowWatchAircraftScale(7.0f), 0f)
    }

    @Test
    fun closeZoomScale_matchesDefaultCloseZoomConstant() {
        assertEquals(
            LIVE_FOLLOW_WATCH_AIRCRAFT_CLOSE_ICON_SCALE,
            resolveLiveFollowWatchAircraftScale(10.5f),
            0f
        )
    }

    @Test
    fun reapplyCurrentStyle_rerendersLastKnownStateAfterStyleSwap() {
        val fixture = createRuntimeFixture()
        val state = LiveFollowWatchAircraftOverlayState(
            shareCode = "TEST1",
            latitudeDeg = -35.0,
            longitudeDeg = 149.0,
            trackDeg = 95.0
        )

        fixture.overlay.render(state)
        fixture.currentStyle = fixture.secondStyle
        fixture.overlay.reapplyCurrentStyle()

        verify(fixture.secondSource).setGeoJson(any<org.maplibre.geojson.Feature>())
    }
}

private fun renderDrawableBitmap(
    context: Context,
    drawableId: Int,
    iconSizePx: Int
): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableId)
        ?: error("Missing drawable id: $drawableId")
    val bitmap = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, iconSizePx, iconSizePx)
    drawable.draw(canvas)
    return bitmap
}

private fun createRuntimeFixture(): RuntimeFixture {
    val context: Context = mock()
    val map: MapLibreMap = mock()
    val firstStyle: Style = mock()
    val secondStyle: Style = mock()
    val firstSource: GeoJsonSource = mock()
    val secondSource: GeoJsonSource = mock()
    val firstLayer: SymbolLayer = mock()
    val secondLayer: SymbolLayer = mock()
    val bitmap: Bitmap = mock()
    var currentStyle: Style = firstStyle

    whenever(map.cameraPosition).thenReturn(
        CameraPosition.Builder()
            .zoom(10.5)
            .build()
    )
    whenever(map.style).thenAnswer { currentStyle }
    whenever(firstStyle.getImage(any<String>())).thenReturn(bitmap)
    whenever(secondStyle.getImage(any<String>())).thenReturn(bitmap)
    whenever(firstStyle.getSourceAs<GeoJsonSource>(any())).thenReturn(firstSource)
    whenever(secondStyle.getSourceAs<GeoJsonSource>(any())).thenReturn(secondSource)
    whenever(firstStyle.getLayerAs<SymbolLayer>(any())).thenReturn(firstLayer)
    whenever(secondStyle.getLayerAs<SymbolLayer>(any())).thenReturn(secondLayer)

    return RuntimeFixture(
        overlay = LiveFollowWatchAircraftOverlay(map = map, context = context),
        secondStyle = secondStyle,
        secondSource = secondSource,
        currentStyleSetter = { style -> currentStyle = style },
        currentStyleGetter = { currentStyle }
    )
}

private data class RuntimeFixture(
    val overlay: LiveFollowWatchAircraftOverlay,
    val secondStyle: Style,
    val secondSource: GeoJsonSource,
    val currentStyleSetter: (Style) -> Unit,
    val currentStyleGetter: () -> Style
) {
    var currentStyle: Style
        get() = currentStyleGetter()
        set(value) {
            currentStyleSetter(value)
        }
}
