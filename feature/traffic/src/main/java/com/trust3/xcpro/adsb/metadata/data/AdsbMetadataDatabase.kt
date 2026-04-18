package com.trust3.xcpro.adsb.metadata.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AircraftMetadataEntity::class, AircraftMetadataStagingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AdsbMetadataDatabase : RoomDatabase() {
    abstract fun aircraftMetadataDao(): AircraftMetadataDao
}
