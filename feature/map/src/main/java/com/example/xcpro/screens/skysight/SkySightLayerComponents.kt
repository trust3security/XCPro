package com.example.xcpro.screens.skysight

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.maplibre.android.maps.MapLibreMap

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