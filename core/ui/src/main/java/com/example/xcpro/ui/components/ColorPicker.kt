package com.example.xcpro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Modern color picker with HSV wheel, sliders, and compact input methods.
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
            CompactColorPreviewHeader(
                currentColor = selectedColor,
                originalColor = initialColor
            )

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

            CompactColorSlider(
                value = value,
                onValueChanged = { value = it },
                colors = listOf(
                    Color.hsv(hue, saturation, 0f),
                    Color.hsv(hue, saturation, 1f)
                ),
                label = "Brightness"
            )

            CompactColorInputMethods(
                color = selectedColor,
                onColorChanged = { newColor ->
                    selectedColor = newColor
                    hue = getHue(newColor)
                    saturation = getSaturation(newColor)
                    value = getValue(newColor)
                }
            )

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

@Composable
private fun CompactHSVColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChanged: (Float) -> Unit,
    onSaturationValueChanged: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(160.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val offset = change.position - center
                        val distance = sqrt(offset.x * offset.x + offset.y * offset.y)

                        if (distance > size.width * 0.3f) {
                            val angle = atan2(offset.y, offset.x)
                            val hueValue = ((angle * 180 / PI + 360) % 360).toFloat()
                            onHueChanged(hueValue)
                        } else {
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
