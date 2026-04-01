package com.example.xcpro.map.ui

import com.example.xcpro.map.DisplayPoseSnapshot
import com.example.xcpro.map.trail.SnailTrailManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MapScreenTrailRuntimeEffectsPolicyTest {

    @Test
    fun resolveTrailDisplayPoseSeed_liveModeClearsDisplayPoseSeed() {
        val seed = resolveTrailDisplayPoseSeed(
            suppressLiveGps = false,
            displayLocation = LatLng(-35.3, 149.1),
            displayTimeMillis = 1234L
        )

        assertNull(seed.displayLocation)
        assertNull(seed.displayTimeMillis)
    }

    @Test
    fun resolveTrailDisplayPoseSeed_suppressedModeKeepsDisplayPoseSeed() {
        val location = LatLng(-35.3, 149.1)
        val seed = resolveTrailDisplayPoseSeed(
            suppressLiveGps = true,
            displayLocation = location,
            displayTimeMillis = 1234L
        )

        assertEquals(location, seed.displayLocation)
        assertEquals(1234L, seed.displayTimeMillis)
    }

    @Test
    fun resolveDisplayPoseUpdateMode_returnsOff_whenLiveGpsIsNotSuppressed() {
        assertEquals(
            DisplayPoseUpdateMode.OFF,
            resolveDisplayPoseUpdateMode(
                suppressLiveGps = false,
                useRenderFrameSync = false
            )
        )
    }

    @Test
    fun resolveDisplayPoseUpdateMode_returnsFrameLoop_whenReplayUsesLegacyLoop() {
        assertEquals(
            DisplayPoseUpdateMode.FRAME_LOOP,
            resolveDisplayPoseUpdateMode(
                suppressLiveGps = true,
                useRenderFrameSync = false
            )
        )
    }

    @Test
    fun resolveDisplayPoseUpdateMode_returnsFrameListener_whenRenderFrameSyncIsEnabled() {
        assertEquals(
            DisplayPoseUpdateMode.FRAME_LISTENER,
            resolveDisplayPoseUpdateMode(
                suppressLiveGps = true,
                useRenderFrameSync = true
            )
        )
    }

    @Test
    fun forwardDisplayPoseSnapshot_passesSnapshotFieldsThroughUnchanged() {
        val snailTrailManager = mock<SnailTrailManager>()
        val snapshot = DisplayPoseSnapshot(
            location = LatLng(-35.3, 149.1),
            timestampMs = 1234L,
            frameId = 77L
        )

        forwardDisplayPoseSnapshot(
            snailTrailManager = snailTrailManager,
            snapshot = snapshot
        )

        verify(snailTrailManager).updateDisplayPose(
            displayLocation = snapshot.location,
            displayTimeMillis = snapshot.timestampMs,
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
            frameId = null
        )
    }
}
