package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficStoreTest {

    @Test
    fun purgeExpired_removesOnlyExpiredTargets() {
        val store = AdsbTrafficStore()
        val now = 200_000L
        val fresh = target(index = 1, receivedMonoMs = now - 10_000L)
        val expired = target(index = 2, receivedMonoMs = now - 130_000L)

        store.upsertAll(listOf(fresh, expired))
        val removed = store.purgeExpired(nowMonoMs = now, expiryAfterSec = 120)
        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            radiusMeters = 20_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        assertTrue(removed)
        assertEquals(1, selection.displayed.size)
        assertEquals(fresh.id, selection.displayed.first().id)
    }

    @Test
    fun select_appliesCapAndStaleFlag() {
        val store = AdsbTrafficStore()
        val now = 300_000L
        val targets = buildList {
            for (i in 0 until 31) {
                add(
                    target(
                        index = i + 1,
                        lat = -33.8688 + (i * 0.0006),
                        lon = 151.2093,
                        receivedMonoMs = if (i == 0) now - 70_000L else now
                    )
                )
            }
        }
        store.upsertAll(targets)

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            radiusMeters = 20_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        assertEquals(31, selection.withinRadiusCount)
        assertEquals(30, selection.displayed.size)
        val uncappedSelection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            radiusMeters = 20_000.0,
            maxDisplayed = 31,
            staleAfterSec = 60
        )
        assertTrue(uncappedSelection.displayed.any { it.id == targets.first().id && it.isStale })
    }

    @Test
    fun select_marksEmergencyWhenTargetIsCloseAndInbound() {
        val store = AdsbTrafficStore()
        val now = 400_000L
        val inbound = target(
            index = 1,
            lat = -33.8688,
            lon = 151.2140,
            receivedMonoMs = now
        ).copy(trackDeg = 270.0)
        val outbound = target(
            index = 2,
            lat = -33.8688,
            lon = 151.2140,
            receivedMonoMs = now
        ).copy(trackDeg = 90.0)
        store.upsertAll(listOf(inbound, outbound))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            radiusMeters = 20_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val inboundUi = selection.displayed.first { it.id == inbound.id }
        val outboundUi = selection.displayed.first { it.id == outbound.id }
        assertTrue(inboundUi.isEmergencyCollisionRisk)
        assertTrue(!outboundUi.isEmergencyCollisionRisk)
    }

    @Test
    fun select_usesReferencePositionForDistanceAndBearing() {
        val store = AdsbTrafficStore()
        val now = 500_000L
        val target = target(
            index = 3,
            lat = -33.8688,
            lon = 151.2140,
            receivedMonoMs = now
        )
        store.upsertAll(listOf(target))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8600,
            referenceLon = 151.2000,
            radiusMeters = 20_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val displayed = selection.displayed.first()
        assertTrue(displayed.distanceMeters > 1_000.0)
    }

    private fun target(
        index: Int,
        lat: Double = -33.8688,
        lon: Double = 151.2093,
        receivedMonoMs: Long
    ): AdsbTarget {
        val id = Icao24.from("%06x".format(index)) ?: error("invalid test id")
        return AdsbTarget(
            id = id,
            callsign = "T$index",
            lat = lat,
            lon = lon,
            altitudeM = 1200.0,
            speedMps = 30.0,
            trackDeg = 180.0,
            climbMps = 0.5,
            positionSource = 0,
            category = 2,
            lastContactEpochSec = 1_710_000_000L,
            receivedMonoMs = receivedMonoMs
        )
    }
}
