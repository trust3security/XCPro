package com.trust3.xcpro.livefollow.data.session

import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.trust3.xcpro.livefollow.model.LiveFollowConfidence
import com.trust3.xcpro.livefollow.model.LiveFollowValueQuality
import com.trust3.xcpro.livefollow.model.LiveFollowValueState
import com.trust3.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.trust3.xcpro.livefollow.model.LiveOwnshipSourceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveFollowCurrentApiPositionMapperTest {

    @Test
    fun map_prefersPressureAltitude_and_preservesGroundSpeedMsUnits() {
        val request = LiveFollowCurrentApiPositionMapper.map(
            sessionId = "pilot-1",
            snapshot = ownshipSnapshot(
                gpsAltitudeMslMeters = 500.0,
                pressureAltitudeMslMeters = 495.0,
                groundSpeedMs = 12.5,
                trackDeg = 182.0,
                fixWallMs = 1_700_000_123_000L
            )
        )

        requireNotNull(request)
        assertEquals(495.0, request.altitudeMslMeters, 0.0)
        assertEquals(45.0, requireNotNull(request.aglMeters), 0.0)
        assertEquals(12.5, request.groundSpeedMs, 0.0)
        assertEquals("2023-11-14T22:15:23Z", request.timestampIsoUtc)
    }

    @Test
    fun map_fallsBackToGpsAltitude_whenPressureAltitudeUnavailable() {
        val request = LiveFollowCurrentApiPositionMapper.map(
            sessionId = "pilot-1",
            snapshot = ownshipSnapshot(
                gpsAltitudeMslMeters = 500.0,
                pressureAltitudeMslMeters = null,
                aglMeters = null
            )
        )

        requireNotNull(request)
        assertEquals(500.0, request.altitudeMslMeters, 0.0)
        assertNull(request.aglMeters)
    }

    @Test
    fun map_returnsNull_whenAnyRequiredUploadFieldIsMissing() {
        val request = LiveFollowCurrentApiPositionMapper.map(
            sessionId = "pilot-1",
            snapshot = ownshipSnapshot(
                pressureAltitudeMslMeters = null,
                gpsAltitudeMslMeters = null,
                trackDeg = null
            )
        )

        assertNull(request)
    }

    private fun ownshipSnapshot(
        gpsAltitudeMslMeters: Double? = 500.0,
        pressureAltitudeMslMeters: Double? = 495.0,
        aglMeters: Double? = 45.0,
        groundSpeedMs: Double? = 12.0,
        trackDeg: Double? = 180.0,
        fixWallMs: Long? = 20_000L
    ): LiveOwnshipSnapshot {
        return LiveOwnshipSnapshot(
            latitudeDeg = -33.9,
            longitudeDeg = 151.2,
            gpsAltitudeMslMeters = gpsAltitudeMslMeters,
            pressureAltitudeMslMeters = pressureAltitudeMslMeters,
            aglMeters = aglMeters,
            groundSpeedMs = groundSpeedMs,
            trackDeg = trackDeg,
            verticalSpeedMs = 1.2,
            fixMonoMs = 10_000L,
            fixWallMs = fixWallMs,
            positionQuality = LiveFollowValueQuality(
                state = LiveFollowValueState.VALID,
                confidence = LiveFollowConfidence.HIGH
            ),
            verticalQuality = LiveFollowValueQuality(
                state = LiveFollowValueState.VALID,
                confidence = LiveFollowConfidence.HIGH
            ),
            canonicalIdentity = LiveFollowAircraftIdentity.create(
                type = LiveFollowAircraftIdentityType.FLARM,
                rawValue = "AB12CD",
                verified = true
            ),
            sourceLabel = LiveOwnshipSourceLabel.LIVE_FLIGHT_RUNTIME
        )
    }
}
