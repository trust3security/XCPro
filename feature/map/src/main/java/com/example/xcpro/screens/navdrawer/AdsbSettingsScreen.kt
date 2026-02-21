package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.adsb.ADSB_ICON_SIZE_MAX_PX
import com.example.xcpro.adsb.ADSB_ICON_SIZE_MIN_PX
import com.example.xcpro.adsb.ADSB_MAX_DISTANCE_MAX_KM
import com.example.xcpro.adsb.ADSB_MAX_DISTANCE_MIN_KM
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_MAX_METERS
import com.example.xcpro.adsb.ADSB_VERTICAL_FILTER_MIN_METERS
import com.example.xcpro.adsb.clampAdsbVerticalFilterMeters
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsConverter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val VERTICAL_STEP_FEET = 100f
private const val VERTICAL_STEP_METERS = 50f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdsbSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val viewModel: AdsbSettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val iconSizePx by viewModel.iconSizePx.collectAsStateWithLifecycle()
    val maxDistanceKm by viewModel.maxDistanceKm.collectAsStateWithLifecycle()
    val verticalAboveMeters by viewModel.verticalAboveMeters.collectAsStateWithLifecycle()
    val verticalBelowMeters by viewModel.verticalBelowMeters.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val altitudeUnit = units.altitude

    var iconSliderValue by remember { mutableStateOf(iconSizePx.toFloat()) }
    var distanceSliderValue by remember { mutableStateOf(maxDistanceKm.toFloat()) }
    var aboveSliderDisplayValue by remember {
        mutableStateOf(verticalDisplayValue(verticalAboveMeters, altitudeUnit))
    }
    var belowSliderDisplayValue by remember {
        mutableStateOf(verticalDisplayValue(verticalBelowMeters, altitudeUnit))
    }

    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var credentialsStatus by remember { mutableStateOf("OpenSky credentials not set") }

    LaunchedEffect(iconSizePx) {
        iconSliderValue = iconSizePx.toFloat()
    }
    LaunchedEffect(maxDistanceKm) {
        distanceSliderValue = maxDistanceKm.toFloat()
    }
    LaunchedEffect(verticalAboveMeters, altitudeUnit) {
        aboveSliderDisplayValue = verticalDisplayValue(verticalAboveMeters, altitudeUnit)
    }
    LaunchedEffect(verticalBelowMeters, altitudeUnit) {
        belowSliderDisplayValue = verticalDisplayValue(verticalBelowMeters, altitudeUnit)
    }

    LaunchedEffect(Unit) {
        val credentials = viewModel.loadOpenSkyCredentials()
        clientId = credentials?.clientId.orEmpty()
        clientSecret = credentials?.clientSecret.orEmpty()
        credentialsStatus = if (credentials == null) {
            "OpenSky credentials not set"
        } else {
            "OpenSky credentials saved"
        }
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "ADS-b",
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
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
                        Text("ADS-b", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Configure display distance and vertical traffic filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Horizontal max distance: ${distanceSliderValue.roundToInt()} km",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = distanceSliderValue,
                            onValueChange = { value ->
                                distanceSliderValue = value.roundToInt()
                                    .coerceIn(ADSB_MAX_DISTANCE_MIN_KM, ADSB_MAX_DISTANCE_MAX_KM)
                                    .toFloat()
                            },
                            onValueChangeFinished = {
                                val snapped = distanceSliderValue.roundToInt()
                                    .coerceIn(ADSB_MAX_DISTANCE_MIN_KM, ADSB_MAX_DISTANCE_MAX_KM)
                                if (snapped != maxDistanceKm) {
                                    viewModel.setMaxDistanceKm(snapped)
                                }
                            },
                            valueRange = ADSB_MAX_DISTANCE_MIN_KM.toFloat()..ADSB_MAX_DISTANCE_MAX_KM.toFloat(),
                            steps = ADSB_MAX_DISTANCE_MAX_KM - ADSB_MAX_DISTANCE_MIN_KM - 1
                        )
                        Text(
                            text = "Minimum ${ADSB_MAX_DISTANCE_MIN_KM}km, maximum ${ADSB_MAX_DISTANCE_MAX_KM}km.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Above ownship: ${verticalLabel(aboveSliderDisplayValue, altitudeUnit)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = aboveSliderDisplayValue,
                            onValueChange = { value ->
                                aboveSliderDisplayValue = snapVerticalSliderValue(value, altitudeUnit)
                            },
                            onValueChangeFinished = {
                                val meters = clampAdsbVerticalFilterMeters(
                                    verticalMetersFromDisplay(aboveSliderDisplayValue, altitudeUnit)
                                )
                                if (abs(meters - verticalAboveMeters) > 1e-3) {
                                    viewModel.setVerticalAboveMeters(meters)
                                }
                            },
                            valueRange = verticalSliderRange(altitudeUnit),
                            steps = verticalSliderSteps(altitudeUnit)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Below ownship: ${verticalLabel(belowSliderDisplayValue, altitudeUnit)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = belowSliderDisplayValue,
                            onValueChange = { value ->
                                belowSliderDisplayValue = snapVerticalSliderValue(value, altitudeUnit)
                            },
                            onValueChangeFinished = {
                                val meters = clampAdsbVerticalFilterMeters(
                                    verticalMetersFromDisplay(belowSliderDisplayValue, altitudeUnit)
                                )
                                if (abs(meters - verticalBelowMeters) > 1e-3) {
                                    viewModel.setVerticalBelowMeters(meters)
                                }
                            },
                            valueRange = verticalSliderRange(altitudeUnit),
                            steps = verticalSliderSteps(altitudeUnit)
                        )
                        Text(
                            text = "Vertical limits follow General -> Units altitude setting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "ADS-b icon size: ${iconSliderValue.roundToInt()} px",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = iconSliderValue,
                            onValueChange = { value ->
                                val snapped = value.roundToInt().coerceIn(
                                    ADSB_ICON_SIZE_MIN_PX,
                                    ADSB_ICON_SIZE_MAX_PX
                                )
                                iconSliderValue = snapped.toFloat()
                            },
                            onValueChangeFinished = {
                                val snapped = iconSliderValue.roundToInt().coerceIn(
                                    ADSB_ICON_SIZE_MIN_PX,
                                    ADSB_ICON_SIZE_MAX_PX
                                )
                                if (snapped != iconSizePx) {
                                    viewModel.setIconSizePx(snapped)
                                }
                            },
                            valueRange = ADSB_ICON_SIZE_MIN_PX.toFloat()..ADSB_ICON_SIZE_MAX_PX.toFloat(),
                            steps = ADSB_ICON_SIZE_MAX_PX - ADSB_ICON_SIZE_MIN_PX - 1
                        )
                        Text(
                            text = "Minimum ${ADSB_ICON_SIZE_MIN_PX}px, maximum ${ADSB_ICON_SIZE_MAX_PX}px.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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
                        Text("OpenSky Credentials", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Use your OpenSky API credentials to avoid anonymous rate-limit backoff.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = clientSecret,
                            onValueChange = { clientSecret = it },
                            label = { Text("Client Secret") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = credentialsStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val trimmedId = clientId.trim()
                                    val trimmedSecret = clientSecret.trim()
                                    if (trimmedId.isBlank() || trimmedSecret.isBlank()) {
                                        credentialsStatus = "Client ID and secret are required"
                                    } else {
                                        viewModel.saveOpenSkyCredentials(
                                            clientId = trimmedId,
                                            clientSecret = trimmedSecret
                                        )
                                        credentialsStatus = "OpenSky credentials saved. ADS-b reconnect requested."
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Credentials")
                            }
                            TextButton(
                                onClick = {
                                    clientId = ""
                                    clientSecret = ""
                                    viewModel.clearOpenSkyCredentials()
                                    credentialsStatus = "OpenSky credentials cleared. ADS-b reconnect requested."
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun verticalSliderRange(unit: AltitudeUnit): ClosedFloatingPointRange<Float> =
    when (unit) {
        AltitudeUnit.FEET -> 0f..UnitsConverter.metersToFeet(ADSB_VERTICAL_FILTER_MAX_METERS).toFloat()
        AltitudeUnit.METERS -> ADSB_VERTICAL_FILTER_MIN_METERS.toFloat()..ADSB_VERTICAL_FILTER_MAX_METERS.toFloat()
    }

private fun verticalSliderSteps(unit: AltitudeUnit): Int {
    val step = verticalStepValue(unit)
    val range = verticalSliderRange(unit)
    return (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
}

private fun verticalStepValue(unit: AltitudeUnit): Float = when (unit) {
    AltitudeUnit.FEET -> VERTICAL_STEP_FEET
    AltitudeUnit.METERS -> VERTICAL_STEP_METERS
}

private fun snapVerticalSliderValue(value: Float, unit: AltitudeUnit): Float {
    val range = verticalSliderRange(unit)
    val step = verticalStepValue(unit)
    val snapped = (value / step).roundToInt() * step
    return snapped.coerceIn(range.start, range.endInclusive)
}

private fun verticalMetersFromDisplay(displayValue: Float, unit: AltitudeUnit): Double = when (unit) {
    AltitudeUnit.FEET -> UnitsConverter.feetToMeters(displayValue.toDouble())
    AltitudeUnit.METERS -> displayValue.toDouble()
}

private fun verticalDisplayValue(meters: Double, unit: AltitudeUnit): Float = when (unit) {
    AltitudeUnit.FEET -> UnitsConverter.metersToFeet(meters).toFloat()
    AltitudeUnit.METERS -> meters.toFloat()
}

private fun verticalLabel(displayValue: Float, unit: AltitudeUnit): String = when (unit) {
    AltitudeUnit.FEET -> "${displayValue.roundToInt()} ft"
    AltitudeUnit.METERS -> "${displayValue.roundToInt()} m"
}
