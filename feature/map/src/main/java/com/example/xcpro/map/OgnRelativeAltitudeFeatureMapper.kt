package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.ogn.OgnAircraftIcon

internal data class OgnRelativeAltitudeFeatureMapperInput(
    val targetAltitudeMeters: Double?,
    val ownshipAltitudeMeters: Double?,
    val altitudeUnit: AltitudeUnit,
    val icon: OgnAircraftIcon,
    val defaultIconStyleImageId: String,
    val gliderAboveIconStyleImageId: String,
    val gliderBelowIconStyleImageId: String,
    val gliderNearIconStyleImageId: String,
    val secondaryLabelText: String
)

internal data class OgnRelativeAltitudeFeatureMapping(
    val iconStyleImageId: String,
    val topLabel: String,
    val bottomLabel: String,
    val band: OgnRelativeAltitudeBand,
    val deltaText: String,
    val secondaryLabelText: String
)

internal object OgnRelativeAltitudeFeatureMapper {
    fun map(input: OgnRelativeAltitudeFeatureMapperInput): OgnRelativeAltitudeFeatureMapping {
        val deltaMeters = computeDeltaMeters(
            targetAltitudeMeters = input.targetAltitudeMeters,
            ownshipAltitudeMeters = input.ownshipAltitudeMeters
        )
        val band = OgnRelativeAltitudeColorPolicy.resolveBand(deltaMeters)
        val deltaText = OgnRelativeAltitudeLabelFormatter.formatDelta(
            deltaMeters = deltaMeters,
            altitudeUnit = input.altitudeUnit
        )
        val secondaryLabel = input.secondaryLabelText
            .trim()
            .ifBlank { OgnIdentifierDistanceLabelMapper.UNKNOWN_IDENTIFIER }
        val layout = OgnRelativeAltitudeLabelLayoutPolicy.resolve(band)
        val topLabel: String
        val bottomLabel: String
        if (layout.deltaOnTop) {
            topLabel = deltaText
            bottomLabel = secondaryLabel
        } else {
            topLabel = secondaryLabel
            bottomLabel = deltaText
        }

        val iconStyleImageId = when {
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
}
