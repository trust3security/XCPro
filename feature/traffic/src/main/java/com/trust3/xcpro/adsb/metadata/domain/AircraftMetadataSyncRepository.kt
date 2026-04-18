package com.trust3.xcpro.adsb.metadata.domain

import kotlinx.coroutines.flow.StateFlow

interface AircraftMetadataSyncRepository {
    val syncState: StateFlow<MetadataSyncState>

    suspend fun onScheduled()
    suspend fun onPausedByUser()
    suspend fun runSyncNow(): MetadataSyncRunResult
}

