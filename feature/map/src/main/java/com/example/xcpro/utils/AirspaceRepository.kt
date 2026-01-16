package com.example.xcpro

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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

    suspend fun loadAirspaceFiles(): Pair<List<Uri>, MutableMap<String, Boolean>> =
        withContext(ioDispatcher) { loadAirspaceFiles(appContext) }

    suspend fun saveAirspaceFiles(files: List<Uri>, checkedStates: Map<String, Boolean>) =
        withContext(ioDispatcher) { saveAirspaceFiles(appContext, files, checkedStates) }

    suspend fun loadSelectedClasses(): MutableMap<String, Boolean>? =
        withContext(ioDispatcher) { loadSelectedClasses(appContext) }

    suspend fun saveSelectedClasses(selectedClasses: Map<String, Boolean>) =
        withContext(ioDispatcher) { saveSelectedClasses(appContext, selectedClasses) }

    suspend fun parseClasses(files: List<Uri>): List<String> = withContext(ioDispatcher) {
        parseClassesInternal(files)
    }

    suspend fun buildGeoJson(files: List<Uri>, selectedClasses: Set<String>): String =
        withContext(ioDispatcher) {
            buildGeoJsonInternal(files, selectedClasses)
        }

    private fun parseClassesInternal(files: List<Uri>): List<String> {
        val classes = mutableSetOf<String>()
        files.forEach { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@forEach
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

    private fun buildGeoJsonInternal(files: List<Uri>, selectedClasses: Set<String>): String {
        val features = JSONArray()
        val selectedKey = selectedClassesKey(selectedClasses)

        files.forEach { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@forEach
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
}
