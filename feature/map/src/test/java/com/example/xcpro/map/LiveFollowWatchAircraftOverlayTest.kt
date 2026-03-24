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
    fun watchedAircraftMarker_usesTwoTimesScaleForFriendsFlying() {
        assertEquals(2.0f, LIVE_FOLLOW_WATCH_AIRCRAFT_ICON_SCALE, 0f)
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
