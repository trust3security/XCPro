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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.DistanceUnit
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.SpeedUnit
import com.example.xcpro.common.units.TemperatureC
import com.example.xcpro.common.units.TemperatureUnit
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.UnitsRepository
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.common.units.VerticalSpeedUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsSettingsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        UnitsRepository(context.applicationContext)
    }
    val units by repository.unitsFlow.collectAsState(initial = UnitsPreferences())
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Units") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                example = UnitsFormatter.altitude(AltitudeM(1234.0), units).text,
                onSelected = { unit ->
                    scope.launch { repository.setAltitude(unit) }
                },
                label = { "${it.label} (${it.abbreviation})" }
            )

            UnitSelectionCard(
                title = "Vertical Speed",
                description = "Applies to variometer readings, audio cues, TE/Netto, and climb averages.",
                options = VerticalSpeedUnit.values().toList(),
                selected = units.verticalSpeed,
                example = UnitsFormatter.verticalSpeed(VerticalSpeedMs(2.3), units).text,
                onSelected = { unit ->
                    scope.launch { repository.setVerticalSpeed(unit) }
                },
                label = { "${it.label}" }
            )

            UnitSelectionCard(
                title = "Air/Ground Speed",
                description = "Displayed in navboxes, glide calculators, and profile summaries.",
                options = SpeedUnit.values().toList(),
                selected = units.speed,
                example = UnitsFormatter.speed(SpeedMs(27.8), units).text,
                onSelected = { unit ->
                    scope.launch { repository.setSpeed(unit) }
                },
                label = { "${it.label}" }
            )

            UnitSelectionCard(
                title = "Distance",
                description = "Used for leg lengths, task distances, and glides.",
                options = DistanceUnit.values().toList(),
                selected = units.distance,
                example = UnitsFormatter.distance(DistanceM(5000.0), units).text,
                onSelected = { unit ->
                    scope.launch { repository.setDistance(unit) }
                },
                label = { "${it.label}" }
            )

            UnitSelectionCard(
                title = "Pressure",
                description = "Impacts QNH entry and barometric readouts.",
                options = PressureUnit.values().toList(),
                selected = units.pressure,
                example = UnitsFormatter.pressure(PressureHpa(1013.25), units).text,
                onSelected = { unit ->
                    scope.launch { repository.setPressure(unit) }
                },
                label = { "${it.label}" }
            )

            UnitSelectionCard(
                title = "Temperature",
                description = "Used in weather overlays and card summaries.",
                options = TemperatureUnit.values().toList(),
                selected = units.temperature,
                example = UnitsFormatter.temperature(TemperatureC(18.0), units).text,
                onSelected = { unit ->
                    scope.launch { repository.setTemperature(unit) }
                },
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
    example: String,
    onSelected: (T) -> Unit,
    label: (T) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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

            Text(
                text = "Example: $example",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
