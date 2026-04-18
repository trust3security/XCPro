package com.trust3.xcpro.igc.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.trust3.xcpro.common.documents.DocumentRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import javax.inject.Inject

class IgcFlightLogPublishTransport @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    fun publish(
        fileName: String,
        payload: ByteArray,
        utcDate: LocalDate
    ): IgcFinalizeResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishMediaStore(fileName = fileName, payload = payload, utcDate = utcDate)
        } else {
            publishLegacy(fileName = fileName, payload = payload, utcDate = utcDate)
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
            put(MediaStore.Downloads.RELATIVE_PATH, "${IgcDownloadsStoragePaths.DOWNLOAD_RELATIVE_PATH}/")
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
        val outputDir = File(downloadsRoot, IgcDownloadsStoragePaths.LEGACY_SUBDIR)
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

    private companion object {
        private const val MIME_IGC = "application/vnd.fai.igc"
    }
}
