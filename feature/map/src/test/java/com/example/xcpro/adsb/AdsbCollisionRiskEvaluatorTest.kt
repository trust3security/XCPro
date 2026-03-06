package com.example.xcpro.adsb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbCollisionRiskEvaluatorTest {

    private val evaluator = AdsbCollisionRiskEvaluator()

    @Test
    fun evaluate_returnsTrue_forHeadOnWithinAllGates() {
        val risk = evaluator.evaluate(
            distanceMeters = 900.0,
            trackDeg = 270.0,
            bearingDegFromUser = 90.0,
            altitudeDeltaMeters = 50.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            hasOwnshipReference = true,
            isClosing = true,
            ageSec = 10
        )

        assertTrue(risk)
    }

    @Test
    fun evaluate_returnsFalse_whenHeadingOutsideTolerance() {
        val risk = evaluator.evaluate(
            distanceMeters = 900.0,
            trackDeg = 291.0,
            bearingDegFromUser = 90.0,
            altitudeDeltaMeters = 50.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            hasOwnshipReference = true,
            isClosing = true,
            ageSec = 10
        )

        assertFalse(risk)
    }

    @Test
    fun evaluate_returnsFalse_whenAltitudeDeltaMissing() {
        val risk = evaluator.evaluate(
            distanceMeters = 900.0,
            trackDeg = 270.0,
            bearingDegFromUser = 90.0,
            altitudeDeltaMeters = null,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            hasOwnshipReference = true,
            isClosing = true,
            ageSec = 10
        )

        assertFalse(risk)
    }

    @Test
    fun evaluate_returnsFalse_whenVerticalOutsideGate() {
        val risk = evaluator.evaluate(
            distanceMeters = 900.0,
            trackDeg = 270.0,
            bearingDegFromUser = 90.0,
            altitudeDeltaMeters = 600.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            hasOwnshipReference = true,
            isClosing = true,
            ageSec = 10
        )

        assertFalse(risk)
    }

    @Test
    fun evaluate_returnsFalse_whenAgeIsStale() {
        val risk = evaluator.evaluate(
            distanceMeters = 900.0,
            trackDeg = 270.0,
            bearingDegFromUser = 90.0,
            altitudeDeltaMeters = 50.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            hasOwnshipReference = true,
            isClosing = true,
            ageSec = 21
        )

        assertFalse(risk)
    }

    @Test
    fun evaluate_returnsTrue_whenProjectedCpaIndicatesConvergingConflict() {
        val risk = evaluator.evaluate(
            distanceMeters = 900.0,
            trackDeg = 270.0,
            targetSpeedMps = 35.0,
            bearingDegFromUser = 90.0,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 20.0,
            altitudeDeltaMeters = 50.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            hasOwnshipReference = true,
            isClosing = true,
            ageSec = 10
        )

        assertTrue(risk)
    }

    @Test
    fun evaluate_returnsFalse_whenProjectedCpaMissDistanceIsLarge() {
        val risk = evaluator.evaluate(
            distanceMeters = 900.0,
            trackDeg = 270.0,
            targetSpeedMps = 35.0,
            bearingDegFromUser = 90.0,
            ownshipTrackDeg = 0.0,
            ownshipSpeedMps = 25.0,
            altitudeDeltaMeters = 50.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            hasOwnshipReference = true,
            isClosing = true,
            ageSec = 10
        )

        assertFalse(risk)
    }

    @Test
    fun evaluate_returnsFalse_whenOwnshipSpeedIsBelowMotionFloor() {
        val risk = evaluator.evaluate(
            distanceMeters = 900.0,
            trackDeg = 270.0,
            targetSpeedMps = 35.0,
            bearingDegFromUser = 90.0,
            ownshipTrackDeg = 90.0,
            ownshipSpeedMps = 1.0,
            altitudeDeltaMeters = 50.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            hasOwnshipReference = true,
            isClosing = true,
            ageSec = 10
        )

        assertFalse(risk)
    }
}
