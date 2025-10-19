package com.example.xcpro.skysight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.maplibre.android.maps.MapLibreMap

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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                imageVector = getCategoryIcon(category.id),
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
    android.util.Log.d("EnhancedLayers", "Rendering layer: ${'$'}{layer.name} (${ '$'}{layer.id}) - enabled: ${'$'}isEnabled")
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
        Column(modifier = Modifier.padding(16.dp)) {
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
                            text = "Units: ${'$'}{layer.units}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${'$'}{layer.updateFrequency}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { newValue -> onToggle(layer.id, newValue) }
                    )
                    OutlinedButton(
                        onClick = { onToggle(layer.id, !isEnabled) },
                        modifier = Modifier.padding(top = 4.dp)
                    ) { Text(text = if (isEnabled) "ON" else "OFF", style = MaterialTheme.typography.bodySmall) }
                    if (isEnabled) {
                        TextButton(onClick = { showOpacityControl = !showOpacityControl }, modifier = Modifier.padding(0.dp)) {
                            Text(text = "${'$'}{(layer.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isEnabled && showOpacityControl,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Opacity", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(60.dp))
                        Slider(value = layer.opacity, onValueChange = onOpacityChange, valueRange = 0.1f..1.0f, modifier = Modifier.weight(1f))
                        Text(text = "${'$'}{(layer.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
                    }
                }
            }

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
        Text(text = "Color Scale", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = legend.colors.firstOrNull()?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = legend.colors.lastOrNull()?.name ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AuthenticationRequiredCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Authentication Required", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
            }
            Text(text = "Please login to SkySight in Settings to access weather layers and data.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
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
