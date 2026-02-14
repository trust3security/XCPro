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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdsbSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val viewModel: AdsbSettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val iconSizePx by viewModel.iconSizePx.collectAsStateWithLifecycle()
    var sliderValue by remember { mutableStateOf(iconSizePx.toFloat()) }
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var credentialsStatus by remember { mutableStateOf("OpenSky credentials not set") }

    LaunchedEffect(iconSizePx) {
        sliderValue = iconSizePx.toFloat()
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
                            "Adjust the ADS-b aircraft icon size shown on the map.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ADS-b icon size: ${sliderValue.roundToInt()} px",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { value ->
                                val snapped = value.roundToInt().coerceIn(
                                    ADSB_ICON_SIZE_MIN_PX,
                                    ADSB_ICON_SIZE_MAX_PX
                                )
                                sliderValue = snapped.toFloat()
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
