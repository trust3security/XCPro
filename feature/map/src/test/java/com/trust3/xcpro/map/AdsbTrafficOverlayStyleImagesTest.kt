package com.trust3.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.trust3.xcpro.adsb.ADSB_ICON_BITMAP_BASE_SIZE_PX
import com.trust3.xcpro.traffic.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.Style
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdsbTrafficOverlayStyleImagesTest {

    @Test
    fun ensureAdsbTrafficOverlayStyleImages_registersLegacyUnknownDrawableForLegacyStyleId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val style: Style = mock()
        whenever(style.getImage(any())).thenReturn(null)

        ensureAdsbTrafficOverlayStyleImages(
            context = context,
            style = style,
            emergencyIconColor = 0xFFFF0000.toInt()
        )

        val styleIdCaptor = argumentCaptor<String>()
        val bitmapCaptor = argumentCaptor<Bitmap>()
        verify(style, atLeast(1)).addImage(
            styleIdCaptor.capture(),
            bitmapCaptor.capture(),
            any<Boolean>()
        )

        val styleIds = styleIdCaptor.allValues
        val addedBitmaps = bitmapCaptor.allValues

        val defaultUnknownIndex = styleIds.indexOf(AdsbAircraftIcon.Unknown.styleImageId)
        val legacyUnknownIndex = styleIds.indexOf(ADSB_ICON_STYLE_UNKNOWN_LEGACY)
        assertTrue(defaultUnknownIndex >= 0)
        assertTrue(legacyUnknownIndex >= 0)
        assertFalse(defaultUnknownIndex == legacyUnknownIndex)

        val expectedDefaultBitmap = renderAdsbDrawableBitmap(
            context = context,
            drawableId = AdsbAircraftIcon.Unknown.resId
        )
        val expectedLegacyBitmap = renderAdsbDrawableBitmap(
            context = context,
            drawableId = R.drawable.ic_adsb_unknown
        )

        assertTrue(addedBitmaps[defaultUnknownIndex].sameAs(expectedDefaultBitmap))
        assertTrue(addedBitmaps[legacyUnknownIndex].sameAs(expectedLegacyBitmap))
        assertFalse(addedBitmaps[legacyUnknownIndex].sameAs(addedBitmaps[defaultUnknownIndex]))
    }
}

private fun renderAdsbDrawableBitmap(context: Context, drawableId: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: error("Missing drawable id: $drawableId")
    val bitmap = Bitmap.createBitmap(
        ADSB_ICON_BITMAP_BASE_SIZE_PX,
        ADSB_ICON_BITMAP_BASE_SIZE_PX,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, ADSB_ICON_BITMAP_BASE_SIZE_PX, ADSB_ICON_BITMAP_BASE_SIZE_PX)
    drawable.draw(canvas)
    return bitmap
}
