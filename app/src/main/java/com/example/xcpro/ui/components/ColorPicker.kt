package com.example.xcpro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Modern Color Picker - Main entry point
 *
 * Refactored for file size compliance (<500 lines).
 * Uses extracted components from:
 * - ColorPickerDrawing.kt: Drawing functions for color wheels
 * - ColorPickerInputs.kt: HEX/RGB/HSV input methods
 * - ColorPickerComponents.kt: Sliders, palettes, previews, helpers
 */

/**
 * Modern color picker with HSV wheel, sliders, and input methods
 *
 * @param initialColor Starting color
 * @param onColorChanged Callback when color changes
 * @param modifier Optional modifier
 */
@Composable
fun ModernColorPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var hue by remember { mutableStateOf(getHue(initialColor)) }
    var saturation by remember { mutableStateOf(getSaturation(initialColor)) }
    var value by remember { mutableStateOf(getValue(initialColor)) }

    // Update color when HSV changes
    LaunchedEffect(hue, saturation, value) {
        selectedColor = Color.hsv(hue, saturation, value)
        onColorChanged(selectedColor)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Compact Header with current color preview
            CompactColorPreviewHeader(
                currentColor = selectedColor,
                originalColor = initialColor
            )

            // Compact HSV Color Wheel
            CompactHSVColorWheel(
                hue = hue,
                saturation = saturation,
                value = value,
                onHueChanged = { hue = it },
                onSaturationValueChanged = { newSat, newVal ->
                    saturation = newSat
                    value = newVal
                }
            )

            // Brightness Slider (more compact)
            CompactColorSlider(
                value = value,
                onValueChanged = { value = it },
                colors = listOf(
                    Color.hsv(hue, saturation, 0f),
                    Color.hsv(hue, saturation, 1f)
                ),
                label = "Brightness"
            )

            // Compact Color Input Methods
            CompactColorInputMethods(
                color = selectedColor,
                onColorChanged = { newColor ->
                    selectedColor = newColor
                    hue = getHue(newColor)
                    saturation = getSaturation(newColor)
                    value = getValue(newColor)
                }
            )

            // Compact Predefined Color Palette
            CompactPredefinedColorPalette(
                onColorSelected = { newColor ->
                    selectedColor = newColor
                    hue = getHue(newColor)
                    saturation = getSaturation(newColor)
                    value = getValue(newColor)
                }
            )
        }
    }
}

/**
 * Compact HSV color wheel with drag interaction
 *
 * Combines hue ring (outer) with saturation/value square (center).
 * Drag outer ring to change hue, drag center square for saturation/value.
 */
@Composable
private fun CompactHSVColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChanged: (Float) -> Unit,
    onSaturationValueChanged: (Float, Float) -> Unit
) {
    // Much smaller and more compact color wheel
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp), // Fixed smaller height
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(160.dp) // Fixed smaller size
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val offset = change.position - center
                        val distance = sqrt(offset.x * offset.x + offset.y * offset.y)

                        if (distance > size.width * 0.3f) {
                            // Hue ring interaction
                            val angle = atan2(offset.y, offset.x)
                            val hueValue = ((angle * 180 / PI + 360) % 360).toFloat()
                            onHueChanged(hueValue)
                        } else {
                            // Saturation/Value square interaction
                            val squareSize = size.width * 0.5f
                            val squareCenter = Offset(size.width / 2f, size.height / 2f)
                            val localX = change.position.x - (squareCenter.x - squareSize / 2f)
                            val localY = change.position.y - (squareCenter.y - squareSize / 2f)

                            val newSat = (localX / squareSize).coerceIn(0f, 1f)
                            val newVal = 1f - (localY / squareSize).coerceIn(0f, 1f)
                            onSaturationValueChanged(newSat, newVal)
                        }
                    }
                }
        ) {
            drawCompactHueRing(hue, saturation, value)
        }
    }
}
