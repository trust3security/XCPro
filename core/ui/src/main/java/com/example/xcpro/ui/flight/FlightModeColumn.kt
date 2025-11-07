package com.example.xcpro.ui.flight

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.flight.FlightMode

/**
 * Simple column that renders the current flight mode header.
 * Lives in core/ui so multiple features can reuse the same widget.
 */
@Composable
fun FlightModeColumn(mode: FlightMode, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(12.dp)
    ) {
        Text(
            text = mode.displayName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
