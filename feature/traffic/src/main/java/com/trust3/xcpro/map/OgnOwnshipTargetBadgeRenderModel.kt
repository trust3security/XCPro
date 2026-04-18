package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.AltitudeUnit
import com.trust3.xcpro.common.units.DistanceM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.common.units.UnitsFormatter
import com.trust3.xcpro.common.units.UnitsPreferences

internal data class OgnOwnshipTargetBadgeRenderRequest(
    val enabled: Boolean,
    val target: OgnTrafficTarget?,
    val ownshipAltitudeMeters: Double?,
    val altitudeUnit: AltitudeUnit,
    val unitsPreferences: UnitsPreferences
)

internal data class OgnOwnshipTargetBadgeRenderModel(
    val labelText: String,
    val textColorHex: String
)

internal object OgnOwnshipTargetBadgeRenderModelBuilder {
    const val UNKNOWN_DISTANCE_TEXT = "--"
    const val UNKNOWN_SPEED_TEXT = "--"
    const val ABOVE_OR_LEVEL_TEXT_COLOR_HEX = "#0B2E59"
    const val BELOW_TEXT_COLOR_HEX = "#C62828"

    fun build(request: OgnOwnshipTargetBadgeRenderRequest): OgnOwnshipTargetBadgeRenderModel? {
        if (!request.enabled) return null
        if (request.target == null) return null

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
        val speedText = request.target.groundSpeedMps
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let {
                UnitsFormatter.speed(
                    speed = SpeedMs(it),
                    preferences = request.unitsPreferences
                ).text
            }
            ?: UNKNOWN_SPEED_TEXT
        return OgnOwnshipTargetBadgeRenderModel(
            labelText = "$distanceText\n$deltaText | $speedText",
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
