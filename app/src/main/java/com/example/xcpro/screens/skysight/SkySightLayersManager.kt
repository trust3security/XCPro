package com.example.xcpro.screens.skysight

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.xcpro.skysight.SkysightClient
import kotlinx.coroutines.flow.first
import org.maplibre.android.maps.MapLibreMap
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkysightLayersBottomSheet(
    onDismiss: () -> Unit,
    mapLibreMap: MapLibreMap?,
    selectedLayers: Set<String>,
    onLayerToggle: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val skysightClient = remember { SkysightClient.getInstance(context) }

    val isAuthenticated by skysightClient.isAuthenticated.collectAsState()
    val availableLayers by skysightClient.availableLayers.collectAsState()

    android.util.Log.d("SkysightLayersBottomSheet", "🎯 Bottom sheet opened - Current selected layers: $selectedLayers")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weather Layers",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }

            HorizontalDivider()

            if (!isAuthenticated) {
                // Not authenticated message
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Authentication Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Please login to SkySight in General Settings to access weather layers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (availableLayers.isEmpty()) {
                // Loading state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Loading available layers...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // Available layers
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableLayers) { layer ->
                        LayerToggleItem(
                            layer = layer,
                            isSelected = selectedLayers.contains(layer.id),
                            mapLibreMap = mapLibreMap,
                            skysightClient = skysightClient,
                            onToggle = { enabled ->
                                onLayerToggle(layer.id, enabled)
                            }
                        )
                    }
                }

                // Static layers for demo (until API layers are loaded)
                if (availableLayers.isEmpty()) {
                    val demoLayers = listOf(
                        "Wind" to "🌬️ Wind speed and direction",
                        "Convergence" to "🔀 Convergence lines",
                        "Thermals" to "🌡️ Thermal strength",
                        "Precipitation" to "🌧️ Rain forecast",
                        "Satellite" to "🛰️ Satellite imagery",
                        "Cloud Base" to "☁️ Cloud base height",
                        "Wave Activity" to "🌊 Mountain wave",
                        "Star Rating" to "⭐ Soaring quality"
                    )

                    demoLayers.forEach { (layerId, description) ->
                        DemoLayerToggleItem(
                            layerId = layerId,
                            description = description,
                            isSelected = selectedLayers.contains(layerId),
                            mapLibreMap = mapLibreMap,
                            skysightClient = skysightClient,
                            onToggle = { enabled ->
                                onLayerToggle(layerId, enabled)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun LayerToggleItem(
    layer: com.example.xcpro.skysight.LayerInfo,
    isSelected: Boolean,
    mapLibreMap: MapLibreMap?,
    skysightClient: com.example.xcpro.skysight.SkysightClient,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = layer.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = layer.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Units: ${layer.legend.units}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Switch(
                checked = isSelected,
                onCheckedChange = { enabled ->
                    android.util.Log.d("LayerToggle", "🎯 Layer ${layer.name} (${layer.id}) toggled: $enabled")
                    onToggle(enabled)

                    // Handle actual map layer toggle
                    mapLibreMap?.let { map ->
                        android.util.Log.d("LayerToggle", "🗺️ MapLibreMap is available")
                        val selectedRegion = kotlinx.coroutines.runBlocking {
                            skysightClient.selectedRegion.first()
                        }
                        val apiToken = skysightClient.getAuthToken()

                        android.util.Log.d("LayerToggle", "🌍 Selected region: $selectedRegion, API token: ${apiToken?.take(8)}...")

                        if (enabled && selectedRegion != null && apiToken != null) {
                            android.util.Log.d("LayerToggle", "➕ Adding layer ${layer.name} (type: ${layer.data_type}) to map")
                            android.util.Log.d("LayerToggle", "🔍 Parameters - Layer ID: ${layer.id}, Region: $selectedRegion, Token length: ${apiToken.length}")
                            // Add layer to map
                            // DISABLED: Old layer approach - now using programmatic tiles in SkysightMapControls
                            android.util.Log.d("LayerToggle", "⚠️ Old layer toggle disabled - use SkySight FAB controls")
                        } else {
                            if (apiToken == null) {
                                android.util.Log.w("LayerToggle", "⚠️ No API token available - cannot add layer ${layer.name}")
                            }
                            android.util.Log.d("LayerToggle", "➖ Removing layer ${layer.name} from map")
                            // Remove layer from map
                            removeSkysightLayerFromMap(map, layer.id)
                        }
                    } ?: run {
                        android.util.Log.w("LayerToggle", "⚠️ MapLibreMap is null, cannot toggle layer")
                    }
                }
            )
        }
    }
}

@Composable
fun DemoLayerToggleItem(
    layerId: String,
    description: String,
    isSelected: Boolean,
    mapLibreMap: MapLibreMap?,
    skysightClient: com.example.xcpro.skysight.SkysightClient,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = layerId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isSelected,
                onCheckedChange = { enabled ->
                    onToggle(enabled)

                    // Handle actual demo layer toggle
                    mapLibreMap?.let { map ->
                        val selectedRegion = kotlinx.coroutines.runBlocking {
                            skysightClient.selectedRegion.first()
                        }
                        val apiToken = skysightClient.getAuthToken()

                        if (enabled && selectedRegion != null && apiToken != null) {
                            // Map demo layer names to actual SkySight layer types
                            val layerType = when (layerId) {
                                "Precipitation", "Rain" -> "rain"
                                "Satellite" -> "satellite"
                                else -> "satellite" // Default to satellite for demo layers
                            }

                            // DISABLED: Old approach - use SkySight FAB controls
                            android.util.Log.d("LayerToggle", "⚠️ Demo layer toggle disabled - use SkySight FAB")
                        } else {
                            removeSkysightLayerFromMap(map, layerId)
                        }
                    }
                }
            )
        }
    }
}

// ✅ SkySight Layer Management Functions - RasterSource Approach with Authentication
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

// Helper function to get available forecast times from SkySight API
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

// Helper function to create RasterSource with authenticated HTTP client
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