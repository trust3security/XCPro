package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.MapOrientationPreferences
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.map.domain.MapShiftBiasMode
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Dedicated Orientation settings screen accessed from General settings.
 * Mirrors the Navboxes top bar (back arrow, drawer shortcut, map icon) per UX request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrientationSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    orientationManager: MapOrientationManager
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val preferences = remember { MapOrientationPreferences(context) }
    var cruiseMode by remember { mutableStateOf(preferences.getCruiseOrientationMode()) }
    var circlingMode by remember { mutableStateOf(preferences.getCirclingOrientationMode()) }
    var gliderOffsetPercent by remember { mutableStateOf(preferences.getGliderScreenPercent()) }
    var biasMode by remember { mutableStateOf(preferences.getMapShiftBiasMode()) }
    var biasStrength by remember { mutableStateOf(preferences.getMapShiftBiasStrength()) }

    fun persistAndRefresh() {
        orientationManager.reloadFromPreferences()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SettingsTopAppBar(
                title = "Orientation",
                onNavigateUp = { navController.navigateUp() },
                onSecondaryNavigate = {
                    scope.launch {
                        navController.popBackStack("map", inclusive = false)
                        drawerState.open()
                    }
                },
                onNavigateToMap = {
                    scope.launch {
                        drawerState.close()
                        navController.popBackStack("map", inclusive = false)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OrientationModeCard(
                    title = "Cruise / Final Glide",
                    description = "Applies when flying straight, final glide, or navigating menus.",
                    selectedMode = cruiseMode,
                    onModeSelected = {
                        cruiseMode = it
                        preferences.setCruiseOrientationMode(it)
                        persistAndRefresh()
                    }
                )
            }
            item {
                OrientationModeCard(
                    title = "Thermal / Circling",
                    description = "Used while thermalling or whenever flight mode switches to Thermal.",
                    selectedMode = circlingMode,
                    onModeSelected = {
                        circlingMode = it
                        preferences.setCirclingOrientationMode(it)
                        persistAndRefresh()
                    }
                )
            }
            item {
                GliderPositionCard(
                    percentFromBottom = gliderOffsetPercent,
                    onPercentChanged = {
                        gliderOffsetPercent = it
                        preferences.setGliderScreenPercent(it)
                    }
                )
            }
            item {
                MapShiftBiasCard(
                    mode = biasMode,
                    strength = biasStrength,
                    onModeChanged = {
                        biasMode = it
                        preferences.setMapShiftBiasMode(it)
                    },
                    onStrengthChanged = {
                        biasStrength = it
                        preferences.setMapShiftBiasStrength(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun OrientationModeCard(
    title: String,
    description: String,
    selectedMode: MapOrientationMode,
    onModeSelected: (MapOrientationMode) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            listOf(
                MapOrientationMode.NORTH_UP,
                MapOrientationMode.TRACK_UP,
                MapOrientationMode.HEADING_UP
            ).forEach { mode ->
                    OrientationModeRow(
                        title = when (mode) {
                            MapOrientationMode.NORTH_UP -> "North Up"
                            MapOrientationMode.TRACK_UP -> "Track Up"
                            MapOrientationMode.HEADING_UP -> "Heading Up"
                        },
                        description = when (mode) {
                            MapOrientationMode.NORTH_UP -> "Never rotate the map."
                            MapOrientationMode.TRACK_UP -> "Rotate map to match GPS course."
                            MapOrientationMode.HEADING_UP -> "Rotate map to match sensor heading."
                        },
                        selected = selectedMode == mode,
                        onSelect = { onModeSelected(mode) }
                    )
                }
        }
    }
}

@Composable
private fun GliderPositionCard(
    percentFromBottom: Int,
    onPercentChanged: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Glider vertical position",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val percentFromTop = 100 - percentFromBottom
            Text(
                text = "Offsets the aircraft icon while auto-centering. " +
                    "$percentFromBottom% from bottom - $percentFromTop% from top.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Slider(
                value = percentFromBottom.toFloat(),
                onValueChange = {
                    val snapped = it.roundToInt().coerceIn(10, 50)
                    if (snapped != percentFromBottom) {
                        onPercentChanged(snapped)
                    }
                },
                valueRange = 10f..50f
            )
        }
    }
}

@Composable
private fun MapShiftBiasCard(
    mode: MapShiftBiasMode,
    strength: Double,
    onModeChanged: (MapShiftBiasMode) -> Unit,
    onStrengthChanged: (Double) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Directional look-ahead",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Shifts the map forward in North Up to show more ahead. " +
                    "Disabled in Thermal/Circling and when Track Up or Heading Up is selected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            listOf(
                MapShiftBiasMode.NONE to "Off",
                MapShiftBiasMode.TRACK to "Track"
            ).forEach { (itemMode, label) ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mode == itemMode, onClick = { onModeChanged(itemMode) })
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val strengthPercent = (strength * 100.0).roundToInt().coerceIn(0, 100)
            Text(
                text = "Strength: $strengthPercent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Slider(
                value = strengthPercent.toFloat(),
                onValueChange = {
                    val snapped = it.roundToInt().coerceIn(0, 100)
                    val newStrength = snapped / 100.0
                    if (newStrength != strength) {
                        onStrengthChanged(newStrength)
                    }
                },
                valueRange = 0f..100f,
                enabled = mode != MapShiftBiasMode.NONE
            )
        }
    }
}

@Composable
private fun OrientationModeRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
