package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbProximityTier
import org.maplibre.android.style.expressions.Expression

internal object AdsbProximityIconOutlinePolicy {
    private const val OUTLINED_HALO_WIDTH = 0.8f
    private const val NO_HALO_WIDTH = 0f

    fun haloWidthFor(proximityTier: AdsbProximityTier): Float = when (proximityTier) {
        AdsbProximityTier.GREEN,
        AdsbProximityTier.AMBER,
        AdsbProximityTier.RED -> OUTLINED_HALO_WIDTH
        AdsbProximityTier.NEUTRAL,
        AdsbProximityTier.EMERGENCY -> NO_HALO_WIDTH
    }

    fun haloWidthExpression(): Expression {
        val tierExpr = Expression.coalesce(
            Expression.get(AdsbGeoJsonMapper.PROP_PROXIMITY_TIER),
            Expression.literal(AdsbProximityTier.NEUTRAL.code)
        )
        return Expression.switchCase(
            Expression.eq(
                tierExpr,
                Expression.literal(AdsbProximityTier.RED.code)
            ),
            Expression.literal(OUTLINED_HALO_WIDTH),
            Expression.eq(
                tierExpr,
                Expression.literal(AdsbProximityTier.AMBER.code)
            ),
            Expression.literal(OUTLINED_HALO_WIDTH),
            Expression.eq(
                tierExpr,
                Expression.literal(AdsbProximityTier.GREEN.code)
            ),
            Expression.literal(OUTLINED_HALO_WIDTH),
            Expression.literal(NO_HALO_WIDTH)
        )
    }
}
