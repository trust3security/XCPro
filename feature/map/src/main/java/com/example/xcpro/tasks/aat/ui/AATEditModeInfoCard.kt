package com.example.xcpro.tasks.aat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint

@Composable
internal fun TargetPointInfoCard(
    waypoint: AATWaypoint,
    currentTargetPoint: AATLatLng,
    originalTargetPoint: AATLatLng
) {
    val distanceFromCenter = remember(currentTargetPoint, waypoint) {
        AATMathUtils.calculateDistanceMeters(
            waypoint.lat,
            waypoint.lon,
            currentTargetPoint.latitude,
            currentTargetPoint.longitude
        ) / 1000.0
    }

    val distanceMoved = remember(currentTargetPoint, originalTargetPoint) {
        AATMathUtils.calculateDistanceMeters(
            originalTargetPoint.latitude,
            originalTargetPoint.longitude,
            currentTargetPoint.latitude,
            currentTargetPoint.longitude
        ) / 1000.0
    }

    val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0
    val remainingDistance = areaRadiusKm - distanceFromCenter

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = " Target Point Position",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF388E3C)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lat: ${String.format("%.6f", currentTargetPoint.latitude)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Lon: ${String.format("%.6f", currentTargetPoint.longitude)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            InfoRow(
                label = "Distance from center:",
                value = "${String.format("%.2f", distanceFromCenter)} km",
                icon = Icons.Default.MyLocation
            )

            InfoRow(
                label = "Remaining area:",
                value = "${String.format("%.2f", remainingDistance)} km",
                icon = Icons.Default.RadioButtonUnchecked,
                valueColor = if (remainingDistance < 0.5) Color.Red else MaterialTheme.colorScheme.onSurface
            )

            if (distanceMoved > 0.01) {
                InfoRow(
                    label = "Moved from original:",
                    value = "${String.format("%.2f", distanceMoved)} km",
                    icon = Icons.Default.OpenWith,
                    valueColor = Color(0xFF388E3C)
                )
            }

            LinearProgressIndicator(
                progress = { (distanceFromCenter / areaRadiusKm).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF388E3C),
                trackColor = Color(0xFF388E3C).copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF388E3C)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}
