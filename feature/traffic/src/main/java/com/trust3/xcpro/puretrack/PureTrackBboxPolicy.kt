package com.trust3.xcpro.puretrack

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class PureTrackBboxPolicy(
    private val maxWidthMeters: Double = 200_000.0,
    private val maxHeightMeters: Double = 200_000.0,
    private val maxDiagonalMeters: Double = 300_000.0
) {
    init {
        require(maxWidthMeters.isFinite() && maxWidthMeters > 0.0) {
            "maxWidthMeters must be finite and positive"
        }
        require(maxHeightMeters.isFinite() && maxHeightMeters > 0.0) {
            "maxHeightMeters must be finite and positive"
        }
        require(maxDiagonalMeters.isFinite() && maxDiagonalMeters > 0.0) {
            "maxDiagonalMeters must be finite and positive"
        }
    }

    fun evaluate(bounds: PureTrackBounds): PureTrackBboxPolicyResult {
        val validationFailure = bounds.validationFailure()
        if (validationFailure != null) {
            return PureTrackBboxPolicyResult.BoundsTooWide(
                dimensions = null,
                reason = validationFailure
            )
        }

        val dimensions = bounds.dimensions()
        return when {
            dimensions.widthMeters > maxWidthMeters -> PureTrackBboxPolicyResult.BoundsTooWide(
                dimensions = dimensions,
                reason = PureTrackBboxPolicyReason.WIDTH_TOO_WIDE
            )
            dimensions.heightMeters > maxHeightMeters -> PureTrackBboxPolicyResult.BoundsTooWide(
                dimensions = dimensions,
                reason = PureTrackBboxPolicyReason.HEIGHT_TOO_TALL
            )
            dimensions.diagonalMeters > maxDiagonalMeters -> PureTrackBboxPolicyResult.BoundsTooWide(
                dimensions = dimensions,
                reason = PureTrackBboxPolicyReason.DIAGONAL_TOO_WIDE
            )
            else -> PureTrackBboxPolicyResult.Allowed(dimensions)
        }
    }

    private fun PureTrackBounds.validationFailure(): PureTrackBboxPolicyReason? {
        val coordinates = listOf(
            topRightLatitude,
            topRightLongitude,
            bottomLeftLatitude,
            bottomLeftLongitude
        )
        if (coordinates.any { !it.isFinite() }) {
            return PureTrackBboxPolicyReason.INVALID_COORDINATES
        }
        if (topRightLatitude !in -90.0..90.0 || bottomLeftLatitude !in -90.0..90.0) {
            return PureTrackBboxPolicyReason.INVALID_COORDINATES
        }
        if (topRightLongitude !in -180.0..180.0 || bottomLeftLongitude !in -180.0..180.0) {
            return PureTrackBboxPolicyReason.INVALID_COORDINATES
        }
        if (topRightLatitude < bottomLeftLatitude) {
            return PureTrackBboxPolicyReason.INVERTED_LATITUDE
        }
        if (topRightLongitude < bottomLeftLongitude) {
            return PureTrackBboxPolicyReason.INVERTED_LONGITUDE
        }
        return null
    }

    private fun PureTrackBounds.dimensions(): PureTrackBboxDimensions {
        val topWidth = haversineMeters(
            lat1 = topRightLatitude,
            lon1 = bottomLeftLongitude,
            lat2 = topRightLatitude,
            lon2 = topRightLongitude
        )
        val bottomWidth = haversineMeters(
            lat1 = bottomLeftLatitude,
            lon1 = bottomLeftLongitude,
            lat2 = bottomLeftLatitude,
            lon2 = topRightLongitude
        )
        val leftHeight = haversineMeters(
            lat1 = bottomLeftLatitude,
            lon1 = bottomLeftLongitude,
            lat2 = topRightLatitude,
            lon2 = bottomLeftLongitude
        )
        val rightHeight = haversineMeters(
            lat1 = bottomLeftLatitude,
            lon1 = topRightLongitude,
            lat2 = topRightLatitude,
            lon2 = topRightLongitude
        )
        val bottomLeftToTopRight = haversineMeters(
            lat1 = bottomLeftLatitude,
            lon1 = bottomLeftLongitude,
            lat2 = topRightLatitude,
            lon2 = topRightLongitude
        )
        val topLeftToBottomRight = haversineMeters(
            lat1 = topRightLatitude,
            lon1 = bottomLeftLongitude,
            lat2 = bottomLeftLatitude,
            lon2 = topRightLongitude
        )
        return PureTrackBboxDimensions(
            widthMeters = maxOf(topWidth, bottomWidth),
            heightMeters = maxOf(leftHeight, rightHeight),
            diagonalMeters = maxOf(bottomLeftToTopRight, topLeftToBottomRight)
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radLat1 = Math.toRadians(lat1)
        val radLon1 = Math.toRadians(lon1)
        val radLat2 = Math.toRadians(lat2)
        val radLon2 = Math.toRadians(lon2)
        val dLat = radLat2 - radLat1
        val dLon = radLon2 - radLon1
        val h = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(radLat1) * cos(radLat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(h), sqrt(1.0 - h))
        return EARTH_RADIUS_METERS * c
    }

    private companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

internal data class PureTrackBboxDimensions(
    val widthMeters: Double,
    val heightMeters: Double,
    val diagonalMeters: Double
)

internal sealed interface PureTrackBboxPolicyResult {
    data class Allowed(
        val dimensions: PureTrackBboxDimensions
    ) : PureTrackBboxPolicyResult

    data class BoundsTooWide(
        val dimensions: PureTrackBboxDimensions?,
        val reason: PureTrackBboxPolicyReason
    ) : PureTrackBboxPolicyResult
}

internal enum class PureTrackBboxPolicyReason {
    INVALID_COORDINATES,
    INVERTED_LATITUDE,
    INVERTED_LONGITUDE,
    WIDTH_TOO_WIDE,
    HEIGHT_TOO_TALL,
    DIAGONAL_TOO_WIDE
}
