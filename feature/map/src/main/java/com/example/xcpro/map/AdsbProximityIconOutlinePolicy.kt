package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbProximityTier
import org.maplibre.android.style.expressions.Expression

internal object AdsbProximityIconOutlinePolicy {
    private const val OUTLINED_HALO_WIDTH = 0.8f

    @Suppress("UNUSED_PARAMETER")
    fun haloWidthFor(proximityTier: AdsbProximityTier): Float = OUTLINED_HALO_WIDTH

    fun haloWidthExpression(): Expression = Expression.literal(OUTLINED_HALO_WIDTH)
}
