package com.example.xcpro.skysight

import android.util.Log
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.PropertyFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import org.maplibre.android.style.sources.TileSet
import java.text.SimpleDateFormat
import java.util.*

/**
 * SkySight Layer Operations
 *
 * Single responsibility: SkySight map layer management and operations
 * Extracted from MapScreen.kt to follow 500-line design principle
 */

private const val TAG = "SkysightLayers"

// ✅ SkySight Layer Management Functions - RasterSource Approach with Authentication
suspend fun addSkysightLayerToMap(
    mapLibreMap: MapLibreMap,
    layerId: String,
    layerType: String = "satellite",
    regionId: String,
    apiKey: String,
    skysightClient: SkysightClient
) {
    try {
        Log.d(TAG, "🚀🚀🚀 STARTING SKYSIGHT LAYER ADDITION 🚀🚀🚀")
        Log.d(TAG, "📝 Parameters:")
        Log.d(TAG, "   - layerId: $layerId")
        Log.d(TAG, "   - layerType: $layerType")
        Log.d(TAG, "   - regionId: $regionId")
        Log.d(TAG, "   - apiKey: ${apiKey.take(8)}...")
        Log.d(TAG, "   - mapLibreMap: $mapLibreMap")
        Log.d(TAG, "   - skysightClient: $skysightClient")

        // Check if map is valid
        Log.d(TAG, "🔍 Step 1: Checking map validity...")
        Log.d(TAG, "   - Map object exists: ${mapLibreMap != null}")

        // Check if map style is loaded
        Log.d(TAG, "🔍 Step 2: Checking map style...")
        val mapStyle = mapLibreMap.style
        Log.d(TAG, "   - Map style object: $mapStyle")
        Log.d(TAG, "   - Map style is null: ${mapStyle == null}")

        if (mapStyle == null) {
            Log.e(TAG, "❌ FATAL: Map style is null, cannot add layer")
            return
        }

        Log.d(TAG, "✅ Map style is valid, proceeding...")

        // Log current sources and layers on the map
        try {
            Log.d(TAG, "🔍 Step 3: Current map sources and layers:")
            val sources = mapStyle.sources
            Log.d(TAG, "   - Current sources count: ${sources.size}")
            sources.forEach { source ->
                Log.d(TAG, "     Source: ${source.id} (${source.javaClass.simpleName})")
            }

            val layers = mapStyle.layers
            Log.d(TAG, "   - Current layers count: ${layers.size}")
            layers.forEach { layer ->
                Log.d(TAG, "     Layer: ${layer.id} (${layer.javaClass.simpleName})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not enumerate sources/layers: ${e.message}")
        }

        // Remove existing layer if it exists
        Log.d(TAG, "🔍 Step 4: Removing existing layer...")
        removeSkysightLayerFromMap(mapLibreMap, layerId)

        // Get available forecast times for this region
        Log.d(TAG, "🔍 Step 5: Getting available forecast times...")
        val availableTimes = try {
            getAvailableForecastTimes(skysightClient, regionId, apiKey)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error getting forecast times: ${e.message}")
            emptyList()
        }

        val bestTime = if (availableTimes.isNotEmpty()) {
            availableTimes.first()
        } else {
            // Use our known working time as fallback
            "2025/08/29/1200"
        }
        Log.d(TAG, "📅 Available times: ${availableTimes.take(3)}")
        Log.d(TAG, "⏰ Selected forecast time: $bestTime")

        // Create authenticated HTTP client for this layer
        Log.d(TAG, "🔍 Step 6: Creating authenticated HTTP client...")
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor(SkysightHeaderInterceptor(apiKey))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()

        Log.d(TAG, "✅ HTTP client created with SkysightHeaderInterceptor")

        // Create RasterSource with authenticated HTTP client
        val sourceId = "skysight-source-$layerId"

        // Create SkySight authenticated tile URL
        val skysightUrlTemplate = "https://skysight.io/api/$layerType/{z}/{x}/{y}/$bestTime"
        Log.d(TAG, "🌐 Using SkySight URL template: $skysightUrlTemplate")

        // ALTERNATIVE APPROACH: Since MapLibre RasterSource can't send custom headers,
        // let's try using programmatic tile downloading and ImageSource instead
        Log.d(TAG, "🔍 Step 7: Creating RasterSource with FALLBACK to standard approach...")
        Log.w(TAG, "⚠️ NOTE: MapLibre RasterSource cannot authenticate with SkySight API")
        Log.w(TAG, "⚠️ Tiles will likely fail with 401, but layer structure will be created")

        // Create standard RasterSource (authentication will fail, but we'll see the layer structure)
        val rasterSource = RasterSource(sourceId, skysightUrlTemplate, 512)
        Log.d(TAG, "✅ RasterSource created (authentication expected to fail): $rasterSource")

        // TODO: Future enhancement - implement local proxy server or ImageSource approach

        // Add source to map
        Log.d(TAG, "🔍 Step 8: Adding RasterSource to map...")
        try {
            mapLibreMap.style?.addSource(rasterSource)
            Log.d(TAG, "✅ RasterSource added successfully: $sourceId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to add RasterSource: ${e.message}", e)
            throw e
        }

        // Verify source was added
        val addedSource = mapLibreMap.style?.getSource(sourceId)
        Log.d(TAG, "🔍 Verification: Source '$sourceId' exists after adding: ${addedSource != null}")

        // Create raster layer
        Log.d(TAG, "🔍 Step 9: Creating RasterLayer...")
        val rasterLayerId = "skysight-layer-$layerId"
        val opacity = when (layerType) {
            "satellite" -> 1.0f  // Full opacity for satellite
            "rain" -> 0.7f       // Semi-transparent for rain
            else -> 0.8f
        }

        Log.d(TAG, "🎨 RasterLayer properties: layerId=$rasterLayerId, sourceId=$sourceId, opacity=$opacity")

        val rasterLayer = RasterLayer(rasterLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.rasterOpacity(opacity)
            )
        }

        Log.d(TAG, "✅ RasterLayer created: $rasterLayer")

        // Add layer to map below labels
        Log.d(TAG, "🔍 Step 10: Adding RasterLayer to map...")
        try {
            val labelLayers = listOf("place-label", "road-label", "poi-label", "waterway-label")
            var addedBelow = false

            Log.d(TAG, "🔍 Searching for label layers to position below...")
            for (labelLayer in labelLayers) {
                val existingLayer = mapLibreMap.style?.getLayer(labelLayer)
                Log.d(TAG, "   - Checking for '$labelLayer': ${existingLayer != null}")
                if (existingLayer != null) {
                    mapLibreMap.style?.addLayerBelow(rasterLayer, labelLayer)
                    addedBelow = true
                    Log.d(TAG, "✅ Added weather layer BELOW: $labelLayer")
                    break
                }
            }

            if (!addedBelow) {
                Log.d(TAG, "🔍 No label layers found, adding on top...")
                mapLibreMap.style?.addLayer(rasterLayer)
                Log.d(TAG, "✅ Added weather layer ON TOP")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error positioning layer: ${e.message}", e)
            Log.d(TAG, "🔄 Fallback: Adding layer without positioning...")
            try {
                mapLibreMap.style?.addLayer(rasterLayer)
                Log.d(TAG, "✅ Fallback layer addition succeeded")
            } catch (e2: Exception) {
                Log.e(TAG, "💥 Even fallback failed: ${e2.message}", e2)
                throw e2
            }
        }

        // Verify layer was added
        val addedLayer = mapLibreMap.style?.getLayer(rasterLayerId)
        Log.d(TAG, "🔍 Final verification: Layer '$rasterLayerId' exists: ${addedLayer != null}")

        Log.d(TAG, "🎉🎉🎉 SUCCESSFULLY COMPLETED SKYSIGHT LAYER ADDITION: $rasterLayerId 🎉🎉🎉")

    } catch (e: Exception) {
        Log.e(TAG, "💥 Error adding RasterSource layer: ${e.message}", e)
    }
}

// Helper function to get available forecast times from SkySight API
suspend fun getAvailableForecastTimes(
    skysightClient: SkysightClient,
    regionId: String,
    apiKey: String
): List<String> {
    return try {
        val response = skysightClient.getDataApi().getDataLastUpdated(apiKey, regionId)
        if (response.isSuccessful) {
            val lastUpdatedData: List<LastUpdateInfo> = response.body() ?: emptyList()
            Log.d(TAG, "📅 Found ${lastUpdatedData.size} data layers with timestamps")

            // Convert unix timestamps to date/time strings
            val times: List<String> = lastUpdatedData.mapNotNull { dataInfo ->
                try {
                    val date = Date(dataInfo.last_updated * 1000)
                    val dateFormat = SimpleDateFormat("yyyy/MM/dd/HHmm", Locale.US)
                    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                    dateFormat.format(date)
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error parsing timestamp ${dataInfo.last_updated}: ${e.message}")
                    null
                }
            }
            times.distinct().take(5) // Use up to 5 most recent times
        } else {
            Log.w(TAG, "⚠️ Failed to get forecast times: HTTP ${response.code()}")
            emptyList<String>()
        }
    } catch (e: Exception) {
        Log.e(TAG, "💥 Error getting forecast times: ${e.message}")
        emptyList<String>()
    }
}

// Helper function to create RasterSource with authenticated HTTP client
fun createAuthenticatedRasterSource(
    sourceId: String,
    urlTemplate: String,
    httpClient: OkHttpClient
): RasterSource {
    Log.d(TAG, "🔧 Creating authenticated RasterSource...")
    Log.d(TAG, "   - sourceId: $sourceId")
    Log.d(TAG, "   - urlTemplate: $urlTemplate")
    Log.d(TAG, "   - httpClient: $httpClient")

    return try {
        // Try creating RasterSource with custom HTTP client using reflection
        Log.d(TAG, "🔧 Step A: Creating base RasterSource...")
        val rasterSource = RasterSource(sourceId, urlTemplate, 512)
        Log.d(TAG, "✅ Base RasterSource created: $rasterSource")

        // Set the HTTP client using reflection (internal MapLibre API)
        Log.d(TAG, "🔧 Step B: Setting custom HTTP client via reflection...")
        try {
            val httpClientField = rasterSource.javaClass.getDeclaredField("httpClient")
            Log.d(TAG, "🔍 Found httpClient field: $httpClientField")
            httpClientField.isAccessible = true
            Log.d(TAG, "🔍 Made httpClient field accessible")
            httpClientField.set(rasterSource, httpClient)
            Log.d(TAG, "✅ Successfully set custom HTTP client on RasterSource via reflection")
        } catch (reflectionError: Exception) {
            Log.w(TAG, "⚠️ Reflection failed: ${reflectionError.message}")
            Log.w(TAG, "🔍 RasterSource class: ${rasterSource.javaClass}")
            Log.w(TAG, "🔍 Available fields:")
            rasterSource.javaClass.declaredFields.forEach { field ->
                Log.w(TAG, "     Field: ${field.name} (${field.type})")
            }
            throw reflectionError
        }

        rasterSource
    } catch (e: Exception) {
        Log.e(TAG, "❌ Could not set HTTP client via reflection: ${e.message}", e)
        Log.w(TAG, "🔄 Fallback: Creating standard RasterSource (authentication may fail)")
        // Fallback: create standard RasterSource (will fail authentication)
        RasterSource(sourceId, urlTemplate, 512)
    }
}

// Helper function to calculate tile coordinates for current viewport
data class TileCoordinate(val z: Int, val x: Int, val y: Int, val bounds: LatLngBounds)

fun calculateTileCoordinates(bounds: LatLngBounds, zoom: Int): List<TileCoordinate> {
    val tiles = mutableListOf<TileCoordinate>()

    // Convert lat/lng bounds to tile coordinates
    val minTileX = longitudeToTileX(bounds.longitudeWest, zoom)
    val maxTileX = longitudeToTileX(bounds.longitudeEast, zoom)
    val minTileY = latitudeToTileY(bounds.latitudeNorth, zoom) // Note: Y is flipped
    val maxTileY = latitudeToTileY(bounds.latitudeSouth, zoom)

    // Limit number of tiles to prevent performance issues
    val maxTilesPerDimension = 4
    val tileXRange = (maxTileX - minTileX + 1).coerceAtMost(maxTilesPerDimension)
    val tileYRange = (maxTileY - minTileY + 1).coerceAtMost(maxTilesPerDimension)

    Log.d(TAG, "🔢 Tile range: X($minTileX-$maxTileX), Y($minTileY-$maxTileY), limited to ${tileXRange}x$tileYRange")

    // Validate tile coordinate ranges
    val maxTileCoordinate = (1 shl zoom) - 1 // 2^zoom - 1

    for (x in minTileX until (minTileX + tileXRange)) {
        for (y in minTileY until (minTileY + tileYRange)) {
            // Validate tile coordinates are within valid range
            if (x >= 0 && x <= maxTileCoordinate && y >= 0 && y <= maxTileCoordinate) {
                // Calculate bounds for this tile
                val tileBounds = LatLngBounds.Builder()
                    .include(org.maplibre.android.geometry.LatLng(tileYToLatitude(y, zoom), tileXToLongitude(x, zoom)))
                    .include(org.maplibre.android.geometry.LatLng(tileYToLatitude(y + 1, zoom), tileXToLongitude(x + 1, zoom)))
                    .build()

                tiles.add(TileCoordinate(zoom, x, y, tileBounds))
            }
        }
    }

    Log.d(TAG, "📍 Generated ${tiles.size} tile coordinates for zoom $zoom")
    return tiles
}

// Helper function to generate tiles in the current map view (zoom-aware approach)
suspend fun generateTileCoordinates(mapLibreMap: MapLibreMap): List<TileCoordinate> {
    try {
        Log.d(TAG, "🗺️ Generating tile coordinates for current viewport...")

        // Get current map bounds and zoom
        val bounds = mapLibreMap.projection.visibleRegion.latLngBounds
        val zoom = mapLibreMap.cameraPosition.zoom.toInt().coerceAtMost(14) // Cap at zoom 14 for performance

        Log.d(TAG, "📍 Current bounds: $bounds")
        Log.d(TAG, "🔍 Current zoom: $zoom")

        return calculateTileCoordinates(bounds, zoom)
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error generating tile coordinates: ${e.message}", e)
        return emptyList()
    }
}

// Map projection helper functions
private fun longitudeToTileX(longitude: Double, zoom: Int): Int {
    return ((longitude + 180.0) / 360.0 * (1 shl zoom)).toInt()
}

private fun latitudeToTileY(latitude: Double, zoom: Int): Int {
    val radians = Math.toRadians(latitude)
    return ((1.0 - kotlin.math.asinh(kotlin.math.tan(radians)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
}

private fun tileXToLongitude(x: Int, zoom: Int): Double {
    return x.toDouble() / (1 shl zoom) * 360.0 - 180.0
}

private fun tileYToLatitude(y: Int, zoom: Int): Double {
    val n = Math.PI - 2.0 * Math.PI * y / (1 shl zoom)
    return Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n)))
}

// Helper function to remove SkySight layers from map
fun removeSkysightLayerFromMap(mapLibreMap: MapLibreMap, layerId: String) {
    try {
        val rasterLayerId = "skysight-layer-$layerId"
        val sourceId = "skysight-source-$layerId"

        Log.d(TAG, "🗑️ Removing SkySight layer: $rasterLayerId")

        // Remove layer first
        mapLibreMap.style?.getLayer(rasterLayerId)?.let { layer ->
            mapLibreMap.style?.removeLayer(layer)
            Log.d(TAG, "✅ Removed layer: $rasterLayerId")
        }

        // Then remove source
        mapLibreMap.style?.getSource(sourceId)?.let { source ->
            mapLibreMap.style?.removeSource(source)
            Log.d(TAG, "✅ Removed source: $sourceId")
        }

    } catch (e: Exception) {
        Log.e(TAG, "❌ Error removing SkySight layer: ${e.message}", e)
    }
}