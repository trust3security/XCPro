package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.ogn.OGN_ICON_SIZE_MAX_PX
import com.example.xcpro.ogn.OGN_ICON_SIZE_MIN_PX
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OgnSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val viewModel: OgnSettingsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val iconSizePx = uiState.iconSizePx
    var sliderValue by remember { mutableStateOf(iconSizePx.toFloat()) }

    LaunchedEffect(iconSizePx) {
        sliderValue = iconSizePx.toFloat()
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "OGN",
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
                        Text("OGN", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Adjust the OGN glider icon size shown on the map.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "OGN icon size: ${sliderValue.roundToInt()} px",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { value ->
                                val snapped = value.roundToInt().coerceIn(
                                    OGN_ICON_SIZE_MIN_PX,
                                    OGN_ICON_SIZE_MAX_PX
                                )
                                sliderValue = snapped.toFloat()
                                if (snapped != iconSizePx) {
                                    viewModel.setIconSizePx(snapped)
                                }
                            },
                            valueRange = OGN_ICON_SIZE_MIN_PX.toFloat()..OGN_ICON_SIZE_MAX_PX.toFloat(),
                            steps = OGN_ICON_SIZE_MAX_PX - OGN_ICON_SIZE_MIN_PX - 1
                        )
                        Text(
                            text = "Minimum ${OGN_ICON_SIZE_MIN_PX}px, maximum ${OGN_ICON_SIZE_MAX_PX}px.",
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
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Ownship OGN IDs", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Enter your own FLARM and ICAO24 hex IDs to suppress your own OGN marker, trails, and thermals.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.ownFlarmDraft,
                            onValueChange = viewModel::onOwnFlarmDraftChanged,
                            label = { Text("Own FLARM ID (6 hex)") },
                            singleLine = true,
                            isError = uiState.ownFlarmError != null,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.commitOwnFlarmDraft() }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text(
                                    text = uiState.ownFlarmError
                                        ?: "Example: DDA85C. Leave blank to disable FLARM self-filter."
                                )
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = viewModel::commitOwnFlarmDraft,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save FLARM")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.onOwnFlarmDraftChanged("")
                                    viewModel.commitOwnFlarmDraft()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.ownIcaoDraft,
                            onValueChange = viewModel::onOwnIcaoDraftChanged,
                            label = { Text("Own ICAO24 (6 hex)") },
                            singleLine = true,
                            isError = uiState.ownIcaoError != null,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.commitOwnIcaoDraft() }
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text(
                                    text = uiState.ownIcaoError
                                        ?: "Example: 4CA6A4. Leave blank to disable ICAO self-filter."
                                )
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = viewModel::commitOwnIcaoDraft,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save ICAO")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.onOwnIcaoDraftChanged("")
                                    viewModel.commitOwnIcaoDraft()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }
    }
}
