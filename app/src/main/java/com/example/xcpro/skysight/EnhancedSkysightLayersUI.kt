package com.example.xcpro.skysight

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import java.text.SimpleDateFormat
import java.util.*

data class WeatherLayerCategory(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val description: String
)

data class EnhancedWeatherLayer(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val units: String,
    val opacity: Float = 0.8f,
    val isEnabled: Boolean = false,
    val legend: Legend? = null,
    val updateFrequency: String = "Every 3 hours"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSkysightLayersPanel(
    mapLibreMap: MapLibreMap?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    android.util.Log.d("EnhancedLayers", "🎯🎯🎯🎯 ENHANCED LAYER PANEL COMPOSABLE STARTED")
    val context = LocalContext.current
    val skysightClient = remember { SkysightClient.getInstance(context) }
    val scope = rememberCoroutineScope()

    val isAuthenticated by skysightClient.isAuthenticated.collectAsState()
    val availableLayers by skysightClient.availableLayers.collectAsState()
    val selectedRegion by skysightClient.selectedRegion.collectAsState()

    var selectedCategory by remember { mutableStateOf("all") }
    var enabledLayers by remember { mutableStateOf(setOf<String>("potfd")) }
    var layerOpacities by remember { mutableStateOf(mapOf<String, Float>()) }

    // Auto-load default layer on component initialization
    LaunchedEffect(availableLayers, mapLibreMap) {
        if (availableLayers.isNotEmpty() && mapLibreMap != null && enabledLayers.contains("potfd")) {
            android.util.Log.d("EnhancedLayers", "🚀🚀🚀 AUTO-LOADING XC SPEED LAYER ON STARTUP 🚀🚀🚀")

            val apiKey = skysightClient.getApiKey()
            val region = skysightClient.selectedRegion.value

            if (apiKey != null && region != null) {
                try {
                    val tileUrl = "https://skysight.io/api/tiles/v1/$region/potfd/{z}/{x}/{y}.png?api_key=$apiKey"
                    android.util.Log.d("EnhancedLayers", "🔗 Auto-load tile URL: $tileUrl")

                    val rasterSource = org.maplibre.android.style.sources.RasterSource(
                        "skysight-potfd",
                        tileUrl,
                        256
                    )

                    val rasterLayer = org.maplibre.android.style.layers.RasterLayer(
                        "skysight-layer-potfd",
                        "skysight-potfd"
                    ).withProperties(
                        org.maplibre.android.style.layers.PropertyFactory.rasterOpacity(0.8f)
                    )

                    mapLibreMap.style?.let { style ->
                        style.addSource(rasterSource)
                        style.addLayer(rasterLayer)
                        android.util.Log.d("EnhancedLayers", "🟢🟢🟢 XC SPEED AUTO-LOADED! Should be visible on map")
                    } ?: android.util.Log.e("EnhancedLayers", "❌ Map style not available for auto-load")

                } catch (e: Exception) {
                    android.util.Log.e("EnhancedLayers", "❌ Error auto-loading XC Speed: ${e.message}")
                }
            } else {
                android.util.Log.e("EnhancedLayers", "❌ Auto-load failed - API Key: ${apiKey != null}, Region: ${region != null}")
            }
        } else {
            android.util.Log.d("EnhancedLayers", "⏳ Auto-load waiting - Layers: ${availableLayers.size}, Map: ${mapLibreMap != null}, Enabled: ${enabledLayers.contains("potfd")}")
        }
    }

    val categories = remember {
        listOf(
            WeatherLayerCategory("all", "All", Icons.AutoMirrored.Filled.ViewList, Color.Gray, "Show all available layers"),
            WeatherLayerCategory("wind", "Wind", Icons.Default.Air, Color.Blue, "Wind speed and direction"),
            WeatherLayerCategory("thermal", "Thermals", Icons.Default.Thermostat, Color.Red, "Thermal strength and lift"),
            WeatherLayerCategory("precipitation", "Precipitation", Icons.Default.Cloud, Color.Blue, "Rain and precipitation"),
            WeatherLayerCategory("satellite", "Satellite", Icons.Default.Satellite, Color.Green, "Satellite imagery"),
            WeatherLayerCategory("convergence", "Convergence", Icons.AutoMirrored.Filled.CompareArrows, Color.Magenta, "Convergence lines"),
            WeatherLayerCategory("wave", "Mountain Wave", Icons.Default.Waves, Color.Cyan, "Mountain wave activity")
        )
    }

    val enhancedLayers = remember(availableLayers) {
        android.util.Log.d("EnhancedLayers", "🔍 Building enhanced layers - availableLayers.size: ${availableLayers.size}")
        if (availableLayers.isNotEmpty()) {
            android.util.Log.d("EnhancedLayers", "✅ Using real SkySight layers: ${availableLayers.size} layers")
            availableLayers.map { layer ->
                android.util.Log.d("EnhancedLayers", "📋 Real layer: ${layer.id} - ${layer.name}")
                EnhancedWeatherLayer(
                    id = layer.id,
                    name = layer.name,
                    category = categorizeLayer(layer.id, layer.name),
                    description = layer.description,
                    units = layer.legend.units,
                    opacity = layerOpacities[layer.id] ?: 0.8f,
                    isEnabled = enabledLayers.contains(layer.id),
                    legend = layer.legend
                )
            }
        } else {
            android.util.Log.d("EnhancedLayers", "⚠️ Using demo layers - no real SkySight layers available")
            // Demo layers for when API layers aren't loaded
            listOf(
                EnhancedWeatherLayer("wind", "Wind Speed", "wind", "Surface wind speed and direction", "m/s"),
                EnhancedWeatherLayer("thermal", "Thermal Strength", "thermal", "Thermal updraft velocity", "m/s"),
                EnhancedWeatherLayer("convergence", "Convergence Lines", "convergence", "Wind convergence zones", "1/s"),
                EnhancedWeatherLayer("satellite", "Satellite Imagery", "satellite", "Visible satellite imagery", "RGB"),
                EnhancedWeatherLayer("rain", "Precipitation", "precipitation", "Rain and snow forecast", "mm/h"),
                EnhancedWeatherLayer("wave", "Mountain Wave", "wave", "Mountain wave lee waves", "m/s"),
                EnhancedWeatherLayer("cloud_base", "Cloud Base", "thermal", "Cloud base height", "m"),
                EnhancedWeatherLayer("star_rating", "Soaring Quality", "thermal", "Overall soaring conditions", "0-5 stars")
            )
        }
    }

    val filteredLayers = remember(enhancedLayers, selectedCategory) {
        android.util.Log.d("EnhancedLayers", "🎯 Filtering layers - selectedCategory: '$selectedCategory', total layers: ${enhancedLayers.size}")
        val filtered = if (selectedCategory == "all") {
            android.util.Log.d("EnhancedLayers", "📋 Showing ALL layers: ${enhancedLayers.size}")
            enhancedLayers
        } else {
            val result = enhancedLayers.filter { it.category == selectedCategory }
            android.util.Log.d("EnhancedLayers", "🔍 Filtered to category '$selectedCategory': ${result.size} layers")
            result.forEach { layer ->
                android.util.Log.d("EnhancedLayers", "   - ${layer.id}: ${layer.name} (category: ${layer.category})")
            }
            result
        }
        android.util.Log.d("EnhancedLayers", "✅ Final filtered layers count: ${filtered.size}")
        filtered
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with connection status
            WeatherLayersHeader(
                isAuthenticated = isAuthenticated,
                selectedRegion = selectedRegion,
                onDismiss = onDismiss
            )

            HorizontalDivider()

            if (!isAuthenticated) {
                AuthenticationRequiredCard()
            } else {
                // Category tabs
                CategorySelectionTabs(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelect = { selectedCategory = it }
                )

                // Layers list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    android.util.Log.d("EnhancedLayers", "🔧 LAZY COLUMN ITEMS - about to render ${filteredLayers.size} items")
                    items(filteredLayers) { layer ->
                        android.util.Log.d("EnhancedLayers", "🔧 LAZY COLUMN ITEM BLOCK - rendering: ${layer.name}")

                        // Auto-enable first thermal layer for testing
                        LaunchedEffect(layer.id) {
                            if (layer.category == "thermal" && layer.id == "wstar_bsratio" && !enabledLayers.contains(layer.id)) {
                                android.util.Log.d("EnhancedLayers", "🧪🧪🧪 AUTO-ENABLING TEST LAYER: ${layer.id} 🧪🧪🧪")
                                enabledLayers = enabledLayers + layer.id
                            }
                        }

                        EnhancedLayerItem(
                            layer = layer,
                            isEnabled = enabledLayers.contains(layer.id),
                            onToggle = { layerId, enabled ->
                                android.util.Log.d("EnhancedLayers", "🎯🎯🎯 ENHANCED LAYER TOGGLE CALLED - Layer: $layerId, Enabled: $enabled 🎯🎯🎯")
                                enabledLayers = if (enabled) {
                                    enabledLayers + layerId
                                } else {
                                    enabledLayers - layerId
                                }

                                // Handle map layer toggle
                                scope.launch {
                                    if (enabled) {
                                        android.util.Log.d("EnhancedLayers", "🟢🟢🟢 ENHANCED LAYER MANAGEMENT - Adding authenticated tiles for layer $layerId 🟢🟢🟢")
                                        val apiKey = skysightClient.getApiKey()
                                        val region = skysightClient.selectedRegion.value
                                        android.util.Log.d("EnhancedLayers", "🔑 API Key: ${apiKey?.take(8)}..., Region: $region")

                                        if (apiKey != null && region != null && mapLibreMap != null) {
                                            try {
                                                android.util.Log.d("EnhancedLayers", "✅ ENHANCED LAYER MANAGEMENT WORKING! Adding layer $layerId to map")

                                                // Add SkySight authenticated tile layer to map
                                                val tileUrl = "https://skysight.io/api/tiles/v1/$region/potfd/{z}/{x}/{y}.png?api_key=$apiKey"
                                                android.util.Log.d("EnhancedLayers", "🔗 Tile URL: $tileUrl")

                                                val rasterSource = org.maplibre.android.style.sources.RasterSource(
                                                    "skysight-$layerId",
                                                    tileUrl,
                                                    256
                                                )

                                                val rasterLayer = org.maplibre.android.style.layers.RasterLayer(
                                                    "skysight-layer-$layerId",
                                                    "skysight-$layerId"
                                                ).withProperties(
                                                    org.maplibre.android.style.layers.PropertyFactory.rasterOpacity(layer.opacity)
                                                )

                                                mapLibreMap.style?.let { style ->
                                                    style.addSource(rasterSource)
                                                    style.addLayer(rasterLayer)
                                                    android.util.Log.d("EnhancedLayers", "🟢🟢🟢 LAYER ADDED TO MAP! $layerId should now be visible")
                                                    android.util.Log.d("EnhancedLayers", "📊 Layer: ${layer.name} - ${layer.description}")
                                                    android.util.Log.d("EnhancedLayers", "🎯 Opacity: ${layer.opacity}")
                                                } ?: android.util.Log.e("EnhancedLayers", "❌ Map style not available")

                                            } catch (e: Exception) {
                                                android.util.Log.e("EnhancedLayers", "❌ Error adding layer to map: ${e.message}")
                                            }
                                        } else {
                                            android.util.Log.e("EnhancedLayers", "❌ Missing requirements - API Key: ${apiKey != null}, Region: ${region != null}, Map: ${mapLibreMap != null}")
                                        }
                                    } else {
                                        android.util.Log.d("EnhancedLayers", "🔴 ENHANCED LAYER MANAGEMENT - Removing layer $layerId from map")

                                        // Remove SkySight layer from map
                                        mapLibreMap?.style?.let { style ->
                                            try {
                                                style.removeLayer("skysight-layer-$layerId")
                                                style.removeSource("skysight-$layerId")
                                                android.util.Log.d("EnhancedLayers", "🟢 LAYER REMOVED FROM MAP! $layerId is no longer visible")
                                            } catch (e: Exception) {
                                                android.util.Log.e("EnhancedLayers", "❌ Error removing layer from map: ${e.message}")
                                            }
                                        } ?: android.util.Log.e("EnhancedLayers", "❌ Map style not available for layer removal")
                                    }
                                }
                            },
                            onOpacityChange = { opacity ->
                                layerOpacities = layerOpacities + (layer.id to opacity)
                                // Update map layer opacity
                                updateMapLayerOpacity(mapLibreMap, layer.id, opacity)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherLayersHeader(
    isAuthenticated: Boolean,
    selectedRegion: String?,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Weather Layers",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (isAuthenticated && selectedRegion != null) {
                Text(
                    text = "Region: $selectedRegion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row {
            if (isAuthenticated) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Connected",
                    tint = Color.Green,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }
    }
}

@Composable
fun CategorySelectionTabs(
    categories: List<WeatherLayerCategory>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit
) {
    // Compact 2x3 grid layout to save vertical space
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: All, Wind
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.take(2).forEach { category ->
                CategoryButton(
                    category = category,
                    isSelected = selectedCategory == category.id,
                    onCategorySelect = onCategorySelect,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 2: Thermal, Precipitation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.drop(2).take(2).forEach { category ->
                CategoryButton(
                    category = category,
                    isSelected = selectedCategory == category.id,
                    onCategorySelect = onCategorySelect,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 3: Satellite, Wave (if available)
        if (categories.size > 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.drop(4).take(2).forEach { category ->
                    CategoryButton(
                        category = category,
                        isSelected = selectedCategory == category.id,
                        onCategorySelect = onCategorySelect,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space if only one button in this row
                if (categories.size == 5) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CategoryButton(
    category: WeatherLayerCategory,
    isSelected: Boolean,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = { onCategorySelect(category.id) },
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else category.color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun EnhancedLayerItem(
    layer: EnhancedWeatherLayer,
    isEnabled: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit
) {
    android.util.Log.d("EnhancedLayers", "🔧 RENDERING LAYER ITEM: ${layer.name} (${layer.id}) - enabled: $isEnabled")
    var showOpacityControl by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isEnabled) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Main layer toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getCategoryIcon(layer.category),
                    contentDescription = null,
                    tint = if (layer.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
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
                    Row {
                        Text(
                            text = "Units: ${layer.units}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${layer.updateFrequency}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    // Add both Switch and backup Button for testing
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { newValue ->
                            android.util.Log.d("EnhancedLayers", "🔵🔵🔵 SWITCH onCheckedChange called - Layer: ${layer.id}, Old: $isEnabled, New: $newValue 🔵🔵🔵")
                            onToggle(layer.id, newValue)
                        }
                    )

                    // Backup toggle button for testing
                    OutlinedButton(
                        onClick = {
                            android.util.Log.d("EnhancedLayers", "🟡🟡🟡 BACKUP BUTTON CLICKED - Layer: ${layer.id}, Current: $isEnabled 🟡🟡🟡")
                            onToggle(layer.id, !isEnabled)
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (isEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (isEnabled) {
                        TextButton(
                            onClick = { showOpacityControl = !showOpacityControl },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                text = "${(layer.opacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Opacity control (when enabled and expanded)
            AnimatedVisibility(
                visible = isEnabled && showOpacityControl,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opacity",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(60.dp)
                        )
                        Slider(
                            value = layer.opacity,
                            onValueChange = onOpacityChange,
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(layer.opacity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }

            // Color legend preview (if available)
            layer.legend?.let { legend ->
                if (isEnabled && legend.colors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WeatherLegendPreview(legend = legend)
                }
            }
        }
    }
}

@Composable
fun WeatherLegendPreview(legend: Legend) {
    Column {
        Text(
            text = "Color Scale",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
        ) {
            legend.colors.forEach { colorItem ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            Color(
                                red = colorItem.color[0] / 255f,
                                green = colorItem.color[1] / 255f,
                                blue = colorItem.color[2] / 255f
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = legend.colors.firstOrNull()?.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = legend.colors.lastOrNull()?.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AuthenticationRequiredCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Authentication Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Please login to SkySight in Settings to access weather layers and data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// Helper functions
private fun categorizeLayer(layerId: String, layerName: String): String {
    val id = layerId.lowercase()
    val name = layerName.lowercase()

    return when {
        id.contains("wind") || name.contains("wind") -> "wind"
        id.contains("thermal") || name.contains("thermal") || id.contains("lift") -> "thermal"
        id.contains("rain") || id.contains("precip") || name.contains("rain") || name.contains("precipitation") -> "precipitation"
        id.contains("satellite") || name.contains("satellite") -> "satellite"
        id.contains("convergence") || name.contains("convergence") -> "convergence"
        id.contains("wave") || name.contains("wave") -> "wave"
        else -> "thermal" // Default to thermal category
    }
}

private fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "wind" -> Icons.Default.Air
        "thermal" -> Icons.Default.Thermostat
        "precipitation" -> Icons.Default.Cloud
        "satellite" -> Icons.Default.Satellite
        "convergence" -> Icons.AutoMirrored.Filled.CompareArrows
        "wave" -> Icons.Default.Waves
        else -> Icons.Default.Layers
    }
}


private fun mapLayerType(category: String, layerId: String): String {
    return when {
        category == "satellite" || layerId.contains("satellite") -> "satellite"
        category == "precipitation" || layerId.contains("rain") -> "rain"
        else -> "satellite" // Default fallback
    }
}

private fun updateMapLayerOpacity(mapLibreMap: MapLibreMap?, layerId: String, opacity: Float) {
    mapLibreMap?.style?.let { style ->
        val layer = style.getLayer("skysight-layer-$layerId")
        layer?.let {
            if (it is org.maplibre.android.style.layers.RasterLayer) {
                it.setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.rasterOpacity(opacity)
                )
                android.util.Log.d("EnhancedLayers", "🎨 Updated opacity for $layerId to $opacity")
            }
        }
    }
}
