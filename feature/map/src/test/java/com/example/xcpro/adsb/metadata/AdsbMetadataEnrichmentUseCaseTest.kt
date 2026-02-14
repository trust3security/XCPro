package com.example.xcpro.adsb.metadata

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import com.example.xcpro.adsb.metadata.domain.AdsbMetadataEnrichmentUseCase
import com.example.xcpro.adsb.metadata.domain.AircraftMetadata
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataSyncRepository
import com.example.xcpro.adsb.metadata.domain.MetadataAvailability
import com.example.xcpro.adsb.metadata.domain.MetadataSyncRunResult
import com.example.xcpro.adsb.metadata.domain.MetadataSyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbMetadataEnrichmentUseCaseTest {

    @Test
    fun selectedTarget_withMetadata_emitsReadyDetails() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Success(100L, "key", "etag"))
        val metadataRepository = FakeMetadataRepository(
            mapOf(
                "abc123" to AircraftMetadata(
                    icao24 = "abc123",
                    registration = "VH-DFV",
                    typecode = "C208",
                    model = "CESSNA 208 Caravan",
                    manufacturerName = "Textron",
                    owner = null,
                    operator = "Operator",
                    operatorCallsign = "OPR",
                    icaoAircraftType = "L1T"
                )
            )
        )
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val selectedId = MutableStateFlow(Icao24.from("abc123"))
        val targets = MutableStateFlow(listOf(target("abc123")))
        val flow = useCase.selectedTargetDetails(selectedId, targets)

        advanceUntilIdle()
        val latest = flow.first()

        requireNotNull(latest)
        assertEquals("VH-DFV", latest.registration)
        assertEquals("C208", latest.typecode)
        assertEquals("CESSNA 208 Caravan", latest.model)
        assertEquals(MetadataAvailability.Ready, latest.metadataAvailability)
    }

    @Test
    fun selectedTarget_missingWhileRunning_emitsSyncInProgress() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Running)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val selectedId = MutableStateFlow(Icao24.from("abc123"))
        val targets = MutableStateFlow(listOf(target("abc123")))
        val latest = useCase.selectedTargetDetails(selectedId, targets).first()

        requireNotNull(latest)
        assertEquals(MetadataAvailability.SyncInProgress, latest.metadataAvailability)
    }

    @Test
    fun selectedTarget_missingAfterFailure_emitsUnavailable() = runTest {
        val syncRepository = FakeSyncRepository(
            MetadataSyncState.Failed(
                reason = "network down",
                lastAttemptWallMs = 10L,
                retryAtWallMs = null
            )
        )
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val selectedId = MutableStateFlow(Icao24.from("abc123"))
        val targets = MutableStateFlow(listOf(target("abc123")))
        val latest = useCase.selectedTargetDetails(selectedId, targets).first()

        requireNotNull(latest)
        assertTrue(latest.metadataAvailability is MetadataAvailability.Unavailable)
    }

    @Test
    fun selectedTargetSwitch_doesNotCarryOverOldMetadata() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Success(100L, "key", null))
        val metadataRepository = FakeMetadataRepository(
            mapOf(
                "abc123" to AircraftMetadata(
                    icao24 = "abc123",
                    registration = "VH-DFV",
                    typecode = "C208",
                    model = "Model A",
                    manufacturerName = null,
                    owner = null,
                    operator = null,
                    operatorCallsign = null,
                    icaoAircraftType = null
                )
            )
        )
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val selectedId = MutableStateFlow(Icao24.from("abc123"))
        val targets = MutableStateFlow(listOf(target("abc123"), target("def456")))
        val flow = useCase.selectedTargetDetails(selectedId, targets)

        var latest = flow.first()
        requireNotNull(latest)
        assertEquals("VH-DFV", latest.registration)

        selectedId.value = Icao24.from("def456")
        advanceUntilIdle()
        latest = flow.first()
        requireNotNull(latest)
        assertEquals("def456", latest.id.raw)
        assertEquals(null, latest.registration)
        assertEquals(MetadataAvailability.Missing, latest.metadataAvailability)
    }

    @Test
    fun targetsWithMetadata_appliesTypecodeAndIcaoAircraftTypeByIcao() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(
            mapOf(
                "abc123" to AircraftMetadata(
                    icao24 = "abc123",
                    registration = "VH-DFV",
                    typecode = "R44",
                    model = null,
                    manufacturerName = null,
                    owner = null,
                    operator = null,
                    operatorCallsign = null,
                    icaoAircraftType = "H1P"
                )
            )
        )
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val targets = MutableStateFlow(listOf(target("abc123"), target("def456")))

        val enriched = useCase.targetsWithMetadata(targets).first()

        val first = enriched.first { it.id.raw == "abc123" }
        assertEquals("R44", first.metadataTypecode)
        assertEquals("H1P", first.metadataIcaoAircraftType)
        val second = enriched.first { it.id.raw == "def456" }
        assertEquals(null, second.metadataTypecode)
        assertEquals(null, second.metadataIcaoAircraftType)
    }

    @Test
    fun targetsWithMetadata_clearsStaleMetadataWhenNoLongerAvailable() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val baseTarget = target("abc123").copy(
            metadataTypecode = "C172",
            metadataIcaoAircraftType = "L1P"
        )
        val targets = MutableStateFlow(listOf(baseTarget))

        val enriched = useCase.targetsWithMetadata(targets).first()

        val first = enriched.first()
        assertEquals(null, first.metadataTypecode)
        assertEquals(null, first.metadataIcaoAircraftType)
    }

    @Test
    fun targetsWithMetadata_reactsImmediatelyToMetadataRevisionWithoutTargetReemit() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val targets = MutableStateFlow(listOf(target("abc123")))

        val shared = useCase.targetsWithMetadata(targets).shareIn(
            scope = backgroundScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )
        advanceUntilIdle()

        val initial = shared.first()
        assertEquals(null, initial.first().metadataTypecode)

        val awaitUpdated = backgroundScope.async {
            shared.drop(1).first()
        }

        metadataRepository.upsertMetadata(
            AircraftMetadata(
                icao24 = "abc123",
                registration = "N123AB",
                typecode = "B738",
                model = "Boeing 737-800",
                manufacturerName = "Boeing",
                owner = null,
                operator = null,
                operatorCallsign = null,
                icaoAircraftType = "L2J"
            )
        )
        advanceUntilIdle()

        val updated = withTimeout(1_000) { awaitUpdated.await() }
        assertEquals("B738", updated.first().metadataTypecode)
        assertEquals("L2J", updated.first().metadataIcaoAircraftType)
    }

    @Test
    fun selectedTargetDetails_reactsImmediatelyToMetadataRevisionWithoutTargetReemit() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val selectedId = MutableStateFlow(Icao24.from("abc123"))
        val targets = MutableStateFlow(listOf(target("abc123")))

        val shared = useCase.selectedTargetDetails(selectedId, targets)
            .filterNotNull()
            .shareIn(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                replay = 1
            )
        advanceUntilIdle()

        val initial = shared.first()
        assertEquals(MetadataAvailability.Missing, initial.metadataAvailability)

        val awaitUpdated = backgroundScope.async {
            shared.drop(1).first()
        }

        metadataRepository.upsertMetadata(
            AircraftMetadata(
                icao24 = "abc123",
                registration = "N123AB",
                typecode = "B738",
                model = "Boeing 737-800",
                manufacturerName = "Boeing",
                owner = null,
                operator = null,
                operatorCallsign = null,
                icaoAircraftType = "L2J"
            )
        )
        advanceUntilIdle()

        val updated = withTimeout(1_000) { awaitUpdated.await() }
        assertEquals("N123AB", updated.registration)
        assertEquals("B738", updated.typecode)
        assertEquals(MetadataAvailability.Ready, updated.metadataAvailability)
    }

    @Test
    fun targetsWithMetadata_prioritizesLookupForUnknownCategoryTargets() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val targets = MutableStateFlow(
            listOf(
                target("abc123", category = 2),
                target("def456", category = 0),
                target("fedcba", category = 8)
            )
        )

        useCase.targetsWithMetadata(targets).first()

        assertEquals(listOf("def456", "abc123", "fedcba"), metadataRepository.lastLookupOrder)
    }

    private fun target(rawIcao24: String, category: Int = 2): AdsbTrafficUiModel {
        val id = Icao24.from(rawIcao24) ?: error("invalid ICAO24")
        return AdsbTrafficUiModel(
            id = id,
            callsign = rawIcao24.uppercase(),
            lat = -33.86,
            lon = 151.20,
            altitudeM = 1000.0,
            speedMps = 70.0,
            trackDeg = 180.0,
            climbMps = 0.5,
            ageSec = 2,
            isStale = false,
            distanceMeters = 1200.0,
            bearingDegFromUser = 90.0,
            positionSource = 0,
            category = category,
            lastContactEpochSec = 1_710_000_000L
        )
    }

    private class FakeMetadataRepository(
        private val values: Map<String, AircraftMetadata>
    ) : AircraftMetadataRepository {
        override val metadataRevision = MutableStateFlow(0L)
        private val metadataByIcao24 = values.toMutableMap()
        var lastLookupOrder: List<String> = emptyList()
            private set

        override suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata> {
            lastLookupOrder = icao24s
            return metadataByIcao24.filterKeys { it in icao24s }
        }

        fun upsertMetadata(metadata: AircraftMetadata) {
            metadataByIcao24[metadata.icao24] = metadata
            metadataRevision.value = metadataRevision.value + 1L
        }
    }

    private class FakeSyncRepository(initialState: MetadataSyncState) : AircraftMetadataSyncRepository {
        private val mutableState = MutableStateFlow(initialState)
        override val syncState: StateFlow<MetadataSyncState> = mutableState

        override suspend fun onScheduled() {
            mutableState.value = MetadataSyncState.Scheduled
        }

        override suspend fun onPausedByUser() {
            mutableState.value = MetadataSyncState.PausedByUser(lastSuccessWallMs = null)
        }

        override suspend fun runSyncNow(): MetadataSyncRunResult = MetadataSyncRunResult.Skipped
    }
}
