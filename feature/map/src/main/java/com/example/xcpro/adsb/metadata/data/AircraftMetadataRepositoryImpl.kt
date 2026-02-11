package com.example.xcpro.adsb.metadata.data

import com.example.xcpro.adsb.metadata.domain.AircraftMetadata
import com.example.xcpro.adsb.metadata.domain.AircraftMetadataRepository
import com.example.xcpro.core.time.Clock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AircraftMetadataRepositoryImpl @Inject constructor(
    private val dao: AircraftMetadataDao,
    private val onDemandClient: OpenSkyIcaoMetadataClient,
    private val clock: Clock
) : AircraftMetadataRepository {

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
            .take(ON_DEMAND_MAX_BATCH_SIZE)
        val hydratedRows = ArrayList<AircraftMetadataEntity>(missing.size)
        val nowMonoMs = clock.nowMonoMs()

        missing.forEach { missingIcao24 ->
            val lastAttemptMonoMs = onDemandAttemptByIcao24[missingIcao24]
            if (lastAttemptMonoMs != null && nowMonoMs - lastAttemptMonoMs < ON_DEMAND_RETRY_COOLDOWN_MS) {
                return@forEach
            }

            onDemandAttemptByIcao24[missingIcao24] = nowMonoMs
            val hydrated = onDemandClient.fetchByIcao24(missingIcao24).getOrNull() ?: return@forEach
            hydratedRows += hydrated
        }

        if (hydratedRows.isNotEmpty()) {
            dao.upsertActive(hydratedRows)
        }

        return (rows + hydratedRows).associate { row ->
            row.icao24 to row.toDomain()
        }
    }

    private fun normalizeIcao24(raw: String?): String? {
        return raw
            ?.trim()
            ?.lowercase()
            ?.takeIf { ICAO24_REGEX.matches(it) }
    }

    private companion object {
        val ICAO24_REGEX = Regex("[0-9a-f]{6}")
        const val ON_DEMAND_MAX_BATCH_SIZE = 3
        const val ON_DEMAND_RETRY_COOLDOWN_MS = 10L * 60L * 1000L
    }

    private val onDemandAttemptByIcao24 = ConcurrentHashMap<String, Long>()
}
