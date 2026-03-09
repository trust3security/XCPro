package com.example.xcpro.igc.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.xcpro.common.documents.DocumentRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface IgcDownloadsRepository {
    val entries: StateFlow<List<IgcLogEntry>>

    fun refreshEntries()

    fun listExistingNamesForUtcDate(utcDate: LocalDate): Set<String>

    fun copyToDestination(source: DocumentRef, destinationUri: String): Result<Unit>
}

@Singleton
class MediaStoreIgcDownloadsRepository @Inject constructor(
    @ApplicationContext private val appContext: Context
) : IgcDownloadsRepository {

    private val _entries = MutableStateFlow<List<IgcLogEntry>>(emptyList())
    override val entries: StateFlow<List<IgcLogEntry>> = _entries.asStateFlow()

    override fun refreshEntries() {
        _entries.value = queryEntries()
    }

    override fun listExistingNamesForUtcDate(utcDate: LocalDate): Set<String> {
        val prefix = "$utcDate-"
        return queryEntries()
            .asSequence()
            .map { it.displayName }
            .filter { it.startsWith(prefix) && it.endsWith(".IGC", ignoreCase = true) }
            .toSet()
    }

    override fun copyToDestination(source: DocumentRef, destinationUri: String): Result<Unit> {
        val sourceUri = Uri.parse(source.uri)
        val destUri = Uri.parse(destinationUri)
        return runCatching {
            appContext.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "Unable to open source stream" }
                appContext.contentResolver.openOutputStream(destUri, "w").use { output ->
                    requireNotNull(output) { "Unable to open destination stream" }
                    input.copyTo(output)
                }
            }
        }.map { Unit }
    }

    private fun queryEntries(): List<IgcLogEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return queryEntriesLegacy()
        }
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED
        )
        val selection = "(" +
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? OR ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?" +
            ") AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%.IGC", "%.igc", "$DOWNLOAD_RELATIVE_PATH%")
        val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

        val entries = mutableListOf<IgcLogEntry>()
        resolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val id = cursor.getLong(idColumn)
                val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                val modifiedSeconds = cursor.getLong(modifiedColumn).coerceAtLeast(0L)
                val uri = ContentUris.withAppendedId(collection, id)
                entries += buildEntry(
                    document = DocumentRef(uri = uri.toString(), displayName = name),
                    sizeBytes = size,
                    lastModifiedEpochMillis = if (modifiedSeconds > 0L) modifiedSeconds * 1_000L else 0L
                )
            }
        }
        return entries
    }

    private fun queryEntriesLegacy(): List<IgcLogEntry> {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            LEGACY_SUBDIR
        )
        if (!directory.exists() || !directory.isDirectory) return emptyList()
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".igc", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                buildEntry(
                    document = DocumentRef(uri = Uri.fromFile(file).toString(), displayName = file.name),
                    sizeBytes = file.length(),
                    lastModifiedEpochMillis = file.lastModified()
                )
            }
            ?.toList()
            ?: emptyList()
    }

    private fun buildEntry(
        document: DocumentRef,
        sizeBytes: Long,
        lastModifiedEpochMillis: Long
    ): IgcLogEntry {
        val durationSeconds = parseDurationSeconds(document)
        return IgcLogEntry(
            document = document,
            displayName = document.fileName(),
            sizeBytes = sizeBytes,
            lastModifiedEpochMillis = lastModifiedEpochMillis,
            utcDate = parseUtcDate(document.fileName()),
            durationSeconds = durationSeconds
        )
    }

    private fun parseDurationSeconds(document: DocumentRef): Long? {
        val input = openInputStream(document) ?: return null
        input.bufferedReader().useLines { lines ->
            var firstSeconds: Long? = null
            var lastAbsoluteSeconds: Long? = null
            var dayOffsetSeconds = 0L
            var previousSeconds: Long? = null
            lines.forEach { line ->
                if (!line.startsWith("B") || line.length < 7) return@forEach
                val hh = line.substring(1, 3).toIntOrNull() ?: return@forEach
                val mm = line.substring(3, 5).toIntOrNull() ?: return@forEach
                val ss = line.substring(5, 7).toIntOrNull() ?: return@forEach
                val current = (hh * 3600L) + (mm * 60L) + ss
                val previous = previousSeconds
                if (previous != null && current < previous) {
                    dayOffsetSeconds += 86_400L
                }
                val absolute = current + dayOffsetSeconds
                if (firstSeconds == null) firstSeconds = absolute
                lastAbsoluteSeconds = absolute
                previousSeconds = current
            }
            val first = firstSeconds ?: return null
            val last = lastAbsoluteSeconds ?: return null
            return (last - first).coerceAtLeast(0L)
        }
    }

    private fun openInputStream(document: DocumentRef): InputStream? {
        return runCatching {
            appContext.contentResolver.openInputStream(Uri.parse(document.uri))
        }.getOrNull()
    }

    private fun parseUtcDate(displayName: String): LocalDate? {
        if (displayName.length < 10) return null
        return runCatching {
            LocalDate.parse(displayName.substring(0, 10))
        }.getOrNull()
    }

    companion object {
        const val DOWNLOAD_RELATIVE_PATH = "Download/XCPro/IGC"
        private const val LEGACY_SUBDIR = "XCPro/IGC"
    }
}
