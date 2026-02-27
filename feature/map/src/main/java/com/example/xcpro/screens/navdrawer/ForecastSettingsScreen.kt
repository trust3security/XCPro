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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.example.xcpro.forecast.FORECAST_OPACITY_MAX
import com.example.xcpro.forecast.FORECAST_OPACITY_MIN
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_MAX
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_MIN
import com.example.xcpro.forecast.forecastRegionLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val viewModel: ForecastSettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val opacity by viewModel.opacity.collectAsStateWithLifecycle()
    val windOverlayScale by viewModel.windOverlayScale.collectAsStateWithLifecycle()
    val windDisplayMode by viewModel.windDisplayMode.collectAsStateWithLifecycle()
    val selectedRegion by viewModel.selectedRegion.collectAsStateWithLifecycle()
    val authConfirmation by viewModel.authConfirmation.collectAsStateWithLifecycle()
    val authReturnCode by viewModel.authReturnCode.collectAsStateWithLifecycle()
    val authChecking by viewModel.authChecking.collectAsStateWithLifecycle()
    var sliderValue by remember { mutableStateOf(opacity) }
    var windOverlayScaleSlider by remember { mutableStateOf(windOverlayScale) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var credentialsStatus by remember { mutableStateOf("Credentials not set") }
    var regionMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(opacity) {
        sliderValue = opacity
    }
    LaunchedEffect(windOverlayScale) {
        windOverlayScaleSlider = windOverlayScale
    }
    LaunchedEffect(Unit) {
        val credentials = viewModel.loadCredentials()
        username = credentials?.username.orEmpty()
        password = credentials?.password.orEmpty()
        credentialsStatus = if (credentials == null) {
            "Credentials not set"
        } else {
            "Credentials saved"
        }
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "SkySight",
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
                        Text("SkySight", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tune overlay display behavior used by SkySight on the map.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Opacity: ${(sliderValue * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { value ->
                                val clamped = value.coerceIn(FORECAST_OPACITY_MIN, FORECAST_OPACITY_MAX)
                                sliderValue = clamped
                            },
                            onValueChangeFinished = {
                                val clamped = sliderValue.coerceIn(FORECAST_OPACITY_MIN, FORECAST_OPACITY_MAX)
                                if (clamped != opacity) {
                                    viewModel.setOpacity(clamped)
                                }
                            },
                            valueRange = FORECAST_OPACITY_MIN..FORECAST_OPACITY_MAX
                        )
                        Text(
                            text = "Minimum 0%, maximum 100%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Wind display",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = viewModel.windDisplayModes,
                                key = { mode -> mode.storageValue }
                            ) { mode ->
                                FilterChip(
                                    selected = mode == windDisplayMode,
                                    onClick = { viewModel.setWindDisplayMode(mode) },
                                    label = { Text(mode.label) },
                                    enabled = true
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Wind marker size ${(windOverlayScaleSlider * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = windOverlayScaleSlider,
                            onValueChange = { value ->
                                windOverlayScaleSlider = value.coerceIn(
                                    FORECAST_WIND_OVERLAY_SCALE_MIN,
                                    FORECAST_WIND_OVERLAY_SCALE_MAX
                                )
                            },
                            onValueChangeFinished = {
                                val clamped = windOverlayScaleSlider.coerceIn(
                                    FORECAST_WIND_OVERLAY_SCALE_MIN,
                                    FORECAST_WIND_OVERLAY_SCALE_MAX
                                )
                                if (clamped != windOverlayScale) {
                                    viewModel.setWindOverlayScale(clamped)
                                }
                            },
                            valueRange = FORECAST_WIND_OVERLAY_SCALE_MIN..FORECAST_WIND_OVERLAY_SCALE_MAX
                        )
                        Text(
                            text = "Used by wind parameters in SkySight overlays.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SkySight region",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Box {
                            Button(
                                onClick = { regionMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(forecastRegionLabel(selectedRegion))
                            }
                            DropdownMenu(
                                expanded = regionMenuExpanded,
                                onDismissRequest = { regionMenuExpanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                viewModel.regionOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            viewModel.setSelectedRegion(option.code)
                                            regionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Used for provider region-targeted SkySight requests.",
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
                        Text("Provider Credentials", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Store credentials securely for provider authentication.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
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
                                    val trimmedUsername = username.trim()
                                    val trimmedPassword = password.trim()
                                    if (trimmedUsername.isBlank() || trimmedPassword.isBlank()) {
                                        credentialsStatus = "Username and password are required"
                                    } else {
                                        viewModel.saveCredentials(
                                            username = trimmedUsername,
                                            password = trimmedPassword
                                        )
                                        credentialsStatus = "Credentials saved"
                                        viewModel.verifyCredentials()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save")
                            }
                            TextButton(
                                onClick = {
                                    username = ""
                                    password = ""
                                    viewModel.clearCredentials()
                                    credentialsStatus = "Credentials cleared"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = viewModel::verifyCredentials,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (authChecking) "Verifying..." else "Test Login")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Auth confirmation: $authConfirmation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Return code: ${authReturnCode?.toString() ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
