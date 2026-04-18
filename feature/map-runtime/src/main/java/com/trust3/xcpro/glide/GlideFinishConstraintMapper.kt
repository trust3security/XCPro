package com.trust3.xcpro.glide

import com.trust3.xcpro.tasks.core.RacingFinishCustomParams

fun RacingFinishCustomParams.toGlideFinishConstraintOrNull(): GlideFinishConstraint? {
    val requiredAltitudeMeters = minAltitudeMeters?.takeIf { it.isFinite() } ?: return null
    return GlideFinishConstraint(
        requiredAltitudeMeters = requiredAltitudeMeters,
        altitudeReference = altitudeReference
    )
}
