package com.example.xcpro.screens.navdrawer

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.glider.ThreePointPolar
import com.example.xcpro.glider.GliderRepository

@Composable
fun ThreePointPolarCard() {
    val context = LocalContext.current
    val repo = remember(context) { GliderRepository.getInstance(context) }
    val cfg by repo.config.collectAsState()
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
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(lowKmh = it)) }
                    },
                    label = { Text("Low Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.lowSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(lowSinkMs = it)) }
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
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(midKmh = it)) }
                    },
                    label = { Text("Mid Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.midSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(midSinkMs = it)) }
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
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(highKmh = it)) }
                    },
                    label = { Text("High Speed (km/h)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpp.highSinkMs.toString(),
                    onValueChange = { value ->
                        value.toDoubleOrNull()?.let { repo.setThreePointPolar(tpp.copy(highSinkMs = it)) }
                    },
                    label = { Text("High Sink (m/s)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}
