package com.example.xcpro

import android.content.Context
import android.net.Uri
import com.example.xcpro.common.documents.DocumentRef
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AirspaceClassesState(
    val classes: List<String>,
    val selectedClasses: Map<String, Boolean>
)

data class AirspaceGeoJsonState(
    val geoJson: String?,
    val error: Throwable? = null
)

class AirspaceRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext

    private data class ClassCacheEntry(
        val lastModified: Long,
        val classes: Set<String>
    )

    private data class GeoJsonCacheKey(
        val fileName: String,
        val lastModified: Long,
        val selectedKey: Int
    )

    private companion object {
        private val classCache = mutableMapOf<String, ClassCacheEntry>()
        private val geoJsonCache = mutableMapOf<GeoJsonCacheKey, String>()
        private val cacheLock = Any()
    }

    suspend fun loadAirspaceFiles(): Pair<List<DocumentRef>, MutableMap<String, Boolean>> =
        withContext(ioDispatcher) {
            val (files, checkedStates) = loadAirspaceFiles(appContext)
            val refs = files.map { uri ->
                DocumentRef(
                    uri = uri.toString(),
                    displayName = uri.lastPathSegment?.substringAfterLast("/") ?: "Unknown"
                )
            }
            Pair(refs, checkedStates)
        }

    suspend fun saveAirspaceFiles(files: List<DocumentRef>, checkedStates: Map<String, Boolean>) =
        withContext(ioDispatcher) {
            val uris = files.mapNotNull { ref -> ref.toUriOrNull() }
            saveAirspaceFiles(appContext, uris, checkedStates)
        }

    suspend fun loadSelectedClasses(): MutableMap<String, Boolean>? =
        withContext(ioDispatcher) { loadSelectedClasses(appContext) }

    suspend fun saveSelectedClasses(selectedClasses: Map<String, Boolean>) =
        withContext(ioDispatcher) { saveSelectedClasses(appContext, selectedClasses) }

    suspend fun parseClasses(files: List<DocumentRef>): List<String> = withContext(ioDispatcher) {
        parseClassesInternal(files)
    }

    suspend fun buildGeoJson(files: List<DocumentRef>, selectedClasses: Set<String>): String =
        withContext(ioDispatcher) {
            buildGeoJsonInternal(files, selectedClasses)
        }

    suspend fun countZones(document: DocumentRef): Int = withContext(ioDispatcher) {
        val fileName = document.fileName()
        val file = File(appContext.filesDir, fileName)
        if (!file.exists()) return@withContext 0
        runCatching {
            file.useLines { lines -> lines.count { it.trimStart().startsWith("AC ") } }
        }.getOrDefault(0)
    }

    suspend fun importAirspaceFile(
        document: DocumentRef,
        maxSizeMb: Int = 5
    ): AirspaceImportResult = withContext(ioDispatcher) {
        runCatching {
            val sourceUri = Uri.parse(document.uri)
            val fileName = copyFileToInternalStorage(appContext, sourceUri)
            val file = File(appContext.filesDir, fileName)
            val sizeInMb = file.length() / (1024 * 1024)
            if (sizeInMb > maxSizeMb) {
                file.delete()
                return@runCatching AirspaceImportResult.Failure(
                    "File too large (${sizeInMb}MB). Maximum size is ${maxSizeMb}MB."
                )
            }

            if (!fileName.endsWith(".txt", ignoreCase = true)) {
                file.delete()
                return@runCatching AirspaceImportResult.Failure(
                    "Only .txt files are supported for airspace files."
                )
            }

            val (isValid, message) = validateOpenAirFile(file.readText())
            if (!isValid) {
                file.delete()
                return@runCatching AirspaceImportResult.Failure("Invalid file format: $message")
            }

            AirspaceImportResult.Success(
                fileName,
                DocumentRef(uri = Uri.fromFile(file).toString(), displayName = fileName)
            )
        }.getOrElse { error ->
            AirspaceImportResult.Failure(error.message ?: "Unknown error while processing file.")
        }
    }

    suspend fun deleteAirspaceFile(fileName: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            File(appContext.filesDir, fileName).delete()
        }.getOrDefault(false)
    }

    private fun parseClassesInternal(files: List<DocumentRef>): List<String> {
        val classes = mutableSetOf<String>()
        files.forEach { ref ->
            val fileName = ref.fileName()
            val file = File(appContext.filesDir, fileName)
            if (!file.exists()) return@forEach
            val lastModified = file.lastModified()

            val cached = synchronized(cacheLock) {
                classCache[fileName]?.takeIf { it.lastModified == lastModified }
            }

            if (cached != null) {
                classes.addAll(cached.classes)
                return@forEach
            }

            val parsed = file.readLines()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("AC ")) trimmed.substring(3).trim() else null
                }
                .filter { it.isNotBlank() }
                .toSet()

            synchronized(cacheLock) {
                classCache[fileName] = ClassCacheEntry(lastModified, parsed)
            }
            classes.addAll(parsed)
        }
        return classes.toList().sorted()
    }

    private fun buildGeoJsonInternal(files: List<DocumentRef>, selectedClasses: Set<String>): String {
        val features = JSONArray()
        val selectedKey = selectedClassesKey(selectedClasses)

        files.forEach { ref ->
            val fileName = ref.fileName()
            val file = File(appContext.filesDir, fileName)
            if (!file.exists()) return@forEach
            val lastModified = file.lastModified()
            val cacheKey = GeoJsonCacheKey(fileName, lastModified, selectedKey)

            val geoJsonData = synchronized(cacheLock) {
                geoJsonCache[cacheKey]
            } ?: run {
                val parsed = parseOpenAirToGeoJson(file.readText(), selectedClasses)
                synchronized(cacheLock) {
                    geoJsonCache.keys.removeIf { key ->
                        key.fileName == fileName && key.lastModified != lastModified
                    }
                    geoJsonCache[cacheKey] = parsed
                }
                parsed
            }

            val geoJsonObject = JSONObject(geoJsonData)
            val fileFeatures = geoJsonObject.optJSONArray("features") ?: JSONArray()
            for (i in 0 until fileFeatures.length()) {
                features.put(fileFeatures.getJSONObject(i))
            }
        }

        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    private fun selectedClassesKey(selectedClasses: Set<String>): Int {
        if (selectedClasses.isEmpty()) return 0
        return selectedClasses.sorted().joinToString("|").hashCode()
    }

    private fun DocumentRef.toUriOrNull(): Uri? =
        runCatching { Uri.parse(uri) }.getOrNull()
}

sealed interface AirspaceImportResult {
    data class Success(val fileName: String, val document: DocumentRef) : AirspaceImportResult
    data class Failure(val reason: String) : AirspaceImportResult
}
