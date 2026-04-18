package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.DistanceM
import com.trust3.xcpro.common.units.UnitsFormatter
import com.trust3.xcpro.common.units.UnitsPreferences
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AdsbRelativeAltitudeBand {
    ABOVE,
    BELOW,
    UNKNOWN
}

data class AdsbMarkerLabelMapping(
    val topLabel: String,
    val bottomLabel: String,
    val relativeBand: AdsbRelativeAltitudeBand,
    val heightDiffLabel: String,
    val distanceLabel: String
)

object AdsbMarkerLabelMapper {
    const val UNKNOWN_TEXT = "--"

    fun map(
        targetAltitudeMeters: Double?,
        ownshipAltitudeMeters: Double?,
        distanceMeters: Double,
        unitsPreferences: UnitsPreferences
    ): AdsbMarkerLabelMapping {
        val deltaMeters = computeDeltaMeters(
            targetAltitudeMeters = targetAltitudeMeters,
            ownshipAltitudeMeters = ownshipAltitudeMeters
        )
        val heightDiffLabel = formatHeightDiff(
            deltaMeters = deltaMeters,
            unitsPreferences = unitsPreferences
        )
        val distanceLabel = formatDistance(
            distanceMeters = distanceMeters,
            unitsPreferences = unitsPreferences
        )
        return when {
            deltaMeters != null && deltaMeters > 0.0 -> AdsbMarkerLabelMapping(
                topLabel = heightDiffLabel,
                bottomLabel = distanceLabel,
                relativeBand = AdsbRelativeAltitudeBand.ABOVE,
                heightDiffLabel = heightDiffLabel,
                distanceLabel = distanceLabel
            )

            deltaMeters != null && deltaMeters < 0.0 -> AdsbMarkerLabelMapping(
                topLabel = distanceLabel,
                bottomLabel = heightDiffLabel,
                relativeBand = AdsbRelativeAltitudeBand.BELOW,
                heightDiffLabel = heightDiffLabel,
                distanceLabel = distanceLabel
            )

            else -> AdsbMarkerLabelMapping(
                topLabel = heightDiffLabel,
                bottomLabel = distanceLabel,
                relativeBand = AdsbRelativeAltitudeBand.UNKNOWN,
                heightDiffLabel = heightDiffLabel,
                distanceLabel = distanceLabel
            )
        }
    }

    private fun computeDeltaMeters(
        targetAltitudeMeters: Double?,
        ownshipAltitudeMeters: Double?
    ): Double? {
        val targetAltitude = targetAltitudeMeters?.takeIf { it.isFinite() } ?: return null
        val ownshipAltitude = ownshipAltitudeMeters?.takeIf { it.isFinite() } ?: return null
        return targetAltitude - ownshipAltitude
    }

    private fun formatHeightDiff(
        deltaMeters: Double?,
        unitsPreferences: UnitsPreferences
    ): String {
        val delta = deltaMeters?.takeIf { it.isFinite() } ?: return UNKNOWN_TEXT
        val altitudeUnit = unitsPreferences.altitude
        val converted = altitudeUnit.fromSi(AltitudeM(delta))
        val roundedAbs = abs(converted).roundToInt()
        return when {
            delta > 0.0 -> "+$roundedAbs ${altitudeUnit.abbreviation}"
            delta < 0.0 -> "-$roundedAbs ${altitudeUnit.abbreviation}"
            else -> "0 ${altitudeUnit.abbreviation}"
        }
    }

    private fun formatDistance(
        distanceMeters: Double,
        unitsPreferences: UnitsPreferences
    ): String {
        if (!distanceMeters.isFinite() || distanceMeters < 0.0) return UNKNOWN_TEXT
        return UnitsFormatter.distance(
            distance = DistanceM(distanceMeters),
            preferences = unitsPreferences
        ).text
    }
}
