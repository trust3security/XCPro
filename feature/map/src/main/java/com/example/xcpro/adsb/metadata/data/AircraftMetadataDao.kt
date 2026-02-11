package com.example.xcpro.adsb.metadata.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AircraftMetadataDao {

    @Query("DELETE FROM adsb_aircraft_metadata_staging")
    suspend fun clearStaging()

    @Query("DELETE FROM adsb_aircraft_metadata")
    suspend fun clearActive()

    @Query(
        """
        INSERT INTO adsb_aircraft_metadata_staging(
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
        ) VALUES (
            :icao24,
            :registration,
            :typecode,
            :model,
            :manufacturerName,
            :owner,
            :operator,
            :operatorCallsign,
            :icaoAircraftType,
            :qualityScore,
            :sourceRowOrder
        )
        ON CONFLICT(icao24) DO UPDATE SET
            registration = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.registration ELSE adsb_aircraft_metadata_staging.registration END,
            typecode = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.typecode ELSE adsb_aircraft_metadata_staging.typecode END,
            model = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.model ELSE adsb_aircraft_metadata_staging.model END,
            manufacturer_name = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.manufacturer_name ELSE adsb_aircraft_metadata_staging.manufacturer_name END,
            owner = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.owner ELSE adsb_aircraft_metadata_staging.owner END,
            operator = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.operator ELSE adsb_aircraft_metadata_staging.operator END,
            operator_callsign = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.operator_callsign ELSE adsb_aircraft_metadata_staging.operator_callsign END,
            icao_aircraft_type = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.icao_aircraft_type ELSE adsb_aircraft_metadata_staging.icao_aircraft_type END,
            quality_score = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.quality_score ELSE adsb_aircraft_metadata_staging.quality_score END,
            source_row_order = CASE WHEN (
                excluded.quality_score > adsb_aircraft_metadata_staging.quality_score OR
                (excluded.quality_score = adsb_aircraft_metadata_staging.quality_score AND
                 excluded.source_row_order >= adsb_aircraft_metadata_staging.source_row_order)
            ) THEN excluded.source_row_order ELSE adsb_aircraft_metadata_staging.source_row_order END
        """
    )
    suspend fun upsertStagingRow(
        icao24: String,
        registration: String?,
        typecode: String?,
        model: String?,
        manufacturerName: String?,
        owner: String?,
        operator: String?,
        operatorCallsign: String?,
        icaoAircraftType: String?,
        qualityScore: Int,
        sourceRowOrder: Long
    )

    @Query("SELECT COUNT(*) FROM adsb_aircraft_metadata_staging")
    suspend fun countStaging(): Int

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
