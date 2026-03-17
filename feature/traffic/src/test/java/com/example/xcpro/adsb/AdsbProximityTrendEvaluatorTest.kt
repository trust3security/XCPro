package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbProximityTrendEvaluatorTest {

    private val id = Icao24.from("abc123") ?: error("invalid test id")

    @Test
    fun evaluate_firstSampleKeepsAlertButHasNoTrendSample() {
        val evaluator = AdsbProximityTrendEvaluator()

        val first = evaluator.evaluate(
            id = id,
            distanceMeters = 1_500.0,
            nowMonoMs = 1_000L,
            hasOwnshipReference = true
        )

        assertFalse(first.hasTrendSample)
        assertFalse(first.hasFreshTrendSample)
        assertFalse(first.isClosing)
        assertTrue(first.showClosingAlert)
        assertEquals(0, first.postPassDivergingSampleCount)
    }

    @Test
    fun evaluate_withoutFreshSample_doesNotPromoteToTrendSample() {
        val evaluator = AdsbProximityTrendEvaluator()

        evaluator.evaluate(
            id = id,
            distanceMeters = 1_500.0,
            nowMonoMs = 1_000L,
            hasOwnshipReference = true,
            sampleMonoMs = 1_000L
        )
        val noFreshSample = evaluator.evaluate(
            id = id,
            distanceMeters = 1_500.0,
            nowMonoMs = 6_000L,
            hasOwnshipReference = true,
            sampleMonoMs = 1_000L
        )

        assertFalse(noFreshSample.hasTrendSample)
        assertFalse(noFreshSample.hasFreshTrendSample)
        assertFalse(noFreshSample.isClosing)
        assertTrue(noFreshSample.showClosingAlert)
        assertEquals(0, noFreshSample.postPassDivergingSampleCount)
    }

    @Test
    fun evaluate_detectsClosingAndThenRecoversAfterDwell() {
        val evaluator = AdsbProximityTrendEvaluator()

        evaluator.evaluate(
            id = id,
            distanceMeters = 2_000.0,
            nowMonoMs = 1_000L,
            hasOwnshipReference = true
        )
        val closing = evaluator.evaluate(
            id = id,
            distanceMeters = 1_980.0,
            nowMonoMs = 2_000L,
            hasOwnshipReference = true
        )
        val divergingStart = evaluator.evaluate(
            id = id,
            distanceMeters = 1_981.0,
            nowMonoMs = 3_000L,
            hasOwnshipReference = true
        )
        val stillRecovering = evaluator.evaluate(
            id = id,
            distanceMeters = 1_982.0,
            nowMonoMs = 6_000L,
            hasOwnshipReference = true
        )
        val recovered = evaluator.evaluate(
            id = id,
            distanceMeters = 1_983.0,
            nowMonoMs = 7_100L,
            hasOwnshipReference = true
        )
        val postPassSecond = evaluator.evaluate(
            id = id,
            distanceMeters = 1_984.0,
            nowMonoMs = 8_300L,
            hasOwnshipReference = true
        )

        assertTrue(closing.hasTrendSample)
        assertTrue(closing.hasFreshTrendSample)
        assertTrue(closing.isClosing)
        assertTrue(closing.showClosingAlert)
        assertEquals(0, closing.postPassDivergingSampleCount)
        assertFalse(divergingStart.isClosing)
        assertTrue(divergingStart.hasFreshTrendSample)
        assertTrue(divergingStart.showClosingAlert)
        assertEquals(0, divergingStart.postPassDivergingSampleCount)
        assertFalse(stillRecovering.isClosing)
        assertTrue(stillRecovering.hasFreshTrendSample)
        assertTrue(stillRecovering.showClosingAlert)
        assertEquals(0, stillRecovering.postPassDivergingSampleCount)
        assertFalse(recovered.isClosing)
        assertTrue(recovered.hasFreshTrendSample)
        assertFalse(recovered.showClosingAlert)
        assertEquals(1, recovered.postPassDivergingSampleCount)
        assertFalse(postPassSecond.isClosing)
        assertFalse(postPassSecond.showClosingAlert)
        assertEquals(2, postPassSecond.postPassDivergingSampleCount)
    }

    @Test
    fun evaluate_ignoresShortSampleDtForTrendTransitions() {
        val evaluator = AdsbProximityTrendEvaluator()

        evaluator.evaluate(
            id = id,
            distanceMeters = 1_500.0,
            nowMonoMs = 1_000L,
            hasOwnshipReference = true
        )
        val shortDt = evaluator.evaluate(
            id = id,
            distanceMeters = 1_400.0,
            nowMonoMs = 1_300L,
            hasOwnshipReference = true
        )
        val longDt = evaluator.evaluate(
            id = id,
            distanceMeters = 1_300.0,
            nowMonoMs = 2_000L,
            hasOwnshipReference = true
        )

        assertFalse(shortDt.hasTrendSample)
        assertFalse(shortDt.hasFreshTrendSample)
        assertTrue(shortDt.showClosingAlert)
        assertEquals(0, shortDt.postPassDivergingSampleCount)
        assertTrue(longDt.hasTrendSample)
        assertTrue(longDt.hasFreshTrendSample)
        assertTrue(longDt.isClosing)
        assertEquals(0, longDt.postPassDivergingSampleCount)
    }

    @Test
    fun evaluate_resetsTrendWhenOwnshipReferenceUnavailable() {
        val evaluator = AdsbProximityTrendEvaluator()

        evaluator.evaluate(
            id = id,
            distanceMeters = 1_500.0,
            nowMonoMs = 1_000L,
            hasOwnshipReference = true
        )
        evaluator.evaluate(
            id = id,
            distanceMeters = 1_300.0,
            nowMonoMs = 2_000L,
            hasOwnshipReference = true
        )
        val neutral = evaluator.evaluate(
            id = id,
            distanceMeters = 1_200.0,
            nowMonoMs = 3_000L,
            hasOwnshipReference = false
        )
        val afterReset = evaluator.evaluate(
            id = id,
            distanceMeters = 1_100.0,
            nowMonoMs = 4_000L,
            hasOwnshipReference = true
        )

        assertFalse(neutral.hasTrendSample)
        assertFalse(neutral.hasFreshTrendSample)
        assertFalse(neutral.isClosing)
        assertFalse(neutral.showClosingAlert)
        assertEquals(0, neutral.postPassDivergingSampleCount)
        assertFalse(afterReset.hasTrendSample)
        assertFalse(afterReset.hasFreshTrendSample)
        assertTrue(afterReset.showClosingAlert)
        assertEquals(0, afterReset.postPassDivergingSampleCount)
    }

    @Test
    fun evaluate_requiresConsecutiveClosingSamplesToReEnterAfterRecovery() {
        val evaluator = AdsbProximityTrendEvaluator()

        evaluator.evaluate(
            id = id,
            distanceMeters = 2_000.0,
            nowMonoMs = 1_000L,
            hasOwnshipReference = true
        )
        evaluator.evaluate(
            id = id,
            distanceMeters = 1_980.0,
            nowMonoMs = 2_000L,
            hasOwnshipReference = true
        )
        evaluator.evaluate(
            id = id,
            distanceMeters = 1_982.0,
            nowMonoMs = 3_000L,
            hasOwnshipReference = true
        )
        val recovered = evaluator.evaluate(
            id = id,
            distanceMeters = 1_982.0,
            nowMonoMs = 7_100L,
            hasOwnshipReference = true
        )
        val reClosingNoise = evaluator.evaluate(
            id = id,
            distanceMeters = 1_979.0,
            nowMonoMs = 8_200L,
            hasOwnshipReference = true
        )
        val reClosing = evaluator.evaluate(
            id = id,
            distanceMeters = 1_976.0,
            nowMonoMs = 9_300L,
            hasOwnshipReference = true
        )

        assertFalse(recovered.isClosing)
        assertFalse(recovered.showClosingAlert)
        assertEquals(1, recovered.postPassDivergingSampleCount)
        assertFalse(reClosingNoise.isClosing)
        assertFalse(reClosingNoise.showClosingAlert)
        assertTrue(reClosing.isClosing)
        assertTrue(reClosing.showClosingAlert)
        assertEquals(0, reClosing.postPassDivergingSampleCount)
    }

    @Test
    fun evaluate_marksPostPassDivergingWithoutPriorClosingWhenDistanceMovesPastClosestApproach() {
        val evaluator = AdsbProximityTrendEvaluator()

        evaluator.evaluate(
            id = id,
            distanceMeters = 3_400.0,
            nowMonoMs = 1_000L,
            hasOwnshipReference = true
        )
        val lowRateApproach = evaluator.evaluate(
            id = id,
            distanceMeters = 3_399.5,
            nowMonoMs = 2_000L,
            hasOwnshipReference = true
        )
        val divergingPastClosest = evaluator.evaluate(
            id = id,
            distanceMeters = 3_525.0,
            nowMonoMs = 3_000L,
            hasOwnshipReference = true
        )

        assertTrue(lowRateApproach.hasTrendSample)
        assertFalse(lowRateApproach.isClosing)
        assertEquals(0, lowRateApproach.postPassDivergingSampleCount)
        assertFalse(divergingPastClosest.isClosing)
        assertFalse(divergingPastClosest.showClosingAlert)
        assertEquals(1, divergingPastClosest.postPassDivergingSampleCount)
    }

    @Test
    fun evaluate_constantDistanceJitterDoesNotOscillateIntoClosing() {
        val evaluator = AdsbProximityTrendEvaluator()

        evaluator.evaluate(
            id = id,
            distanceMeters = 1_500.0,
            nowMonoMs = 1_000L,
            hasOwnshipReference = true
        )
        val sample1 = evaluator.evaluate(
            id = id,
            distanceMeters = 1_499.6,
            nowMonoMs = 2_000L,
            hasOwnshipReference = true
        )
        val sample2 = evaluator.evaluate(
            id = id,
            distanceMeters = 1_500.0,
            nowMonoMs = 3_000L,
            hasOwnshipReference = true
        )
        val sample3 = evaluator.evaluate(
            id = id,
            distanceMeters = 1_499.7,
            nowMonoMs = 4_000L,
            hasOwnshipReference = true
        )
        val sample4 = evaluator.evaluate(
            id = id,
            distanceMeters = 1_500.2,
            nowMonoMs = 5_000L,
            hasOwnshipReference = true
        )

        assertTrue(sample1.hasTrendSample)
        assertFalse(sample1.isClosing)
        assertFalse(sample1.showClosingAlert)
        assertEquals(0, sample1.postPassDivergingSampleCount)
        assertFalse(sample2.isClosing)
        assertFalse(sample2.showClosingAlert)
        assertEquals(0, sample2.postPassDivergingSampleCount)
        assertFalse(sample3.isClosing)
        assertFalse(sample3.showClosingAlert)
        assertEquals(0, sample3.postPassDivergingSampleCount)
        assertFalse(sample4.isClosing)
        assertFalse(sample4.showClosingAlert)
        assertEquals(0, sample4.postPassDivergingSampleCount)
    }

    @Test
    fun evaluate_sameSequenceIsDeterministicAcrossInstances() {
        val samples = listOf(
            TrendSample(nowMonoMs = 1_000L, distanceMeters = 2_500.0, hasOwnshipReference = true),
            TrendSample(nowMonoMs = 2_000L, distanceMeters = 2_498.0, hasOwnshipReference = true),
            TrendSample(nowMonoMs = 3_000L, distanceMeters = 2_499.0, hasOwnshipReference = true),
            TrendSample(nowMonoMs = 7_100L, distanceMeters = 2_499.0, hasOwnshipReference = true),
            TrendSample(nowMonoMs = 8_200L, distanceMeters = 2_496.0, hasOwnshipReference = true),
            TrendSample(nowMonoMs = 9_300L, distanceMeters = 2_496.0, hasOwnshipReference = false),
            TrendSample(nowMonoMs = 10_400L, distanceMeters = 2_495.0, hasOwnshipReference = true)
        )

        val firstRun = runSequence(samples)
        val secondRun = runSequence(samples)

        assertEquals(firstRun.size, secondRun.size)
        firstRun.zip(secondRun).forEachIndexed { index, (first, second) ->
            assertEquals("hasTrendSample mismatch at index $index", first.hasTrendSample, second.hasTrendSample)
            assertEquals("isClosing mismatch at index $index", first.isClosing, second.isClosing)
            assertEquals(
                "showClosingAlert mismatch at index $index",
                first.showClosingAlert,
                second.showClosingAlert
            )
            assertEquals(
                "postPassDivergingSampleCount mismatch at index $index",
                first.postPassDivergingSampleCount,
                second.postPassDivergingSampleCount
            )
            if (first.closingRateMps == null || second.closingRateMps == null) {
                assertEquals(
                    "closingRate nullability mismatch at index $index",
                    first.closingRateMps,
                    second.closingRateMps
                )
            } else {
                assertEquals(
                    "closingRate mismatch at index $index",
                    first.closingRateMps,
                    second.closingRateMps,
                    1e-9
                )
            }
        }
    }

    private fun runSequence(samples: List<TrendSample>): List<AdsbProximityTrendAssessment> {
        val evaluator = AdsbProximityTrendEvaluator()
        return samples.map { sample ->
            evaluator.evaluate(
                id = id,
                distanceMeters = sample.distanceMeters,
                nowMonoMs = sample.nowMonoMs,
                hasOwnshipReference = sample.hasOwnshipReference
            )
        }
    }

    private data class TrendSample(
        val nowMonoMs: Long,
        val distanceMeters: Double,
        val hasOwnshipReference: Boolean
    )
}
