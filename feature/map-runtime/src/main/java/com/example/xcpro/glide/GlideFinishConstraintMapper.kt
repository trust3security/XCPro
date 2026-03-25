package com.example.xcpro.glide

import com.example.xcpro.tasks.core.RacingFinishCustomParams

fun RacingFinishCustomParams.toGlideFinishConstraintOrNull(): GlideFinishConstraint? {
    val requiredAltitudeMeters = minAltitudeMeters?.takeIf { it.isFinite() } ?: return null
    return GlideFinishConstraint(
        requiredAltitudeMeters = requiredAltitudeMeters,
        altitudeReference = altitudeReference
    )
}
