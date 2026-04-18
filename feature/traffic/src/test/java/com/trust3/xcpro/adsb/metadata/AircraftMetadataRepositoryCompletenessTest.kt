package com.trust3.xcpro.adsb.metadata

import com.trust3.xcpro.adsb.metadata.data.AircraftMetadataDao
import com.trust3.xcpro.adsb.metadata.data.AircraftMetadataEntity
import com.trust3.xcpro.adsb.metadata.data.AircraftMetadataRepositoryImpl
import com.trust3.xcpro.adsb.metadata.data.OpenSkyIcaoMetadataClient
import com.trust3.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AircraftMetadataRepositoryCompletenessTest {

    @Test
    fun existingIncompleteRow_triggersOnDemandHydration() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val importedRow = sampleEntity(
            icao24 = "aa3487",
            registration = "N757F",
            typecode = null,
            model = "A36",
            icaoAircraftType = null,
            qualityScore = 2,
            sourceRowOrder = 1L
        )
        val hydratedRow = sampleEntity(
            icao24 = "aa3487",
            registration = "N757F",
            typecode = "BE36",
            model = "A36",
            icaoAircraftType = "L1P",
            qualityScore = 3,
            sourceRowOrder = Long.MAX_VALUE
        )
        whenever(dao.getActiveByIcao24s(listOf("aa3487")))
            .thenReturn(listOf(importedRow), listOf(hydratedRow))
        whenever(client.fetchByIcao24("aa3487")).thenReturn(Result.success(hydratedRow))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        val first = repository.getMetadataFor(listOf("AA3487"))
        assertNull(first["aa3487"]?.typecode)
        assertNull(first["aa3487"]?.icaoAircraftType)
        advanceUntilIdle()
        val second = repository.getMetadataFor(listOf("AA3487"))

        assertEquals("BE36", second["aa3487"]?.typecode)
        assertEquals("L1P", second["aa3487"]?.icaoAircraftType)
        assertEquals(1L, repository.metadataRevision.value)
        verify(client, times(1)).fetchByIcao24("aa3487")
        verify(dao).upsertActive(listOf(hydratedRow))
    }

    @Test
    fun incompleteOnDemandResult_respectsRetryCooldown() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val incompleteRow = sampleEntity(
            icao24 = "aa3487",
            registration = "N757F",
            typecode = null,
            model = "A36",
            icaoAircraftType = null,
            qualityScore = 2,
            sourceRowOrder = Long.MAX_VALUE
        )
        whenever(dao.getActiveByIcao24s(listOf("aa3487")))
            .thenReturn(listOf(incompleteRow), listOf(incompleteRow), listOf(incompleteRow))
        whenever(client.fetchByIcao24("aa3487")).thenReturn(Result.success(incompleteRow))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        repository.getMetadataFor(listOf("AA3487"))
        advanceUntilIdle()
        repository.getMetadataFor(listOf("AA3487"))
        advanceUntilIdle()

        verify(client, times(1)).fetchByIcao24("aa3487")
        assertEquals(1L, repository.lookupTelemetry.value.lookupSampleCount)
        assertEquals(1L, repository.lookupTelemetry.value.successCount)
    }

    private fun sampleEntity(
        icao24: String,
        registration: String?,
        typecode: String?,
        model: String?,
        icaoAircraftType: String?,
        qualityScore: Int,
        sourceRowOrder: Long
    ): AircraftMetadataEntity {
        return AircraftMetadataEntity(
            icao24 = icao24,
            registration = registration,
            typecode = typecode,
            model = model,
            manufacturerName = "Raytheon Aircraft Company",
            owner = "Vintage Aircraft Llc",
            operator = null,
            operatorCallsign = null,
            icaoAircraftType = icaoAircraftType,
            qualityScore = qualityScore,
            sourceRowOrder = sourceRowOrder
        )
    }
}
