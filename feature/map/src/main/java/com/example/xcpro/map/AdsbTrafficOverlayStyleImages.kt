package com.example.xcpro.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.content.ContextCompat
import com.example.xcpro.adsb.ADSB_ICON_SIZE_DEFAULT_PX
import com.example.xcpro.adsb.ui.AdsbAircraftIcon
import com.example.xcpro.adsb.ui.emergencyStyleImageId
import org.maplibre.android.maps.Style

internal fun ensureAdsbTrafficOverlayStyleImages(
    context: Context,
    style: Style,
    emergencyIconColor: Int
) {
    AdsbAircraftIcon.values().forEach { icon ->
        val normalImage = runCatching { style.getImage(icon.styleImageId) }.getOrNull()
        val emergencyId = icon.emergencyStyleImageId()
        val emergencyImage = runCatching { style.getImage(emergencyId) }.getOrNull()
        if (normalImage != null && emergencyImage != null) return@forEach

        val baseBitmap = drawableToBitmap(context = context, drawableId = icon.resId) ?: return@forEach
        if (normalImage == null) {
            style.addImage(icon.styleImageId, baseBitmap, true)
        }
        if (emergencyImage == null) {
            val emergencyBitmap = tintBitmap(source = baseBitmap, tintColor = emergencyIconColor)
            style.addImage(emergencyId, emergencyBitmap, true)
        }
    }

    val legacyEmergencyId = "${ADSB_ICON_STYLE_UNKNOWN_LEGACY}_emergency"
    val legacyNormal = runCatching { style.getImage(ADSB_ICON_STYLE_UNKNOWN_LEGACY) }.getOrNull()
    val legacyEmergency = runCatching { style.getImage(legacyEmergencyId) }.getOrNull()
    if (legacyNormal == null || legacyEmergency == null) {
        val baseBitmap = drawableToBitmap(context = context, drawableId = R.drawable.ic_adsb_unknown)
        if (baseBitmap != null) {
            if (legacyNormal == null) {
                style.addImage(ADSB_ICON_STYLE_UNKNOWN_LEGACY, baseBitmap, true)
            }
            if (legacyEmergency == null) {
                val emergencyBitmap = tintBitmap(source = baseBitmap, tintColor = emergencyIconColor)
                style.addImage(legacyEmergencyId, emergencyBitmap, true)
            }
        }
    }
}

internal fun removeAdsbTrafficOverlayStyleImages(style: Style) {
    AdsbAircraftIcon.values().forEach { icon ->
        style.removeImage(icon.styleImageId)
        style.removeImage(icon.emergencyStyleImageId())
    }
    style.removeImage(ADSB_ICON_STYLE_UNKNOWN_LEGACY)
    style.removeImage("${ADSB_ICON_STYLE_UNKNOWN_LEGACY}_emergency")
}

private fun tintBitmap(source: Bitmap, tintColor: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(
        source.width,
        source.height,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
    }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return bitmap
}

private fun drawableToBitmap(context: Context, drawableId: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
    val bitmap = Bitmap.createBitmap(
        ADSB_ICON_SIZE_DEFAULT_PX,
        ADSB_ICON_SIZE_DEFAULT_PX,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, ADSB_ICON_SIZE_DEFAULT_PX, ADSB_ICON_SIZE_DEFAULT_PX)
    drawable.draw(canvas)
    return bitmap
}
