package com.example.xcpro.map.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import com.example.xcpro.map.OgnTrafficSnapshot

@Composable
fun OgnDebugPanel(
    visible: Boolean,
    snapshot: OgnTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible || shouldHideOgnDebugPanelWhileConnecting(snapshot)) return
    Surface(
        modifier = modifier,
        color = Color(0xCC111827),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = "OGN ${snapshot.connectionState.toDebugLabel()}",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Targets: ${snapshot.targets.size}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Suppressed: ${snapshot.suppressedTargetIds.size}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Radius: ${snapshot.receiveRadiusKm} km",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "DDB age: ${formatAge(snapshot.ddbCacheAgeMs)}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Backoff: ${formatBackoff(snapshot.reconnectBackoffMs)}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Drops (order/motion): ${snapshot.droppedOutOfOrderSourceFrames}/${snapshot.droppedImplausibleMotionFrames}",
                color = Color(0xFFD1D5DB),
                style = MaterialTheme.typography.bodySmall
            )
            snapshot.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = "Error: $error",
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
