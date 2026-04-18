package com.trust3.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.common.glider.ThreePointPolar
import com.trust3.xcpro.common.units.UnitsConverter
import com.trust3.xcpro.glider.GliderViewModel

@Composable
fun ThreePointPolarCard() {
    val viewModel: GliderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cfg = uiState.config
    val tpp = cfg.threePointPolar ?: ThreePointPolar()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LX/Hawk 3-Point Polar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.lowKmh.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { speedKmh ->
                            viewModel.setThreePointPolar(tpp.copy(lowMs = UnitsConverter.kmhToMs(speedKmh)))
                        }
                    },
                    label = { Text("Low Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.lowSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { viewModel.setThreePointPolar(tpp.copy(lowSinkMs = it)) }
                    },
                    label = { Text("Low Sink (m/s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.midKmh.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { speedKmh ->
                            viewModel.setThreePointPolar(tpp.copy(midMs = UnitsConverter.kmhToMs(speedKmh)))
                        }
                    },
                    label = { Text("Mid Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.midSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { viewModel.setThreePointPolar(tpp.copy(midSinkMs = it)) }
                    },
                    label = { Text("Mid Sink (m/s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tpp.highKmh.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { speedKmh ->
                            viewModel.setThreePointPolar(tpp.copy(highMs = UnitsConverter.kmhToMs(speedKmh)))
                        }
                    },
                    label = { Text("High Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.highSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { viewModel.setThreePointPolar(tpp.copy(highSinkMs = it)) }
                    },
                    label = { Text("High Sink (m/s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}
