package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences

internal data class OgnOwnshipTargetBadgeRenderRequest(
    val enabled: Boolean,
    val target: OgnTrafficTarget?,
    val ownshipAltitudeMeters: Double?,
    val altitudeUnit: AltitudeUnit,
    val unitsPreferences: UnitsPreferences,
    val targetOnScreen: Boolean
)

internal data class OgnOwnshipTargetBadgeRenderModel(
    val labelText: String,
    val textColorHex: String
)

internal object OgnOwnshipTargetBadgeRenderModelBuilder {
    const val UNKNOWN_DISTANCE_TEXT = "--"
    const val ABOVE_OR_LEVEL_TEXT_COLOR_HEX = "#0B2E59"
    const val BELOW_TEXT_COLOR_HEX = "#C62828"

    fun build(request: OgnOwnshipTargetBadgeRenderRequest): OgnOwnshipTargetBadgeRenderModel? {
        if (!request.enabled) return null
        if (request.target == null) return null
        if (request.targetOnScreen) return null

        val deltaMeters = relativeAltitudeDeltaMeters(
            targetAltitudeMeters = request.target.altitudeMeters,
            ownshipAltitudeMeters = request.ownshipAltitudeMeters
        )
        val distanceText = request.target.distanceMeters
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let {
                UnitsFormatter.distance(
                    distance = DistanceM(it),
                    preferences = request.unitsPreferences
                ).text
            }
            ?: UNKNOWN_DISTANCE_TEXT
        val deltaText = OgnRelativeAltitudeLabelFormatter.formatDelta(
            deltaMeters = deltaMeters,
            altitudeUnit = request.altitudeUnit
        )
        return OgnOwnshipTargetBadgeRenderModel(
            labelText = "$distanceText\n$deltaText",
            textColorHex = resolveTextColorHex(deltaMeters)
        )
    }

    private fun relativeAltitudeDeltaMeters(
        targetAltitudeMeters: Double?,
        ownshipAltitudeMeters: Double?
    ): Double? {
        val targetAltitude = targetAltitudeMeters?.takeIf { it.isFinite() } ?: return null
        val ownshipAltitude = ownshipAltitudeMeters?.takeIf { it.isFinite() } ?: return null
        return targetAltitude - ownshipAltitude
    }

    private fun resolveTextColorHex(deltaMeters: Double?): String =
        if (deltaMeters != null && deltaMeters < 0.0) BELOW_TEXT_COLOR_HEX else ABOVE_OR_LEVEL_TEXT_COLOR_HEX
}
