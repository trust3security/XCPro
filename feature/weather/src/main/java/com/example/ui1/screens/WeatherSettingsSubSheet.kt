package com.example.ui1.screens

import androidx.compose.runtime.Composable
import com.example.xcpro.screens.navdrawer.WeatherSettingsSheet

@Composable
fun WeatherSettingsSubSheet(
    onDismiss: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    WeatherSettingsSheet(
        onDismissRequest = onDismiss,
        onNavigateUp = onDismiss,
        onSecondaryNavigate = null,
        onNavigateToMap = onNavigateToMap
    )
}
