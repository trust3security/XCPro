package com.example.xcpro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


enum class FlightMode(val number: Int, val displayName: String) {
    CRUISE(1, "Cruise"),
    THERMAL(2, "Thermal"),
    FINAL_GLIDE(3, "Final Glide")
}

@Composable
fun FlightModeColumn(mode: FlightMode, modifier: Modifier = Modifier) {
    val columnCount = when (mode) {
        FlightMode.CRUISE -> 1
        FlightMode.THERMAL -> 2
        FlightMode.FINAL_GLIDE -> 3
        else -> 1
    }

    Column(
        modifier = modifier
            .padding(12.dp)
        //.border(2.dp, Color.Blue) // Blue border for Column
    ) {
        Text(
            text = "${mode.displayName}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}