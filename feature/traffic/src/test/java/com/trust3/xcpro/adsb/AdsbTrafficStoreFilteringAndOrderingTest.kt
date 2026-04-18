package com.trust3.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbTrafficStoreFilteringAndOrderingTest {

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
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
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
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
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
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 31,
            staleAfterSec = 60
        )
        assertTrue(uncappedSelection.displayed.any { it.id == targets.first().id && it.isStale })
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
            ownshipAltitudeMeters = 1000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 5_000.0,
            verticalBelowMeters = 5_000.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        val displayed = selection.displayed.first()
        assertTrue(displayed.distanceMeters > 1_000.0)
    }

    @Test
    fun select_appliesVerticalFilterWhenOwnshipAltitudeAvailable() {
        val store = AdsbTrafficStore()
        val now = 510_000L
        val low = target(index = 10, receivedMonoMs = now).copy(altitudeM = 1_050.0)
        val high = target(index = 11, receivedMonoMs = now).copy(altitudeM = 1_900.0)
        store.upsertAll(listOf(low, high))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = 1_000.0,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 500.0,
            verticalBelowMeters = 500.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        assertEquals(2, selection.withinRadiusCount)
        assertEquals(1, selection.withinVerticalCount)
        assertEquals(1, selection.filteredByVerticalCount)
        assertEquals(low.id, selection.displayed.first().id)
    }

    @Test
    fun select_verticalFilterFailsOpenWhenOwnshipAltitudeMissing() {
        val store = AdsbTrafficStore()
        val now = 520_000L
        val low = target(index = 20, receivedMonoMs = now).copy(altitudeM = 1_050.0)
        val high = target(index = 21, receivedMonoMs = now).copy(altitudeM = 1_900.0)
        store.upsertAll(listOf(low, high))

        val selection = store.select(
            nowMonoMs = now,
            queryCenterLat = -33.8688,
            queryCenterLon = 151.2093,
            referenceLat = -33.8688,
            referenceLon = 151.2093,
            ownshipAltitudeMeters = null,
            usesOwnshipReference = true,
            radiusMeters = 20_000.0,
            verticalAboveMeters = 100.0,
            verticalBelowMeters = 100.0,
            maxDisplayed = 30,
            staleAfterSec = 60
        )

        assertEquals(2, selection.withinRadiusCount)
        assertEquals(2, selection.withinVerticalCount)
        assertEquals(0, selection.filteredByVerticalCount)
    }

    @Test
    fun select_usesStableTieBreakOrderingForEqualPriorityTargets() {
        val store = AdsbTrafficStore()
        val now = 540_000L
        val firstInserted = target(index = 2, receivedMonoMs = now).copy(
            lat = -33.8688,
            lon = 151.2093,
            trackDeg = null
        )
        val secondInserted = target(index = 1, receivedMonoMs = now).copy(
            lat = -33.8688,
            lon = 151.2093,
            trackDeg = null
        )
        store.upsertAll(listOf(firstInserted, secondInserted))

        repeat(5) {
            val selection = store.select(
                nowMonoMs = now,
                queryCenterLat = -33.8688,
                queryCenterLon = 151.2093,
                referenceLat = -33.8688,
                referenceLon = 151.2093,
                ownshipAltitudeMeters = 1_000.0,
                usesOwnshipReference = false,
                radiusMeters = 20_000.0,
                verticalAboveMeters = 5_000.0,
                verticalBelowMeters = 5_000.0,
                maxDisplayed = 30,
                staleAfterSec = 60
            )
            val orderedIds = selection.displayed.map { it.id.raw }
            assertEquals(listOf("000001", "000002"), orderedIds)
        }
    }

}
