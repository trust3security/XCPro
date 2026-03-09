package com.example.xcpro.adsb.metadata.data

import com.example.xcpro.adsb.metadata.domain.AircraftMetadata
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdsbMetadataLookupTelemetrySnapshot(
    val lookupSampleCount: Long = 0L,
    val totalLookupLatencyMs: Long = 0L,
    val lastLookupLatencyMs: Long? = null,
    val maxLookupLatencyMs: Long? = null,
    val successCount: Long = 0L,
    val notFoundCount: Long = 0L,
    val errorCount: Long = 0L
) {
    val averageLookupLatencyMs: Long?
        get() = if (lookupSampleCount <= 0L) null else totalLookupLatencyMs / lookupSampleCount
}

@Singleton
class AircraftMetadataRepositoryImpl @Inject constructor(
    private val dao: AircraftMetadataDao,
    private val onDemandClient: OpenSkyIcaoMetadataClient,
    private val clock: Clock,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : AircraftMetadataRepository {

    private val lookupScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutableMetadataRevision = MutableStateFlow(0L)
    override val metadataRevision: StateFlow<Long> = mutableMetadataRevision.asStateFlow()
    private val mutableLookupTelemetry = MutableStateFlow(AdsbMetadataLookupTelemetrySnapshot())
    val lookupTelemetry: StateFlow<AdsbMetadataLookupTelemetrySnapshot> = mutableLookupTelemetry.asStateFlow()

    override suspend fun getMetadataFor(icao24s: List<String>): Map<String, AircraftMetadata> {
        if (icao24s.isEmpty()) {
            return emptyMap()
        }
        val normalized = icao24s.mapNotNull(::normalizeIcao24).distinct()
        if (normalized.isEmpty()) {
            return emptyMap()
        }

        val rows = ArrayList<AircraftMetadataEntity>(normalized.size)
        normalized.chunked(AircraftMetadataSyncPolicy.LOOKUP_CHUNK_SIZE).forEach { chunk ->
            rows += dao.getActiveByIcao24s(chunk)
        }
        val fromDb = rows.associate { row ->
            row.icao24 to row.toDomain()
        }
        val missing = normalized.filterNot(fromDb::containsKey)
        val incomplete = rows
            .asSequence()
            .filter(::requiresOnDemandHydration)
            .map { row -> row.icao24 }
            .toList()
        if (missing.isEmpty() && incomplete.isEmpty()) {
            return fromDb
        }
        val nowMonoMs = clock.nowMonoMs()
        pruneOnDemandAttemptCache(nowMonoMs)
        scheduleOnDemandLookup(
            missing = (missing + incomplete).distinct(),
            nowMonoMs = nowMonoMs
        )
        return fromDb
    }

    private fun normalizeIcao24(raw: String?): String? {
        return raw
            ?.trim()
            ?.lowercase()
            ?.takeIf { ICAO24_REGEX.matches(it) }
    }

    private fun isEligibleForOnDemandLookup(icao24: String, nowMonoMs: Long): Boolean {
        val lastAttempt = onDemandAttemptByIcao24[icao24] ?: return true
        return nowMonoMs - lastAttempt.attemptedAtMonoMs >= lastAttempt.cooldownMs
    }

    private fun requiresOnDemandHydration(row: AircraftMetadataEntity): Boolean {
        val missingIdentificationFields = row.qualityScore < COMPLETE_IDENTIFICATION_QUALITY_SCORE
        val missingIconHint = row.icaoAircraftType.isNullOrBlank()
        return missingIdentificationFields || missingIconHint
    }

    private fun scheduleOnDemandLookup(missing: List<String>, nowMonoMs: Long) {
        if (missing.isEmpty()) return

        val eligibleMissing = ArrayList<String>(ON_DEMAND_MAX_BATCH_SIZE)
        val startIndex = nextRoundRobinStartIndex(missing.size)
        for (offset in 0 until missing.size) {
            if (eligibleMissing.size >= ON_DEMAND_MAX_BATCH_SIZE) break
            val icao24 = missing[(startIndex + offset) % missing.size]
            if (!isEligibleForOnDemandLookup(icao24 = icao24, nowMonoMs = nowMonoMs)) continue
            if (!onDemandInFlightByIcao24.add(icao24)) continue
            eligibleMissing += icao24
        }
        if (eligibleMissing.isEmpty()) {
            return
        }

        lookupScope.launch {
            val hydratedRows = ArrayList<AircraftMetadataEntity>(eligibleMissing.size)
            try {
                eligibleMissing
                    .chunked(ON_DEMAND_FETCH_PARALLELISM)
                    .forEach { batch ->
                        val outcomes = fetchOnDemandBatch(batch)
                        outcomes.forEach { outcome ->
                            recordLookupOutcome(outcome)
                            val missingIcao24 = outcome.icao24
                            val fetchResult = outcome.fetchResult
                            if (fetchResult.isFailure) {
                                onDemandAttemptByIcao24[missingIcao24] = OnDemandAttempt(
                                    attemptedAtMonoMs = outcome.attemptedAtMonoMs,
                                    cooldownMs = ON_DEMAND_ERROR_RETRY_COOLDOWN_MS
                                )
                                return@forEach
                            }

                            val hydrated = fetchResult.getOrNull()
                            if (hydrated == null) {
                                onDemandAttemptByIcao24[missingIcao24] = OnDemandAttempt(
                                    attemptedAtMonoMs = outcome.attemptedAtMonoMs,
                                    cooldownMs = ON_DEMAND_NOT_FOUND_RETRY_COOLDOWN_MS
                                )
                                return@forEach
                            }

                            hydratedRows += hydrated
                            if (requiresOnDemandHydration(hydrated)) {
                                onDemandAttemptByIcao24[missingIcao24] = OnDemandAttempt(
                                    attemptedAtMonoMs = outcome.attemptedAtMonoMs,
                                    cooldownMs = ON_DEMAND_INCOMPLETE_RETRY_COOLDOWN_MS
                                )
                            } else {
                                onDemandAttemptByIcao24.remove(missingIcao24)
                            }
                        }
                    }

                if (hydratedRows.isNotEmpty()) {
                    dao.upsertActive(hydratedRows)
                    mutableMetadataRevision.update { revision -> revision + 1L }
                }
            } finally {
                eligibleMissing.forEach(onDemandInFlightByIcao24::remove)
            }
        }
    }

    private fun nextRoundRobinStartIndex(size: Int): Int {
        if (size <= 0) return 0
        while (true) {
            val current = onDemandRoundRobinCursor.get()
            val normalized = current.mod(size)
            val next = (normalized + 1).mod(size)
            if (onDemandRoundRobinCursor.compareAndSet(current, next)) {
                return normalized
            }
        }
    }

    private suspend fun fetchOnDemandBatch(batch: List<String>): List<OnDemandFetchOutcome> =
        coroutineScope {
            batch.map { icao24 ->
                async {
                    val attemptedAtMonoMs = clock.nowMonoMs()
                    val fetchResult = onDemandClient.fetchByIcao24(icao24)
                    OnDemandFetchOutcome(
                        icao24 = icao24,
                        attemptedAtMonoMs = attemptedAtMonoMs,
                        completedAtMonoMs = clock.nowMonoMs(),
                        fetchResult = fetchResult
                    )
                }
            }.awaitAll()
        }

    private fun recordLookupOutcome(outcome: OnDemandFetchOutcome) {
        val latencyMs = (outcome.completedAtMonoMs - outcome.attemptedAtMonoMs).coerceAtLeast(0L)
        val isSuccess = outcome.fetchResult.getOrNull() != null
        val isNotFound = outcome.fetchResult.isSuccess && outcome.fetchResult.getOrNull() == null
        val isError = outcome.fetchResult.isFailure
        mutableLookupTelemetry.update { current ->
            val samples = current.lookupSampleCount + 1L
            val totalLatency = current.totalLookupLatencyMs + latencyMs
            AdsbMetadataLookupTelemetrySnapshot(
                lookupSampleCount = samples,
                totalLookupLatencyMs = totalLatency,
                lastLookupLatencyMs = latencyMs,
                maxLookupLatencyMs = maxOf(current.maxLookupLatencyMs ?: latencyMs, latencyMs),
                successCount = current.successCount + if (isSuccess) 1L else 0L,
                notFoundCount = current.notFoundCount + if (isNotFound) 1L else 0L,
                errorCount = current.errorCount + if (isError) 1L else 0L
            )
        }
    }

    private fun pruneOnDemandAttemptCache(nowMonoMs: Long) {
        if (onDemandAttemptByIcao24.isEmpty()) return

        onDemandAttemptByIcao24.entries.removeIf { (_, attempt) ->
            val ageMs = nowMonoMs - attempt.attemptedAtMonoMs
            ageMs >= ON_DEMAND_ATTEMPT_ENTRY_TTL_MS
        }

        val overflow = onDemandAttemptByIcao24.size - ON_DEMAND_ATTEMPT_CACHE_MAX_ENTRIES
        if (overflow <= 0) return

        val oldestKeys = onDemandAttemptByIcao24.entries
            .asSequence()
            .sortedBy { it.value.attemptedAtMonoMs }
            .take(overflow)
            .map { it.key }
            .toList()
        oldestKeys.forEach(onDemandAttemptByIcao24::remove)
    }

    companion object {
        val ICAO24_REGEX = Regex("[0-9a-f]{6}")
        internal const val COMPLETE_IDENTIFICATION_QUALITY_SCORE = 3
        internal const val ON_DEMAND_MAX_BATCH_SIZE = 8
        internal const val ON_DEMAND_FETCH_PARALLELISM = 4
        internal const val ON_DEMAND_NOT_FOUND_RETRY_COOLDOWN_MS = 10L * 60L * 1000L
        internal const val ON_DEMAND_ERROR_RETRY_COOLDOWN_MS = 60L * 1000L
        internal const val ON_DEMAND_INCOMPLETE_RETRY_COOLDOWN_MS = 12L * 60L * 60L * 1000L
        internal const val ON_DEMAND_ATTEMPT_ENTRY_TTL_MS = 6L * 60L * 60L * 1000L
        internal const val ON_DEMAND_ATTEMPT_CACHE_MAX_ENTRIES = 2_048
    }

    private val onDemandInFlightByIcao24 = ConcurrentHashMap.newKeySet<String>()
    private val onDemandAttemptByIcao24 = ConcurrentHashMap<String, OnDemandAttempt>()
    private val onDemandRoundRobinCursor = AtomicInteger(0)

    private data class OnDemandAttempt(
        val attemptedAtMonoMs: Long,
        val cooldownMs: Long
    )

    private data class OnDemandFetchOutcome(
        val icao24: String,
        val attemptedAtMonoMs: Long,
        val completedAtMonoMs: Long,
        val fetchResult: Result<AircraftMetadataEntity?>
    )
}
