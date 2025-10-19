package com.example.dfcards

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp

data class CardDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: CardCategory,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val unit: String = "",
    val labelFontSize: Int = 9,
    val primaryFontSize: Int = 14,
    val secondaryFontSize: Int = 7,
    val unitFontSize: Int = 9,
    val unitFontWeight: FontWeight = FontWeight.Medium
)

enum class CardCategory(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
) {
    ESSENTIAL("Essential", Icons.Filled.Star, Color(0xFF4CAF50)),
    VARIO("Variometers", Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF00BCD4)),
    NAVIGATION("Navigation", Icons.Filled.LocationOn, Color(0xFF2196F3)),
    PERFORMANCE("Performance", Icons.Filled.ThumbUp, Color(0xFFFF9800)),
    TIME_WEATHER("Time & Weather", Icons.Filled.Notifications, Color(0xFF9C27B0)),
    COMPETITION("Competition", Icons.Filled.Star, Color(0xFFF44336)),
    ADVANCED("Advanced", Icons.Filled.Settings, Color(0xFF607D8B))
}
