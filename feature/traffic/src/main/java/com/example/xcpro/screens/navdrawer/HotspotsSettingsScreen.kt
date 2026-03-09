package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.ogn.OGN_HOTSPOTS_DISPLAY_PERCENT_MAX
import com.example.xcpro.ogn.OGN_HOTSPOTS_DISPLAY_PERCENT_MIN
import com.example.xcpro.ogn.OGN_THERMAL_RETENTION_ALL_DAY_HOURS
import com.example.xcpro.ogn.OGN_THERMAL_RETENTION_MIN_HOURS
import com.example.xcpro.ogn.clampOgnHotspotsDisplayPercent
import com.example.xcpro.ogn.clampOgnThermalRetentionHours
import com.example.xcpro.ogn.isOgnThermalRetentionAllDay
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotsSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val viewModel: HotspotsSettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = { navController.navigateUp() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            TrafficSettingsTopAppBar(
                title = "Hotspots",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                HotspotsSettingsContent(
                    uiState = uiState,
                    onSetRetentionHours = viewModel::setRetentionHours,
                    onSetDisplayPercent = viewModel::setDisplayPercent
                )
            }
        }
    }
}

@Composable
internal fun HotspotsSettingsContent(
    uiState: HotspotsSettingsUiState,
    onSetRetentionHours: (Int) -> Unit,
    onSetDisplayPercent: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val retentionHours = uiState.retentionHours
    val displayPercent = uiState.displayPercent

    var retentionSliderValue by remember { mutableFloatStateOf(retentionHours.toFloat()) }
    var displayPercentSliderValue by remember { mutableFloatStateOf(displayPercent.toFloat()) }

    LaunchedEffect(retentionHours, displayPercent) {
        retentionSliderValue = retentionHours.toFloat()
        displayPercentSliderValue = displayPercent.toFloat()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hotspots", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose how long thermal hotspots remain on the map.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Visibility window: ${retentionLabel(retentionSliderValue.roundToInt())}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = retentionSliderValue,
                    onValueChange = { value ->
                        retentionSliderValue = clampOgnThermalRetentionHours(value.roundToInt()).toFloat()
                    },
                    onValueChangeFinished = {
                        val snapped = clampOgnThermalRetentionHours(retentionSliderValue.roundToInt())
                        if (snapped != retentionHours) {
                            onSetRetentionHours(snapped)
                        }
                    },
                    valueRange = OGN_THERMAL_RETENTION_MIN_HOURS.toFloat()..OGN_THERMAL_RETENTION_ALL_DAY_HOURS.toFloat(),
                    steps = OGN_THERMAL_RETENTION_ALL_DAY_HOURS - OGN_THERMAL_RETENTION_MIN_HOURS - 1
                )
                Text(
                    text = "1 hour removes hotspots older than 1 hour. All day keeps hotspots until local midnight (12:00 AM).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Hotspots shown: ${displayPercentSliderValue.roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = displayPercentSliderValue,
                    onValueChange = { value ->
                        displayPercentSliderValue = clampOgnHotspotsDisplayPercent(value.roundToInt()).toFloat()
                    },
                    onValueChangeFinished = {
                        val snapped = clampOgnHotspotsDisplayPercent(displayPercentSliderValue.roundToInt())
                        if (snapped != displayPercent) {
                            onSetDisplayPercent(snapped)
                        }
                    },
                    valueRange = OGN_HOTSPOTS_DISPLAY_PERCENT_MIN.toFloat()..OGN_HOTSPOTS_DISPLAY_PERCENT_MAX.toFloat(),
                    steps = OGN_HOTSPOTS_DISPLAY_PERCENT_MAX - OGN_HOTSPOTS_DISPLAY_PERCENT_MIN - 1
                )
                Text(
                    text = "Lower percentages keep only the strongest climbs. Example: 5% shows only the top 5% strongest hotspots.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun retentionLabel(hours: Int): String {
    val clamped = clampOgnThermalRetentionHours(hours)
    return if (isOgnThermalRetentionAllDay(clamped)) {
        "All day (until 12:00 AM)"
    } else if (clamped == 1) {
        "1 hour"
    } else {
        "$clamped hours"
    }
}
