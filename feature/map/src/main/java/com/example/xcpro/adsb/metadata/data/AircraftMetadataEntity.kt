package com.example.xcpro.adsb.metadata.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.xcpro.adsb.metadata.domain.AircraftMetadata

@Entity(
    tableName = "adsb_aircraft_metadata",
    indices = [Index(value = ["icao24"], unique = true)]
)
data class AircraftMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "icao24")
    val icao24: String,
    @ColumnInfo(name = "registration")
    val registration: String?,
    @ColumnInfo(name = "typecode")
    val typecode: String?,
    @ColumnInfo(name = "model")
    val model: String?,
    @ColumnInfo(name = "manufacturer_name")
    val manufacturerName: String?,
    @ColumnInfo(name = "owner")
    val owner: String?,
    @ColumnInfo(name = "operator")
    val operator: String?,
    @ColumnInfo(name = "operator_callsign")
    val operatorCallsign: String?,
    @ColumnInfo(name = "icao_aircraft_type")
    val icaoAircraftType: String?,
    @ColumnInfo(name = "quality_score")
    val qualityScore: Int,
    @ColumnInfo(name = "source_row_order")
    val sourceRowOrder: Long
)

@Entity(
    tableName = "adsb_aircraft_metadata_staging",
    indices = [Index(value = ["icao24"], unique = true)]
)
data class AircraftMetadataStagingEntity(
    @PrimaryKey
    @ColumnInfo(name = "icao24")
    val icao24: String,
    @ColumnInfo(name = "registration")
    val registration: String?,
    @ColumnInfo(name = "typecode")
    val typecode: String?,
    @ColumnInfo(name = "model")
    val model: String?,
    @ColumnInfo(name = "manufacturer_name")
    val manufacturerName: String?,
    @ColumnInfo(name = "owner")
    val owner: String?,
    @ColumnInfo(name = "operator")
    val operator: String?,
    @ColumnInfo(name = "operator_callsign")
    val operatorCallsign: String?,
    @ColumnInfo(name = "icao_aircraft_type")
    val icaoAircraftType: String?,
    @ColumnInfo(name = "quality_score")
    val qualityScore: Int,
    @ColumnInfo(name = "source_row_order")
    val sourceRowOrder: Long
)

internal fun AircraftMetadataEntity.toDomain(): AircraftMetadata {
    return AircraftMetadata(
        icao24 = icao24,
        registration = registration,
        typecode = typecode,
        model = model,
        manufacturerName = manufacturerName,
        owner = owner,
        operator = operator,
        operatorCallsign = operatorCallsign,
        icaoAircraftType = icaoAircraftType
    )
}

