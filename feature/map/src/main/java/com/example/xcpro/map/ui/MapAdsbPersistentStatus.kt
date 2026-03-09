package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.xcpro.map.AdsbAuthMode
import com.example.xcpro.map.AdsbConnectionState
import com.example.xcpro.map.AdsbTrafficSnapshot
import com.example.xcpro.map.isBackingOff
import com.example.xcpro.map.isError

@Composable
internal fun AdsbPersistentStatusBadge(
    visible: Boolean,
    snapshot: AdsbTrafficSnapshot,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val (title, body, background) = persistentAdsbStatusPresentation(snapshot)
    Surface(
        modifier = modifier.testTag(ADSB_PERSISTENT_STATUS_BADGE_TAG),
        color = background,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.testTag(ADSB_PERSISTENT_STATUS_TITLE_TAG),
                color = Color(0xFFF9FAFB),
                style = MaterialTheme.typography.labelMedium
            )
            body?.let {
                Text(
                    text = it,
                    color = Color(0xFFE5E7EB),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

internal fun shouldSurfacePersistentAdsbStatus(snapshot: AdsbTrafficSnapshot): Boolean =
    snapshot.connectionState.isError() ||
        snapshot.connectionState.isBackingOff() ||
        snapshot.authMode == AdsbAuthMode.AuthFailed

internal fun persistentAdsbStatusPresentation(
    snapshot: AdsbTrafficSnapshot
): Triple<String, String?, Color> {
    val reason = snapshot.debugReasonLabel() ?: snapshot.lastError?.takeIf { it.isNotBlank() }
    return when {
        snapshot.connectionState.isError() -> Triple(
            "ADS-B Offline",
            reason,
            Color(0xFF991B1B)
        )

        snapshot.connectionState.isBackingOff() -> Triple(
            "ADS-B Backoff",
            reason ?: "Waiting before next retry",
            Color(0xFF92400E)
        )

        snapshot.authMode == AdsbAuthMode.AuthFailed -> Triple(
            "ADS-B Credential Issue",
            reason ?: "Using anonymous fallback",
            Color(0xFF7C2D12)
        )

        else -> Triple("ADS-B Active", null, Color(0xFF065F46))
    }
}
