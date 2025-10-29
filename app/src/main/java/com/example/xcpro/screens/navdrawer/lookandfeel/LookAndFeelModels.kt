package com.example.xcpro.screens.navdrawer.lookandfeel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
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
    val icon: ImageVector
) {
    TRANSPARENT(
        id = "transparent",
        title = "Transparent",
        description = "Map shows through the status bar",
        detailedDescription = "The status bar becomes completely transparent, letting the map remain visible behind flight data for an immersive view.",
        icon = Icons.Filled.VisibilityOff
    ),
    THEMED(
        id = "themed",
        title = "Themed",
        description = "Status bar matches the app theme",
        detailedDescription = "Applies your current theme color to the status bar, automatically adapting between light and dark modes.",
        icon = Icons.Filled.Palette
    ),
    EDGE_TO_EDGE(
        id = "edge_to_edge",
        title = "Edge to Edge",
        description = "Content extends under the status bar",
        detailedDescription = "Uses edge-to-edge content with a subtle scrim so system icons stay legible while maximizing map real estate.",
        icon = Icons.Filled.Fullscreen
    ),
    OVERLAY(
        id = "overlay",
        title = "Overlay",
        description = "Semi-transparent overlay on the map",
        detailedDescription = "Applies a translucent overlay to balance map visibility with status bar readability.",
        icon = Icons.Filled.Fullscreen
    );

    companion object {
        val default = TRANSPARENT
    }
}

enum class CardStyle(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    STANDARD(
        id = "standard",
        title = "Standard",
        description = "Default layout with labels",
        icon = Icons.Filled.Dashboard
    ),
    COMPACT(
        id = "compact",
        title = "Compact",
        description = "Smaller layout, more cards on screen",
        icon = Icons.Filled.FormatSize
    ),
    LARGE(
        id = "large",
        title = "Large",
        description = "Bigger typography and padding",
        icon = Icons.Filled.FormatSize
    );

    companion object {
        val default = STANDARD
    }
}

object LookAndFeelMenuDefaults {
    fun defaultMenuOptions(
        statusBarStyle: StatusBarStyle,
        cardStyle: CardStyle
    ): List<LookAndFeelOption> = listOf(
        LookAndFeelOption(
            id = "colors",
            title = "Colors",
            subtitle = "App color theme",
            icon = Icons.Filled.ColorLens,
            route = "colors"
        ),
        LookAndFeelOption(
            id = "status_bar",
            title = "Status Bar Style",
            subtitle = statusBarStyle.title,
            icon = Icons.Filled.PhoneAndroid
        ),
        LookAndFeelOption(
            id = "card_style",
            title = "Card Style",
            subtitle = cardStyle.title,
            icon = Icons.Filled.Dashboard
        ),
        LookAndFeelOption(
            id = "theme",
            title = "Theme",
            subtitle = "System default",
            icon = Icons.Filled.Palette
        ),
        LookAndFeelOption(
            id = "animations",
            title = "Animations",
            subtitle = "Enabled",
            icon = Icons.Filled.Animation
        ),
        LookAndFeelOption(
            id = "font_size",
            title = "Font Size",
            subtitle = "Medium",
            icon = Icons.Filled.FormatSize
        )
    )
}
