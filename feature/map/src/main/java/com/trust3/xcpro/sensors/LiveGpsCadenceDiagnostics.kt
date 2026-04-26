package com.trust3.xcpro.sensors

/**
 * Aggregate-only diagnostics for phone GPS cadence.
 *
 * This owner deliberately stores intervals and quality buckets only. It must
 * not record coordinates, tracks, or raw location history.
 */
internal class LiveGpsCadenceDiagnostics {
    companion object {
        const val LOG_TOKEN: String = "LIVE_GPS_CADENCE_DIAGNOSTICS"
    }

    private var requestedIntervalCount: Long = 0L
    private var clampedRequestCount: Long = 0L
    private var gpsCallbackCount: Long = 0L
    private var monotonicGpsCallbackCount: Long = 0L
    private var missingMonotonicGpsCallbackCount: Long = 0L
    private var nonIncreasingMonotonicGpsCallbackCount: Long = 0L
    private var lastRequestedIntervalMs: Long? = null
    private var lastClampedIntervalMs: Long? = null
    private var lastMonotonicFixMs: Long? = null

    private val actualFixIntervalBuckets = IntervalBucketCounts()
    private val horizontalAccuracyBuckets = AccuracyBucketCounts()
    private val bearingAccuracyBuckets = AccuracyBucketCounts()
    private val speedAccuracyBuckets = SpeedAccuracyBucketCounts()

    @Synchronized
    fun reset() {
        requestedIntervalCount = 0L
        clampedRequestCount = 0L
        gpsCallbackCount = 0L
        monotonicGpsCallbackCount = 0L
        missingMonotonicGpsCallbackCount = 0L
        nonIncreasingMonotonicGpsCallbackCount = 0L
        lastRequestedIntervalMs = null
        lastClampedIntervalMs = null
        lastMonotonicFixMs = null
        actualFixIntervalBuckets.clear()
        horizontalAccuracyBuckets.clear()
        bearingAccuracyBuckets.clear()
        speedAccuracyBuckets.clear()
    }

    @Synchronized
    fun recordRequestedInterval(requestedMs: Long, clampedMs: Long) {
        requestedIntervalCount += 1L
        if (requestedMs != clampedMs) {
            clampedRequestCount += 1L
        }
        lastRequestedIntervalMs = requestedMs
        lastClampedIntervalMs = clampedMs
    }

    @Synchronized
    fun recordGpsCallback(
        monotonicTimestampMs: Long,
        accuracyMeters: Float?,
        bearingAccuracyDeg: Double?,
        speedAccuracyMs: Double?
    ) {
        gpsCallbackCount += 1L
        horizontalAccuracyBuckets.record(accuracyMeters?.toDouble())
        bearingAccuracyBuckets.record(bearingAccuracyDeg)
        speedAccuracyBuckets.record(speedAccuracyMs)

        if (monotonicTimestampMs <= 0L) {
            missingMonotonicGpsCallbackCount += 1L
            return
        }

        monotonicGpsCallbackCount += 1L
        val previous = lastMonotonicFixMs
        if (previous != null) {
            val intervalMs = monotonicTimestampMs - previous
            if (intervalMs > 0L) {
                actualFixIntervalBuckets.record(intervalMs)
            } else {
                nonIncreasingMonotonicGpsCallbackCount += 1L
            }
        }
        lastMonotonicFixMs = monotonicTimestampMs
    }

    @Synchronized
    fun snapshot(): Snapshot =
        Snapshot(
            requestedIntervalCount = requestedIntervalCount,
            clampedRequestCount = clampedRequestCount,
            gpsCallbackCount = gpsCallbackCount,
            monotonicGpsCallbackCount = monotonicGpsCallbackCount,
            missingMonotonicGpsCallbackCount = missingMonotonicGpsCallbackCount,
            nonIncreasingMonotonicGpsCallbackCount = nonIncreasingMonotonicGpsCallbackCount,
            lastRequestedIntervalMs = lastRequestedIntervalMs,
            lastClampedIntervalMs = lastClampedIntervalMs,
            lastMonotonicFixMs = lastMonotonicFixMs,
            actualFixIntervalBuckets = actualFixIntervalBuckets.snapshot(),
            horizontalAccuracyBuckets = horizontalAccuracyBuckets.snapshot(),
            bearingAccuracyBuckets = bearingAccuracyBuckets.snapshot(),
            speedAccuracyBuckets = speedAccuracyBuckets.snapshot()
        )

    fun buildCompactStatus(
        reason: String,
        token: String = LOG_TOKEN
    ): String {
        val snapshot = snapshot()
        return buildString {
            append(token)
            append(" reason=").append(reason)
            append(" requested_interval_count=").append(snapshot.requestedIntervalCount)
            append(" clamped_request_count=").append(snapshot.clampedRequestCount)
            append(" gps_callback_count=").append(snapshot.gpsCallbackCount)
            append(" monotonic_gps_callback_count=").append(snapshot.monotonicGpsCallbackCount)
            append(" missing_monotonic_gps_callback_count=")
                .append(snapshot.missingMonotonicGpsCallbackCount)
            append(" non_increasing_monotonic_gps_callback_count=")
                .append(snapshot.nonIncreasingMonotonicGpsCallbackCount)
            append(" last_requested_interval_ms=").append(snapshot.lastRequestedIntervalMs)
            append(" last_clamped_interval_ms=").append(snapshot.lastClampedIntervalMs)
            append(" last_monotonic_fix_ms=").append(snapshot.lastMonotonicFixMs)
            append(" actual_fix_interval_buckets=")
                .append(snapshot.actualFixIntervalBuckets.toCompactString())
            append(" horizontal_accuracy_buckets=")
                .append(snapshot.horizontalAccuracyBuckets.toCompactString())
            append(" bearing_accuracy_buckets=")
                .append(snapshot.bearingAccuracyBuckets.toCompactString())
            append(" speed_accuracy_buckets=")
                .append(snapshot.speedAccuracyBuckets.toCompactString())
        }
    }

    data class Snapshot(
        val requestedIntervalCount: Long,
        val clampedRequestCount: Long,
        val gpsCallbackCount: Long,
        val monotonicGpsCallbackCount: Long,
        val missingMonotonicGpsCallbackCount: Long,
        val nonIncreasingMonotonicGpsCallbackCount: Long,
        val lastRequestedIntervalMs: Long?,
        val lastClampedIntervalMs: Long?,
        val lastMonotonicFixMs: Long?,
        val actualFixIntervalBuckets: IntervalBucketSnapshot,
        val horizontalAccuracyBuckets: AccuracyBucketSnapshot,
        val bearingAccuracyBuckets: AccuracyBucketSnapshot,
        val speedAccuracyBuckets: SpeedAccuracyBucketSnapshot
    )

    data class IntervalBucketSnapshot(
        val le100Ms: Long,
        val le200Ms: Long,
        val le250Ms: Long,
        val le500Ms: Long,
        val le1000Ms: Long,
        val le2000Ms: Long,
        val over2000Ms: Long
    ) {
        fun toCompactString(): String =
            "le100=$le100Ms,le200=$le200Ms,le250=$le250Ms,le500=$le500Ms," +
                "le1000=$le1000Ms,le2000=$le2000Ms,over2000=$over2000Ms"
    }

    data class AccuracyBucketSnapshot(
        val unknown: Long,
        val le5: Long,
        val le10: Long,
        val le20: Long,
        val over20: Long
    ) {
        fun toCompactString(): String =
            "unknown=$unknown,le5=$le5,le10=$le10,le20=$le20,over20=$over20"
    }

    data class SpeedAccuracyBucketSnapshot(
        val unknown: Long,
        val le0_5: Long,
        val le1: Long,
        val le2_5: Long,
        val over2_5: Long
    ) {
        fun toCompactString(): String =
            "unknown=$unknown,le0_5=$le0_5,le1=$le1,le2_5=$le2_5,over2_5=$over2_5"
    }

    private class IntervalBucketCounts {
        private var le100Ms: Long = 0L
        private var le200Ms: Long = 0L
        private var le250Ms: Long = 0L
        private var le500Ms: Long = 0L
        private var le1000Ms: Long = 0L
        private var le2000Ms: Long = 0L
        private var over2000Ms: Long = 0L

        fun clear() {
            le100Ms = 0L
            le200Ms = 0L
            le250Ms = 0L
            le500Ms = 0L
            le1000Ms = 0L
            le2000Ms = 0L
            over2000Ms = 0L
        }

        fun record(intervalMs: Long) {
            when {
                intervalMs <= 100L -> le100Ms += 1L
                intervalMs <= 200L -> le200Ms += 1L
                intervalMs <= 250L -> le250Ms += 1L
                intervalMs <= 500L -> le500Ms += 1L
                intervalMs <= 1_000L -> le1000Ms += 1L
                intervalMs <= 2_000L -> le2000Ms += 1L
                else -> over2000Ms += 1L
            }
        }

        fun snapshot(): IntervalBucketSnapshot =
            IntervalBucketSnapshot(
                le100Ms = le100Ms,
                le200Ms = le200Ms,
                le250Ms = le250Ms,
                le500Ms = le500Ms,
                le1000Ms = le1000Ms,
                le2000Ms = le2000Ms,
                over2000Ms = over2000Ms
            )
    }

    private class AccuracyBucketCounts {
        private var unknown: Long = 0L
        private var le5: Long = 0L
        private var le10: Long = 0L
        private var le20: Long = 0L
        private var over20: Long = 0L

        fun clear() {
            unknown = 0L
            le5 = 0L
            le10 = 0L
            le20 = 0L
            over20 = 0L
        }

        fun record(value: Double?) {
            val finite = value?.takeIf { it.isFinite() && it >= 0.0 }
            when {
                finite == null -> unknown += 1L
                finite <= 5.0 -> le5 += 1L
                finite <= 10.0 -> le10 += 1L
                finite <= 20.0 -> le20 += 1L
                else -> over20 += 1L
            }
        }

        fun snapshot(): AccuracyBucketSnapshot =
            AccuracyBucketSnapshot(
                unknown = unknown,
                le5 = le5,
                le10 = le10,
                le20 = le20,
                over20 = over20
            )
    }

    private class SpeedAccuracyBucketCounts {
        private var unknown: Long = 0L
        private var le0_5: Long = 0L
        private var le1: Long = 0L
        private var le2_5: Long = 0L
        private var over2_5: Long = 0L

        fun clear() {
            unknown = 0L
            le0_5 = 0L
            le1 = 0L
            le2_5 = 0L
            over2_5 = 0L
        }

        fun record(value: Double?) {
            val finite = value?.takeIf { it.isFinite() && it >= 0.0 }
            when {
                finite == null -> unknown += 1L
                finite <= 0.5 -> le0_5 += 1L
                finite <= 1.0 -> le1 += 1L
                finite <= 2.5 -> le2_5 += 1L
                else -> over2_5 += 1L
            }
        }

        fun snapshot(): SpeedAccuracyBucketSnapshot =
            SpeedAccuracyBucketSnapshot(
                unknown = unknown,
                le0_5 = le0_5,
                le1 = le1,
                le2_5 = le2_5,
                over2_5 = over2_5
            )
    }
}
