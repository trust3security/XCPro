package com.example.xcpro.igc.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.domain.IgcRecoveryErrorCode
import com.example.xcpro.igc.domain.IgcRecoveryMetadata
import com.example.xcpro.igc.domain.IgcRecoveryResult
import com.example.xcpro.igc.domain.IgcFileNamingPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
    @ApplicationContext private val appContext: Context,
    private val downloadsRepository: IgcDownloadsRepository,
    private val recoveryMetadataStore: IgcRecoveryMetadataStore,
    private val namingPolicy: IgcFileNamingPolicy
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
            val fileBytes = textWriter.toByteArray(request.lines)
            if (writeStagingFile(request.sessionId, fileBytes) == null) {
                return@synchronized IgcFinalizeResult.Failure(
                    code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                    message = "Failed to write IGC staging file"
                )
            }

            val publishResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                publishMediaStore(
                    fileName = naming.fileName,
                    payload = fileBytes,
                    utcDate = naming.utcDate
                )
            } else {
                publishLegacy(
                    fileName = naming.fileName,
                    payload = fileBytes,
                    utcDate = naming.utcDate
                )
            }
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
            when (val existing = findExistingFinalizedEntriesForSession(
                sessionId = sessionId,
                metadata = metadata
            )) {
                is ExistingFinalizedMatch.Duplicate -> {
                    cleanupPendingRowsForMetadata(metadata)
                    deleteRecoveryArtifacts(sessionId)
                    return@synchronized IgcRecoveryResult.Failure(
                        code = IgcRecoveryErrorCode.DUPLICATE_SESSION_GUARD,
                        message = "Multiple finalized IGC files matched session $sessionId"
                    )
                }
                is ExistingFinalizedMatch.Single -> {
                    cleanupPendingRowsForMetadata(metadata)
                    publishedBySessionId[sessionId] = existing.entry
                    deleteRecoveryArtifacts(sessionId)
                    return@synchronized IgcRecoveryResult.Recovered(existing.entry.displayName)
                }
                ExistingFinalizedMatch.None -> Unit
            }

            val staged = stagedFile(sessionId)
            if (!staged.exists()) {
                cleanupPendingRowsForMetadata(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_MISSING,
                    message = "Recovery staging file missing for session $sessionId"
                )
            }

            val lines = runCatching {
                staged.readLines()
            }.getOrNull() ?: run {
                cleanupPendingRowsForMetadata(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                    message = "Failed to read staging file for session $sessionId"
                )
            }

            if (lines.isEmpty()) {
                cleanupPendingRowsForMetadata(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                    message = "Recovery staging file is empty for session $sessionId"
                )
            }

            if (stagedMetadata == null) {
                cleanupPendingRowsForMetadata(metadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                    message = "Recovery staging metadata could not be parsed for session $sessionId"
                )
            }

            val authoritativeMetadata = metadata ?: stagedMetadata
            if (!authoritativeMetadata.hasUtcIdentity()) {
                cleanupPendingRowsForMetadata(authoritativeMetadata)
                deleteRecoveryArtifacts(sessionId)
                return@synchronized IgcRecoveryResult.Failure(
                    code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                    message = "Recovery metadata missing UTC date for session $sessionId"
                )
            }

            cleanupPendingRowsForMetadata(authoritativeMetadata)

            val request = IgcFinalizeRequest(
                sessionId = sessionId,
                sessionStartWallTimeMs = authoritativeMetadata.sessionStartWallTimeMs,
                firstValidFixWallTimeMs = authoritativeMetadata.firstValidFixWallTimeMs,
                manufacturerId = authoritativeMetadata.manufacturerId,
                sessionSerial = authoritativeMetadata.sessionSerial,
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
        val staged = stagedFile(sessionId)
        if (!staged.exists()) return null
        val lines = runCatching { staged.readLines() }.getOrNull() ?: return null
        if (lines.isEmpty()) return null
        val aLine = lines.firstOrNull { it.startsWith("A") } ?: return null
        if (aLine.length < 10) return null
        val manufacturerId = aLine.substring(1, 4)
        val sessionSerial = aLine.substring(4, 10)
        val startDate = lines.firstOrNull(::isSessionHeaderDateLine)
            ?.let(::parseSessionHeaderDate)
        val firstB = lines.firstOrNull { it.startsWith("B") }
        val firstFixMs = startDate?.let { date ->
            parseFirstBWallTime(firstB, date)
        }
        return IgcRecoveryMetadata(
            manufacturerId = manufacturerId,
            sessionSerial = sessionSerial,
            sessionStartWallTimeMs = startDate?.toEpochMillisAtUtcStartOfDay() ?: 0L,
            firstValidFixWallTimeMs = firstFixMs
        )
    }

    override fun deleteRecoveryArtifacts(sessionId: Long) {
        runCatching { stagedFile(sessionId).delete() }
        runCatching { recoveryMetadataStore.clearMetadata(sessionId) }
    }

    private fun publishMediaStore(
        fileName: String,
        payload: ByteArray,
        utcDate: LocalDate
    ): IgcFinalizeResult {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val pendingValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_IGC)
            put(MediaStore.Downloads.RELATIVE_PATH, "${MediaStoreIgcDownloadsRepository.DOWNLOAD_RELATIVE_PATH}/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(collection, pendingValues)
            ?: return IgcFinalizeResult.Failure(
                code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                message = "Failed to insert MediaStore row for $fileName"
            )
        val writeSucceeded = runCatching {
            resolver.openOutputStream(itemUri, "w").use { output ->
                requireNotNull(output) { "Unable to open output stream for $fileName" }
                output.write(payload)
                output.flush()
            }
            val updated = resolver.update(
                itemUri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            require(updated > 0) { "Failed to finalize pending row for $fileName" }
        }.isSuccess
        if (!writeSucceeded) {
            runCatching { resolver.delete(itemUri, null, null) }
            return IgcFinalizeResult.Failure(
                code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                message = "Failed to publish IGC file $fileName"
            )
        }

        val sizeBytes = payload.size.toLong()
        val modifiedEpochMillis = readDateModifiedEpochMillis(itemUri)
        return IgcFinalizeResult.Published(
            entry = IgcLogEntry(
                document = DocumentRef(uri = itemUri.toString(), displayName = fileName),
                displayName = fileName,
                sizeBytes = sizeBytes,
                lastModifiedEpochMillis = modifiedEpochMillis,
                utcDate = utcDate,
                durationSeconds = null
            ),
            fileName = fileName
        )
    }

    private fun publishLegacy(
        fileName: String,
        payload: ByteArray,
        utcDate: LocalDate
    ): IgcFinalizeResult {
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outputDir = File(downloadsRoot, "XCPro/IGC")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputFile = File(outputDir, fileName)
        val writeSucceeded = runCatching {
            FileOutputStream(outputFile).use { stream ->
                stream.write(payload)
                stream.flush()
            }
        }.isSuccess
        if (!writeSucceeded) {
            return IgcFinalizeResult.Failure(
                code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                message = "Failed to write legacy IGC file $fileName"
            )
        }
        return IgcFinalizeResult.Published(
            entry = IgcLogEntry(
                document = DocumentRef(uri = Uri.fromFile(outputFile).toString(), displayName = fileName),
                displayName = fileName,
                sizeBytes = outputFile.length(),
                lastModifiedEpochMillis = outputFile.lastModified(),
                utcDate = utcDate,
                durationSeconds = null
            ),
            fileName = fileName
        )
    }

    private fun writeStagingFile(sessionId: Long, payload: ByteArray): File? {
        val stagingDir = File(appContext.filesDir, STAGING_SUBDIR)
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            return null
        }
        val stagingFile = stagedFile(sessionId)
        return runCatching {
            FileOutputStream(stagingFile).use { stream ->
                stream.write(payload)
                stream.flush()
            }
            stagingFile
        }.getOrNull()
    }

    private fun utcDateForRequest(request: IgcFinalizeRequest): LocalDate {
        val wallTime = request.firstValidFixWallTimeMs ?: request.sessionStartWallTimeMs
        return java.time.Instant.ofEpochMilli(wallTime)
            .atOffset(java.time.ZoneOffset.UTC)
            .toLocalDate()
    }

    private fun readDateModifiedEpochMillis(itemUri: Uri): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0L
        val projection = arrayOf(MediaStore.Downloads.DATE_MODIFIED)
        return runCatching {
            appContext.contentResolver.query(itemUri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use 0L
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val seconds = cursor.getLong(modifiedColumn).coerceAtLeast(0L)
                seconds * 1_000L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun findExistingFinalizedEntriesForSession(
        sessionId: Long,
        metadata: IgcRecoveryMetadata?
    ): ExistingFinalizedMatch {
        publishedBySessionId[sessionId]?.let {
            return ExistingFinalizedMatch.Single(it)
        }
        val safeMetadata = metadata ?: return ExistingFinalizedMatch.None
        val utcDate = resolveRecoveryUtcDate(safeMetadata) ?: return ExistingFinalizedMatch.None
        val expectedPrefix = buildString {
            append(RECOVERY_DATE_FORMATTER.format(utcDate))
            append('-')
            append(normalizeManufacturer(safeMetadata.manufacturerId))
            append('-')
            append(normalizeSerial(safeMetadata.sessionSerial))
            append('-')
        }
        val matches = downloadsRepository.entries.value
            .asSequence()
            .filter { entry ->
                entry.displayName.startsWith(expectedPrefix) &&
                    entry.displayName.endsWith(".IGC", ignoreCase = true)
            }
            .sortedBy { it.displayName }
            .toList()
        return when (matches.size) {
            0 -> ExistingFinalizedMatch.None
            1 -> ExistingFinalizedMatch.Single(matches.first())
            else -> ExistingFinalizedMatch.Duplicate(matches)
        }
    }

    private fun resolveRecoveryUtcDate(metadata: IgcRecoveryMetadata): LocalDate? {
        val wallTime = metadata.firstValidFixWallTimeMs
            ?: metadata.sessionStartWallTimeMs
        if (wallTime <= 0L) return null
        return wallTime.toUtcDate()
    }

    private fun cleanupPendingRowsForMetadata(metadata: IgcRecoveryMetadata?) {
        val safeMetadata = metadata ?: return
        val utcDate = resolveRecoveryUtcDate(safeMetadata) ?: return
        deletePendingRowsForSession(
            manufacturerId = safeMetadata.manufacturerId,
            sessionSerial = safeMetadata.sessionSerial,
            utcDate = utcDate
        )
    }

    private fun mergeRecoveryMetadata(
        stored: IgcRecoveryMetadata?,
        staged: IgcRecoveryMetadata?
    ): IgcRecoveryMetadata? {
        if (stored == null && staged == null) return null
        return IgcRecoveryMetadata(
            manufacturerId = stored?.manufacturerId ?: staged?.manufacturerId.orEmpty(),
            sessionSerial = stored?.sessionSerial ?: staged?.sessionSerial.orEmpty(),
            sessionStartWallTimeMs = stored?.sessionStartWallTimeMs
                ?.takeIf { it > 0L }
                ?: staged?.sessionStartWallTimeMs
                ?: 0L,
            firstValidFixWallTimeMs = stored?.firstValidFixWallTimeMs
                ?: staged?.firstValidFixWallTimeMs
        )
    }

    private fun deletePendingRowsForSession(
        manufacturerId: String,
        sessionSerial: String,
        utcDate: LocalDate
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = buildString {
            append("${MediaStore.Downloads.DISPLAY_NAME} LIKE ?")
            append(" AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?")
            append(" AND ${MediaStore.Downloads.IS_PENDING} = 1")
        }
        val selectionArgs = arrayOf(
            buildString {
                append(RECOVERY_DATE_FORMATTER.format(utcDate))
                append('-')
                append(normalizeManufacturer(manufacturerId))
                append('-')
                append(normalizeSerial(sessionSerial))
                append("-%")
            },
            "${MediaStoreIgcDownloadsRepository.DOWNLOAD_RELATIVE_PATH}%"
        )
        runCatching {
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) {
                    val rowUri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    resolver.delete(rowUri, null, null)
                }
            }
        }
    }

    private fun stagedFile(sessionId: Long): File =
        File(File(appContext.filesDir, STAGING_SUBDIR), "session_${sessionId}.igc.tmp")

    private fun parseSessionHeaderDate(line: String): LocalDate? {
        val raw = when {
            line.startsWith("HFDTEDATE:") -> line.removePrefix("HFDTEDATE:")
            line.startsWith("HFDTE") -> line.removePrefix("HFDTE")
            else -> return null
        }
        val digits = raw.takeWhile { it.isDigit() }.take(6)
        if (digits.length != 6) return null
        val day = digits.substring(0, 2).toIntOrNull() ?: return null
        val month = digits.substring(2, 4).toIntOrNull() ?: return null
        val year = 2000 + (digits.substring(4, 6).toIntOrNull() ?: return null)
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun parseFirstBWallTime(line: String?, date: LocalDate): Long? {
        if (line == null || line.length < 7) return null
        val hour = line.substring(1, 3).toIntOrNull() ?: return null
        val minute = line.substring(3, 5).toIntOrNull() ?: return null
        val second = line.substring(5, 7).toIntOrNull() ?: return null
        return runCatching {
            date.atTime(LocalTime.of(hour, minute, second))
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrNull()
    }

    private fun isSessionHeaderDateLine(line: String): Boolean {
        return line.startsWith("HFDTEDATE:") || line.startsWith("HFDTE")
    }

    private fun normalizeManufacturer(raw: String): String {
        val normalized = raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        return when {
            normalized.length >= 3 -> normalized.take(3)
            normalized.isBlank() -> "XCP"
            else -> normalized.padEnd(3, 'X')
        }
    }

    private fun normalizeSerial(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length >= 6 -> digits.takeLast(6)
            digits.isBlank() -> "000000"
            else -> digits.padStart(6, '0')
        }
    }

    private fun LocalDate.toEpochMillisAtUtcStartOfDay(): Long =
        atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun Long.toUtcDate(): LocalDate =
        java.time.Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()

    private fun IgcRecoveryMetadata.hasUtcIdentity(): Boolean {
        return (firstValidFixWallTimeMs ?: sessionStartWallTimeMs) > 0L
    }

    private fun IgcFinalizeResult.Failure.toRecoveryFailure(): IgcRecoveryResult.Failure {
        val recoveryCode = when (code) {
            IgcFinalizeResult.ErrorCode.WRITE_FAILED -> IgcRecoveryErrorCode.PENDING_ROW_WRITE_FAILED
            IgcFinalizeResult.ErrorCode.NAME_SPACE_EXHAUSTED -> IgcRecoveryErrorCode.NAME_COLLISION_UNRESOLVED
            IgcFinalizeResult.ErrorCode.EMPTY_PAYLOAD -> IgcRecoveryErrorCode.STAGING_CORRUPT
        }
        return IgcRecoveryResult.Failure(code = recoveryCode, message = message)
    }

    companion object {
        private const val MIME_IGC = "application/vnd.fai.igc"
        private const val STAGING_SUBDIR = "igc/staging"
        private val RECOVERY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    private sealed interface ExistingFinalizedMatch {
        data object None : ExistingFinalizedMatch
        data class Single(val entry: IgcLogEntry) : ExistingFinalizedMatch
        data class Duplicate(val entries: List<IgcLogEntry>) : ExistingFinalizedMatch
    }
}
