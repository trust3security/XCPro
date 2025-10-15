package com.example.xcpro.skysight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.maplibre.android.maps.MapLibreMap

@Composable
fun SkysightTestControl(
    mapLibreMap: MapLibreMap?,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val skysightClient = remember { SkysightClient.getInstance(context) }

    val isAuthenticated by skysightClient.isAuthenticated.collectAsState()
    val selectedRegion by skysightClient.selectedRegion.collectAsState()

    var showTestPanel by remember { mutableStateOf(false) }
    var showLayerManagement by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Test FAB (positioned to not conflict with existing FAB)
        FloatingActionButton(
            onClick = {
                if (isAuthenticated) {
                    showTestPanel = !showTestPanel
                } else {
                    onNavigateToSettings()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 80.dp, bottom = 16.dp), // Offset from original FAB
            containerColor = MaterialTheme.colorScheme.tertiary
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = "Test SkySight UI",
                tint = MaterialTheme.colorScheme.onTertiary
            )
        }

        // Test panel
        if (showTestPanel && isAuthenticated) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp)
                    .width(280.dp),
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
                            text = "SkySight Test UI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = { showTestPanel = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close"
                            )
                        }
                    }

                    HorizontalDivider()

                    // Status
                    StatusSection(
                        isAuthenticated = isAuthenticated,
                        selectedRegion = selectedRegion
                    )

                    // Test buttons
                    TestButtonsSection(
                        onLayerManagementClick = {
                            android.util.Log.d("SkysightTest", "🎯🎯 LAYER MANAGEMENT CALLBACK TRIGGERED")
                            android.util.Log.d("SkysightTest", "🎯🎯 showTestPanel before: $showTestPanel")
                            android.util.Log.d("SkysightTest", "🎯🎯 showLayerManagement before: $showLayerManagement")
                            showTestPanel = false
                            showLayerManagement = true
                            android.util.Log.d("SkysightTest", "🎯🎯 showTestPanel after: $showTestPanel")
                            android.util.Log.d("SkysightTest", "🎯🎯 showLayerManagement after: $showLayerManagement")
                        }
                    )
                }
            }
        }

        // Enhanced Layer Management Panel
        if (showLayerManagement) {
            android.util.Log.d("SkysightTest", "🎯🎯🎯 RENDERING ENHANCED LAYER MANAGEMENT PANEL")
            EnhancedSkysightLayersPanel(
                mapLibreMap = mapLibreMap,
                onDismiss = {
                    android.util.Log.d("SkysightTest", "🎯🎯🎯 Enhanced Layer Management dismissed")
                    showLayerManagement = false
                }
            )
        }
    }
}

@Composable
fun StatusSection(
    isAuthenticated: Boolean,
    selectedRegion: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Status",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAuthenticated) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isAuthenticated) Color.Green else Color.Red,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAuthenticated) "Authenticated" else "Not authenticated",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (selectedRegion != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Region: $selectedRegion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TestButtonsSection(
    onLayerManagementClick: () -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Test Features",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // Test buttons for each major feature
        val testFeatures = listOf(
            "Layer Management" to Icons.Default.Layers,
            "Color Legends" to Icons.Default.Palette,
            "Time Controls" to Icons.Default.Schedule,
            "Data Downloads" to Icons.Default.CloudDownload,
            "Task Downloads" to Icons.Default.FlightTakeoff,
            "Server Status" to Icons.Default.Sensors
        )

        testFeatures.forEach { (name, icon) ->
            OutlinedButton(
                onClick = {
                    android.util.Log.d("SkysightTest", "🎯 Testing: $name")
                    when (name) {
                        "Layer Management" -> {
                            android.util.Log.d("SkysightTest", "🎯 Layer Management button clicked - calling callback")
                            onLayerManagementClick()
                            android.util.Log.d("SkysightTest", "🎯 Layer Management callback completed")
                        }
                        else -> {
                            android.util.Log.d("SkysightTest", "Feature not yet implemented: $name")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Test $name",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}