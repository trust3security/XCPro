package com.trust3.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnTrafficRepositoryPolicyTest {

    @Test
    fun shouldRefreshOgnDistanceForCenterUpdate_returnsFalseWithoutTargets() {
        val shouldRefresh = shouldRefreshOgnDistanceForCenterUpdate(
            hasTargets = false,
            previousRefreshLat = 0.0,
            previousRefreshLon = 0.0,
            nextCenterLat = 0.001,
            nextCenterLon = 0.0,
            lastRefreshMonoMs = 1_000L,
            nowMonoMs = 2_000L,
            minMoveMeters = 200.0,
            minIntervalMs = 5_000L
        )

        assertFalse(shouldRefresh)
    }

    @Test
    fun shouldRefreshOgnDistanceForCenterUpdate_usesMovementOrIntervalGate() {
        val smallMoveAndShortInterval = shouldRefreshOgnDistanceForCenterUpdate(
            hasTargets = true,
            previousRefreshLat = 0.0,
            previousRefreshLon = 0.0,
            nextCenterLat = 0.0005,
            nextCenterLon = 0.0,
            lastRefreshMonoMs = 1_000L,
            nowMonoMs = 2_000L,
            minMoveMeters = 200.0,
            minIntervalMs = 5_000L
        )
        val largeMove = shouldRefreshOgnDistanceForCenterUpdate(
            hasTargets = true,
            previousRefreshLat = 0.0,
            previousRefreshLon = 0.0,
            nextCenterLat = 0.01,
            nextCenterLon = 0.0,
            lastRefreshMonoMs = 1_000L,
            nowMonoMs = 2_000L,
            minMoveMeters = 200.0,
            minIntervalMs = 5_000L
        )
        val elapsedInterval = shouldRefreshOgnDistanceForCenterUpdate(
            hasTargets = true,
            previousRefreshLat = 0.0,
            previousRefreshLon = 0.0,
            nextCenterLat = 0.0005,
            nextCenterLon = 0.0,
            lastRefreshMonoMs = 1_000L,
            nowMonoMs = 6_500L,
            minMoveMeters = 200.0,
            minIntervalMs = 5_000L
        )

        assertFalse(smallMoveAndShortInterval)
        assertTrue(largeMove)
        assertTrue(elapsedInterval)
    }

    @Test
    fun isWithinReceiveRadiusMeters_prefersRequestedCenterWhenAvailable() {
        val within = isWithinReceiveRadiusMeters(
            targetLat = -1.34,
            targetLon = 0.0,
            requestedCenterLat = 0.09,
            requestedCenterLon = 0.0,
            subscriptionCenterLat = 0.0,
            subscriptionCenterLon = 0.0,
            radiusMeters = 150_000.0
        )

        assertFalse(within)
    }

    @Test
    fun isWithinReceiveRadiusMeters_fallsBackToSubscriptionCenterWhenRequestedCenterMissing() {
        val within = isWithinReceiveRadiusMeters(
            targetLat = 1.0,
            targetLon = 0.0,
            requestedCenterLat = null,
            requestedCenterLon = null,
            subscriptionCenterLat = 0.0,
            subscriptionCenterLon = 0.0,
            radiusMeters = 150_000.0
        )

        assertTrue(within)
    }

    @Test
    fun isWithinReceiveRadiusMeters_includesTargetAtExactRadiusBoundary() {
        val targetLat = 1.0
        val radiusMeters = OgnSubscriptionPolicy.haversineMeters(
            lat1 = 0.0,
            lon1 = 0.0,
            lat2 = targetLat,
            lon2 = 0.0
        )

        val within = isWithinReceiveRadiusMeters(
            targetLat = targetLat,
            targetLon = 0.0,
            requestedCenterLat = null,
            requestedCenterLon = null,
            subscriptionCenterLat = 0.0,
            subscriptionCenterLon = 0.0,
            radiusMeters = radiusMeters
        )

        assertTrue(within)
    }

    @Test
    fun isWithinReceiveRadiusMeters_excludesTargetSlightlyOutsideRadiusBoundary() {
        val boundaryLat = 1.0
        val outsideLat = 1.0006
        val radiusMeters = OgnSubscriptionPolicy.haversineMeters(
            lat1 = 0.0,
            lon1 = 0.0,
            lat2 = boundaryLat,
            lon2 = 0.0
        )

        val within = isWithinReceiveRadiusMeters(
            targetLat = outsideLat,
            targetLon = 0.0,
            requestedCenterLat = null,
            requestedCenterLon = null,
            subscriptionCenterLat = 0.0,
            subscriptionCenterLon = 0.0,
            radiusMeters = radiusMeters
        )

        assertFalse(within)
    }

    @Test
    fun parseLogrespStatus_detectsVerified() {
        val status = parseLogrespStatus("# logresp OGNXC1 verified, server GLIDERN1")
        assertEquals(OgnLogrespStatus.VERIFIED, status)
    }

    @Test
    fun parseLogrespStatus_detectsUnverified() {
        val status = parseLogrespStatus("# logresp OGNXC1 unverified, server GLIDERN1")
        assertEquals(OgnLogrespStatus.UNVERIFIED, status)
    }

    @Test
    fun parseLogrespStatus_ignoresNonLogrespLines() {
        val status = parseLogrespStatus("# aprsc 2.1.20-gdaa359f")
        assertNull(status)
    }

    @Test
    fun mergeOgnIdentity_usesParsedIdentityWhenDdbMissing() {
        val parsed = OgnTrafficIdentity(
            registration = null,
            competitionNumber = null,
            aircraftModel = null,
            tracked = null,
            identified = null,
            aircraftTypeCode = 4
        )

        val merged = mergeOgnIdentity(ddbIdentity = null, parsedIdentity = parsed)

        assertEquals(4, merged?.aircraftTypeCode)
    }

    @Test
    fun mergeOgnIdentity_fillsMissingDdbAircraftTypeFromParsedType() {
        val ddb = OgnTrafficIdentity(
            registration = "D-1234",
            competitionNumber = "AB",
            aircraftModel = "Discus",
            tracked = true,
            identified = true,
            aircraftTypeCode = null
        )
        val parsed = OgnTrafficIdentity(
            registration = null,
            competitionNumber = null,
            aircraftModel = null,
            tracked = null,
            identified = null,
            aircraftTypeCode = 5
        )

        val merged = mergeOgnIdentity(ddbIdentity = ddb, parsedIdentity = parsed)

        assertEquals(5, merged?.aircraftTypeCode)
        assertEquals("D-1234", merged?.registration)
        assertTrue(merged?.tracked == true)
        assertTrue(merged?.identified == true)
    }

    @Test
    fun mergeOgnIdentity_preservesDdbAircraftTypeWhenPresent() {
        val ddb = OgnTrafficIdentity(
            registration = "D-1234",
            competitionNumber = "AB",
            aircraftModel = "Discus",
            tracked = true,
            identified = true,
            aircraftTypeCode = 1
        )
        val parsed = OgnTrafficIdentity(
            registration = null,
            competitionNumber = null,
            aircraftModel = null,
            tracked = null,
            identified = null,
            aircraftTypeCode = 5
        )

        val merged = mergeOgnIdentity(ddbIdentity = ddb, parsedIdentity = parsed)

        assertEquals(1, merged?.aircraftTypeCode)
    }

    @Test
    fun shouldAcceptOgnSourceTimestamp_rejectsSourceTimeRewind() {
        val accepted = shouldAcceptOgnSourceTimestamp(
            previousSourceTimestampWallMs = 10_000L,
            incomingSourceTimestampWallMs = 9_000L,
            rewindToleranceMs = 0L
        )

        assertFalse(accepted)
    }

    @Test
    fun shouldAcceptOgnSourceTimestamp_acceptsWhenSourceTimestampMissing() {
        val accepted = shouldAcceptOgnSourceTimestamp(
            previousSourceTimestampWallMs = 10_000L,
            incomingSourceTimestampWallMs = null,
            rewindToleranceMs = 0L
        )

        assertTrue(accepted)
    }

    @Test
    fun shouldAcceptUntimedAfterTimedSourceLock_rejectsUntimedBeforeFallbackWindow() {
        val accepted = shouldAcceptUntimedAfterTimedSourceLock(
            lastTimedSourceSeenMonoMs = 50_000L,
            incomingSourceTimestampWallMs = null,
            nowMonoMs = 60_000L,
            fallbackAfterMs = 30_000L
        )

        assertFalse(accepted)
    }

    @Test
    fun shouldAcceptUntimedAfterTimedSourceLock_acceptsUntimedAfterFallbackWindow() {
        val accepted = shouldAcceptUntimedAfterTimedSourceLock(
            lastTimedSourceSeenMonoMs = 50_000L,
            incomingSourceTimestampWallMs = null,
            nowMonoMs = 90_000L,
            fallbackAfterMs = 30_000L
        )

        assertTrue(accepted)
    }

    @Test
    fun shouldAcceptUntimedAfterTimedSourceLock_acceptsTimedFramesImmediately() {
        val accepted = shouldAcceptUntimedAfterTimedSourceLock(
            lastTimedSourceSeenMonoMs = 50_000L,
            incomingSourceTimestampWallMs = 1_000L,
            nowMonoMs = 60_000L,
            fallbackAfterMs = 30_000L
        )

        assertTrue(accepted)
    }

    @Test
    fun isPlausibleOgnMotion_rejectsTeleportLikeSpeed() {
        val plausible = isPlausibleOgnMotion(
            previousLatitude = 0.0,
            previousLongitude = 0.0,
            previousSourceTimestampWallMs = 1_000L,
            previousSeenMonoMs = 10_000L,
            incomingLatitude = 1.0,
            incomingLongitude = 0.0,
            incomingSourceTimestampWallMs = 2_000L,
            incomingSeenMonoMs = 11_000L,
            maxPlausibleSpeedMps = 250.0
        )

        assertFalse(plausible)
    }

    @Test
    fun isPlausibleOgnMotion_acceptsReasonableSpeed() {
        val plausible = isPlausibleOgnMotion(
            previousLatitude = 0.0,
            previousLongitude = 0.0,
            previousSourceTimestampWallMs = 1_000L,
            previousSeenMonoMs = 10_000L,
            incomingLatitude = 0.001,
            incomingLongitude = 0.0,
            incomingSourceTimestampWallMs = 2_000L,
            incomingSeenMonoMs = 11_000L,
            maxPlausibleSpeedMps = 250.0
        )

        assertTrue(plausible)
    }

    @Test
    fun isPlausibleOgnMotion_usesMonotonicFallbackWhenSourceTimestampMissing() {
        val plausible = isPlausibleOgnMotion(
            previousLatitude = 0.0,
            previousLongitude = 0.0,
            previousSourceTimestampWallMs = 1_000L,
            previousSeenMonoMs = 10_000L,
            incomingLatitude = 1.0,
            incomingLongitude = 0.0,
            incomingSourceTimestampWallMs = null,
            incomingSeenMonoMs = 11_000L,
            maxPlausibleSpeedMps = 250.0
        )

        assertFalse(plausible)
    }

    @Test
    fun isOwnshipTarget_matchesOnlyWhenTypeAndHexMatch() {
        val flarmTarget = sampleTarget(addressType = OgnAddressType.FLARM, addressHex = "ABC123")
        val icaoTarget = sampleTarget(addressType = OgnAddressType.ICAO, addressHex = "ABC123")

        assertTrue(
            isOwnshipTarget(
                target = flarmTarget,
                ownFlarmHex = "ABC123",
                ownIcaoHex = "ABC123"
            )
        )
        assertFalse(
            isOwnshipTarget(
                target = icaoTarget,
                ownFlarmHex = "ABC123",
                ownIcaoHex = null
            )
        )
        assertTrue(
            isOwnshipTarget(
                target = icaoTarget,
                ownFlarmHex = null,
                ownIcaoHex = "ABC123"
            )
        )
    }

    @Test
    fun isOwnshipTarget_doesNotMatchUnknownType() {
        val unknown = sampleTarget(addressType = OgnAddressType.UNKNOWN, addressHex = "ABC123")
        assertFalse(
            isOwnshipTarget(
                target = unknown,
                ownFlarmHex = "ABC123",
                ownIcaoHex = "ABC123"
            )
        )
    }

    private fun sampleTarget(
        addressType: OgnAddressType,
        addressHex: String?
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = "TEST01",
        callsign = "TEST01",
        destination = "APRS",
        latitude = 0.0,
        longitude = 0.0,
        altitudeMeters = null,
        trackDegrees = null,
        groundSpeedMps = null,
        verticalSpeedMps = null,
        deviceIdHex = addressHex,
        signalDb = null,
        displayLabel = "TEST01",
        identity = null,
        rawComment = null,
        rawLine = "line",
        timestampMillis = 1L,
        lastSeenMillis = 1L,
        addressType = addressType,
        addressHex = addressHex
    )
}
