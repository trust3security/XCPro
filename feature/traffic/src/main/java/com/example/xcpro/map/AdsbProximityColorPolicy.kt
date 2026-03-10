package com.example.xcpro.map

import android.graphics.Color

import org.maplibre.android.style.expressions.Expression

object AdsbProximityColorPolicy {
    const val GREEN_HEX = "#2E7D32"
    const val AMBER_HEX = "#FF8F00"
    const val RED_HEX = "#FF1744"
    const val NEUTRAL_HEX = "#A7B4C4"
    const val EMERGENCY_HEX = "#8E24AA"

    fun colorHexFor(proximityTier: AdsbProximityTier): String {
        return when (proximityTier) {
            AdsbProximityTier.EMERGENCY -> EMERGENCY_HEX
            AdsbProximityTier.NEUTRAL -> NEUTRAL_HEX
            AdsbProximityTier.RED -> RED_HEX
            AdsbProximityTier.AMBER -> AMBER_HEX
            AdsbProximityTier.GREEN -> GREEN_HEX
        }
    }

    fun expression(): Expression {
        val tierExpr = Expression.coalesce(
            Expression.get(AdsbGeoJsonMapper.PROP_PROXIMITY_TIER),
            Expression.literal(AdsbProximityTier.NEUTRAL.code)
        )
        return Expression.switchCase(
            Expression.eq(
                tierExpr,
                Expression.literal(AdsbProximityTier.EMERGENCY.code)
            ),
            Expression.color(Color.parseColor(EMERGENCY_HEX)),
            Expression.eq(
                tierExpr,
                Expression.literal(AdsbProximityTier.NEUTRAL.code)
            ),
            Expression.color(Color.parseColor(NEUTRAL_HEX)),
            Expression.eq(
                tierExpr,
                Expression.literal(AdsbProximityTier.RED.code)
            ),
            Expression.color(Color.parseColor(RED_HEX)),
            Expression.eq(
                tierExpr,
                Expression.literal(AdsbProximityTier.AMBER.code)
            ),
            Expression.color(Color.parseColor(AMBER_HEX)),
            Expression.color(Color.parseColor(GREEN_HEX))
        )
    }
}
