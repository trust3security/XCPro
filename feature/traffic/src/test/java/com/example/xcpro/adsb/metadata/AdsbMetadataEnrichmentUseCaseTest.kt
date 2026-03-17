package com.example.xcpro.adsb.metadata

import com.example.xcpro.adsb.AdsbProximityTier
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
import kotlinx.coroutines.launch
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
        val targets = MutableStateFlow(
            listOf(
                target("abc123").copy(
                    proximityTier = AdsbProximityTier.RED,
                    isClosing = true,
                    closingRateMps = 1.8,
                    isEmergencyCollisionRisk = true
                )
            )
        )
        val flow = useCase.selectedTargetDetails(selectedId, targets)

        advanceUntilIdle()
        val latest = flow.first()

        requireNotNull(latest)
        assertEquals("VH-DFV", latest.registration)
        assertEquals("C208", latest.typecode)
        assertEquals("CESSNA 208 Caravan", latest.model)
        assertEquals(MetadataAvailability.Ready, latest.metadataAvailability)
        assertEquals(true, latest.usesOwnshipReference)
        assertEquals(AdsbProximityTier.RED, latest.proximityTier)
        assertEquals(true, latest.isClosing)
        assertEquals(1.8, latest.closingRateMps ?: Double.NaN, 1e-6)
        assertEquals(true, latest.isEmergencyCollisionRisk)
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
        val targets = MutableStateFlow(
            listOf(
                target("abc123").copy(
                    proximityTier = AdsbProximityTier.RED,
                    isClosing = true,
                    closingRateMps = 1.8,
                    isEmergencyCollisionRisk = true
                )
            )
        )
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
    fun selectedTarget_unknownCategoryAndMissingMetadata_preservesUnknownTruth() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val selectedId = MutableStateFlow(Icao24.from("abc123"))
        val targets = MutableStateFlow(listOf(target("abc123", category = 0)))
        val latest = useCase.selectedTargetDetails(selectedId, targets).first()

        requireNotNull(latest)
        assertEquals(0, latest.category)
        assertEquals(MetadataAvailability.Missing, latest.metadataAvailability)
        assertEquals(null, latest.typecode)
        assertEquals(null, latest.icaoAircraftType)
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

        assertEquals(listOf("def456", "fedcba", "abc123"), metadataRepository.lastLookupOrder)
    }

    @Test
    fun targetsWithMetadata_prioritizesTargetsWithExistingMetadataHintsAboveRegularTargets() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val hinted = target("abc123", category = 0).copy(
            metadataTypecode = "B738",
            metadataIcaoAircraftType = "L2J"
        )
        val unknown = target("def456", category = 0)
        val regular = target("fedcba", category = 2)
        val targets = MutableStateFlow(listOf(hinted, unknown, regular))

        useCase.targetsWithMetadata(targets).first()

        assertEquals(listOf("abc123", "def456", "fedcba"), metadataRepository.lastLookupOrder)
    }

    @Test
    fun targetsWithMetadata_prioritizesMetadataHintsForRegularCategoryTargets() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val hinted = target("abc123", category = 2).copy(
            metadataTypecode = "C172"
        )
        val regularA = target("def456", category = 2)
        val regularB = target("fedcba", category = 2)
        val targets = MutableStateFlow(listOf(hinted, regularA, regularB))

        useCase.targetsWithMetadata(targets).first()

        assertEquals(listOf("abc123", "def456", "fedcba"), metadataRepository.lastLookupOrder)
    }

    @Test
    fun targetsWithMetadata_sameIcaoSetRawChurn_doesNotRepeatLookups() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(
            mapOf(
                "abc123" to AircraftMetadata(
                    icao24 = "abc123",
                    registration = "N123AB",
                    typecode = "B738",
                    model = null,
                    manufacturerName = null,
                    owner = null,
                    operator = null,
                    operatorCallsign = null,
                    icaoAircraftType = "L2J"
                )
            )
        )
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val targets = MutableStateFlow(
            listOf(
                target("abc123").copy(distanceMeters = 1_000.0, ageSec = 1),
                target("def456").copy(distanceMeters = 2_000.0, ageSec = 1)
            )
        )
        val shared = useCase.targetsWithMetadata(targets).shareIn(
            scope = backgroundScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )
        advanceUntilIdle()
        shared.first()
        assertEquals(1, metadataRepository.lookupCallCount)

        targets.value = listOf(
            target("abc123").copy(distanceMeters = 1_100.0, ageSec = 2),
            target("def456").copy(distanceMeters = 2_100.0, ageSec = 2)
        )
        advanceUntilIdle()
        shared.first()
        assertEquals(1, metadataRepository.lookupCallCount)
    }

    @Test
    fun targetsWithMetadata_retriesLookupWhenLookupProgressAdvancesWithoutMetadataInsert() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(emptyMap())
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val targets = MutableStateFlow(listOf(target("abc123"), target("def456")))
        val job = backgroundScope.launch {
            useCase.targetsWithMetadata(targets).collect {}
        }

        advanceUntilIdle()
        assertEquals(1, metadataRepository.lookupCallCount)
        metadataRepository.advanceLookupProgress()
        advanceUntilIdle()
        job.cancel()

        assertEquals(2, metadataRepository.lookupCallCount)
    }

    @Test
    fun selectedTargetDetails_sameSelectionRawChurn_doesNotRepeatLookups() = runTest {
        val syncRepository = FakeSyncRepository(MetadataSyncState.Idle)
        val metadataRepository = FakeMetadataRepository(
            mapOf(
                "abc123" to AircraftMetadata(
                    icao24 = "abc123",
                    registration = "N123AB",
                    typecode = "B738",
                    model = null,
                    manufacturerName = null,
                    owner = null,
                    operator = null,
                    operatorCallsign = null,
                    icaoAircraftType = "L2J"
                )
            )
        )
        val useCase = AdsbMetadataEnrichmentUseCase(
            aircraftMetadataRepository = metadataRepository,
            metadataSyncRepository = syncRepository,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )
        val selectedId = MutableStateFlow(Icao24.from("abc123"))
        val targets = MutableStateFlow(
            listOf(target("abc123").copy(distanceMeters = 1_000.0, ageSec = 1))
        )
        val shared = useCase.selectedTargetDetails(selectedId, targets)
            .filterNotNull()
            .shareIn(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                replay = 1
            )
        advanceUntilIdle()
        shared.first()
        assertEquals(1, metadataRepository.lookupCallCount)

        targets.value = listOf(target("abc123").copy(distanceMeters = 1_200.0, ageSec = 3))
        advanceUntilIdle()
        shared.first()
        assertEquals(1, metadataRepository.lookupCallCount)
    }

    @Test
    fun selectedTargetDetails_retriesLookupWhenLookupProgressAdvancesWithoutMetadataInsert() = runTest {
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
        shared.first()
        assertEquals(1, metadataRepository.lookupCallCount)

        val awaitUpdated = backgroundScope.async {
            shared.drop(1).first()
        }
        metadataRepository.advanceLookupProgress()
        advanceUntilIdle()
        withTimeout(1_000) { awaitUpdated.await() }

        assertEquals(2, metadataRepository.lookupCallCount)
    }

}
