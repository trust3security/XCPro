package com.example.xcpro.map

import android.graphics.Color
import org.maplibre.android.style.expressions.Expression

internal object AdsbProximityColorPolicy {
    private const val DISTANCE_FALLBACK_METERS = 100_000.0
    private const val DISTANCE_RED_THRESHOLD_METERS = 2_000.0
    private const val DISTANCE_AMBER_THRESHOLD_METERS = 5_000.0

    internal const val GREEN_HEX = "#2E7D32"
    internal const val AMBER_HEX = "#F9A825"
    internal const val RED_HEX = "#E53935"
    internal const val NEUTRAL_HEX = "#A7B4C4"
    internal const val EMERGENCY_HEX = "#8E24AA"

    fun colorHexFor(
        distanceMeters: Double?,
        hasOwnshipReference: Boolean,
        isEmergency: Boolean
    ): String {
        if (isEmergency) return EMERGENCY_HEX
        if (!hasOwnshipReference) return NEUTRAL_HEX

        val distance = distanceMeters?.takeIf { it.isFinite() } ?: DISTANCE_FALLBACK_METERS
        return when {
            distance <= DISTANCE_RED_THRESHOLD_METERS -> RED_HEX
            distance <= DISTANCE_AMBER_THRESHOLD_METERS -> AMBER_HEX
            else -> GREEN_HEX
        }
    }

    fun expression(): Expression {
        val distanceExpr = Expression.coalesce(
            Expression.get(AdsbGeoJsonMapper.PROP_DISTANCE_M),
            Expression.literal(DISTANCE_FALLBACK_METERS)
        )
        return Expression.switchCase(
            Expression.eq(
                Expression.get(AdsbGeoJsonMapper.PROP_IS_EMERGENCY),
                Expression.literal(1)
            ),
            Expression.color(Color.parseColor(EMERGENCY_HEX)),
            Expression.eq(
                Expression.get(AdsbGeoJsonMapper.PROP_HAS_OWNSHIP_REF),
                Expression.literal(0)
            ),
            Expression.color(Color.parseColor(NEUTRAL_HEX)),
            Expression.lte(distanceExpr, Expression.literal(DISTANCE_RED_THRESHOLD_METERS)),
            Expression.color(Color.parseColor(RED_HEX)),
            Expression.lte(distanceExpr, Expression.literal(DISTANCE_AMBER_THRESHOLD_METERS)),
            Expression.color(Color.parseColor(AMBER_HEX)),
            Expression.color(Color.parseColor(GREEN_HEX))
        )
    }
}
