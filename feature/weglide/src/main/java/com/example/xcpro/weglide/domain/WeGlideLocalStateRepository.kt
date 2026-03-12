package com.example.xcpro.weglide.domain

import kotlinx.coroutines.flow.Flow

interface WeGlideLocalStateRepository : WeGlideAircraftMappingReadRepository {
    fun observeAircraft(): Flow<List<WeGlideAircraft>>

    fun observeMappings(): Flow<List<WeGlideAircraftMapping>>

    fun observeQueue(): Flow<List<WeGlideUploadQueueRecord>>

    suspend fun replaceAircraft(aircraft: List<WeGlideAircraft>, updatedAtEpochMs: Long)

    suspend fun saveMapping(
        profileId: String,
        aircraft: WeGlideAircraft,
        updatedAtEpochMs: Long
    )

    suspend fun clearMapping(profileId: String)
}
