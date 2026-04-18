package com.trust3.xcpro.weglide.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weglide_aircraft_mapping")
data class WeGlideAircraftMappingEntity(
    @PrimaryKey val localProfileId: String,
    val weglideAircraftId: Long,
    val weglideAircraftName: String,
    val updatedAtEpochMs: Long
)
