package com.trust3.xcpro.flightdata

import android.content.Context
import android.net.Uri
import com.trust3.xcpro.ConfigurationRepository
import com.trust3.xcpro.WaypointParser
import com.trust3.xcpro.common.documents.DocumentRef
import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.copyFileToInternalStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Singleton
class WaypointFilesRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val configurationRepository: ConfigurationRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext

    suspend fun loadWaypointFiles(): Pair<List<DocumentRef>, MutableMap<String, Boolean>> =
        configurationRepository.loadWaypointFiles()

    suspend fun saveWaypointFiles(files: List<DocumentRef>, checkedStates: Map<String, Boolean>) {
        configurationRepository.saveWaypointFiles(files, checkedStates)
    }

    suspend fun importWaypointFile(document: DocumentRef): WaypointImportResult = withContext(ioDispatcher) {
        runCatching {
            val sourceUri = Uri.parse(document.uri)
            val fileName = copyFileToInternalStorage(appContext, sourceUri)
            val file = File(appContext.filesDir, fileName)
            if (!fileName.endsWith(".cup", ignoreCase = true)) {
                file.delete()
                return@runCatching WaypointImportResult.Failure(
                    "Only .cup files are supported for waypoint files."
                )
            }
            WaypointImportResult.Success(
                fileName,
                DocumentRef(uri = Uri.fromFile(file).toString(), displayName = fileName)
            )
        }.getOrElse { error ->
            WaypointImportResult.Failure(error.message ?: "Unknown error while processing file.")
        }
    }

    suspend fun deleteWaypointFile(fileName: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            File(appContext.filesDir, fileName).delete()
        }.getOrDefault(false)
    }

    suspend fun getWaypointCount(document: DocumentRef): Int = withContext(ioDispatcher) {
        val uri = Uri.parse(document.uri)
        runCatching { WaypointParser.parseWaypointFile(appContext, uri).size }
            .getOrDefault(0)
    }

    suspend fun loadAllWaypoints(
        files: List<DocumentRef>,
        checkedStates: Map<String, Boolean>
    ): List<WaypointData> = withContext(ioDispatcher) {
        files.flatMap { document ->
            val fileName = document.fileName()
            if (checkedStates[fileName] == true) {
                val uri = Uri.parse(document.uri)
                runCatching { WaypointParser.parseWaypointFile(appContext, uri) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
    }
}

sealed interface WaypointImportResult {
    data class Success(val fileName: String, val document: DocumentRef) : WaypointImportResult
    data class Failure(val reason: String) : WaypointImportResult
}
