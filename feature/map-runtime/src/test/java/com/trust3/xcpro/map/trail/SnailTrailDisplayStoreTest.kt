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
            frameId = 1L,
            minStepMillis = 100L,
            minDistanceMeters = 0.5
        )

        assertFalse(accepted)
        assertEquals(0, store.snapshot().size)
    }

    @Test
    fun appendDisplayPose_keepsDisplayPointsWhenRawAnchorChanges() {
        val store = SnailTrailDisplayStore()
        store.updateRawState(
            rawPoints = listOf(rawPoint(timestampMillis = 2_000L, altitudeMeters = 1_000.0, varioMs = 0.5)),
            rawTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            isReplay = false
        )

        assertTrue(
            store.appendDisplayPose(
                location = TrailGeoPoint(46.0, 7.0),
                timestampMillis = 2_000L,
                poseTimeBase = TrailTimeBase.LIVE_MONOTONIC,
                frameId = 1L,
                minStepMillis = 100L,
                minDistanceMeters = 0.5
            )
        )

        store.updateRawState(
            rawPoints = listOf(rawPoint(timestampMillis = 2_500L, altitudeMeters = 1_100.0, varioMs = 1.2)),
            rawTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            isReplay = false
        )
        assertTrue(
            store.appendDisplayPose(
                location = TrailGeoPoint(46.0001, 7.0001),
                timestampMillis = 2_500L,
                poseTimeBase = TrailTimeBase.LIVE_MONOTONIC,
                frameId = 2L,
                minStepMillis = 100L,
                minDistanceMeters = 0.5
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

        assertTrue(
            store.appendDisplayPose(
                location = TrailGeoPoint(46.0, 7.0),
                timestampMillis = 2_000L,
                poseTimeBase = TrailTimeBase.LIVE_MONOTONIC,
                frameId = 7L,
                minStepMillis = 100L,
                minDistanceMeters = 0.5
            )
        )
        assertFalse(
            store.appendDisplayPose(
                location = TrailGeoPoint(46.0002, 7.0002),
                timestampMillis = 2_200L,
                poseTimeBase = TrailTimeBase.LIVE_MONOTONIC,
                frameId = 7L,
                minStepMillis = 100L,
                minDistanceMeters = 0.5
            )
        )

        assertEquals(1, store.snapshot().size)
    }

    @Test
    fun updateRawState_replayClearsDisplayTrail() {
        val store = anchoredStore()
        assertTrue(
            store.appendDisplayPose(
                location = TrailGeoPoint(46.0, 7.0),
                timestampMillis = 2_000L,
                poseTimeBase = TrailTimeBase.LIVE_MONOTONIC,
                frameId = 1L,
                minStepMillis = 100L,
                minDistanceMeters = 0.5
            )
        )

        store.updateRawState(
            rawPoints = listOf(rawPoint(timestampMillis = 3_000L)),
            rawTimeBase = TrailTimeBase.REPLAY_IGC,
            isReplay = true
        )

        assertEquals(0, store.snapshot().size)
    }

    @Test
    fun appendDisplayPose_capsByAgeAndPointCount() {
        val store = SnailTrailDisplayStore(maxAgeMillis = 1_000L, maxPoints = 2)
        store.updateRawState(
            rawPoints = listOf(rawPoint(timestampMillis = 1_000L)),
            rawTimeBase = TrailTimeBase.LIVE_MONOTONIC,
            isReplay = false
        )

        for (index in 0 until 4) {
            assertTrue(
                store.appendDisplayPose(
                    location = TrailGeoPoint(46.0 + index * 0.001, 7.0),
                    timestampMillis = 1_000L + index * 700L,
                    poseTimeBase = TrailTimeBase.LIVE_MONOTONIC,
                    frameId = index.toLong(),
                    minStepMillis = 0L,
                    minDistanceMeters = 0.0
                )
            )
        }

        val points = store.snapshot()
        assertEquals(2, points.size)
        assertEquals(2_400L, points.first().timestampMillis)
        assertEquals(3_100L, points.last().timestampMillis)
    }

    private fun anchoredStore(): SnailTrailDisplayStore {
        return SnailTrailDisplayStore().apply {
            updateRawState(
                rawPoints = listOf(rawPoint(timestampMillis = 2_000L)),
                rawTimeBase = TrailTimeBase.LIVE_MONOTONIC,
                isReplay = false
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
