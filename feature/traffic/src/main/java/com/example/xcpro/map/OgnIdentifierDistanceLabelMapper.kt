package com.example.xcpro.map

import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import java.util.Locale

data class OgnIdentifierDistanceLabel(
    val identifier: String,
    val text: String
)

object OgnIdentifierDistanceLabelMapper {
    const val UNKNOWN_IDENTIFIER = "--"

    fun map(
        competitionId: String?,
        registration: String?,
        distanceMeters: Double?,
        unitsPreferences: UnitsPreferences
    ): OgnIdentifierDistanceLabel {
        val identifier = resolveIdentifier(
            competitionId = competitionId,
            registration = registration
        )
        val distanceText = distanceMeters
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let {
                UnitsFormatter.distance(
                    distance = DistanceM(it),
                    preferences = unitsPreferences
                ).text
            }
        val combinedText = if (distanceText != null) {
            "$identifier $distanceText"
        } else {
            identifier
        }
        return OgnIdentifierDistanceLabel(
            identifier = identifier,
            text = combinedText
        )
    }

    private fun resolveIdentifier(
        competitionId: String?,
        registration: String?
    ): String {
        val compId = competitionId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (compId != null) return compId

        val registrationTail = registration
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeLast(3)
            ?.uppercase(Locale.US)
        return registrationTail ?: UNKNOWN_IDENTIFIER
    }
}
