package com.example.xcpro.adsb

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbTrafficPositionFreshnessBaselineTest {

    @Test
    fun latestGeometryWins_olderWriteDoesNotRewindSameIcaoPosition() {
        val store = AdsbTrafficStore()
        val now = 600_000L
        val forwardPosition = target(
            index = 61,
            lat = -33.8688,
            lon = 151.2120,
            receivedMonoMs = now
        ).copy(
            positionTimestampEpochSec = 1_710_000_010L,
            responseTimestampEpochSec = 1_710_000_012L,
            effectivePositionEpochSec = 1_710_000_010L,
            positionAgeAtReceiptSec = 2,
            contactAgeAtReceiptSec = 1
        )
        val rewindPosition = forwardPosition.copy(
            lon = 151.2200,
            receivedMonoMs = now + 2_000L,
            contactReceivedMonoMs = now + 2_000L,
            positionTimestampEpochSec = 1_710_000_000L,
            responseTimestampEpochSec = 1_710_000_020L,
            effectivePositionEpochSec = 1_710_000_000L,
            positionAgeAtReceiptSec = 20,
            contactAgeAtReceiptSec = 1
        )

        store.upsertAll(listOf(forwardPosition))
        store.upsertAll(listOf(rewindPosition))

        val displayed = selectAt(
            store = store,
            nowMonoMs = now + 2_000L
        ).displayed.single()

        assertEquals(forwardPosition.id, displayed.id)
        assertEquals(forwardPosition.lon, displayed.lon, 1e-6)
    }
}
