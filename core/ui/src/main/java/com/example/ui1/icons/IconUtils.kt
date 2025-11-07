package com.example.ui1.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Utility function to create ImageVector icons with consistent patterns.
 * Eliminates boilerplate code across icon definitions.
 */
fun createIcon(
    name: String,
    defaultWidth: Dp = 24.dp,
    defaultHeight: Dp = 24.dp,
    viewportWidth: Float = 24f,
    viewportHeight: Float = 24f,
    pathBuilder: ImageVector.Builder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = defaultWidth,
    defaultHeight = defaultHeight,
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight
).apply(pathBuilder).build()