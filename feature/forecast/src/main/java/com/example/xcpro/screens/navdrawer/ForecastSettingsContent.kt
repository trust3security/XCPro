package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.xcpro.forecast.FORECAST_OPACITY_MAX
import com.example.xcpro.forecast.FORECAST_OPACITY_MIN
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_MAX
import com.example.xcpro.forecast.FORECAST_WIND_OVERLAY_SCALE_MIN
import com.example.xcpro.forecast.ForecastCredentialStorageMode
import com.example.xcpro.forecast.ForecastProviderCredentials
import com.example.xcpro.forecast.ForecastRegionOption
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.forecast.forecastRegionLabel
import kotlin.math.roundToInt

data class ForecastSettingsContentState(
    val opacity: Float,
    val windOverlayScale: Float,
    val windDisplayMode: ForecastWindDisplayMode,
    val windDisplayModes: List<ForecastWindDisplayMode>,
    val selectedRegion: String,
    val regionOptions: List<ForecastRegionOption>,
    val authConfirmation: String,
    val authReturnCode: Int?,
    val authChecking: Boolean,
    val savedCredentials: ForecastProviderCredentials?,
    val credentialsStatus: String,
    val credentialStorageMode: ForecastCredentialStorageMode,
    val volatileFallbackAllowed: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastSettingsContent(
    state: ForecastSettingsContentState,
    onOpacityCommitted: (Float) -> Unit,
    onWindDisplayModeSelected: (ForecastWindDisplayMode) -> Unit,
    onWindOverlayScaleCommitted: (Float) -> Unit,
    onSelectedRegion: (String) -> Unit,
    onVolatileFallbackAllowedChanged: (Boolean) -> Unit,
    onSaveCredentials: (String, String) -> Unit,
    onClearCredentials: () -> Unit,
    onVerifyCredentials: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember { mutableStateOf(state.opacity) }
    var windOverlayScaleSlider by remember { mutableStateOf(state.windOverlayScale) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var credentialValidationMessage by remember { mutableStateOf<String?>(null) }
    var regionMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.opacity) {
        sliderValue = state.opacity
    }
    LaunchedEffect(state.windOverlayScale) {
        windOverlayScaleSlider = state.windOverlayScale
    }
    LaunchedEffect(state.savedCredentials) {
        username = state.savedCredentials?.username.orEmpty()
        password = state.savedCredentials?.password.orEmpty()
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
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
                        sliderValue = value.coerceIn(FORECAST_OPACITY_MIN, FORECAST_OPACITY_MAX)
                    },
                    onValueChangeFinished = {
                        val clamped = sliderValue.coerceIn(FORECAST_OPACITY_MIN, FORECAST_OPACITY_MAX)
                        if (clamped != state.opacity) {
                            onOpacityCommitted(clamped)
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
                        items = state.windDisplayModes,
                        key = { mode -> mode.storageValue }
                    ) { mode ->
                        FilterChip(
                            selected = mode == state.windDisplayMode,
                            onClick = { onWindDisplayModeSelected(mode) },
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
                        if (clamped != state.windOverlayScale) {
                            onWindOverlayScaleCommitted(clamped)
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
                        Text(forecastRegionLabel(state.selectedRegion))
                    }
                    DropdownMenu(
                        expanded = regionMenuExpanded,
                        onDismissRequest = { regionMenuExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        state.regionOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSelectedRegion(option.code)
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
                if (state.credentialStorageMode == ForecastCredentialStorageMode.VOLATILE_MEMORY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Secure encrypted storage unavailable on this device; credentials are held in memory only and cleared on app restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                val shouldShowFallbackToggle =
                    state.credentialStorageMode != ForecastCredentialStorageMode.ENCRYPTED ||
                        state.volatileFallbackAllowed
                if (shouldShowFallbackToggle) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Allow memory-only fallback",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.volatileFallbackAllowed,
                            onCheckedChange = { enabled ->
                                credentialValidationMessage = null
                                onVolatileFallbackAllowedChanged(enabled)
                            }
                        )
                    }
                    Text(
                        text = "When enabled, credentials are stored in app memory and cleared on restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    text = credentialValidationMessage ?: state.credentialsStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val canPersistCredentials =
                        state.credentialStorageMode != ForecastCredentialStorageMode.ENCRYPTION_UNAVAILABLE ||
                            state.volatileFallbackAllowed
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                credentialValidationMessage = "Username and password are required"
                            } else {
                                credentialValidationMessage = null
                                onSaveCredentials(username, password)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = canPersistCredentials
                    ) {
                        Text("Save")
                    }
                    TextButton(
                        onClick = {
                            credentialValidationMessage = null
                            username = ""
                            password = ""
                            onClearCredentials()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onVerifyCredentials,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.credentialStorageMode != ForecastCredentialStorageMode.ENCRYPTION_UNAVAILABLE
                ) {
                    Text(if (state.authChecking) "Verifying..." else "Test Login")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Auth confirmation: ${state.authConfirmation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Return code: ${state.authReturnCode?.toString() ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
