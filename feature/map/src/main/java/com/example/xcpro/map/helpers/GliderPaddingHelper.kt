package com.example.xcpro.map.helpers

import android.content.res.Resources
import com.example.xcpro.MapOrientationPreferences
import org.maplibre.android.maps.MapLibreMap

/**
 * Centralizes glider offset configuration (percent from bottom) and map padding updates.
 *
 * Offsets can range between 10% and 50% from the bottom, aligning with XCSoar's behavior.
 */
class GliderPaddingHelper(
    private val resources: Resources,
    private val orientationPreferences: MapOrientationPreferences
) {
    fun applyPadding(map: MapLibreMap) {
        val (top, bottom) = computePaddings()
        map.setPadding(0, top, 0, bottom)
    }

    fun paddingArray(): IntArray {
        val (top, bottom) = computePaddings()
        return intArrayOf(0, top, 0, bottom)
    }

    private fun computePaddings(): Pair<Int, Int> {
        val percentFromBottom = orientationPreferences.getGliderScreenPercent()
        val fractionFromTop = 1f - (percentFromBottom / 100f)
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        val diffPx = (screenHeight * (2f * fractionFromTop - 1f)).toInt()

        val top = if (diffPx >= 0) diffPx else 0
        val bottom = if (diffPx >= 0) 0 else -diffPx
        return top to bottom
    }
}
