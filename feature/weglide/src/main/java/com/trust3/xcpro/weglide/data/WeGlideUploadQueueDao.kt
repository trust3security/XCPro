package com.trust3.xcpro.weglide.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeGlideUploadQueueDao {

    @Query("SELECT * FROM weglide_upload_queue ORDER BY queuedAtEpochMs DESC")
    fun observeAll(): Flow<List<WeGlideUploadQueueEntity>>

    @Query("SELECT * FROM weglide_upload_queue WHERE localFlightId = :localFlightId LIMIT 1")
    suspend fun getByLocalFlightId(localFlightId: String): WeGlideUploadQueueEntity?

    @Query(
        """
        SELECT * FROM weglide_upload_queue
        WHERE sha256 = :sha256 AND uploadState = 'UPLOADED'
        LIMIT 1
        """
    )
    suspend fun getUploadedBySha256(sha256: String): WeGlideUploadQueueEntity?

    @Query(
        """
        SELECT * FROM weglide_upload_queue
        WHERE uploadState IN ('QUEUED', 'FAILED_RETRYABLE')
        ORDER BY queuedAtEpochMs ASC
        LIMIT 1
        """
    )
    suspend fun nextPending(): WeGlideUploadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WeGlideUploadQueueEntity)

    @Query("DELETE FROM weglide_upload_queue WHERE localFlightId = :localFlightId")
    suspend fun deleteByLocalFlightId(localFlightId: String)
}
