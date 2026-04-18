package com.example.ui1.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

@Composable
fun AirspaceClassCard(
    airspaceClass: AirspaceClassItem,
    onToggle: (String) -> Unit
) {
    Surface(
        onClick = { onToggle(airspaceClass.className) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (airspaceClass.enabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = Color(airspaceClass.color.toColorInt())
                            .copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Class ${airspaceClass.className}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = airspaceClass.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.material3.Icon(
                imageVector = if (airspaceClass.enabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (airspaceClass.enabled) "Hide class ${airspaceClass.className}" else "Show class ${airspaceClass.className}",
                modifier = Modifier.size(20.dp),
                tint = if (airspaceClass.enabled) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
