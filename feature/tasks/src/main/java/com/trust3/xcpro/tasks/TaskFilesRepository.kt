package com.trust3.xcpro.tasks

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.trust3.xcpro.common.documents.DocumentRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TaskFilesRepository @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    suspend fun queryDownloads(): List<CupDownloadEntry> = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED
        )
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ? OR ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%.cup", "%.xcp.json")
        val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

        val entries = mutableListOf<CupDownloadEntry>()
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
                entries += CupDownloadEntry(
                    document = DocumentRef(uri = uri.toString(), displayName = name),
                    displayName = name,
                    sizeBytes = size,
                    lastModifiedEpochMillis = if (modifiedSeconds > 0) modifiedSeconds * 1000 else 0L
                )
            }
        }

        entries
    }

    suspend fun resolveDisplayName(document: DocumentRef): String? = withContext(Dispatchers.IO) {
        val cursor = appContext.contentResolver.query(
            Uri.parse(document.uri),
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    suspend fun readText(document: DocumentRef): String? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(document.uri)
        appContext.contentResolver
            .openInputStream(uri)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
    }

    fun resolveMimeType(document: DocumentRef): String? =
        appContext.contentResolver.getType(Uri.parse(document.uri))

    suspend fun saveToDownloads(fileName: String, content: String): DocumentRef? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            resolver.delete(
                collection,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName)
            )

            val pendingValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                val mime = if (fileName.endsWith(".json", ignoreCase = true)) {
                    "application/json"
                } else {
                    "application/octet-stream"
                }
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val itemUri = resolver.insert(collection, pendingValues) ?: return@withContext null
            val writeSucceeded = runCatching {
                resolver.openOutputStream(itemUri)?.use { output ->
                    output.write(content.toByteArray(StandardCharsets.UTF_8))
                } ?: error("Unable to open output stream for $fileName")

                val publishValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
            }.isSuccess

            if (!writeSucceeded) {
                runCatching { resolver.delete(itemUri, null, null) }
                return@withContext null
            }

            DocumentRef(uri = itemUri.toString(), displayName = fileName)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(content.toByteArray(StandardCharsets.UTF_8))
            }
            DocumentRef(uri = Uri.fromFile(file).toString(), displayName = fileName)
        }
    }

    suspend fun findDownloadFileRef(fileName: String): DocumentRef? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Downloads._ID)
            appContext.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(collection, id)
                    DocumentRef(uri = uri.toString(), displayName = fileName)
                } else {
                    null
                }
            }
        } else {
            val legacyFile = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            if (legacyFile.exists()) {
                DocumentRef(uri = Uri.fromFile(legacyFile).toString(), displayName = fileName)
            } else {
                null
            }
        }
    }

    suspend fun writeCacheFile(fileName: String, content: String): DocumentRef = withContext(Dispatchers.IO) {
        val file = File(appContext.cacheDir, fileName)
        FileOutputStream(file).use { output ->
            output.write(content.toByteArray(StandardCharsets.UTF_8))
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        DocumentRef(uri = uri.toString(), displayName = fileName)
    }

    fun appContext(): Context = appContext
}
