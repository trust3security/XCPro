package com.trust3.xcpro.igc.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.trust3.xcpro.common.documents.DocumentRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface IgcRecoveryDownloadsLookup {
    fun findFinalizedEntriesByPrefix(expectedPrefix: String, utcDate: LocalDate): List<IgcLogEntry>
}

@Singleton
class MediaStoreIgcRecoveryDownloadsLookup @Inject constructor(
    @ApplicationContext private val appContext: Context
) : IgcRecoveryDownloadsLookup {

    override fun findFinalizedEntriesByPrefix(
        expectedPrefix: String,
        utcDate: LocalDate
    ): List<IgcLogEntry> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStore(expectedPrefix = expectedPrefix, utcDate = utcDate)
        } else {
            queryLegacy(expectedPrefix = expectedPrefix, utcDate = utcDate)
        }
    }

    private fun queryMediaStore(expectedPrefix: String, utcDate: LocalDate): List<IgcLogEntry> {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED
        )
        val selection = buildString {
            append("${MediaStore.Downloads.DISPLAY_NAME} LIKE ?")
            append(" AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?")
            append(" AND ${MediaStore.Downloads.IS_PENDING} = 0")
        }
        val selectionArgs = arrayOf(
            "${expectedPrefix}%",
            "${IgcDownloadsStoragePaths.DOWNLOAD_RELATIVE_PATH}%"
        )
        val sortOrder = "${MediaStore.Downloads.DISPLAY_NAME} ASC"

        val entries = mutableListOf<IgcLogEntry>()
        resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameColumn) ?: continue
                if (!displayName.endsWith(".IGC", ignoreCase = true)) continue
                val itemUri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                entries += IgcLogEntry(
                    document = DocumentRef(uri = itemUri.toString(), displayName = displayName),
                    displayName = displayName,
                    sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L),
                    lastModifiedEpochMillis = cursor.getLong(modifiedColumn).coerceAtLeast(0L) * 1_000L,
                    utcDate = utcDate,
                    durationSeconds = null
                )
            }
        }
        return entries
    }

    private fun queryLegacy(expectedPrefix: String, utcDate: LocalDate): List<IgcLogEntry> {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            IgcDownloadsStoragePaths.LEGACY_SUBDIR
        )
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        return directory.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(expectedPrefix) &&
                    file.name.endsWith(".igc", ignoreCase = true)
            }
            ?.sortedBy { it.name }
            ?.map { file ->
                IgcLogEntry(
                    document = DocumentRef(
                        uri = Uri.fromFile(file).toString(),
                        displayName = file.name
                    ),
                    displayName = file.name,
                    sizeBytes = file.length(),
                    lastModifiedEpochMillis = file.lastModified(),
                    utcDate = utcDate,
                    durationSeconds = null
                )
            }
            ?.toList()
            ?: emptyList()
    }
}
