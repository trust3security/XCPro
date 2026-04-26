package com.trust3.xcpro.puretrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PureTrackBboxPolicyTest {

    @Test
    fun evaluate_smallValidBoundsAllowed() {
        val result = PureTrackBboxPolicy().evaluate(
            PureTrackBounds(
                topRightLatitude = 0.5,
                topRightLongitude = 0.5,
                bottomLeftLatitude = -0.5,
                bottomLeftLongitude = -0.5
            )
        )

        val allowed = assertAllowed(result)
        assertTrue(allowed.dimensions.widthMeters < 200_000.0)
        assertTrue(allowed.dimensions.heightMeters < 200_000.0)
        assertTrue(allowed.dimensions.diagonalMeters < 300_000.0)
    }

    @Test
    fun evaluate_usesMaximumEdgeWidth() {
        val result = PureTrackBboxPolicy(
            maxWidthMeters = 100_000.0,
            maxHeightMeters = 8_000_000.0,
            maxDiagonalMeters = 8_000_000.0
        ).evaluate(
            PureTrackBounds(
                topRightLatitude = 60.0,
                topRightLongitude = 1.0,
                bottomLeftLatitude = 0.0,
                bottomLeftLongitude = 0.0
            )
        )

        val tooWide = assertBoundsTooWide(
            result = result,
            reason = PureTrackBboxPolicyReason.WIDTH_TOO_WIDE
        )
        val dimensions = requireNotNull(tooWide.dimensions)
        assertTrue(dimensions.widthMeters > 110_000.0)
        assertTrue(dimensions.widthMeters < 112_000.0)
    }

    @Test
    fun evaluate_widthAboveThresholdFailsClosed() {
        val result = PureTrackBboxPolicy().evaluate(
            PureTrackBounds(
                topRightLatitude = 0.5,
                topRightLongitude = 2.0,
                bottomLeftLatitude = -0.5,
                bottomLeftLongitude = -2.0
            )
        )

        val tooWide = assertBoundsTooWide(
            result = result,
            reason = PureTrackBboxPolicyReason.WIDTH_TOO_WIDE
        )
        assertNotNull(tooWide.dimensions)
    }

    @Test
    fun evaluate_heightAboveThresholdFailsClosed() {
        val result = PureTrackBboxPolicy().evaluate(
            PureTrackBounds(
                topRightLatitude = 2.0,
                topRightLongitude = 0.5,
                bottomLeftLatitude = -2.0,
                bottomLeftLongitude = -0.5
            )
        )

        val tooWide = assertBoundsTooWide(
            result = result,
            reason = PureTrackBboxPolicyReason.HEIGHT_TOO_TALL
        )
        assertNotNull(tooWide.dimensions)
    }

    @Test
    fun evaluate_diagonalAboveThresholdFailsClosed() {
        val result = PureTrackBboxPolicy(
            maxWidthMeters = 500_000.0,
            maxHeightMeters = 500_000.0,
            maxDiagonalMeters = 100_000.0
        ).evaluate(
            PureTrackBounds(
                topRightLatitude = 0.5,
                topRightLongitude = 0.5,
                bottomLeftLatitude = -0.5,
                bottomLeftLongitude = -0.5
            )
        )

        val tooWide = assertBoundsTooWide(
            result = result,
            reason = PureTrackBboxPolicyReason.DIAGONAL_TOO_WIDE
        )
        assertNotNull(tooWide.dimensions)
    }

    @Test
    fun evaluate_invalidCoordinatesFailClosed() {
        val result = PureTrackBboxPolicy().evaluate(
            PureTrackBounds(
                topRightLatitude = Double.NaN,
                topRightLongitude = 0.5,
                bottomLeftLatitude = -0.5,
                bottomLeftLongitude = -0.5
            )
        )

        val tooWide = assertBoundsTooWide(
            result = result,
            reason = PureTrackBboxPolicyReason.INVALID_COORDINATES
        )
        assertNull(tooWide.dimensions)
    }

    @Test
    fun evaluate_outOfRangeCoordinatesFailClosed() {
        val result = PureTrackBboxPolicy().evaluate(
            PureTrackBounds(
                topRightLatitude = 91.0,
                topRightLongitude = 0.5,
                bottomLeftLatitude = -0.5,
                bottomLeftLongitude = -0.5
            )
        )

        val tooWide = assertBoundsTooWide(
            result = result,
            reason = PureTrackBboxPolicyReason.INVALID_COORDINATES
        )
        assertNull(tooWide.dimensions)
    }

    @Test
    fun evaluate_invertedLatitudeFailsClosed() {
        val result = PureTrackBboxPolicy().evaluate(
            PureTrackBounds(
                topRightLatitude = -1.0,
                topRightLongitude = 1.0,
                bottomLeftLatitude = 1.0,
                bottomLeftLongitude = -1.0
            )
        )

        val tooWide = assertBoundsTooWide(
            result = result,
            reason = PureTrackBboxPolicyReason.INVERTED_LATITUDE
        )
        assertNull(tooWide.dimensions)
    }

    @Test
    fun evaluate_antiMeridianOrInvertedLongitudeFailsClosed() {
        val result = PureTrackBboxPolicy().evaluate(
            PureTrackBounds(
                topRightLatitude = 1.0,
                topRightLongitude = -179.0,
                bottomLeftLatitude = -1.0,
                bottomLeftLongitude = 179.0
            )
        )

        val tooWide = assertBoundsTooWide(
            result = result,
            reason = PureTrackBboxPolicyReason.INVERTED_LONGITUDE
        )
        assertNull(tooWide.dimensions)
    }

    private fun assertAllowed(
        result: PureTrackBboxPolicyResult
    ): PureTrackBboxPolicyResult.Allowed {
        assertTrue(result is PureTrackBboxPolicyResult.Allowed)
        return result as PureTrackBboxPolicyResult.Allowed
    }

    private fun assertBoundsTooWide(
        result: PureTrackBboxPolicyResult,
        reason: PureTrackBboxPolicyReason
    ): PureTrackBboxPolicyResult.BoundsTooWide {
        assertTrue(result is PureTrackBboxPolicyResult.BoundsTooWide)
        val tooWide = result as PureTrackBboxPolicyResult.BoundsTooWide
        assertEquals(reason, tooWide.reason)
        return tooWide
    }
}
