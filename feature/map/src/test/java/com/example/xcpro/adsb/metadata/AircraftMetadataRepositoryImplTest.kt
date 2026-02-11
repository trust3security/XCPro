package com.example.xcpro.adsb.metadata

import com.example.xcpro.adsb.metadata.data.AircraftMetadataDao
import com.example.xcpro.adsb.metadata.data.AircraftMetadataEntity
import com.example.xcpro.adsb.metadata.data.AircraftMetadataRepositoryImpl
import com.example.xcpro.adsb.metadata.data.OpenSkyIcaoMetadataClient
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AircraftMetadataRepositoryImplTest {

    @Test
    fun returnsExistingDbMetadataWithoutNetworkFallback() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        val row = sampleEntity(icao24 = "aa3487", registration = "N757F")
        whenever(dao.getActiveByIcao24s(listOf("aa3487"))).thenReturn(listOf(row))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock
        )

        val result = repository.getMetadataFor(listOf("AA3487"))

        assertEquals("N757F", result["aa3487"]?.registration)
        verify(client, times(0)).fetchByIcao24("aa3487")
    }

    @Test
    fun hydratesMissingMetadataViaOnDemandLookupAndPersists() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        val row = sampleEntity(icao24 = "aa3487", registration = "N757F")
        whenever(dao.getActiveByIcao24s(listOf("aa3487"))).thenReturn(emptyList())
        whenever(client.fetchByIcao24("aa3487")).thenReturn(Result.success(row))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock
        )

        val result = repository.getMetadataFor(listOf("AA3487"))

        assertEquals("N757F", result["aa3487"]?.registration)
        verify(dao).upsertActive(listOf(row))
        verify(client).fetchByIcao24("aa3487")
    }

    @Test
    fun doesNotRetryOnDemandLookupWithinCooldownWindow() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        whenever(dao.getActiveByIcao24s(listOf("abcdef"))).thenReturn(emptyList())
        whenever(client.fetchByIcao24("abcdef")).thenReturn(Result.success(null))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock
        )

        val first = repository.getMetadataFor(listOf("ABCDEF"))
        val second = repository.getMetadataFor(listOf("ABCDEF"))

        assertNull(first["abcdef"])
        assertNull(second["abcdef"])
        verify(client, times(1)).fetchByIcao24("abcdef")
    }

    @Test
    fun largeBatchStillHydratesBoundedMissingSubset() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 2_000L)
        val first = sampleEntity(icao24 = "aa0001", registration = "N1")
        val second = sampleEntity(icao24 = "aa0002", registration = "N2")
        val third = sampleEntity(icao24 = "aa0003", registration = "N3")
        val ids = listOf("aa0001", "aa0002", "aa0003", "aa0004")
        whenever(dao.getActiveByIcao24s(ids)).thenReturn(emptyList())
        whenever(client.fetchByIcao24("aa0001")).thenReturn(Result.success(first))
        whenever(client.fetchByIcao24("aa0002")).thenReturn(Result.success(second))
        whenever(client.fetchByIcao24("aa0003")).thenReturn(Result.success(third))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock
        )

        val result = repository.getMetadataFor(ids)

        assertEquals("N1", result["aa0001"]?.registration)
        assertEquals("N2", result["aa0002"]?.registration)
        assertEquals("N3", result["aa0003"]?.registration)
        assertNull(result["aa0004"])
        verify(client, times(1)).fetchByIcao24("aa0001")
        verify(client, times(1)).fetchByIcao24("aa0002")
        verify(client, times(1)).fetchByIcao24("aa0003")
        verify(client, times(0)).fetchByIcao24("aa0004")
    }

    private fun sampleEntity(
        icao24: String,
        registration: String?
    ): AircraftMetadataEntity {
        return AircraftMetadataEntity(
            icao24 = icao24,
            registration = registration,
            typecode = "BE36",
            model = "A36",
            manufacturerName = "Raytheon Aircraft Company",
            owner = "Vintage Aircraft Llc",
            operator = null,
            operatorCallsign = null,
            icaoAircraftType = "L1P",
            qualityScore = 3,
            sourceRowOrder = 1L
        )
    }
}
