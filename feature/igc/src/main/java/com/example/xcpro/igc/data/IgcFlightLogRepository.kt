package com.example.xcpro.igc.data

import com.example.xcpro.igc.domain.IgcRecoveryErrorCode
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcRecoveryResult
import com.example.xcpro.igc.domain.IgcFileNamingPolicy
import com.example.xcpro.igc.domain.IgcGRecordSigner
import com.example.xcpro.igc.domain.IgcSecuritySignatureProfile
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface IgcFlightLogRepository {
    fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult
    fun recoverSession(sessionId: Long): IgcRecoveryResult {
        return IgcRecoveryResult.NoRecoveryWork("Recovery not supported by repository")
    }

    fun parseStagedRecoveryMetadata(sessionId: Long): IgcRecoveryMetadata? = null

    fun deleteRecoveryArtifacts(sessionId: Long) = Unit
}

object NoopIgcFlightLogRepository : IgcFlightLogRepository {
    override fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult {
        return IgcFinalizeResult.Failure(
            code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
            message = "IGC finalization repository not configured"
        )
    }

    override fun recoverSession(sessionId: Long): IgcRecoveryResult =
        IgcRecoveryResult.NoRecoveryWork("No-op repository")
}

@Singleton
class MediaStoreIgcFlightLogRepository @Inject constructor(
    private val downloadsRepository: IgcDownloadsRepository,
    private val recoveryMetadataStore: IgcRecoveryMetadataStore,
    private val namingPolicy: IgcFileNamingPolicy,
    private val exportValidationAdapter: IgcExportValidationAdapter,
    private val gRecordSigner: IgcGRecordSigner,
    private val stagingStore: IgcRecoveryStagingStore,
    private val publishTransport: IgcFlightLogPublishTransport,
    private val recoveryFinalizedEntryResolver: IgcRecoveryFinalizedEntryResolver
) : IgcFlightLogRepository {

    private val lock = Any()
    private val publishedBySessionId = mutableMapOf<Long, IgcLogEntry>()
    private val textWriter = IgcTextWriter()

    override fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult {
        return synchronized(lock) {
            publishedBySessionId[request.sessionId]?.let { existing ->
                return@synchronized IgcFinalizeResult.AlreadyPublished(existing)
            }

            if (request.lines.isEmpty()) {
                return@synchronized IgcFinalizeResult.Failure(
                    code = IgcFinalizeResult.ErrorCode.EMPTY_PAYLOAD,
                    message = "Cannot finalize empty IGC payload"
                )
            }
            val naming = namingPolicy.resolve(
                IgcFileNamingPolicy.Request(
                    firstValidFixWallTimeMs = request.firstValidFixWallTimeMs,
                    sessionStartWallTimeMs = request.sessionStartWallTimeMs,
                    manufacturerId = request.manufacturerId,
                    sessionSerial = request.sessionSerial,
                    existingFileNames = downloadsRepository.listExistingNamesForUtcDate(
                        utcDateForRequest(request)
                    )
                )
            )
            if (naming is IgcFileNamingPolicy.Result.Failure) {
                val code = when (naming.code) {
                    IgcFileNamingPolicy.FailureCode.NAME_SPACE_EXHAUSTED ->
                        IgcFinalizeResult.ErrorCode.NAME_SPACE_EXHAUSTED
                }
                return@synchronized IgcFinalizeResult.Failure(code = code, message = naming.message)
            }
            naming as IgcFileNamingPolicy.Result.Success
            val payloadLines = gRecordSigner.sign(
                lines = request.lines,
                profile = request.signatureProfile
            )
            val fileBytes = textWriter.toByteArray(payloadLines)
            when (val validation = exportValidationAdapter.validate(fileBytes)) {
                is IgcExportValidationResult.Invalid -> {
                    return@synchronized IgcFinalizeResult.Failure(
                        code = IgcFinalizeResult.ErrorCode.LINT_VALIDATION_FAILED,
                        message = validation.message,
                        lintIssues = validation.issues
                    )
                }
                IgcExportValidationResult.Valid -> Unit
            }
            if (!stagingStore.write(request.sessionId, fileBytes)) {
                return@synchronized IgcFinalizeResult.Failure(
                    code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                    message = "Failed to write IGC staging file"
                )
            }

            val publishResult = publishTransport.publish(
                fileName = naming.fileName,
                payload = fileBytes,
                utcDate = naming.utcDate
            )
            if (publishResult !is IgcFinalizeResult.Published) {
                return@synchronized publishResult
            }

            publishedBySessionId[request.sessionId] = publishResult.entry
            downloadsRepository.refreshEntries()
            publishResult
        }
    }

    override fun recoverSession(sessionId: Long): IgcRecoveryResult {
        return synchronized(lock) {
            downloadsRepository.refreshEntries()
            val storedMetadata = recoveryMetadataStore.loadMetadata(sessionId)
            val stagedMetadata = parseStagedRecoveryMetadata(sessionId)
            val metadata = mergeRecoveryMetadata(
                stored = storedMetadata,
                staged = stagedMetadata
            )

            publishedBySessionId[sessionId]?.let { existing ->
                recoveryFinalizedEntryResolver.cleanupPendingRows(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Recovered(existing.displayName)
            }

            if (metadata?.hasUtcIdentity() == true) {
                when (val existing = recoveryFinalizedEntryResolver.findExistingFinalizedMatch(metadata)) {
                    is IgcRecoveryFinalizedEntryMatch.Duplicate -> {
                        recoveryFinalizedEntryResolver.cleanupPendingRows(metadata)
                        deleteRecoveryArtifacts(sessionId)
                        return@synchronized IgcRecoveryResult.Failure(
                            code = IgcRecoveryErrorCode.DUPLICATE_SESSION_GUARD,
                            message = "Multiple finalized IGC files matched session $sessionId"
                        )
                    }
                    is IgcRecoveryFinalizedEntryMatch.Single -> {
                        recoveryFinalizedEntryResolver.cleanupPendingRows(metadata)
                        publishedBySessionId[sessionId] = existing.entry
                        deleteRecoveryArtifacts(sessionId)
                        return@synchronized IgcRecoveryResult.Recovered(existing.entry.displayName)
                    }
                    IgcRecoveryFinalizedEntryMatch.None -> Unit
                }
            }

            if (!stagingStore.exists(sessionId)) {
                recoveryFinalizedEntryResolver.cleanupPendingRows(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_MISSING,
                    message = "Recovery staging file missing for session $sessionId"
                )
            }

            val lines = stagingStore.readLines(sessionId) ?: run {
                recoveryFinalizedEntryResolver.cleanupPendingRows(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                    message = "Failed to read staging file for session $sessionId"
                )
            }

            if (lines.isEmpty()) {
                recoveryFinalizedEntryResolver.cleanupPendingRows(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                    message = "Recovery staging file is empty for session $sessionId"
                )
            }

            if (stagedMetadata == null) {
                recoveryFinalizedEntryResolver.cleanupPendingRows(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                    message = "Recovery staging metadata could not be parsed for session $sessionId"
                )
            }

            val authoritativeMetadata = metadata ?: stagedMetadata
            if (!authoritativeMetadata.hasUtcIdentity()) {
                recoveryFinalizedEntryResolver.cleanupPendingRows(authoritativeMetadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                    message = "Recovery metadata missing UTC date for session $sessionId"
                )
            }

            recoveryFinalizedEntryResolver.cleanupPendingRows(authoritativeMetadata)

            val request = IgcFinalizeRequest(
                sessionId = sessionId,
                sessionStartWallTimeMs = authoritativeMetadata.sessionStartWallTimeMs,
                firstValidFixWallTimeMs = authoritativeMetadata.firstValidFixWallTimeMs,
                manufacturerId = authoritativeMetadata.manufacturerId,
                sessionSerial = authoritativeMetadata.sessionSerial,
                signatureProfile = authoritativeMetadata.signatureProfile,
                lines = lines
            )
            return@synchronized when (val publishResult = finalizeSession(request)) {
                is IgcFinalizeResult.Published -> {
                    deleteRecoveryArtifacts(sessionId)
                    IgcRecoveryResult.Recovered(publishResult.fileName)
                }
                is IgcFinalizeResult.AlreadyPublished -> {
                    deleteRecoveryArtifacts(sessionId)
                    IgcRecoveryResult.Recovered(publishResult.entry.displayName)
                }
                is IgcFinalizeResult.Failure -> {
                    deleteRecoveryArtifacts(sessionId)
                    publishResult.toRecoveryFailure()
                }
            }
        }
    }

    override fun parseStagedRecoveryMetadata(sessionId: Long): IgcRecoveryMetadata? {
        val lines = stagingStore.readLines(sessionId) ?: return null
        return IgcStagedRecoveryMetadataParser.parse(lines)
    }

    override fun deleteRecoveryArtifacts(sessionId: Long) {
        stagingStore.delete(sessionId)
        runCatching { recoveryMetadataStore.clearMetadata(sessionId) }
    }

    private fun utcDateForRequest(request: IgcFinalizeRequest): LocalDate {
        val wallTime = request.firstValidFixWallTimeMs ?: request.sessionStartWallTimeMs
        return java.time.Instant.ofEpochMilli(wallTime)
            .atOffset(java.time.ZoneOffset.UTC)
            .toLocalDate()
    }

    private fun signatureProfileForManufacturer(manufacturerId: String): IgcSecuritySignatureProfile {
        return if (manufacturerId.equals("XCS", ignoreCase = true)) {
            IgcSecuritySignatureProfile.XCS
        } else {
            IgcSecuritySignatureProfile.NONE
        }
    }

    private fun mergeRecoveryMetadata(
        stored: IgcRecoveryMetadata?,
        staged: IgcRecoveryMetadata?
    ): IgcRecoveryMetadata? {
        if (stored == null && staged == null) return null
        val manufacturerId = stored?.manufacturerId ?: staged?.manufacturerId.orEmpty()
        return IgcRecoveryMetadata(
            manufacturerId = manufacturerId,
            sessionSerial = stored?.sessionSerial ?: staged?.sessionSerial.orEmpty(),
            sessionStartWallTimeMs = stored?.sessionStartWallTimeMs
                ?.takeIf { it > 0L }
                ?: staged?.sessionStartWallTimeMs
                ?: 0L,
            firstValidFixWallTimeMs = stored?.firstValidFixWallTimeMs
                ?: staged?.firstValidFixWallTimeMs,
            signatureProfile = stored?.signatureProfile
                ?: staged?.signatureProfile
                ?: signatureProfileForManufacturer(manufacturerId)
        )
    }

    private fun IgcRecoveryMetadata.hasUtcIdentity(): Boolean {
        return (firstValidFixWallTimeMs ?: sessionStartWallTimeMs) > 0L
    }

    private fun IgcFinalizeResult.Failure.toRecoveryFailure(): IgcRecoveryResult.Failure {
        val recoveryCode = when (code) {
            IgcFinalizeResult.ErrorCode.WRITE_FAILED -> IgcRecoveryErrorCode.PENDING_ROW_WRITE_FAILED
            IgcFinalizeResult.ErrorCode.NAME_SPACE_EXHAUSTED -> IgcRecoveryErrorCode.NAME_COLLISION_UNRESOLVED
            IgcFinalizeResult.ErrorCode.EMPTY_PAYLOAD -> IgcRecoveryErrorCode.STAGING_CORRUPT
            IgcFinalizeResult.ErrorCode.LINT_VALIDATION_FAILED -> IgcRecoveryErrorCode.STAGING_CORRUPT
        }
        return IgcRecoveryResult.Failure(code = recoveryCode, message = message)
    }

}
