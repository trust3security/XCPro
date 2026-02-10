package com.example.xcpro.adsb.metadata

import com.example.xcpro.adsb.metadata.data.AircraftMetadataFileSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AircraftMetadataFileSelectorTest {

    private val selector = AircraftMetadataFileSelector()

    @Test
    fun selectLatestCompleteKey_ignoresNonMatchingKeysAndChoosesNewestByYearMonth() {
        val key = selector.selectLatestCompleteKey(
            listOf(
                "metadata/README.md",
                "metadata/aircraftDatabase.csv",
                "metadata/aircraft-database-complete-2025-01.csv",
                "metadata/aircraft-database-complete-2024-12.csv",
                "metadata/aircraft-database-complete-2025-10.csv",
                "metadata/aircraft-database-complete-2025-02.csv",
                "metadata/aircraft-database-complete-2025-10.zip",
                "metadata/aircraft-database-complete-2025-13.csv"
            )
        )

        assertEquals("metadata/aircraft-database-complete-2025-10.csv", key)
    }

    @Test
    fun selectLatestCompleteKey_returnsNullWhenNoCompleteSnapshotExists() {
        val key = selector.selectLatestCompleteKey(
            listOf(
                "metadata/aircraftDatabase.csv",
                "metadata/aircraftDatabase-2025-10.csv",
                "metadata/notes.txt"
            )
        )

        assertNull(key)
    }
}

