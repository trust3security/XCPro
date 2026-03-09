package com.example.xcpro.adsb.metadata

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.xcpro.adsb.metadata.data.AdsbMetadataDatabase
import com.example.xcpro.adsb.metadata.data.AircraftMetadataDao
import com.example.xcpro.adsb.metadata.data.AircraftMetadataStagingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AircraftMetadataDaoMergeTest {

    private lateinit var database: AdsbMetadataDatabase
    private lateinit var dao: AircraftMetadataDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AdsbMetadataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.aircraftMetadataDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun lowerQualityDuplicate_backfillsMissingTypeAndClassOnWinner() = runTest {
        dao.upsertStagingBatch(
            listOf(
                stagingRow(
                    icao24 = "aa3487",
                    registration = "N757F",
                    typecode = null,
                    model = "A36",
                    icaoAircraftType = null,
                    qualityScore = 2,
                    sourceRowOrder = 2L
                ),
                stagingRow(
                    icao24 = "aa3487",
                    registration = null,
                    typecode = "BE36",
                    model = null,
                    icaoAircraftType = "L1P",
                    qualityScore = 1,
                    sourceRowOrder = 3L
                )
            )
        )
        dao.copyStagingToActive()

        val row = dao.getActiveByIcao24s(listOf("aa3487")).single()

        assertEquals("N757F", row.registration)
        assertEquals("BE36", row.typecode)
        assertEquals("A36", row.model)
        assertEquals("L1P", row.icaoAircraftType)
        assertEquals(2, row.qualityScore)
        assertEquals(2L, row.sourceRowOrder)
    }

    @Test
    fun laterEqualQualityIncompleteDuplicate_doesNotEraseExistingTypeAndClass() = runTest {
        dao.upsertStagingBatch(
            listOf(
                stagingRow(
                    icao24 = "aa3487",
                    registration = "N757F",
                    typecode = "BE36",
                    model = "A36",
                    icaoAircraftType = "L1P",
                    qualityScore = 3,
                    sourceRowOrder = 1L
                ),
                stagingRow(
                    icao24 = "aa3487",
                    registration = "N757F",
                    typecode = null,
                    model = "A36",
                    icaoAircraftType = null,
                    qualityScore = 3,
                    sourceRowOrder = 2L
                )
            )
        )
        dao.copyStagingToActive()

        val row = dao.getActiveByIcao24s(listOf("aa3487")).single()

        assertEquals("N757F", row.registration)
        assertEquals("BE36", row.typecode)
        assertEquals("A36", row.model)
        assertEquals("L1P", row.icaoAircraftType)
        assertEquals(3, row.qualityScore)
        assertEquals(2L, row.sourceRowOrder)
    }

    private fun stagingRow(
        icao24: String,
        registration: String?,
        typecode: String?,
        model: String?,
        icaoAircraftType: String?,
        qualityScore: Int,
        sourceRowOrder: Long
    ): AircraftMetadataStagingEntity {
        return AircraftMetadataStagingEntity(
            icao24 = icao24,
            registration = registration,
            typecode = typecode,
            model = model,
            manufacturerName = null,
            owner = null,
            operator = null,
            operatorCallsign = null,
            icaoAircraftType = icaoAircraftType,
            qualityScore = qualityScore,
            sourceRowOrder = sourceRowOrder
        )
    }
}
