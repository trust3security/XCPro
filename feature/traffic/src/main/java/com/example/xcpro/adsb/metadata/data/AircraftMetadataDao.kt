package com.example.xcpro.adsb.metadata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AircraftMetadataDao {

    @Query("DELETE FROM adsb_aircraft_metadata_staging")
    suspend fun clearStaging()

    @Query("DELETE FROM adsb_aircraft_metadata")
    suspend fun clearActive()

    @Query("SELECT * FROM adsb_aircraft_metadata_staging WHERE icao24 IN (:icao24s)")
    suspend fun getStagingByIcao24s(icao24s: List<String>): List<AircraftMetadataStagingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStagingRows(rows: List<AircraftMetadataStagingEntity>)

    @Transaction
    suspend fun upsertStagingBatch(rows: List<AircraftMetadataStagingEntity>) {
        if (rows.isEmpty()) return

        val requestedIcao24s = rows.map { row -> row.icao24 }.distinct()
        val existingByIcao24 = LinkedHashMap<String, AircraftMetadataStagingEntity>(requestedIcao24s.size)
        requestedIcao24s
            .chunked(AircraftMetadataSyncPolicy.LOOKUP_CHUNK_SIZE)
            .forEach { chunk ->
                getStagingByIcao24s(chunk).forEach { row ->
                    existingByIcao24[row.icao24] = row
                }
            }

        val mergedByIcao24 = LinkedHashMap<String, AircraftMetadataStagingEntity>(requestedIcao24s.size)
        rows.forEach { row ->
            val current = mergedByIcao24[row.icao24] ?: existingByIcao24[row.icao24]
            mergedByIcao24[row.icao24] = if (current == null) {
                row
            } else {
                current.mergeWith(row)
            }
        }

        upsertStagingRows(mergedByIcao24.values.toList())
    }

    @Query("SELECT COUNT(*) FROM adsb_aircraft_metadata_staging")
    suspend fun countStaging(): Int

    @Query("SELECT COUNT(*) FROM adsb_aircraft_metadata")
    suspend fun countActive(): Int

    @Query(
        """
        INSERT INTO adsb_aircraft_metadata(
            icao24,
            registration,
            typecode,
            model,
            manufacturer_name,
            owner,
            operator,
            operator_callsign,
            icao_aircraft_type,
            quality_score,
            source_row_order
        )
        SELECT
            icao24,
            registration,
            typecode,
            model,
            manufacturer_name,
            owner,
            operator,
            operator_callsign,
            icao_aircraft_type,
            quality_score,
            source_row_order
        FROM adsb_aircraft_metadata_staging
        """
    )
    suspend fun copyStagingToActive()

    @Query("SELECT * FROM adsb_aircraft_metadata WHERE icao24 IN (:icao24s)")
    suspend fun getActiveByIcao24s(icao24s: List<String>): List<AircraftMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActive(rows: List<AircraftMetadataEntity>)
}

private fun AircraftMetadataStagingEntity.mergeWith(
    incoming: AircraftMetadataStagingEntity
): AircraftMetadataStagingEntity {
    val incomingWins = incoming.qualityScore > qualityScore ||
        (incoming.qualityScore == qualityScore && incoming.sourceRowOrder >= sourceRowOrder)
    val winner = if (incomingWins) incoming else this
    val loser = if (incomingWins) this else incoming

    return AircraftMetadataStagingEntity(
        icao24 = winner.icao24,
        registration = winner.registration ?: loser.registration,
        typecode = winner.typecode ?: loser.typecode,
        model = winner.model ?: loser.model,
        manufacturerName = winner.manufacturerName ?: loser.manufacturerName,
        owner = winner.owner ?: loser.owner,
        operator = winner.operator ?: loser.operator,
        operatorCallsign = winner.operatorCallsign ?: loser.operatorCallsign,
        icaoAircraftType = winner.icaoAircraftType ?: loser.icaoAircraftType,
        qualityScore = winner.qualityScore,
        sourceRowOrder = winner.sourceRowOrder
    )
}
