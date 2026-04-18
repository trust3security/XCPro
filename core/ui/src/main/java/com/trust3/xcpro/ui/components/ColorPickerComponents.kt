package com.trust3.xcpro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun CompactColorPreviewHeader(
    currentColor: Color,
    originalColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Color Picker",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorPreviewCircle(
                color = originalColor,
                label = "Before",
                size = 28.dp
            )

            Text(
                text = "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ColorPreviewCircle(
                color = currentColor,
                label = "After",
                size = 28.dp
            )
        }
    }
}

@Composable
internal fun ColorPreviewCircle(
    color: Color,
    label: String,
    size: Dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val animatedColor by animateColorAsState(
            targetValue = color,
            animationSpec = tween(300),
            label = "color_preview"
        )

        Box(
            modifier = Modifier
                .size(size)
                .background(animatedColor, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ColorSlider(
    value: Float,
    onValueChanged: (Float) -> Unit,
    colors: List<Color>,
    label: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(colors = colors),
                    size = size
                )
            }

            Slider(
                value = value,
                onValueChange = onValueChanged,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
internal fun CompactColorSlider(
    value: Float,
    onValueChanged: (Float) -> Unit,
    colors: List<Color>,
    label: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(colors = colors),
                    size = size
                )
            }

            Slider(
                value = value,
                onValueChange = onValueChanged,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
internal fun PredefinedColorPalette(
    onColorSelected: (Color) -> Unit
) {
    val predefinedColors = listOf(
        Color(0xFFFF0000), Color(0xFF00FF00), Color(0xFF0000FF),
        Color(0xFFFFFF00), Color(0xFFFF00FF), Color(0xFF00FFFF),
        Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
        Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
        Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688),
        Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
        Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800),
        Color(0xFFFF5722), Color(0xFF795548), Color(0xFF9E9E9E),
        Color(0xFF000000), Color(0xFF424242), Color(0xFF757575),
        Color(0xFFBDBDBD), Color(0xFFE0E0E0), Color(0xFFFFFFFF)
    )

    Column {
        Text(
            text = "Quick Colors",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(120.dp)
        ) {
            items(predefinedColors) { color ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(color, RoundedCornerShape(6.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }
    }
}

@Composable
internal fun CompactPredefinedColorPalette(
    onColorSelected: (Color) -> Unit
) {
    val predefinedColors = listOf(
        Color(0xFFFF0000), Color(0xFF00FF00), Color(0xFF0000FF),
        Color(0xFFFFFF00), Color(0xFFFF00FF), Color(0xFF00FFFF),
        Color(0xFFF44336), Color(0xFF4CAF50), Color(0xFF2196F3),
        Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF607D8B),
        Color(0xFF000000), Color(0xFF757575), Color(0xFFFFFFFF)
    )

    Column {
        Text(
            text = "Quick Colors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height(80.dp)
        ) {
            items(predefinedColors) { color ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(color, RoundedCornerShape(4.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }
    }
}

internal fun getHue(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

internal fun getSaturation(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[1]
}

internal fun getValue(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[2]
}
