package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.trail.domain.TrailTimeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnailTrailDisplayStoreTest {

    @Test
    fun appendDisplayPose_requiresRawAnchor() {
        val store = SnailTrailDisplayStore()

        val accepted = store.appendDisplayPose(
            location = TrailGeoPoint(46.0, 7.0),
            timestampMillis = 2_000L,
            poseTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            trailLength = TrailLength.LONG,
            frameId = 1L,
            minStepMillis = 100L,
            minDistanceMeters = 0.5
        )

        assertFalse(accepted)
        assertEquals(0, store.snapshot().size)
    }

    @Test
    fun appendDisplayPose_keepsDisplayPointsWhenRawAnchorChanges() {
        val store = anchoredStore(timestampMillis = 2_000L, altitudeMeters = 1_000.0, varioMs = 0.5)

        assertTrue(
            append(
                store = store,
                timestampMillis = 2_000L,
                latitude = 46.0,
                frameId = 1L
            )
        )

        store.updateRawState(
            rawPoints = listOf(rawPoint(timestampMillis = 2_500L, altitudeMeters = 1_100.0, varioMs = 1.2)),
            rawTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            isReplay = false,
            trailLength = TrailLength.LONG
        )
        assertTrue(
            append(
                store = store,
                timestampMillis = 2_500L,
                latitude = 46.0001,
                frameId = 2L
            )
        )

        val points = store.snapshot()
        assertEquals(2, points.size)
        assertEquals(0.5, points[0].varioMs, 0.0)
        assertEquals(1.2, points[1].varioMs, 0.0)
        assertEquals(46.0, points[0].latitude, 0.0)
        assertEquals(46.0001, points[1].latitude, 0.0)
    }

    @Test
    fun appendDisplayPose_rejectsDuplicateFrame() {
        val store = anchoredStore()

        assertTrue(append(store = store, timestampMillis = 2_000L, frameId = 7L))
        assertFalse(append(store = store, timestampMillis = 2_200L, latitude = 46.0002, frameId = 7L))

        assertEquals(1, store.snapshot().size)
    }

    @Test
    fun updateRawState_replaySuppliesDisplayAnchor() {
        val store = anchoredStore(rawTimeBase = TrailTimeBase.REPLAY_IGC, isReplay = true)

        assertTrue(
            append(
                store = store,
                timestampMillis = 2_000L,
                poseTimeBase = TrailTimeBase.REPLAY_IGC,
                frameId = 1L
            )
        )

        assertEquals(1, store.snapshot().size)
    }

    @Test
    fun fullKeepsRecentDisplayWindow() {
        val store = anchoredStore(timestampMillis = 1_000L)

        for (index in 0 until 65) {
            assertTrue(
                append(
                    store = store,
                    timestampMillis = 1_000L + index * 1_000L,
                    latitude = 46.0 + index * 0.00001,
                    trailLength = TrailLength.FULL,
                    frameId = index.toLong()
                )
            )
        }

        val points = store.snapshot()
        assertEquals(65, points.size)
        assertEquals(1_000L, points.first().timestampMillis)
        assertEquals(65_000L, points.last().timestampMillis)
    }

    @Test
    fun fullCapsDisplayPointCount() {
        val store = anchoredStore(
            store = SnailTrailDisplayStore(maxAgeMillis = 0L, maxPoints = 2),
            timestampMillis = 1_000L
        )

        for (index in 0 until 4) {
            assertTrue(
                append(
                    store = store,
                    timestampMillis = 1_000L + index * 1_000L,
                    latitude = 46.0 + index * 0.00001,
                    trailLength = TrailLength.FULL,
                    frameId = index.toLong()
                )
            )
        }

        val points = store.snapshot()
        assertEquals(2, points.size)
        assertEquals(1_000L, points.first().timestampMillis)
        assertEquals(4_000L, points.last().timestampMillis)
    }

    @Test
    fun appendDisplayPose_replayCadenceRejectsFramesUnderMinStep() {
        val store = anchoredStore(rawTimeBase = TrailTimeBase.REPLAY_IGC, isReplay = true)

        assertTrue(
            append(
                store = store,
                timestampMillis = 1_000L,
                poseTimeBase = TrailTimeBase.REPLAY_IGC,
                frameId = 1L,
                minStepMillis = 180L
            )
        )
        assertFalse(
            append(
                store = store,
                timestampMillis = 1_179L,
                latitude = 46.0001,
                poseTimeBase = TrailTimeBase.REPLAY_IGC,
                frameId = 2L,
                minStepMillis = 180L
            )
        )
        assertTrue(
            append(
                store = store,
                timestampMillis = 1_180L,
                latitude = 46.0002,
                poseTimeBase = TrailTimeBase.REPLAY_IGC,
                frameId = 3L,
                minStepMillis = 180L
            )
        )

        assertEquals(2, store.snapshot().size)
    }

    @Test
    fun fullThinningKeepsNewestProtectedPoints() {
        val store = anchoredStore(
            store = SnailTrailDisplayStore(maxAgeMillis = 0L, maxPoints = 6, optPoints = 5, noThinMillis = 3_000L),
            timestampMillis = 1_000L
        )

        for (index in 0 until 10) {
            assertTrue(
                append(
                    store = store,
                    timestampMillis = 1_000L + index * 1_000L,
                    latitude = 46.0 + index * 0.00001,
                    trailLength = TrailLength.FULL,
                    frameId = index.toLong()
                )
            )
        }

        val points = store.snapshot()
        assertTrue(points.size <= 6)
        assertEquals(10_000L, points.last().timestampMillis)
        assertTrue(points.any { it.timestampMillis == 7_000L })
        assertTrue(points.any { it.timestampMillis == 8_000L })
        assertTrue(points.any { it.timestampMillis == 9_000L })
    }

    @Test
    fun longKeepsSixtyMinutes() {
        val store = anchoredStore(
            store = SnailTrailDisplayStore(maxAgeMillis = 0L, maxGapMillis = Long.MAX_VALUE),
            timestampMillis = 1_000L
        )

        appendWindowPoints(store = store, trailLength = TrailLength.LONG)

        val points = store.snapshot()
        assertEquals(4, points.size)
        assertEquals(40 * 60_000L, points.first().timestampMillis)
        assertEquals(90 * 60_000L, points.last().timestampMillis)
    }

    @Test
    fun mediumKeepsThirtyMinutes() {
        val store = anchoredStore(
            store = SnailTrailDisplayStore(maxAgeMillis = 0L, maxGapMillis = Long.MAX_VALUE),
            timestampMillis = 1_000L
        )

        appendWindowPoints(store = store, trailLength = TrailLength.MEDIUM)

        val points = store.snapshot()
        assertEquals(3, points.size)
        assertEquals(60 * 60_000L, points.first().timestampMillis)
        assertEquals(90 * 60_000L, points.last().timestampMillis)
    }

    @Test
    fun shortKeepsTenMinutes() {
        val store = anchoredStore(
            store = SnailTrailDisplayStore(maxAgeMillis = 0L, maxGapMillis = Long.MAX_VALUE),
            timestampMillis = 1_000L
        )

        appendWindowPoints(store = store, trailLength = TrailLength.SHORT)

        val points = store.snapshot()
        assertEquals(2, points.size)
        assertEquals(80 * 60_000L, points.first().timestampMillis)
        assertEquals(90 * 60_000L, points.last().timestampMillis)
    }

    @Test
    fun appendDisplayPose_clearsPriorTrailAcrossDisplayFrameGap() {
        val store = anchoredStore()

        assertTrue(append(store = store, timestampMillis = 2_000L, frameId = 1L))
        assertTrue(append(store = store, timestampMillis = 5_001L, latitude = 46.0001, frameId = 2L))

        val points = store.snapshot()
        assertEquals(1, points.size)
        assertEquals(5_001L, points.last().timestampMillis)
    }

    @Test
    fun appendDisplayPose_clearsOnLargeJump() {
        val store = anchoredStore()

        assertTrue(append(store = store, timestampMillis = 2_000L, frameId = 1L))
        assertTrue(append(store = store, timestampMillis = 2_500L, latitude = 47.0, frameId = 2L))

        val points = store.snapshot()
        assertEquals(1, points.size)
        assertEquals(47.0, points.first().latitude, 0.0)
    }

    private fun appendWindowPoints(
        store: SnailTrailDisplayStore,
        trailLength: TrailLength
    ) {
        val minutes = listOf(1, 20, 40, 60, 80, 90)
        for ((index, minute) in minutes.withIndex()) {
            val timestampMillis = minute * 60_000L
            assertTrue(
                append(
                    store = store,
                    timestampMillis = timestampMillis,
                    latitude = 46.0 + index * 0.00001,
                    trailLength = trailLength,
                    frameId = index.toLong()
                )
            )
        }
    }

    private fun append(
        store: SnailTrailDisplayStore,
        timestampMillis: Long,
        latitude: Double = 46.0,
        trailLength: TrailLength = TrailLength.LONG,
        poseTimeBase: TrailTimeBase = TrailTimeBase.LIVE_MONOTONIC,
        frameId: Long,
        minStepMillis: Long = 0L
    ): Boolean {
        return store.appendDisplayPose(
            location = TrailGeoPoint(latitude, 7.0),
            timestampMillis = timestampMillis,
            poseTimeBase = poseTimeBase,
            trailLength = trailLength,
            frameId = frameId,
            minStepMillis = minStepMillis,
            minDistanceMeters = 0.0
        )
    }

    private fun anchoredStore(
        store: SnailTrailDisplayStore = SnailTrailDisplayStore(),
        timestampMillis: Long = 2_000L,
        altitudeMeters: Double = 1_000.0,
        varioMs: Double = 0.5,
        rawTimeBase: TrailTimeBase = TrailTimeBase.LIVE_MONOTONIC,
        isReplay: Boolean = false
    ): SnailTrailDisplayStore {
        return store.apply {
            updateRawState(
                rawPoints = listOf(
                    rawPoint(
                        timestampMillis = timestampMillis,
                        altitudeMeters = altitudeMeters,
                        varioMs = varioMs
                    )
                ),
                rawTimeBase = rawTimeBase,
                isReplay = isReplay,
                trailLength = TrailLength.LONG
            )
        }
    }

    private fun rawPoint(
        timestampMillis: Long,
        altitudeMeters: Double = 1_000.0,
        varioMs: Double = 0.5
    ): TrailPoint = TrailPoint(
        latitude = 46.0,
        longitude = 7.0,
        timestampMillis = timestampMillis,
        altitudeMeters = altitudeMeters,
        varioMs = varioMs,
        driftFactor = 0.0,
        windSpeedMs = 0.0,
        windDirectionFromDeg = 0.0
    )
}
