package com.example.xcpro.adsb.metadata.data

import com.example.xcpro.adsb.metadata.domain.AircraftMetadata
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        if (fromDb.size == normalized.size) {
            return fromDb
        }

        val missing = normalized.filterNot(fromDb::containsKey)
        scheduleOnDemandLookup(missing = missing, nowMonoMs = clock.nowMonoMs())
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

    private fun scheduleOnDemandLookup(missing: List<String>, nowMonoMs: Long) {
        val eligibleMissing = ArrayList<String>(ON_DEMAND_MAX_BATCH_SIZE)
        for (icao24 in missing) {
            if (eligibleMissing.size >= ON_DEMAND_MAX_BATCH_SIZE) break
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
                eligibleMissing.forEach { missingIcao24 ->
                    val attemptedAtMonoMs = clock.nowMonoMs()
                    val fetchResult = onDemandClient.fetchByIcao24(missingIcao24)
                    if (fetchResult.isFailure) {
                        onDemandAttemptByIcao24[missingIcao24] = OnDemandAttempt(
                            attemptedAtMonoMs = attemptedAtMonoMs,
                            cooldownMs = ON_DEMAND_ERROR_RETRY_COOLDOWN_MS
                        )
                        return@forEach
                    }

                    val hydrated = fetchResult.getOrNull()
                    if (hydrated == null) {
                        onDemandAttemptByIcao24[missingIcao24] = OnDemandAttempt(
                            attemptedAtMonoMs = attemptedAtMonoMs,
                            cooldownMs = ON_DEMAND_NOT_FOUND_RETRY_COOLDOWN_MS
                        )
                        return@forEach
                    }

                    hydratedRows += hydrated
                    onDemandAttemptByIcao24.remove(missingIcao24)
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

    private companion object {
        val ICAO24_REGEX = Regex("[0-9a-f]{6}")
        const val ON_DEMAND_MAX_BATCH_SIZE = 8
        const val ON_DEMAND_NOT_FOUND_RETRY_COOLDOWN_MS = 10L * 60L * 1000L
        const val ON_DEMAND_ERROR_RETRY_COOLDOWN_MS = 60L * 1000L
    }

    private val onDemandInFlightByIcao24 = ConcurrentHashMap.newKeySet<String>()
    private val onDemandAttemptByIcao24 = ConcurrentHashMap<String, OnDemandAttempt>()

    private data class OnDemandAttempt(
        val attemptedAtMonoMs: Long,
        val cooldownMs: Long
    )
}
