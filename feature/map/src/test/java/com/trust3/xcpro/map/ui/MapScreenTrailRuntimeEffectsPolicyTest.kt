package com.trust3.xcpro.map.ui

import com.trust3.xcpro.map.DisplayClock
import com.trust3.xcpro.map.DisplayPoseSnapshot
import com.trust3.xcpro.map.trail.SnailTrailManager
import com.trust3.xcpro.map.trail.domain.TrailTimeBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MapScreenTrailRuntimeEffectsPolicyTest {

    @Test
    fun resolveTrailDisplayPoseSeed_liveModeKeepsLiveDisplayPoseSeed() {
        val location = LatLng(-35.3, 149.1)
        val seed = resolveTrailDisplayPoseSeed(
            isReplay = false,
            snapshot = DisplayPoseSnapshot(
                location = location,
                timestampMs = 1234L,
                frameId = 1L,
                timeBase = DisplayClock.TimeBase.MONOTONIC
            )
        )

        assertEquals(location, seed.displayLocation)
        assertEquals(1234L, seed.displayTimeMillis)
        assertEquals(TrailTimeBase.LIVE_MONOTONIC, seed.displayTimeBase)
    }

    @Test
    fun resolveTrailDisplayPoseSeed_replayModeKeepsReplayDisplayPoseSeed() {
        val location = LatLng(-35.3, 149.1)
        val seed = resolveTrailDisplayPoseSeed(
            isReplay = true,
            snapshot = DisplayPoseSnapshot(
                location = location,
                timestampMs = 1234L,
                frameId = 2L,
                timeBase = DisplayClock.TimeBase.REPLAY
            )
        )

        assertEquals(location, seed.displayLocation)
        assertEquals(1234L, seed.displayTimeMillis)
        assertEquals(TrailTimeBase.REPLAY_IGC, seed.displayTimeBase)
    }

    @Test
    fun resolveTrailDisplayPoseSeed_liveModeRejectsReplaySeed() {
        val seed = resolveTrailDisplayPoseSeed(
            isReplay = false,
            snapshot = DisplayPoseSnapshot(
                location = LatLng(-35.3, 149.1),
                timestampMs = 1234L,
                frameId = 3L,
                timeBase = DisplayClock.TimeBase.REPLAY
            )
        )

        assertNull(seed.displayLocation)
        assertNull(seed.displayTimeMillis)
        assertNull(seed.displayTimeBase)
    }

    @Test
    fun resolveTrailDisplayPoseSeed_replayModeRejectsLiveSeed() {
        val seed = resolveTrailDisplayPoseSeed(
            isReplay = true,
            snapshot = DisplayPoseSnapshot(
                location = LatLng(-35.3, 149.1),
                timestampMs = 1234L,
                frameId = 4L,
                timeBase = DisplayClock.TimeBase.MONOTONIC
            )
        )

        assertNull(seed.displayLocation)
        assertNull(seed.displayTimeMillis)
        assertNull(seed.displayTimeBase)
    }

    @Test
    fun shouldListenForDisplayPose_returnsFalse_whenTrailIsDisabled() {
        assertEquals(
            false,
            shouldListenForDisplayPose(
                renderLocalOwnship = true,
                trailEnabled = false
            )
        )
    }

    @Test
    fun shouldListenForDisplayPose_returnsFalse_whenLocalOwnshipIsDisabled() {
        assertEquals(
            false,
            shouldListenForDisplayPose(
                renderLocalOwnship = false,
                trailEnabled = true
            )
        )
    }

    @Test
    fun shouldListenForDisplayPose_returnsTrue_whenTrailIsEnabledAndOwnshipIsRendered() {
        assertEquals(
            true,
            shouldListenForDisplayPose(
                renderLocalOwnship = true,
                trailEnabled = true
            )
        )
    }

    @Test
    fun resolveDisplayPoseTrailTimeBase_mapsSnapshotTimeBase() {
        assertEquals(
            TrailTimeBase.LIVE_MONOTONIC,
            resolveDisplayPoseTrailTimeBase(
                DisplayPoseSnapshot(
                    location = LatLng(-35.3, 149.1),
                    timestampMs = 100L,
                    frameId = 3L,
                    timeBase = DisplayClock.TimeBase.MONOTONIC
                )
            )
        )
        assertEquals(
            TrailTimeBase.LIVE_WALL,
            resolveDisplayPoseTrailTimeBase(
                DisplayPoseSnapshot(
                    location = LatLng(-35.3, 149.1),
                    timestampMs = 100L,
                    frameId = 4L,
                    timeBase = DisplayClock.TimeBase.WALL
                )
            )
        )
        assertEquals(
            TrailTimeBase.REPLAY_IGC,
            resolveDisplayPoseTrailTimeBase(
                DisplayPoseSnapshot(
                    location = LatLng(-35.3, 149.1),
                    timestampMs = 100L,
                    frameId = 5L,
                    timeBase = DisplayClock.TimeBase.REPLAY
                )
            )
        )
    }

    @Test
    fun forwardDisplayPoseSnapshot_passesSnapshotFieldsThroughUnchanged() {
        val snailTrailManager = mock<SnailTrailManager>()
        val snapshot = DisplayPoseSnapshot(
            location = LatLng(-35.3, 149.1),
            timestampMs = 1234L,
            frameId = 77L,
            timeBase = DisplayClock.TimeBase.REPLAY
        )

        forwardDisplayPoseSnapshot(
            snailTrailManager = snailTrailManager,
            snapshot = snapshot
        )

        verify(snailTrailManager).updateDisplayPose(
            displayLocation = snapshot.location,
            displayTimeMillis = snapshot.timestampMs,
            displayTimeBase = TrailTimeBase.REPLAY_IGC,
            frameId = snapshot.frameId
        )
    }

    @Test
    fun forwardDisplayPoseSnapshot_clearsFields_whenSnapshotIsNull() {
        val snailTrailManager = mock<SnailTrailManager>()

        forwardDisplayPoseSnapshot(
            snailTrailManager = snailTrailManager,
            snapshot = null
        )

        verify(snailTrailManager).updateDisplayPose(
            displayLocation = null,
            displayTimeMillis = null,
            displayTimeBase = null,
            frameId = null
        )
    }
}
