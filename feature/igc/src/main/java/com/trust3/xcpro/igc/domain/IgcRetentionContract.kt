package com.trust3.xcpro.igc.domain

import java.time.LocalDate

data class IgcRetentionPolicy(
    val maxFileCount: Int = 200,
    val maxFileAgeDays: Int = 365,
    val autoPruneOnFinalize: Boolean = true
)

enum class IgcRetentionErrorCode {
    RETENTION_QUERY_FAILED,
    RETENTION_DELETE_FAILED,
    RETENTION_PERMISSION_DENIED,
    RETENTION_INVALID_POLICY_RANGE
}

sealed interface IgcRetentionResult {
    data class Success(
        val deletedEntries: Int,
        val keptEntries: Int,
        val deletedEntriesNames: List<String>
    ) : IgcRetentionResult

    data class Failure(
        val code: IgcRetentionErrorCode,
        val message: String
    ) : IgcRetentionResult
}

sealed interface IgcRetentionCandidateOrder {
    data class Deleted(val entryName: String, val deletionReason: String) : IgcRetentionCandidateOrder
}

internal fun IgcRetentionPolicy.validateOrNull(): IgcRetentionErrorCode? = when {
    maxFileCount !in 1..999 -> IgcRetentionErrorCode.RETENTION_INVALID_POLICY_RANGE
    maxFileAgeDays !in 1..3650 -> IgcRetentionErrorCode.RETENTION_INVALID_POLICY_RANGE
    else -> null
}

internal fun IgcRetentionPolicy.ageCutoffEpochMs(nowWallMs: Long): Long =
    nowWallMs - maxFileAgeDays.toLong() * 24L * 60L * 60L * 1000L

internal fun IgcRetentionPolicy.shouldAutoPrune(): Boolean = autoPruneOnFinalize

internal fun IgcRetentionPolicy.clampCountOrNull(value: Int): Int = value.coerceIn(1, 999)

internal fun IgcRetentionPolicy.clampAgeOrNull(value: Int): Int = value.coerceIn(1, 3650)

internal fun IgcRetentionPolicy.normalize(): IgcRetentionPolicy =
    copy(maxFileCount = maxFileCount.coerceIn(1, 999), maxFileAgeDays = maxFileAgeDays.coerceIn(1, 3650))

internal fun IgcRetentionPolicy.withDefaultsForUi(): IgcRetentionPolicy =
    copy(maxFileCount = maxFileCount.coerceIn(1, 999), maxFileAgeDays = maxFileAgeDays.coerceIn(1, 3650))

internal data class IgcRetentionAuditEntry(val fileName: String, val deletionReason: LocalDate)
