package com.trust3.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trust3.xcpro.glider.GliderViewModel

@Composable
fun DetailsCard() {
    val viewModel: GliderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val model = uiState.selectedModel

    val selectedModel = model ?: return

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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val detailLines = listOfNotNull(
                selectedModel.wingSpanM?.let { "Wingspan: ${"%.1f".format(it)} m" },
                selectedModel.wingAreaM2?.let { "Wing Area: ${"%.2f".format(it)} m^2" },
                selectedModel.aspectRatio?.let { "Aspect Ratio: ${"%.1f".format(it)}" },
                selectedModel.bestLD?.let { ld ->
                    val speed = selectedModel.bestLDSpeedKmh?.let { " at ${it.toInt()} km/h" } ?: ""
                    "Best L/D: ${"%.0f".format(ld)}:1$speed"
                },
                selectedModel.minSinkMs?.let { "Min Sink: ${"%.2f".format(it)} m/s" },
                selectedModel.speedLimits?.let { limits ->
                    listOfNotNull(
                        limits.vneKmh?.let { "VNE: $it km/h" },
                        limits.vraKmh?.let { "VRA: $it km/h" },
                        limits.vaKmh?.let { "VA: $it km/h" },
                        limits.vwKmh?.let { "VW: $it km/h" },
                        limits.vtKmh?.let { "VT: $it km/h" }
                    ).joinToString(" | ")
                }
            )
            detailLines.forEach { line ->
                if (line.isNotBlank()) {
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
