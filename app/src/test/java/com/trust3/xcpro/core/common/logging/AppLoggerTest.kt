package com.trust3.xcpro.core.common.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLoggerTest {

    @Test
    fun redactLatLonFormatsCoordinates() {
        assertEquals("lat=47.12346, lon=8.76543", AppLogger.redactLatLon(47.123456, 8.7654321))
    }

    @Test
    fun redactCoordHandlesNull() {
        assertEquals("--", AppLogger.redactCoord(null))
    }

    @Test
    fun rateLimitRejectsSecondImmediateEmissionForSameKey() {
        val tag = "AppLoggerTest"
        val key = "rate-limit-${System.nanoTime()}"

        assertTrue(AppLogger.rateLimit(tag, key, 60_000L))
        assertFalse(AppLogger.rateLimit(tag, key, 60_000L))
    }

    @Test
    fun sampleHonorsProbabilityBounds() {
        assertFalse(AppLogger.sample(0.0))
        assertTrue(AppLogger.sample(1.0))
    }

    @Test
    fun dRateLimitedEvaluatesMessageOnlyWhenGateAllowsIt() {
        val tag = "AppLoggerTest"
        val key = "debug-rate-${System.nanoTime()}"
        var messageEvaluations = 0

        AppLogger.dRateLimited(tag, key, 60_000L) {
            messageEvaluations += 1
            "first"
        }
        AppLogger.dRateLimited(tag, key, 60_000L) {
            messageEvaluations += 1
            "second"
        }

        assertEquals(1, messageEvaluations)
    }

    @Test
    fun dSampledSkipsMessageWhenProbabilityIsZero() {
        var messageEvaluations = 0

        AppLogger.dSampled("AppLoggerTest", "sample-${System.nanoTime()}", 0.0) {
            messageEvaluations += 1
            "sampled"
        }

        assertEquals(0, messageEvaluations)
    }
}
