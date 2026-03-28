package com.example.xcpro.map.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview

@Immutable
internal data class MapBottomNavigationItemSpec(
    val tab: MapBottomTab,
    val label: String,
    val testTag: String,
    val icon: MapBottomNavigationIconSpec,
    val isFeatureEnabled: Boolean
)

@Immutable
internal sealed interface MapBottomNavigationIconSpec {
    val iconSize: Dp

    data class BrandLogo(
        @DrawableRes val resId: Int,
        override val iconSize: Dp,
        val tintColor: Color? = null
    ) : MapBottomNavigationIconSpec

    data class VectorIcon(
        val imageVector: ImageVector,
        override val iconSize: Dp
    ) : MapBottomNavigationIconSpec
}

internal object MapBottomNavigationDefaults {
    val SciaFallbackIcon: ImageVector = Icons.Filled.Flight
}

internal val TAB_ENABLED_BORDER_COLOR = Color(0xFF16A34A)
internal const val MAP_BOTTOM_TAB_STRIP_TAG = "map_bottom_tab_strip"

@Composable
internal fun MapBottomNavigationBar(
    selectedTab: MapBottomTab,
    items: List<MapBottomNavigationItemSpec>,
    onTabSelected: (MapBottomTab) -> Unit,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MAP_BOTTOM_BAR_CORNER_RADIUS)
) {
    val layout = mapBottomNavigationLayout(showLabels = showLabels)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        shadowElevation = MAP_BOTTOM_BAR_SHADOW_ELEVATION,
        tonalElevation = MAP_BOTTOM_BAR_TONAL_ELEVATION,
        border = BorderStroke(
            width = MAP_BOTTOM_BAR_BORDER_WIDTH,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MAP_BOTTOM_BAR_BORDER_ALPHA)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.barHeight)
                .padding(horizontal = layout.barHorizontalPadding, vertical = layout.barVerticalPadding)
                .selectableGroup()
                .testTag(MAP_BOTTOM_TAB_STRIP_TAG),
            horizontalArrangement = Arrangement.spacedBy(layout.itemSpacing)
        ) {
            items.forEach { item ->
                MapBottomNavigationItem(
                    spec = item,
                    isSelected = item.tab == selectedTab,
                    onClick = { onTabSelected(item.tab) },
                    showLabels = showLabels,
                    layout = layout
                )
            }
        }
    }
}

@Composable
private fun RowScope.MapBottomNavigationItem(
    spec: MapBottomNavigationItemSpec,
    isSelected: Boolean,
    onClick: () -> Unit,
    showLabels: Boolean,
    layout: MapBottomNavigationLayout
) {
    val colors = mapBottomNavigationItemColors(
        isSelected = isSelected,
        isFeatureEnabled = spec.isFeatureEnabled
    )

    Surface(
        modifier = Modifier
            .weight(1f)
            .height(layout.itemHeight)
            .testTag(spec.testTag)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab
            ),
        shape = RoundedCornerShape(layout.indicatorRadius),
        color = colors.indicatorColor,
        border = BorderStroke(MAP_BOTTOM_ITEM_BORDER_WIDTH, colors.indicatorBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    PaddingValues(
                        horizontal = layout.itemHorizontalPadding,
                        vertical = layout.itemVerticalPadding
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(layout.itemContentSpacing)
        ) {
            Box(
                modifier = Modifier
                    .size(layout.iconBoxSize),
                contentAlignment = Alignment.Center
            ) {
                MapBottomNavigationIcon(
                    icon = spec.icon,
                    iconSizeMultiplier = layout.iconSizeMultiplier,
                    vectorTint = colors.vectorIconTint,
                    brandLogoAlpha = colors.brandLogoAlpha
                )
            }
            if (showLabels) {
                Text(
                    text = spec.label,
                    color = colors.labelColor,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = MAP_BOTTOM_LABEL_FONT_SIZE,
                        lineHeight = MAP_BOTTOM_LABEL_LINE_HEIGHT
                    ),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MapBottomNavigationStatusDot(isVisible = spec.isFeatureEnabled)
        }
    }
}

@Composable
private fun mapBottomNavigationItemColors(
    isSelected: Boolean,
    isFeatureEnabled: Boolean
): MapBottomNavigationItemColors {
    val colorScheme = MaterialTheme.colorScheme
    return when {
        isSelected -> MapBottomNavigationItemColors(
            indicatorColor = colorScheme.primaryContainer,
            indicatorBorderColor = colorScheme.primary.copy(alpha = SELECTED_INDICATOR_BORDER_ALPHA),
            labelColor = colorScheme.onPrimaryContainer,
            vectorIconTint = colorScheme.onPrimaryContainer,
            brandLogoAlpha = BRAND_LOGO_ALPHA
        )

        isFeatureEnabled -> MapBottomNavigationItemColors(
            indicatorColor = Color.Transparent,
            indicatorBorderColor = Color.Transparent,
            labelColor = colorScheme.onSurface,
            vectorIconTint = colorScheme.onSurface,
            brandLogoAlpha = BRAND_LOGO_ALPHA
        )

        else -> MapBottomNavigationItemColors(
            indicatorColor = Color.Transparent,
            indicatorBorderColor = Color.Transparent,
            labelColor = colorScheme.onSurface.copy(alpha = INACTIVE_LABEL_ALPHA),
            vectorIconTint = colorScheme.onSurface.copy(alpha = INACTIVE_VECTOR_ICON_ALPHA),
            brandLogoAlpha = BRAND_LOGO_ALPHA
        )
    }
}

@Composable
private fun MapBottomNavigationIcon(
    icon: MapBottomNavigationIconSpec,
    iconSizeMultiplier: Float,
    vectorTint: Color,
    brandLogoAlpha: Float
) {
    when (icon) {
        is MapBottomNavigationIconSpec.BrandLogo -> {
            Image(
                painter = painterResource(id = icon.resId),
                contentDescription = null,
                modifier = Modifier
                    .size(icon.iconSize * iconSizeMultiplier),
                alpha = brandLogoAlpha,
                colorFilter = icon.tintColor?.let(ColorFilter::tint)
            )
        }

        is MapBottomNavigationIconSpec.VectorIcon -> {
            androidx.compose.material3.Icon(
                imageVector = icon.imageVector,
                contentDescription = null,
                tint = vectorTint,
                modifier = Modifier.size(icon.iconSize * iconSizeMultiplier)
            )
        }
    }
}

@Composable
private fun MapBottomNavigationStatusDot(
    isVisible: Boolean
) {
    Box(
        modifier = Modifier
            .width(MAP_BOTTOM_ITEM_STATUS_DOT_SLOT_WIDTH)
            .height(MAP_BOTTOM_ITEM_STATUS_DOT_SLOT_HEIGHT),
        contentAlignment = Alignment.Center
    ) {
        if (isVisible) {
            Surface(
                modifier = Modifier.size(MAP_BOTTOM_ITEM_STATUS_DOT_SIZE),
                color = TAB_ENABLED_BORDER_COLOR,
                shape = CircleShape
            ) {}
        }
    }
}

internal fun defaultMapBottomNavigationIcon(tab: MapBottomTab): MapBottomNavigationIconSpec {
    return when (tab) {
        MapBottomTab.RAIN -> MapBottomNavigationIconSpec.BrandLogo(
            resId = com.example.xcpro.map.R.drawable.rainviewer,
            iconSize = RAINVIEWER_LOGO_SIZE
        )

        MapBottomTab.SKYSIGHT -> MapBottomNavigationIconSpec.BrandLogo(
            resId = com.example.xcpro.map.R.drawable.ic_skysight,
            iconSize = SKYSIGHT_LOGO_SIZE,
            tintColor = Color.Black
        )

        MapBottomTab.OGN -> MapBottomNavigationIconSpec.VectorIcon(
            imageVector = MapBottomNavigationDefaults.SciaFallbackIcon,
            iconSize = FALLBACK_VECTOR_ICON_SIZE
        )

        MapBottomTab.MAP4 -> MapBottomNavigationIconSpec.BrandLogo(
            resId = com.example.xcpro.map.R.drawable.xcpro_logo,
            iconSize = XCPRO_LOGO_SIZE
        )
    }
}

@Preview(name = "Map Bottom Nav Light", widthDp = 420, showBackground = true)
@Composable
private fun MapBottomNavigationBarLightPreview() {
    MapBottomNavigationPreviewTheme(darkTheme = false) {
        MapBottomNavigationPreviewContent(selectedTab = MapBottomTab.SKYSIGHT)
    }
}

@Preview(
    name = "Map Bottom Nav Dark",
    widthDp = 420,
    showBackground = true,
    backgroundColor = 0xFF121417
)
@Composable
private fun MapBottomNavigationBarDarkPreview() {
    MapBottomNavigationPreviewTheme(darkTheme = true) {
        MapBottomNavigationPreviewContent(selectedTab = MapBottomTab.MAP4)
    }
}

@Preview(name = "Map Bottom Nav Font 1.3x", widthDp = 420, fontScale = 1.3f, showBackground = true)
@Composable
private fun MapBottomNavigationBarFontScale130Preview() {
    MapBottomNavigationPreviewTheme(darkTheme = false) {
        MapBottomNavigationPreviewContent(selectedTab = MapBottomTab.RAIN)
    }
}

@Preview(name = "Map Bottom Nav Font 1.5x", widthDp = 420, fontScale = 1.5f, showBackground = true)
@Composable
private fun MapBottomNavigationBarFontScale150Preview() {
    MapBottomNavigationPreviewTheme(darkTheme = false) {
        MapBottomNavigationPreviewContent(selectedTab = MapBottomTab.SKYSIGHT)
    }
}

@Preview(
    name = "Map Bottom Nav Narrow Landscape",
    widthDp = 640,
    heightDp = 360,
    showBackground = true
)
@Composable
private fun MapBottomNavigationBarNarrowLandscapePreview() {
    MapBottomNavigationPreviewTheme(darkTheme = false) {
        MapBottomNavigationPreviewContent(selectedTab = MapBottomTab.MAP4)
    }
}

@Composable
private fun MapBottomNavigationPreviewContent(
    selectedTab: MapBottomTab
) {
    MapBottomNavigationBar(
        selectedTab = selectedTab,
        items = previewMapBottomNavigationItems(),
        onTabSelected = {},
        modifier = Modifier.padding(16.dp)
    )
}

private fun previewMapBottomNavigationItems(): List<MapBottomNavigationItemSpec> {
    return listOf(
        MapBottomNavigationItemSpec(
            tab = MapBottomTab.RAIN,
            label = "RainViewer",
            testTag = MapBottomTab.RAIN.chipTestTag,
            icon = defaultMapBottomNavigationIcon(MapBottomTab.RAIN),
            isFeatureEnabled = true
        ),
        MapBottomNavigationItemSpec(
            tab = MapBottomTab.SKYSIGHT,
            label = "SkySight",
            testTag = MapBottomTab.SKYSIGHT.chipTestTag,
            icon = defaultMapBottomNavigationIcon(MapBottomTab.SKYSIGHT),
            isFeatureEnabled = true
        ),
        MapBottomNavigationItemSpec(
            tab = MapBottomTab.OGN,
            label = "Scia",
            testTag = MapBottomTab.OGN.chipTestTag,
            icon = defaultMapBottomNavigationIcon(MapBottomTab.OGN),
            isFeatureEnabled = false
        ),
        MapBottomNavigationItemSpec(
            tab = MapBottomTab.MAP4,
            label = "XCPro",
            testTag = MapBottomTab.MAP4.chipTestTag,
            icon = defaultMapBottomNavigationIcon(MapBottomTab.MAP4),
            isFeatureEnabled = true
        )
    )
}

@Composable
private fun MapBottomNavigationPreviewTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF7FC8FF),
            primaryContainer = Color(0xFF153B52),
            onPrimaryContainer = Color(0xFFF3FAFF),
            background = Color(0xFF121417),
            surface = Color(0xFF1B1F24),
            onSurface = Color(0xFFF2F4F7),
            onSurfaceVariant = Color(0xFFCFD6E1),
            outline = Color(0xFF7E8794),
            outlineVariant = Color(0xFF3A4654)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF005E9A),
            primaryContainer = Color(0xFFD9EDFF),
            onPrimaryContainer = Color(0xFF002B46),
            background = Color(0xFFF5F7FA),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF12161C),
            onSurfaceVariant = Color(0xFF4F5B68),
            outline = Color(0xFF6B7280),
            outlineVariant = Color(0xFFC4CBD4)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Immutable
private data class MapBottomNavigationItemColors(
    val indicatorColor: Color,
    val indicatorBorderColor: Color,
    val labelColor: Color,
    val vectorIconTint: Color,
    val brandLogoAlpha: Float
)

@Immutable
private data class MapBottomNavigationLayout(
    val barHeight: Dp,
    val barHorizontalPadding: Dp,
    val barVerticalPadding: Dp,
    val itemSpacing: Dp,
    val itemHeight: Dp,
    val indicatorRadius: Dp,
    val itemHorizontalPadding: Dp,
    val itemVerticalPadding: Dp,
    val iconBoxSize: Dp,
    val itemContentSpacing: Dp,
    val iconSizeMultiplier: Float
)

private fun mapBottomNavigationLayout(showLabels: Boolean): MapBottomNavigationLayout {
    return if (showLabels) {
        MapBottomNavigationLayout(
            barHeight = MAP_BOTTOM_BAR_HEIGHT,
            barHorizontalPadding = MAP_BOTTOM_BAR_HORIZONTAL_PADDING,
            barVerticalPadding = MAP_BOTTOM_BAR_VERTICAL_PADDING,
            itemSpacing = MAP_BOTTOM_BAR_ITEM_SPACING,
            itemHeight = MAP_BOTTOM_ITEM_HEIGHT,
            indicatorRadius = MAP_BOTTOM_ITEM_INDICATOR_RADIUS,
            itemHorizontalPadding = MAP_BOTTOM_ITEM_HORIZONTAL_PADDING,
            itemVerticalPadding = MAP_BOTTOM_ITEM_VERTICAL_PADDING,
            iconBoxSize = MAP_BOTTOM_ITEM_ICON_BOX_SIZE,
            itemContentSpacing = MAP_BOTTOM_ITEM_CONTENT_SPACING,
            iconSizeMultiplier = LABELED_ICON_SIZE_MULTIPLIER
        )
    } else {
        MapBottomNavigationLayout(
            barHeight = MAP_BOTTOM_ICON_ONLY_BAR_HEIGHT,
            barHorizontalPadding = MAP_BOTTOM_ICON_ONLY_BAR_HORIZONTAL_PADDING,
            barVerticalPadding = MAP_BOTTOM_ICON_ONLY_BAR_VERTICAL_PADDING,
            itemSpacing = MAP_BOTTOM_ICON_ONLY_ITEM_SPACING,
            itemHeight = MAP_BOTTOM_ICON_ONLY_ITEM_HEIGHT,
            indicatorRadius = MAP_BOTTOM_ICON_ONLY_INDICATOR_RADIUS,
            itemHorizontalPadding = MAP_BOTTOM_ICON_ONLY_ITEM_HORIZONTAL_PADDING,
            itemVerticalPadding = MAP_BOTTOM_ICON_ONLY_ITEM_VERTICAL_PADDING,
            iconBoxSize = MAP_BOTTOM_ICON_ONLY_ICON_BOX_SIZE,
            itemContentSpacing = MAP_BOTTOM_ICON_ONLY_ITEM_CONTENT_SPACING,
            iconSizeMultiplier = ICON_ONLY_ICON_SIZE_MULTIPLIER
        )
    }
}

private val MAP_BOTTOM_BAR_CORNER_RADIUS = 28.dp
private val MAP_BOTTOM_BAR_HEIGHT = 56.dp
private val MAP_BOTTOM_BAR_HORIZONTAL_PADDING = 6.dp
private val MAP_BOTTOM_BAR_VERTICAL_PADDING = 2.dp
private val MAP_BOTTOM_BAR_ITEM_SPACING = 4.dp
private val MAP_BOTTOM_BAR_BORDER_WIDTH = 1.dp
private val MAP_BOTTOM_BAR_SHADOW_ELEVATION = 8.dp
private val MAP_BOTTOM_BAR_TONAL_ELEVATION = 2.dp
private const val MAP_BOTTOM_BAR_BORDER_ALPHA = 0.72f
private val MAP_BOTTOM_ITEM_HEIGHT = 52.dp
private val MAP_BOTTOM_ITEM_INDICATOR_RADIUS = 16.dp
private val MAP_BOTTOM_ITEM_BORDER_WIDTH = 1.dp
private val MAP_BOTTOM_ITEM_HORIZONTAL_PADDING = 3.dp
private val MAP_BOTTOM_ITEM_VERTICAL_PADDING = 3.dp
private val MAP_BOTTOM_ITEM_ICON_BOX_SIZE = 20.dp
private val MAP_BOTTOM_ITEM_STATUS_DOT_SLOT_WIDTH = 8.dp
private val MAP_BOTTOM_ITEM_STATUS_DOT_SLOT_HEIGHT = 4.dp
private val MAP_BOTTOM_ITEM_STATUS_DOT_SIZE = 4.dp
private val MAP_BOTTOM_ITEM_CONTENT_SPACING = 1.dp
private val MAP_BOTTOM_LABEL_FONT_SIZE = 11.sp
private val MAP_BOTTOM_LABEL_LINE_HEIGHT = 12.sp
private val SKYSIGHT_LOGO_SIZE = 18.dp
private val RAINVIEWER_LOGO_SIZE = 20.dp
private val FALLBACK_VECTOR_ICON_SIZE = 18.dp
private val XCPRO_LOGO_SIZE = 20.dp
private val MAP_BOTTOM_ICON_ONLY_BAR_HEIGHT = 44.dp
private val MAP_BOTTOM_ICON_ONLY_BAR_HORIZONTAL_PADDING = 6.dp
private val MAP_BOTTOM_ICON_ONLY_BAR_VERTICAL_PADDING = 2.dp
private val MAP_BOTTOM_ICON_ONLY_ITEM_SPACING = 4.dp
private val MAP_BOTTOM_ICON_ONLY_ITEM_HEIGHT = 40.dp
private val MAP_BOTTOM_ICON_ONLY_INDICATOR_RADIUS = 16.dp
private val MAP_BOTTOM_ICON_ONLY_ITEM_HORIZONTAL_PADDING = 2.dp
private val MAP_BOTTOM_ICON_ONLY_ITEM_VERTICAL_PADDING = 3.dp
private val MAP_BOTTOM_ICON_ONLY_ICON_BOX_SIZE = 28.dp
private val MAP_BOTTOM_ICON_ONLY_ITEM_CONTENT_SPACING = 2.dp
private const val BRAND_LOGO_ALPHA = 1f
private const val INACTIVE_VECTOR_ICON_ALPHA = 0.84f
private const val INACTIVE_LABEL_ALPHA = 0.86f
private const val SELECTED_INDICATOR_BORDER_ALPHA = 0.24f
private const val LABELED_ICON_SIZE_MULTIPLIER = 1f
private const val ICON_ONLY_ICON_SIZE_MULTIPLIER = 1.3f
