package com.trust3.xcpro.weglide.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WeGlideAircraftDao {

    @Query("SELECT * FROM weglide_aircraft ORDER BY name ASC, aircraftId ASC")
    fun observeAll(): Flow<List<WeGlideAircraftEntity>>

    @Query("SELECT * FROM weglide_aircraft WHERE aircraftId = :aircraftId LIMIT 1")
    suspend fun getById(aircraftId: Long): WeGlideAircraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WeGlideAircraftEntity>)

    @Query("DELETE FROM weglide_aircraft")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(rows: List<WeGlideAircraftEntity>) {
        clearAll()
        if (rows.isNotEmpty()) {
            upsertAll(rows)
        }
    }
}
