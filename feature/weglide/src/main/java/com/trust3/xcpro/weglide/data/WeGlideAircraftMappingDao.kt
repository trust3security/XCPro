package com.trust3.xcpro.weglide.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeGlideAircraftMappingDao {

    @Query("SELECT * FROM weglide_aircraft_mapping ORDER BY localProfileId ASC")
    fun observeAll(): Flow<List<WeGlideAircraftMappingEntity>>

    @Query("SELECT * FROM weglide_aircraft_mapping WHERE localProfileId = :profileId LIMIT 1")
    fun observeByProfileId(profileId: String): Flow<WeGlideAircraftMappingEntity?>

    @Query("SELECT * FROM weglide_aircraft_mapping WHERE localProfileId = :profileId LIMIT 1")
    suspend fun getByProfileId(profileId: String): WeGlideAircraftMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: WeGlideAircraftMappingEntity)

    @Query("DELETE FROM weglide_aircraft_mapping WHERE localProfileId = :profileId")
    suspend fun deleteByProfileId(profileId: String)
}
