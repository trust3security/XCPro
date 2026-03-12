package com.example.xcpro.weglide.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weglide_aircraft")
data class WeGlideAircraftEntity(
    @PrimaryKey val aircraftId: Long,
    val name: String,
    val kind: String?,
    val scoringClass: String?,
    val rawJson: String,
    val updatedAtEpochMs: Long
)
