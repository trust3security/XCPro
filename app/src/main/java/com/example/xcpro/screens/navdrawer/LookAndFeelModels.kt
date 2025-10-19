package com.example.ui1.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

data class LookAndFeelOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String? = null
)

enum class StatusBarStyle(
    val id: String,
    val title: String,
    val description: String,
    val detailedDescription: String,
    val icon: ImageVector,
    val example: String
) {
    TRANSPARENT(
        id = "transparent",
        title = "Transparent",
        description = "Map shows through the status bar",
        detailedDescription = "The status bar becomes completely transparent, allowing the map to be visible behind it. This creates a seamless, immersive experience where your flight data overlays directly on the map.",
        icon = Icons.Filled.VisibilityOff,
        example = "Full map visibility"
    ),
    THEMED(
        id = "themed",
        title = "Themed",
        description = "Status bar matches your app theme",
        detailedDescription = "The status bar color adapts to your current app theme, creating a cohesive look. It changes between light and dark modes automatically, maintaining visual consistency throughout the app.",
        icon = Icons.Filled.Palette,
        example = "Adaptive colors"
    ),
    EDGE_TO_EDGE(
        id = "edge_to_edge",
        title = "Edge to Edge",
        description = "Content extends under the status bar",
        detailedDescription = "Your content extends edge-to-edge on the screen with a subtle scrim on the status bar. This modern approach maximizes screen real estate while keeping system icons visible.",
        icon = Icons.Filled.Fullscreen,
        example = "Modern full-screen"
    ),
    OVERLAY(
        id = "overlay",
        title = "Overlay",
        description = "Semi-transparent overlay on map",
        detailedDescription = "A semi-transparent overlay provides the perfect balance between map visibility and status bar readability. System icons remain clearly visible while the map shows through subtly.",
        icon = Icons.Filled.Fullscreen,
        example = "Balanced visibility"
    )
}

enum class CardStyle(
    val id: String,
    val title: String,
    val description: String,
    val detailedDescription: String,
    val icon: ImageVector
) {
    STANDARD(
        id = "standard",
        title = "Standard",
        description = "Default layout with labels",
        detailedDescription = "Classic layout with labels and values.",
        icon = Icons.Filled.Fullscreen
    ),
    COMPACT(
        id = "compact",
        title = "Compact",
        description = "Smaller layout, more on screen",
        detailedDescription = "Reduces padding and text sizes to fit more cards.",
        icon = Icons.Filled.Fullscreen
    ),
    LARGE(
        id = "large",
        title = "Large",
        description = "Bigger text and spacing",
        detailedDescription = "Improves readability with larger typography and spacing.",
        icon = Icons.Filled.Fullscreen
    )
}

