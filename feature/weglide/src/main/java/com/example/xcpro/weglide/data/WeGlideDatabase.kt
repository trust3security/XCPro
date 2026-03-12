package com.example.xcpro.weglide.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WeGlideAircraftEntity::class,
        WeGlideAircraftMappingEntity::class,
        WeGlideUploadQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WeGlideDatabase : RoomDatabase() {
    abstract fun aircraftDao(): WeGlideAircraftDao
    abstract fun aircraftMappingDao(): WeGlideAircraftMappingDao
    abstract fun uploadQueueDao(): WeGlideUploadQueueDao
}
