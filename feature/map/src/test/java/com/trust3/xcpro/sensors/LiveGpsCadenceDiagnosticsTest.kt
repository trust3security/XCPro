package com.trust3.xcpro.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveGpsCadenceDiagnosticsTest {

    @Test
    fun requestedIntervals_recordRequestedAndClampedValues() {
        val diagnostics = LiveGpsCadenceDiagnostics()

        diagnostics.recordRequestedInterval(requestedMs = 100L, clampedMs = 200L)
        diagnostics.recordRequestedInterval(requestedMs = 500L, clampedMs = 500L)

        val snapshot = diagnostics.snapshot()
        assertEquals(2L, snapshot.requestedIntervalCount)
        assertEquals(1L, snapshot.clampedRequestCount)
        assertEquals(500L, snapshot.lastRequestedIntervalMs)
        assertEquals(500L, snapshot.lastClampedIntervalMs)
    }

    @Test
    fun gpsCallbacks_recordMonotonicIntervalsAndQualityBucketsOnly() {
        val diagnostics = LiveGpsCadenceDiagnostics()

        diagnostics.recordGpsCallback(
            monotonicTimestampMs = 1_000L,
            accuracyMeters = 4f,
            bearingAccuracyDeg = 3.0,
            speedAccuracyMs = 0.4
        )
        diagnostics.recordGpsCallback(
            monotonicTimestampMs = 1_200L,
            accuracyMeters = 8f,
            bearingAccuracyDeg = 12.0,
            speedAccuracyMs = 1.2
        )
        diagnostics.recordGpsCallback(
            monotonicTimestampMs = 1_550L,
            accuracyMeters = 25f,
            bearingAccuracyDeg = null,
            speedAccuracyMs = 3.0
        )

        val snapshot = diagnostics.snapshot()
        assertEquals(3L, snapshot.gpsCallbackCount)
        assertEquals(3L, snapshot.monotonicGpsCallbackCount)
        assertEquals(1L, snapshot.actualFixIntervalBuckets.le200Ms)
        assertEquals(1L, snapshot.actualFixIntervalBuckets.le500Ms)
        assertEquals(1L, snapshot.horizontalAccuracyBuckets.le5)
        assertEquals(1L, snapshot.horizontalAccuracyBuckets.le10)
        assertEquals(1L, snapshot.horizontalAccuracyBuckets.over20)
        assertEquals(1L, snapshot.bearingAccuracyBuckets.le5)
        assertEquals(1L, snapshot.bearingAccuracyBuckets.le20)
        assertEquals(1L, snapshot.bearingAccuracyBuckets.unknown)
        assertEquals(1L, snapshot.speedAccuracyBuckets.le0_5)
        assertEquals(1L, snapshot.speedAccuracyBuckets.le2_5)
        assertEquals(1L, snapshot.speedAccuracyBuckets.over2_5)
    }

    @Test
    fun gpsCallbacks_excludeMissingMonotonicTimeFromIntervalBuckets() {
        val diagnostics = LiveGpsCadenceDiagnostics()

        diagnostics.recordGpsCallback(
            monotonicTimestampMs = 0L,
            accuracyMeters = null,
            bearingAccuracyDeg = null,
            speedAccuracyMs = null
        )
        diagnostics.recordGpsCallback(
            monotonicTimestampMs = 1_000L,
            accuracyMeters = 4f,
            bearingAccuracyDeg = null,
            speedAccuracyMs = null
        )

        val snapshot = diagnostics.snapshot()
        assertEquals(2L, snapshot.gpsCallbackCount)
        assertEquals(1L, snapshot.monotonicGpsCallbackCount)
        assertEquals(1L, snapshot.missingMonotonicGpsCallbackCount)
        assertEquals(0L, snapshot.actualFixIntervalBuckets.le100Ms)
        assertEquals(0L, snapshot.actualFixIntervalBuckets.le200Ms)
        assertEquals(1L, snapshot.horizontalAccuracyBuckets.unknown)
        assertEquals(1_000L, snapshot.lastMonotonicFixMs)
    }

    @Test
    fun compactStatus_isSingleLineAndContainsNoCoordinateFields() {
        val diagnostics = LiveGpsCadenceDiagnostics()

        diagnostics.recordRequestedInterval(requestedMs = 100L, clampedMs = 200L)
        diagnostics.recordGpsCallback(
            monotonicTimestampMs = 1_000L,
            accuracyMeters = 4f,
            bearingAccuracyDeg = 3.0,
            speedAccuracyMs = 0.4
        )

        val status = diagnostics.buildCompactStatus(reason = "test")

        assertTrue(status.startsWith("${LiveGpsCadenceDiagnostics.LOG_TOKEN} reason=test"))
        assertTrue(status.contains("requested_interval_count=1"))
        assertFalse(status.contains("\n"))
        assertFalse(status.contains("latitude", ignoreCase = true))
        assertFalse(status.contains("longitude", ignoreCase = true))
    }

    @Test
    fun reset_clearsRunScopedCounters() {
        val diagnostics = LiveGpsCadenceDiagnostics()

        diagnostics.recordRequestedInterval(requestedMs = 100L, clampedMs = 200L)
        diagnostics.recordGpsCallback(
            monotonicTimestampMs = 1_000L,
            accuracyMeters = 4f,
            bearingAccuracyDeg = 3.0,
            speedAccuracyMs = 0.4
        )

        diagnostics.reset()

        val snapshot = diagnostics.snapshot()
        assertEquals(0L, snapshot.requestedIntervalCount)
        assertEquals(0L, snapshot.gpsCallbackCount)
        assertEquals(null, snapshot.lastRequestedIntervalMs)
        assertEquals(0L, snapshot.actualFixIntervalBuckets.le100Ms)
        assertEquals(0L, snapshot.horizontalAccuracyBuckets.le5)
    }
}
