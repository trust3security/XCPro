package com.trust3.xcpro.screens.navdrawer.lookandfeel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import com.trust3.xcpro.map.trail.TrailLength
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.map.trail.TrailType

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
    ),
    TRANSPARENT(
        id = "transparent",
        title = "Transparent",
        description = "No border or fill, map shows through",
        icon = Icons.Filled.VisibilityOff
    );

    companion object {
        val default = STANDARD
    }
}

object LookAndFeelMenuDefaults {
    fun defaultMenuOptions(
        statusBarStyle: StatusBarStyle,
        cardStyle: CardStyle,
        snailTrailSummary: String
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
            id = "snail_trail",
            title = "Snail Trail",
            subtitle = snailTrailSummary,
            icon = Icons.Filled.Timeline
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

internal fun trailSummary(settings: TrailSettings): String {
    val lengthLabel = trailLengthLabel(settings.length)
    val typeLabel = trailTypeLabel(settings.type)
    return "$lengthLabel \u2022 $typeLabel"
}

internal fun trailLengthLabel(length: TrailLength): String = when (length) {
    TrailLength.FULL -> "Full"
    TrailLength.LONG -> "Long"
    TrailLength.MEDIUM -> "Medium"
    TrailLength.SHORT -> "Short"
    TrailLength.OFF -> "None"
}

internal fun trailTypeLabel(type: TrailType): String = when (type) {
    TrailType.VARIO_1 -> "Vario 1"
    TrailType.VARIO_1_DOTS -> "Vario 1 dots"
    TrailType.VARIO_2 -> "Vario 2"
    TrailType.VARIO_2_DOTS -> "Vario 2 dots"
    TrailType.VARIO_DOTS_AND_LINES -> "Dots + lines"
    TrailType.VARIO_EINK -> "Vario E-ink"
    TrailType.ALTITUDE -> "Altitude"
}
