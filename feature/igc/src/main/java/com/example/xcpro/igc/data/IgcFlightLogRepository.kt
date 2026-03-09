package com.example.xcpro.igc.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.igc.domain.IgcFileNamingPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface IgcFlightLogRepository {
    fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult
}

object NoopIgcFlightLogRepository : IgcFlightLogRepository {
    override fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult {
        return IgcFinalizeResult.Failure(
            code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
            message = "IGC finalization repository not configured"
        )
    }
}

@Singleton
class MediaStoreIgcFlightLogRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val downloadsRepository: IgcDownloadsRepository,
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
            val stagingFile = writeStagingFile(request.sessionId, fileBytes)
                ?: return@synchronized IgcFinalizeResult.Failure(
                    code = IgcFinalizeResult.ErrorCode.WRITE_FAILED,
                    message = "Failed to write IGC staging file"
                )

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
            runCatching { stagingFile.delete() }
            if (publishResult !is IgcFinalizeResult.Published) {
                return@synchronized publishResult
            }

            publishedBySessionId[request.sessionId] = publishResult.entry
            downloadsRepository.refreshEntries()
            publishResult
        }
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
        val stagingFile = File(stagingDir, "session_${sessionId}.igc.tmp")
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

    companion object {
        private const val MIME_IGC = "application/vnd.fai.igc"
        private const val STAGING_SUBDIR = "igc/staging"
    }
}
