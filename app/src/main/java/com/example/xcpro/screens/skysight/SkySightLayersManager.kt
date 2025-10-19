package com.example.xcpro.screens.skysight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.skysight.SkysightClient
import org.maplibre.android.maps.MapLibreMap

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