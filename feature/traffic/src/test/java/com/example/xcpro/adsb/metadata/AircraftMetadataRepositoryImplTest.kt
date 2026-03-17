package com.example.xcpro.adsb.metadata

import com.example.xcpro.adsb.metadata.data.AircraftMetadataDao
import com.example.xcpro.adsb.metadata.data.AircraftMetadataEntity
import com.example.xcpro.adsb.metadata.data.AircraftMetadataRepositoryImpl
import com.example.xcpro.adsb.metadata.data.OpenSkyIcaoMetadataClient
import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AircraftMetadataRepositoryImplTest {

    @Test
    fun returnsExistingDbMetadataWithoutNetworkFallback() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val row = sampleEntity(icao24 = "aa3487", registration = "N757F")
        whenever(dao.getActiveByIcao24s(listOf("aa3487"))).thenReturn(listOf(row))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        val result = repository.getMetadataFor(listOf("AA3487"))
        advanceUntilIdle()

        assertEquals("N757F", result["aa3487"]?.registration)
        assertEquals(0L, repository.metadataRevision.value)
        assertEquals(0L, repository.lookupProgressRevision.value)
        verify(client, times(0)).fetchByIcao24("aa3487")
        assertEquals(0L, repository.lookupTelemetry.value.lookupSampleCount)
    }

    @Test
    fun hydratesMissingMetadataViaOnDemandLookupAndPersists() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val row = sampleEntity(icao24 = "aa3487", registration = "N757F")
        whenever(dao.getActiveByIcao24s(listOf("aa3487")))
            .thenReturn(emptyList(), listOf(row))
        whenever(client.fetchByIcao24("aa3487")).thenReturn(Result.success(row))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        val first = repository.getMetadataFor(listOf("AA3487"))
        assertNull(first["aa3487"])
        assertEquals(0L, repository.metadataRevision.value)
        assertEquals(0L, repository.lookupProgressRevision.value)
        advanceUntilIdle()
        assertEquals(1L, repository.metadataRevision.value)
        assertEquals(1L, repository.lookupProgressRevision.value)
        val second = repository.getMetadataFor(listOf("AA3487"))

        assertEquals("N757F", second["aa3487"]?.registration)
        verify(dao).upsertActive(listOf(row))
        verify(client).fetchByIcao24("aa3487")
        val telemetry = repository.lookupTelemetry.value
        assertEquals(1L, telemetry.lookupSampleCount)
        assertEquals(1L, telemetry.successCount)
        assertEquals(0L, telemetry.notFoundCount)
        assertEquals(0L, telemetry.errorCount)
        assertTrue((telemetry.lastLookupLatencyMs ?: -1L) >= 0L)
    }

    @Test
    fun doesNotRetryOnDemandLookupWithinCooldownWindow() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        whenever(dao.getActiveByIcao24s(listOf("abcdef"))).thenReturn(emptyList())
        whenever(client.fetchByIcao24("abcdef")).thenReturn(Result.success(null))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        val first = repository.getMetadataFor(listOf("ABCDEF"))
        advanceUntilIdle()
        val second = repository.getMetadataFor(listOf("ABCDEF"))
        advanceUntilIdle()

        assertNull(first["abcdef"])
        assertNull(second["abcdef"])
        assertEquals(1L, repository.lookupProgressRevision.value)
        verify(client, times(1)).fetchByIcao24("abcdef")
        val telemetry = repository.lookupTelemetry.value
        assertEquals(1L, telemetry.lookupSampleCount)
        assertEquals(0L, telemetry.successCount)
        assertEquals(1L, telemetry.notFoundCount)
        assertEquals(0L, telemetry.errorCount)
    }

    @Test
    fun largeBatchStillHydratesBoundedMissingSubset() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 2_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ids = (1..9).map { "aa000$it" }
        val firstBatch = ids.take(8).mapIndexed { index, id ->
            sampleEntity(icao24 = id, registration = "N${index + 1}")
        }
        whenever(dao.getActiveByIcao24s(ids))
            .thenReturn(emptyList(), firstBatch)
        firstBatch.forEach { row ->
            whenever(client.fetchByIcao24(row.icao24)).thenReturn(Result.success(row))
        }

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        val firstRead = repository.getMetadataFor(ids)
        ids.forEach { id -> assertNull(firstRead[id]) }
        advanceUntilIdle()
        assertEquals(1L, repository.lookupProgressRevision.value)
        val secondRead = repository.getMetadataFor(ids)

        firstBatch.forEachIndexed { index, row ->
            assertEquals("N${index + 1}", secondRead[row.icao24]?.registration)
        }
        assertNull(secondRead[ids.last()])
        firstBatch.forEach { row ->
            verify(client, times(1)).fetchByIcao24(row.icao24)
        }
        verify(client, times(0)).fetchByIcao24(ids.last())
        verify(dao).upsertActive(firstBatch)
    }

    @Test
    fun cooldownOnLeadingIds_doesNotStarveLaterEligibleMissingIcaos() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 1_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ids = (1..9).map { "aa000$it" }
        val trailing = ids.last()
        val trailingEntity = sampleEntity(icao24 = trailing, registration = "N9")
        whenever(dao.getActiveByIcao24s(ids))
            .thenReturn(emptyList(), emptyList(), listOf(trailingEntity))
        ids.dropLast(1).forEach { id ->
            whenever(client.fetchByIcao24(id)).thenReturn(Result.success(null))
        }
        whenever(client.fetchByIcao24(trailing)).thenReturn(Result.success(trailingEntity))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        repository.getMetadataFor(ids)
        advanceUntilIdle()
        assertEquals(1L, repository.lookupProgressRevision.value)
        clock.advanceMonoMs(1_000L)
        val second = repository.getMetadataFor(ids)
        assertNull(second[trailing])
        advanceUntilIdle()
        assertEquals(2L, repository.lookupProgressRevision.value)
        val third = repository.getMetadataFor(ids)

        assertEquals("N9", third[trailing]?.registration)
        ids.dropLast(1).forEach { id ->
            verify(client, times(1)).fetchByIcao24(id)
        }
        verify(client, times(1)).fetchByIcao24(trailing)
        verify(dao, times(1)).upsertActive(listOf(trailingEntity))
    }

    @Test
    fun transientOnDemandFailure_retriesAfterShortCooldown() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 10_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recovered = sampleEntity(icao24 = "abcdef", registration = "N757F")
        whenever(dao.getActiveByIcao24s(listOf("abcdef")))
            .thenReturn(emptyList(), emptyList(), emptyList(), listOf(recovered))
        whenever(client.fetchByIcao24("abcdef"))
            .thenReturn(Result.failure(IllegalStateException("network")))
            .thenReturn(Result.success(recovered))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        val first = repository.getMetadataFor(listOf("ABCDEF"))
        advanceUntilIdle()
        assertEquals(1L, repository.lookupProgressRevision.value)
        clock.advanceMonoMs(10_000L)
        val stillCoolingDown = repository.getMetadataFor(listOf("ABCDEF"))
        advanceUntilIdle()
        clock.advanceMonoMs(51_000L)
        val beforeRetryHydrate = repository.getMetadataFor(listOf("ABCDEF"))
        assertNull(beforeRetryHydrate["abcdef"])
        advanceUntilIdle()
        assertEquals(2L, repository.lookupProgressRevision.value)
        val retried = repository.getMetadataFor(listOf("ABCDEF"))

        assertNull(first["abcdef"])
        assertNull(stillCoolingDown["abcdef"])
        assertEquals("N757F", retried["abcdef"]?.registration)
        verify(client, times(2)).fetchByIcao24("abcdef")
        val telemetry = repository.lookupTelemetry.value
        assertEquals(2L, telemetry.lookupSampleCount)
        assertEquals(1L, telemetry.successCount)
        assertEquals(0L, telemetry.notFoundCount)
        assertEquals(1L, telemetry.errorCount)
    }

    @Test
    fun onDemandAttemptCache_prunesOldestEntriesWhenCapExceeded() = runTest {
        val dao = mock<AircraftMetadataDao>()
        val client = mock<OpenSkyIcaoMetadataClient>()
        val clock = FakeClock(monoMs = 100_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        whenever(dao.getActiveByIcao24s(any())).thenReturn(emptyList())
        whenever(client.fetchByIcao24(any())).thenReturn(Result.success(null))

        val repository = AircraftMetadataRepositoryImpl(
            dao = dao,
            onDemandClient = client,
            clock = clock,
            ioDispatcher = dispatcher
        )

        val ids = (0 until (AircraftMetadataRepositoryImpl.ON_DEMAND_ATTEMPT_CACHE_MAX_ENTRIES + 16))
            .map { value -> value.toString(16).padStart(6, '0') }
        val rounds = (ids.size / AircraftMetadataRepositoryImpl.ON_DEMAND_MAX_BATCH_SIZE) + 6

        repeat(rounds) {
            clock.advanceMonoMs(1L)
            repository.getMetadataFor(ids)
            advanceUntilIdle()
        }

        verify(client, atLeast(2)).fetchByIcao24(ids.first())
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
