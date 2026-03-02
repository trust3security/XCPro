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
        assertFalse(first.isClosing)
        assertTrue(first.showClosingAlert)
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

        assertTrue(closing.hasTrendSample)
        assertTrue(closing.isClosing)
        assertTrue(closing.showClosingAlert)
        assertFalse(divergingStart.isClosing)
        assertTrue(divergingStart.showClosingAlert)
        assertFalse(stillRecovering.isClosing)
        assertTrue(stillRecovering.showClosingAlert)
        assertFalse(recovered.isClosing)
        assertFalse(recovered.showClosingAlert)
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
        assertTrue(shortDt.showClosingAlert)
        assertTrue(longDt.hasTrendSample)
        assertTrue(longDt.isClosing)
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
        assertFalse(neutral.isClosing)
        assertFalse(neutral.showClosingAlert)
        assertFalse(afterReset.hasTrendSample)
        assertTrue(afterReset.showClosingAlert)
    }

    @Test
    fun evaluate_reEntersClosingAfterRecoveryAndReEscalatesAlert() {
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
        val reClosing = evaluator.evaluate(
            id = id,
            distanceMeters = 1_979.0,
            nowMonoMs = 8_200L,
            hasOwnshipReference = true
        )

        assertFalse(recovered.isClosing)
        assertFalse(recovered.showClosingAlert)
        assertTrue(reClosing.isClosing)
        assertTrue(reClosing.showClosingAlert)
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
        assertFalse(sample2.isClosing)
        assertFalse(sample2.showClosingAlert)
        assertFalse(sample3.isClosing)
        assertFalse(sample3.showClosingAlert)
        assertFalse(sample4.isClosing)
        assertFalse(sample4.showClosingAlert)
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
