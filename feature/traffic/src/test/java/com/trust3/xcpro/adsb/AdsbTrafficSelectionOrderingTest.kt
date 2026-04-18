package com.trust3.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbTrafficSelectionOrderingTest {

    @Test
    fun selectEmergencyAudioCandidate_prioritizesGeometryEmergencyOverCirclingRule() {
        val circlingOnly = uiTarget(
            rawId = "000001",
            isEmergencyCollisionRisk = false,
            isCirclingEmergencyRedRule = true,
            distanceMeters = 200.0,
            ageSec = 1
        )
        val geometryEmergency = uiTarget(
            rawId = "000002",
            isEmergencyCollisionRisk = true,
            isCirclingEmergencyRedRule = false,
            distanceMeters = 900.0,
            ageSec = 10
        )

        val selected = selectEmergencyAudioCandidate(listOf(circlingOnly, geometryEmergency))

        assertEquals(geometryEmergency.id, selected?.id)
    }

    @Test
    fun selectEmergencyAudioCandidate_breaksTiesByDistanceThenAgeThenId() {
        val farther = uiTarget(
            rawId = "000003",
            distanceMeters = 800.0,
            ageSec = 5
        )
        val nearerOlder = uiTarget(
            rawId = "000002",
            distanceMeters = 300.0,
            ageSec = 9
        )
        val nearerYounger = uiTarget(
            rawId = "000001",
            distanceMeters = 300.0,
            ageSec = 2
        )

        val selected = selectEmergencyAudioCandidate(
            listOf(farther, nearerOlder, nearerYounger)
        )

        assertEquals(nearerYounger.id, selected?.id)
    }

    @Test
    fun displayComparator_ordersByEmergencyThenDistanceThenAgeThenId() {
        val normalNear = uiTarget(rawId = "000002", isEmergencyCollisionRisk = false, distanceMeters = 100.0, ageSec = 1)
        val emergencyFar = uiTarget(rawId = "000003", isEmergencyCollisionRisk = true, distanceMeters = 500.0, ageSec = 9)
        val normalNearOlder = uiTarget(rawId = "000001", isEmergencyCollisionRisk = false, distanceMeters = 100.0, ageSec = 5)

        val ordered = listOf(normalNear, emergencyFar, normalNearOlder)
            .sortedWith(ADSB_DISPLAY_PRIORITY_COMPARATOR)
            .map { it.id.raw }

        assertEquals(listOf("000003", "000002", "000001"), ordered)
    }

    private fun uiTarget(
        rawId: String,
        isEmergencyCollisionRisk: Boolean = true,
        isCirclingEmergencyRedRule: Boolean = false,
        distanceMeters: Double,
        ageSec: Int
    ): AdsbTrafficUiModel {
        val id = Icao24.from(rawId) ?: error("invalid ICAO24")
        return AdsbTrafficUiModel(
            id = id,
            callsign = "T$rawId",
            lat = -33.86,
            lon = 151.20,
            altitudeM = 1_200.0,
            speedMps = 30.0,
            trackDeg = 180.0,
            climbMps = 0.0,
            ageSec = ageSec,
            isStale = false,
            distanceMeters = distanceMeters,
            bearingDegFromUser = 180.0,
            usesOwnshipReference = true,
            positionSource = 0,
            category = 2,
            lastContactEpochSec = null,
            isEmergencyCollisionRisk = isEmergencyCollisionRisk,
            isEmergencyAudioEligible = true,
            isCirclingEmergencyRedRule = isCirclingEmergencyRedRule
        )
    }
}
