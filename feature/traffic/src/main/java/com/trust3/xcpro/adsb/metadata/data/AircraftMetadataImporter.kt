package com.trust3.xcpro.adsb.metadata.data

import androidx.room.withTransaction
import com.trust3.xcpro.common.di.IoDispatcher
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class AircraftMetadataImportResult(
    val importedRowCount: Int
)

@Singleton
class AircraftMetadataImporter @Inject constructor(
    private val database: AdsbMetadataDatabase,
    private val dao: AircraftMetadataDao,
    private val parser: AircraftMetadataCsvParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun importCsv(inputStream: InputStream): AircraftMetadataImportResult {
        return withContext(ioDispatcher) {
            var sourceRowOrder = 0L
            var importedRows = 0

            database.withTransaction {
                dao.clearStaging()
                val activeRowCountBeforeImport = dao.countActive()

                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    val headerLine = reader.readLine()
                        ?: throw IllegalArgumentException("Metadata CSV is empty")
                    val headerMapping = parser.parseHeader(headerLine)
                    val stagingBatch = ArrayList<AircraftMetadataStagingEntity>(
                        AircraftMetadataSyncPolicy.INSERT_BATCH_SIZE
                    )

                    while (true) {
                        coroutineContext.ensureActive()
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) {
                            continue
                        }
                        sourceRowOrder += 1
                        val row = parser.parseRecord(
                            rawLine = line,
                            mapping = headerMapping,
                            sourceRowOrder = sourceRowOrder
                        ) ?: continue
                        stagingBatch += row.toStagingEntity()
                        if (stagingBatch.size >= AircraftMetadataSyncPolicy.INSERT_BATCH_SIZE) {
                            dao.upsertStagingBatch(stagingBatch)
                            stagingBatch.clear()
                        }
                    }
                    if (stagingBatch.isNotEmpty()) {
                        dao.upsertStagingBatch(stagingBatch)
                        stagingBatch.clear()
                    }
                }

                val stagingCount = dao.countStaging()
                if (stagingCount <= 0) {
                    throw IllegalStateException("Metadata staging import produced zero rows")
                }
                validateImportHealth(
                    activeRowCountBeforeImport = activeRowCountBeforeImport,
                    stagingRowCount = stagingCount
                )
                importedRows = stagingCount

                dao.clearActive()
                dao.copyStagingToActive()
                dao.clearStaging()
            }

            AircraftMetadataImportResult(importedRowCount = importedRows)
        }
    }

    private fun ParsedMetadataRecord.toStagingEntity(): AircraftMetadataStagingEntity {
        return AircraftMetadataStagingEntity(
            icao24 = icao24,
            registration = registration,
            typecode = typecode,
            model = model,
            manufacturerName = manufacturerName,
            owner = owner,
            operator = operator,
            operatorCallsign = operatorCallsign,
            icaoAircraftType = icaoAircraftType,
            qualityScore = qualityScore,
            sourceRowOrder = sourceRowOrder
        )
    }

    private fun validateImportHealth(
        activeRowCountBeforeImport: Int,
        stagingRowCount: Int
    ) {
        val minExpectedRows = minimumExpectedRowsForPromotion(
            activeRowCountBeforeImport = activeRowCountBeforeImport,
            minRatio = AircraftMetadataSyncPolicy.IMPORT_HEALTH_GUARD_MIN_RATIO
        )
        if (
            shouldRejectMetadataPromotion(
                activeRowCountBeforeImport = activeRowCountBeforeImport,
                stagingRowCount = stagingRowCount,
                minBaselineRows = AircraftMetadataSyncPolicy.IMPORT_HEALTH_GUARD_MIN_BASELINE_ROWS,
                minRatio = AircraftMetadataSyncPolicy.IMPORT_HEALTH_GUARD_MIN_RATIO
            )
        ) {
            throw IllegalStateException(
                "Metadata import health guard blocked promotion: " +
                    "stagingRows=$stagingRowCount baselineRows=$activeRowCountBeforeImport " +
                    "minimumExpectedRows=$minExpectedRows"
            )
        }
    }
}

internal fun shouldRejectMetadataPromotion(
    activeRowCountBeforeImport: Int,
    stagingRowCount: Int,
    minBaselineRows: Int,
    minRatio: Double
): Boolean {
    if (activeRowCountBeforeImport < minBaselineRows) return false
    return stagingRowCount < minimumExpectedRowsForPromotion(
        activeRowCountBeforeImport = activeRowCountBeforeImport,
        minRatio = minRatio
    )
}

internal fun minimumExpectedRowsForPromotion(
    activeRowCountBeforeImport: Int,
    minRatio: Double
): Int = ceil(activeRowCountBeforeImport * minRatio).toInt()
