package com.example.xcpro.screens.navdrawer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.DistanceUnit
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.SpeedUnit
import com.example.xcpro.common.units.TemperatureUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedUnit
import com.example.xcpro.profiles.ProfileIdResolver
import com.example.xcpro.profiles.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsSettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onNavigateUp: (() -> Unit)? = null,
    onSecondaryNavigate: (() -> Unit)? = null,
    onNavigateToMap: (() -> Unit)? = null
) {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val viewModel: UnitsSettingsViewModel = hiltViewModel()
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val profileId = ProfileIdResolver.canonicalOrDefault(profileUiState.activeProfile?.id)
    LaunchedEffect(profileId) {
        viewModel.setProfileId(profileId)
    }
    val units by viewModel.units.collectAsStateWithLifecycle(initialValue = UnitsPreferences())
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val navigateUpAction: () -> Unit = onNavigateUp ?: {
        navController.navigateUp()
        Unit
    }
    val secondaryNavigateAction: () -> Unit = onSecondaryNavigate ?: {
        scope.launch {
            navController.popBackStack("map", inclusive = false)
            drawerState.open()
        }
        Unit
    }
    val navigateToMapAction: () -> Unit = onNavigateToMap ?: {
        scope.launch {
            drawerState.close()
            navController.popBackStack("map", inclusive = false)
        }
        Unit
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = "Units",
                onNavigateUp = navigateUpAction,
                onSecondaryNavigate = secondaryNavigateAction,
                onNavigateToMap = navigateToMapAction
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Choose how flight data is displayed across navboxes, cards, audio prompts, and map overlays. Internal calculations remain in SI units for accuracy.",
                style = MaterialTheme.typography.bodyMedium
            )

            UnitSelectionCard(
                title = "Altitude",
                description = "Used for barometric altitude, AGL, and task heights.",
                options = AltitudeUnit.values().toList(),
                selected = units.altitude,
                onSelected = { unit -> viewModel.setAltitude(unit) },
                label = { "${it.label} (${it.abbreviation})" }
            )

            UnitSelectionCard(
                title = "Vertical Speed",
                description = "Applies to variometer readings, audio cues, TE/Netto, and climb averages.",
                options = VerticalSpeedUnit.values().toList(),
                selected = units.verticalSpeed,
                onSelected = { unit -> viewModel.setVerticalSpeed(unit) },
                label = { "${it.label}" }
            )

            UnitSelectionCard(
                title = "Air/Ground Speed",
                description = "Displayed in navboxes, glide calculators, and profile summaries.",
                options = SpeedUnit.values().toList(),
                selected = units.speed,
                onSelected = { unit -> viewModel.setSpeed(unit) },
                label = { "${it.label}" }
            )

            UnitSelectionCard(
                title = "Distance",
                description = "Used for leg lengths, task distances, and glides.",
                options = DistanceUnit.values().toList(),
                selected = units.distance,
                onSelected = { unit -> viewModel.setDistance(unit) },
                label = { "${it.label}" }
            )

            UnitSelectionCard(
                title = "Pressure",
                description = "Impacts QNH entry and barometric readouts.",
                options = PressureUnit.values().toList(),
                selected = units.pressure,
                onSelected = { unit -> viewModel.setPressure(unit) },
                label = { "${it.label}" }
            )

            UnitSelectionCard(
                title = "Temperature",
                description = "Used in weather overlays and card summaries.",
                options = TemperatureUnit.values().toList(),
                selected = units.temperature,
                onSelected = { unit -> viewModel.setTemperature(unit) },
                label = { "${it.label}" }
            )

            Spacer(modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun <T> UnitSelectionCard(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelected(option) },
                        label = { Text(label(option)) }
                    )
                }
            }
        }
    }
}
