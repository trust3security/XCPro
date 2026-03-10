package com.example.xcpro.igc.domain

enum class IgcRecoveryErrorCode {
    STAGING_MISSING,
    STAGING_CORRUPT,
    PENDING_ROW_WRITE_FAILED,
    NAME_COLLISION_UNRESOLVED,
    DUPLICATE_SESSION_GUARD
}

sealed interface IgcRecoveryResult {
    data class Recovered(val entryName: String) : IgcRecoveryResult

    data class NoRecoveryWork(val reason: String) : IgcRecoveryResult

    data class Failure(
        val code: IgcRecoveryErrorCode,
        val message: String
    ) : IgcRecoveryResult
}

data class IgcRecoveryMetadata(
    val manufacturerId: String,
    val sessionSerial: String,
    val sessionStartWallTimeMs: Long,
    val firstValidFixWallTimeMs: Long?,
    val signatureProfile: IgcSecuritySignatureProfile = IgcSecuritySignatureProfile.NONE
)
