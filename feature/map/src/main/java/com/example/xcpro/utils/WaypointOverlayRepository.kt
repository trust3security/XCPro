package com.example.xcpro

import android.content.Context
import android.net.Uri
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.common.waypoint.WaypointData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WaypointOverlayRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext

    private data class GeoJsonCacheEntry(
        val lastModified: Long,
        val geoJson: String
    )

    private companion object {
        private val geoJsonCache = mutableMapOf<String, GeoJsonCacheEntry>()
        private val cacheLock = Any()
    }

    suspend fun buildGeoJson(files: List<DocumentRef>, checkedStates: Map<String, Boolean>): String =
        withContext(ioDispatcher) {
            val features = JSONArray()
            files.forEach { document ->
                val fileName = document.fileName()
                if (checkedStates[fileName] != true) return@forEach
                val file = File(appContext.filesDir, fileName)
                if (!file.exists()) return@forEach
                val lastModified = file.lastModified()

                val cached = synchronized(cacheLock) {
                    geoJsonCache[fileName]?.takeIf { it.lastModified == lastModified }
                }

                val geoJsonData = cached?.geoJson ?: run {
                    val parsed = buildGeoJsonFromWaypoints(
                        WaypointParser.parseWaypointFile(appContext, Uri.fromFile(file))
                    )
                    synchronized(cacheLock) {
                        geoJsonCache[fileName] = GeoJsonCacheEntry(lastModified, parsed)
                    }
                    parsed
                }

                val geoJsonObject = JSONObject(geoJsonData)
                val fileFeatures = geoJsonObject.optJSONArray("features") ?: JSONArray()
                for (i in 0 until fileFeatures.length()) {
                    features.put(fileFeatures.getJSONObject(i))
                }
            }
            JSONObject().apply {
                put("type", "FeatureCollection")
                put("features", features)
            }.toString()
        }

    private fun buildGeoJsonFromWaypoints(waypoints: List<WaypointData>): String {
        val features = JSONArray()
        waypoints.forEach { waypoint ->
            features.put(
                JSONObject().apply {
                    put("type", "Feature")
                    put(
                        "geometry",
                        JSONObject().apply {
                            put("type", "Point")
                            put(
                                "coordinates",
                                JSONArray().apply {
                                    put(waypoint.longitude)
                                    put(waypoint.latitude)
                                }
                            )
                        }
                    )
                    put(
                        "properties",
                        JSONObject().apply {
                            put("name", waypoint.name)
                            put("elevation", waypoint.elevation)
                        }
                    )
                }
            )
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }
}
