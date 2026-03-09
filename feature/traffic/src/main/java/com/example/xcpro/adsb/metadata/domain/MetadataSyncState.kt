package com.example.xcpro.adsb.metadata.domain

sealed interface MetadataSyncState {
    data object Idle : MetadataSyncState
    data object Scheduled : MetadataSyncState
    data object Running : MetadataSyncState
    data class PausedByUser(val lastSuccessWallMs: Long?) : MetadataSyncState
    data class Success(
        val lastSuccessWallMs: Long,
        val sourceKey: String,
        val etag: String?
    ) : MetadataSyncState
    data class Failed(
        val reason: String,
        val lastAttemptWallMs: Long,
        val retryAtWallMs: Long?
    ) : MetadataSyncState
}

sealed interface MetadataSyncRunResult {
    data object Succeeded : MetadataSyncRunResult
    data object Retry : MetadataSyncRunResult
    data object Skipped : MetadataSyncRunResult
}

