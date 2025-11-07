package com.example.xcpro.skysight

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SkysightMapOverlay(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    mapLibreMap: org.maplibre.android.maps.MapLibreMap? = null
) {
    android.util.Log.d("SkysightDebug", "🚀 SkysightMapOverlay started")
    val context = LocalContext.current
    val skysightClient = remember { SkysightClient.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    val isAuthenticated by skysightClient.isAuthenticated.collectAsState()
    val selectedRegion by skysightClient.selectedRegion.collectAsState()
    val hasCredentials = remember { skysightClient.hasStoredCredentials() }
    
    var showOverlayControls by remember { mutableStateOf(false) }
    var showSatellite by remember { mutableStateOf(skysightClient.getShowSatellite()) }
    var showRain by remember { mutableStateOf(skysightClient.getShowRain()) }
    var forecastTime by remember { mutableStateOf(Date()) }

    android.util.Log.d("SkysightFAB", "🔥 SkysightMapOverlay rendering - authenticated: $isAuthenticated")
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Only show FAB if credentials are stored
        if (hasCredentials || isAuthenticated) {
            // Main Skysight Toggle Button
            android.util.Log.d("SkysightFAB", "☁️ FloatingActionButton rendering")
            FloatingActionButton(
            onClick = { 
                android.util.Log.d("SkysightDebug", "☁️ FAB clicked! authenticated: $isAuthenticated")
                if (isAuthenticated) {
                    android.util.Log.d("SkysightDebug", "Showing overlay controls")
                    showOverlayControls = !showOverlayControls 
                } else {
                    android.util.Log.d("SkysightDebug", "Not authenticated - go to General Settings")
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = if (isAuthenticated) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.outline
        ) {
            Icon(
                imageVector = if (isAuthenticated) Icons.Default.Cloud else Icons.Default.CloudOff,
                contentDescription = "Skysight Weather",
                tint = if (isAuthenticated) MaterialTheme.colorScheme.onPrimary 
                       else MaterialTheme.colorScheme.onSurface
            )
        }
        }

        // Overlay Controls Panel
        AnimatedVisibility(
            visible = showOverlayControls && isAuthenticated,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            SkysightControlPanel(
                showSatellite = showSatellite,
                showRain = showRain,
                forecastTime = forecastTime,
                onSatelliteToggle = { enabled ->
                    showSatellite = enabled
                    skysightClient.setShowSatellite(enabled)
                    
                    // Add/remove satellite layer from map
                    android.util.Log.d("SkysightDebug", "🔍 mapLibreMap is null: ${mapLibreMap == null}")
                    mapLibreMap?.let { map ->
                        val authToken = skysightClient.getAuthToken()
                        val currentRegion = selectedRegion
                        android.util.Log.d("SkysightDebug", "🔍 Debug params - enabled: $enabled, authToken: ${authToken?.take(8)}..., currentRegion: $currentRegion")
                        if (enabled && authToken != null && currentRegion != null) {
                            android.util.Log.d("SkysightDebug", "🛰️ Adding satellite layer to map (PROGRAMMATIC)")
                            android.util.Log.d("SkysightDebug", "🔍 Launching coroutine for addSkysightLayerToMap")
                            try {
                                scope.launch {
                                    android.util.Log.d("SkysightDebug", "🔍 Inside coroutine, calling addSkysightLayerToMap")
                                    addSkysightLayerToMap(
                                        mapLibreMap = map,
                                        layerId = "satellite",
                                        layerType = "satellite",
                                        regionId = currentRegion,
                                        apiKey = authToken,
                                        skysightClient = skysightClient
                                    )
                                    android.util.Log.d("SkysightDebug", "🔍 addSkysightLayerToMap completed")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SkysightDebug", "❌ Error adding satellite layer", e)
                            }
                        } else if (!enabled) {
                            android.util.Log.d("SkysightDebug", "🛰️ Removing satellite layer from map")
                            try {
                                removeSkysightLayerFromMap(map, "satellite")
                            } catch (e: Exception) {
                                android.util.Log.e("SkysightDebug", "❌ Error removing satellite layer", e)
                            }
                        }
                    }
                },
                onRainToggle = { enabled ->
                    showRain = enabled
                    skysightClient.setShowRain(enabled)
                    
                    // Add/remove rain layer from map
                    mapLibreMap?.let { map ->
                        val authToken = skysightClient.getAuthToken()
                        val currentRegion = selectedRegion
                        if (enabled && authToken != null && currentRegion != null) {
                            android.util.Log.d("SkysightDebug", "🌧️ Adding rain layer to map (PROGRAMMATIC)")
                            try {
                                scope.launch {
                                    addSkysightLayerToMap(
                                        mapLibreMap = map,
                                        layerId = "rain",
                                        layerType = "rain",
                                        regionId = currentRegion,
                                        apiKey = authToken,
                                        skysightClient = skysightClient
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SkysightDebug", "❌ Error adding rain layer", e)
                            }
                        } else if (!enabled) {
                            android.util.Log.d("SkysightDebug", "🌧️ Removing rain layer from map")
                            try {
                                removeSkysightLayerFromMap(map, "rain")
                            } catch (e: Exception) {
                                android.util.Log.e("SkysightDebug", "❌ Error removing rain layer", e)
                            }
                        }
                    }
                },
                onTimeChange = { time ->
                    forecastTime = time
                },
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(16.dp)
            )
        }

    }
}

@Composable
fun SkysightControlPanel(
    showSatellite: Boolean,
    showRain: Boolean,
    forecastTime: Date,
    onSatelliteToggle: (Boolean) -> Unit,
    onRainToggle: (Boolean) -> Unit,
    onTimeChange: (Date) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weather Overlays",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider()

            // Satellite Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Satellite,
                    contentDescription = null,
                    tint = if (showSatellite) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Satellite",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = showSatellite,
                    onCheckedChange = onSatelliteToggle
                )
            }

            // Rain Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = if (showRain) Color.Blue 
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Rain Forecast",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = showRain,
                    onCheckedChange = onRainToggle
                )
            }

            HorizontalDivider()

            // Time Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Forecast Time",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    val timeFormat = SimpleDateFormat("HH:mm 'UTC'", Locale.US)
                    timeFormat.timeZone = TimeZone.getTimeZone("UTC")
                    Text(
                        text = timeFormat.format(forecastTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                var sliderValue by remember { mutableStateOf(0f) }
                
                Slider(
                    value = sliderValue,
                    onValueChange = { value ->
                        sliderValue = value
                        val hoursOffset = (value * 24).toInt()
                        val newTime = Date(System.currentTimeMillis() + hoursOffset * 3600000L)
                        onTimeChange(newTime)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+24h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SkysightQuickToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.Cloud else Icons.Default.CloudOff,
                contentDescription = "Toggle Skysight",
                tint = if (isEnabled) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

