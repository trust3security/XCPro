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
