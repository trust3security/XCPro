package com.example.xcpro.screens.skysight

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.maplibre.android.maps.MapLibreMap

suspend fun addSkysightLayerToMap(
    mapLibreMap: MapLibreMap,
    layerId: String,
    layerType: String = "satellite",
    regionId: String,
    apiKey: String,
    skysightClient: com.example.xcpro.skysight.SkysightClient
) {
    try {
        android.util.Log.d("SkysightLayers", "🚀🚀🚀 STARTING SKYSIGHT LAYER ADDITION 🚀🚀🚀")
        android.util.Log.d("SkysightLayers", "📝 Parameters:")
        android.util.Log.d("SkysightLayers", "   - layerId: $layerId")
        android.util.Log.d("SkysightLayers", "   - layerType: $layerType")
        android.util.Log.d("SkysightLayers", "   - regionId: $regionId")
        android.util.Log.d("SkysightLayers", "   - apiKey: ${apiKey.take(8)}...")
        android.util.Log.d("SkysightLayers", "   - mapLibreMap: $mapLibreMap")
        android.util.Log.d("SkysightLayers", "   - skysightClient: $skysightClient")

        // Check if map is valid
        android.util.Log.d("SkysightLayers", "🔍 Step 1: Checking map validity...")
        android.util.Log.d("SkysightLayers", "   - Map object exists: ${mapLibreMap != null}")

        // Check if map style is loaded
        android.util.Log.d("SkysightLayers", "🔍 Step 2: Checking map style...")
        val mapStyle = mapLibreMap.style
        android.util.Log.d("SkysightLayers", "   - Map style object: $mapStyle")
        android.util.Log.d("SkysightLayers", "   - Map style is null: ${mapStyle == null}")

        if (mapStyle == null) {
            android.util.Log.e("SkysightLayers", "❌ FATAL: Map style is null, cannot add layer")
            return
        }

        android.util.Log.d("SkysightLayers", "✅ Map style is valid, proceeding...")

        // Log current sources and layers on the map
        try {
            android.util.Log.d("SkysightLayers", "🔍 Step 3: Current map sources and layers:")
            val sources = mapStyle.sources
            android.util.Log.d("SkysightLayers", "   - Current sources count: ${sources.size}")
            sources.forEach { source ->
                android.util.Log.d("SkysightLayers", "     Source: ${source.id} (${source.javaClass.simpleName})")
            }

            val layers = mapStyle.layers
            android.util.Log.d("SkysightLayers", "   - Current layers count: ${layers.size}")
            layers.forEach { layer ->
                android.util.Log.d("SkysightLayers", "     Layer: ${layer.id} (${layer.javaClass.simpleName})")
            }
        } catch (e: Exception) {
            android.util.Log.w("SkysightLayers", "⚠️ Could not enumerate sources/layers: ${e.message}")
        }

        // Remove existing layer if it exists
        android.util.Log.d("SkysightLayers", "🔍 Step 4: Removing existing layer...")
        removeSkysightLayerFromMap(mapLibreMap, layerId)

        // Get available forecast times for this region
        android.util.Log.d("SkysightLayers", "🔍 Step 5: Getting available forecast times...")
        val availableTimes = try {
            getAvailableForecastTimes(skysightClient, regionId, apiKey)
        } catch (e: Exception) {
            android.util.Log.w("SkysightLayers", "⚠️ Error getting forecast times: ${e.message}")
            emptyList()
        }

        val bestTime = if (availableTimes.isNotEmpty()) {
            availableTimes.first()
        } else {
            // Use our known working time as fallback
            "2025/08/29/1200"
        }
        android.util.Log.d("SkysightLayers", "📅 Available times: ${availableTimes.take(3)}")
        android.util.Log.d("SkysightLayers", "⏰ Selected forecast time: $bestTime")

        // Create authenticated HTTP client for this layer
        android.util.Log.d("SkysightLayers", "🔍 Step 6: Creating authenticated HTTP client...")
        val authenticatedClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor(com.example.xcpro.skysight.SkysightHeaderInterceptor(apiKey))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.HEADERS
            })
            .build()

        android.util.Log.d("SkysightLayers", "✅ HTTP client created with SkysightHeaderInterceptor")

        // Create RasterSource with authenticated HTTP client
        val sourceId = "skysight-source-$layerId"

        // Create SkySight authenticated tile URL
        val skysightUrlTemplate = "https://skysight.io/api/$layerType/{z}/{x}/{y}/$bestTime"
        android.util.Log.d("SkysightLayers", "🌐 Using SkySight URL template: $skysightUrlTemplate")

        // ALTERNATIVE APPROACH: Since MapLibre RasterSource can't send custom headers,
        // let's try using programmatic tile downloading and ImageSource instead
        android.util.Log.d("SkysightLayers", "🔍 Step 7: Creating RasterSource with FALLBACK to standard approach...")
        android.util.Log.w("SkysightLayers", "⚠️ NOTE: MapLibre RasterSource cannot authenticate with SkySight API")
        android.util.Log.w("SkysightLayers", "⚠️ Tiles will likely fail with 401, but layer structure will be created")

        // Create standard RasterSource (authentication will fail, but we'll see the layer structure)
        val rasterSource = org.maplibre.android.style.sources.RasterSource(sourceId, skysightUrlTemplate, 512)
        android.util.Log.d("SkysightLayers", "✅ RasterSource created (authentication expected to fail): $rasterSource")

        // TODO: Future enhancement - implement local proxy server or ImageSource approach

        // Add source to map
        android.util.Log.d("SkysightLayers", "🔍 Step 8: Adding RasterSource to map...")
        try {
            mapLibreMap.style?.addSource(rasterSource)
            android.util.Log.d("SkysightLayers", "✅ RasterSource added successfully: $sourceId")
        } catch (e: Exception) {
            android.util.Log.e("SkysightLayers", "❌ Failed to add RasterSource: ${e.message}", e)
            throw e
        }

        // Verify source was added
        val addedSource = mapLibreMap.style?.getSource(sourceId)
        android.util.Log.d("SkysightLayers", "🔍 Verification: Source '$sourceId' exists after adding: ${addedSource != null}")

        // Create raster layer
        android.util.Log.d("SkysightLayers", "🔍 Step 9: Creating RasterLayer...")
        val rasterLayerId = "skysight-layer-$layerId"
        val opacity = when (layerType) {
            "satellite" -> 1.0f  // Full opacity for satellite
            "rain" -> 0.7f       // Semi-transparent for rain
            else -> 0.8f
        }

        android.util.Log.d("SkysightLayers", "🎨 RasterLayer properties: layerId=$rasterLayerId, sourceId=$sourceId, opacity=$opacity")

        val rasterLayer = org.maplibre.android.style.layers.RasterLayer(rasterLayerId, sourceId).apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.rasterOpacity(opacity)
            )
        }

        android.util.Log.d("SkysightLayers", "✅ RasterLayer created: $rasterLayer")

        // Add layer to map below labels
        android.util.Log.d("SkysightLayers", "🔍 Step 10: Adding RasterLayer to map...")
        try {
            val labelLayers = listOf("place-label", "road-label", "poi-label", "waterway-label")
            var addedBelow = false

            android.util.Log.d("SkysightLayers", "🔍 Searching for label layers to position below...")
            for (labelLayer in labelLayers) {
                val existingLayer = mapLibreMap.style?.getLayer(labelLayer)
                android.util.Log.d("SkysightLayers", "   - Checking for '$labelLayer': ${existingLayer != null}")
                if (existingLayer != null) {
                    mapLibreMap.style?.addLayerBelow(rasterLayer, labelLayer)
                    addedBelow = true
                    android.util.Log.d("SkysightLayers", "✅ Added weather layer BELOW: $labelLayer")
                    break
                }
            }

            if (!addedBelow) {
                android.util.Log.d("SkysightLayers", "🔍 No label layers found, adding on top...")
                mapLibreMap.style?.addLayer(rasterLayer)
                android.util.Log.d("SkysightLayers", "✅ Added weather layer ON TOP")
            }
        } catch (e: Exception) {
            android.util.Log.e("SkysightLayers", "❌ Error positioning layer: ${e.message}", e)
            android.util.Log.d("SkysightLayers", "🔄 Fallback: Adding layer without positioning...")
            try {
                mapLibreMap.style?.addLayer(rasterLayer)
                android.util.Log.d("SkysightLayers", "✅ Fallback layer addition succeeded")
            } catch (e2: Exception) {
                android.util.Log.e("SkysightLayers", "💥 Even fallback failed: ${e2.message}", e2)
                throw e2
            }
        }

        // Verify layer was added
        val addedLayer = mapLibreMap.style?.getLayer(rasterLayerId)
        android.util.Log.d("SkysightLayers", "🔍 Final verification: Layer '$rasterLayerId' exists: ${addedLayer != null}")

        android.util.Log.d("SkysightLayers", "🎉🎉🎉 SUCCESSFULLY COMPLETED SKYSIGHT LAYER ADDITION: $rasterLayerId 🎉🎉🎉")

    } catch (e: Exception) {
        android.util.Log.e("SkysightLayers", "💥 Error adding RasterSource layer: ${e.message}", e)
    }
}

suspend fun getAvailableForecastTimes(
    skysightClient: com.example.xcpro.skysight.SkysightClient,
    regionId: String,
    apiKey: String
): List<String> {
    return try {
        val response = skysightClient.getDataApi().getDataLastUpdated(apiKey, regionId)
        if (response.isSuccessful) {
            val lastUpdatedData: List<com.example.xcpro.skysight.LastUpdateInfo> = response.body() ?: emptyList()
            android.util.Log.d("SkysightLayers", "📅 Found ${lastUpdatedData.size} data layers with timestamps")

            // Convert unix timestamps to date/time strings
            val times: List<String> = lastUpdatedData.mapNotNull { dataInfo ->
                try {
                    val date = java.util.Date(dataInfo.last_updated * 1000)
                    val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd/HHmm", java.util.Locale.US)
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    dateFormat.format(date)
                } catch (e: Exception) {
                    android.util.Log.w("SkysightLayers", "⚠️ Error parsing timestamp ${dataInfo.last_updated}: ${e.message}")
                    null
                }
            }
            times.distinct().take(5) // Use up to 5 most recent times
        } else {
            android.util.Log.w("SkysightLayers", "⚠️ Failed to get forecast times: HTTP ${response.code()}")
            emptyList<String>()
        }
    } catch (e: Exception) {
        android.util.Log.e("SkysightLayers", "💥 Error getting forecast times: ${e.message}")
        emptyList<String>()
    }
}

fun createAuthenticatedRasterSource(
    sourceId: String,
    urlTemplate: String,
    httpClient: okhttp3.OkHttpClient
): org.maplibre.android.style.sources.RasterSource {
    android.util.Log.d("SkysightLayers", "🔧 Creating authenticated RasterSource...")
    android.util.Log.d("SkysightLayers", "   - sourceId: $sourceId")
    android.util.Log.d("SkysightLayers", "   - urlTemplate: $urlTemplate")
    android.util.Log.d("SkysightLayers", "   - httpClient: $httpClient")

    return try {
        // Try creating RasterSource with custom HTTP client using reflection
        android.util.Log.d("SkysightLayers", "🔧 Step A: Creating base RasterSource...")
        val rasterSource = org.maplibre.android.style.sources.RasterSource(sourceId, urlTemplate, 512)
        android.util.Log.d("SkysightLayers", "✅ Base RasterSource created: $rasterSource")

        // Set the HTTP client using reflection (internal MapLibre API)
        android.util.Log.d("SkysightLayers", "🔧 Step B: Setting custom HTTP client via reflection...")
        try {
            val httpClientField = rasterSource.javaClass.getDeclaredField("httpClient")
            android.util.Log.d("SkysightLayers", "🔍 Found httpClient field: $httpClientField")
            httpClientField.isAccessible = true
            android.util.Log.d("SkysightLayers", "🔍 Made httpClient field accessible")
            httpClientField.set(rasterSource, httpClient)
            android.util.Log.d("SkysightLayers", "✅ Successfully set custom HTTP client on RasterSource via reflection")
        } catch (reflectionError: Exception) {
            android.util.Log.w("SkysightLayers", "⚠️ Reflection failed: ${reflectionError.message}")
            android.util.Log.w("SkysightLayers", "🔍 RasterSource class: ${rasterSource.javaClass}")
            android.util.Log.w("SkysightLayers", "🔍 Available fields:")
            rasterSource.javaClass.declaredFields.forEach { field ->
                android.util.Log.w("SkysightLayers", "     Field: ${field.name} (${field.type})")
            }
            throw reflectionError
        }

        rasterSource
    } catch (e: Exception) {
        android.util.Log.e("SkysightLayers", "❌ Could not set HTTP client via reflection: ${e.message}", e)
        android.util.Log.w("SkysightLayers", "🔄 Fallback: Creating standard RasterSource (authentication may fail)")
        // Fallback: create standard RasterSource (will fail authentication)
        org.maplibre.android.style.sources.RasterSource(sourceId, urlTemplate, 512)
    }
}

fun removeSkysightLayerFromMap(mapLibreMap: MapLibreMap, layerId: String) {
    try {
        mapLibreMap.style?.let { style ->
            // Remove all tile-based layers and sources for this layerId
            val layersToRemove = mutableListOf<String>()
            val sourcesToRemove = mutableListOf<String>()

            // Find layers and sources to remove
            style.layers.forEach { layer ->
                if (layer.id.contains(layerId)) {
                    layersToRemove.add(layer.id)
                }
            }

            style.sources.forEach { source ->
                if (source.id.contains(layerId)) {
                    sourcesToRemove.add(source.id)
                }
            }

            // Remove layers first, then sources
            layersToRemove.forEach { layerIdToRemove ->
                style.removeLayer(layerIdToRemove)
                android.util.Log.d("SkysightLayers", "🗑️ Removed layer: $layerIdToRemove")
            }

            sourcesToRemove.forEach { sourceIdToRemove ->
                style.removeSource(sourceIdToRemove)
                android.util.Log.d("SkysightLayers", "🗑️ Removed source: $sourceIdToRemove")
            }

            android.util.Log.d("SkysightLayers", "✅ Cleanup completed for layer: $layerId")
        }
    } catch (e: Exception) {
        android.util.Log.e("SkysightLayers", "💥 Error removing SkySight layer: ${e.message}", e)
    }
}
