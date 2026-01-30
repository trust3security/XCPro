package com.example.dfcards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Compose helper for providing a locale/zone-aware CardTimeFormatter.
 */
@Composable
fun rememberCardTimeFormatter(): CardTimeFormatter {
    val configuration = LocalConfiguration.current
    return remember(configuration) { SystemCardTimeFormatter() }
}
