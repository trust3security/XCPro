package com.trust3.xcpro.adsb.metadata

import com.trust3.xcpro.adsb.metadata.data.minimumExpectedRowsForPromotion
import com.trust3.xcpro.adsb.metadata.data.shouldRejectMetadataPromotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftMetadataImporterTest {

    @Test
    fun shouldRejectMetadataPromotion_blocksLowStagingCountsWhenBaselineIsHealthy() {
        assertTrue(
            shouldRejectMetadataPromotion(
                activeRowCountBeforeImport = 1_000,
                stagingRowCount = 199,
                minBaselineRows = 1_000,
                minRatio = 0.20
            )
        )
    }

    @Test
    fun shouldRejectMetadataPromotion_allowsSmallImportsWhenNoHealthyBaselineExists() {
        assertFalse(
            shouldRejectMetadataPromotion(
                activeRowCountBeforeImport = 500,
                stagingRowCount = 10,
                minBaselineRows = 1_000,
                minRatio = 0.20
            )
        )
    }

    @Test
    fun minimumExpectedRowsForPromotion_roundsUp() {
        assertEquals(
            201,
            minimumExpectedRowsForPromotion(
                activeRowCountBeforeImport = 1_001,
                minRatio = 0.20
            )
        )
    }
}
