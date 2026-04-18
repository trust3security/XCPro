package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.AltitudeUnit


data class OgnRelativeAltitudeFeatureMapperInput(
    val targetAltitudeMeters: Double?,
    val ownshipAltitudeMeters: Double?,
    val distanceMeters: Double?,
    val altitudeUnit: AltitudeUnit,
    val icon: OgnAircraftIcon,
    val defaultIconStyleImageId: String,
    val gliderAboveIconStyleImageId: String,
    val gliderBelowIconStyleImageId: String,
    val gliderNearIconStyleImageId: String,
    val gliderCloseRedIconStyleImageId: String,
    val secondaryLabelText: String,
    val speedText: String? = null
)

data class OgnRelativeAltitudeFeatureMapping(
    val iconStyleImageId: String,
    val topLabel: String,
    val bottomLabel: String,
    val band: OgnRelativeAltitudeBand,
    val deltaText: String,
    val secondaryLabelText: String
)

object OgnRelativeAltitudeFeatureMapper {
    fun map(input: OgnRelativeAltitudeFeatureMapperInput): OgnRelativeAltitudeFeatureMapping {
        val deltaMeters = computeDeltaMeters(
            targetAltitudeMeters = input.targetAltitudeMeters,
            ownshipAltitudeMeters = input.ownshipAltitudeMeters
        )
        val useCloseRedIcon = input.icon == OgnAircraftIcon.Glider &&
            OgnGliderCloseProximityColorPolicy.shouldUseRed(
                distanceMeters = input.distanceMeters,
                deltaMeters = deltaMeters
            )
        val band = OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters)
        val deltaText = OgnRelativeAltitudeLabelFormatter.formatDelta(
            deltaMeters = deltaMeters,
            altitudeUnit = input.altitudeUnit
        )
        val relativeDetailText = buildRelativeDetailText(
            deltaText = deltaText,
            speedText = input.speedText
        )
        val secondaryLabel = input.secondaryLabelText
            .trim()
            .ifBlank { OgnIdentifierDistanceLabelMapper.UNKNOWN_IDENTIFIER }
        val layout = OgnRelativeAltitudeLabelLayoutPolicy.resolve(band)
        val topLabel: String
        val bottomLabel: String
        if (layout.deltaOnTop) {
            topLabel = relativeDetailText
            bottomLabel = secondaryLabel
        } else {
            topLabel = secondaryLabel
            bottomLabel = relativeDetailText
        }

        val iconStyleImageId = when {
            useCloseRedIcon -> input.gliderCloseRedIconStyleImageId
            input.icon != OgnAircraftIcon.Glider -> input.defaultIconStyleImageId
            band == OgnRelativeAltitudeBand.ABOVE -> input.gliderAboveIconStyleImageId
            band == OgnRelativeAltitudeBand.BELOW -> input.gliderBelowIconStyleImageId
            else -> input.gliderNearIconStyleImageId
        }

        return OgnRelativeAltitudeFeatureMapping(
            iconStyleImageId = iconStyleImageId,
            topLabel = topLabel,
            bottomLabel = bottomLabel,
            band = band,
            deltaText = deltaText,
            secondaryLabelText = secondaryLabel
        )
    }

    private fun computeDeltaMeters(
        targetAltitudeMeters: Double?,
        ownshipAltitudeMeters: Double?
    ): Double? {
        val target = targetAltitudeMeters?.takeIf { it.isFinite() } ?: return null
        val ownship = ownshipAltitudeMeters?.takeIf { it.isFinite() } ?: return null
        return target - ownship
    }

    private fun buildRelativeDetailText(
        deltaText: String,
        speedText: String?
    ): String {
        val normalizedSpeedText = speedText
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return deltaText
        return "$deltaText | $normalizedSpeedText"
    }
}
