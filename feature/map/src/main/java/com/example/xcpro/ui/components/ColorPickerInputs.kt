package com.example.xcpro.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Color Picker Input Methods
 *
 * Extracted from ColorPicker.kt for file size compliance.
 * Contains all color input methods: HEX, RGB, and HSV (both full and compact versions).
 */

/**
 * Color input methods switcher with tabs for HEX/RGB/HSV
 */
@Composable
internal fun ColorInputMethods(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var inputMode by remember { mutableStateOf("HEX") }
    val modes = listOf("HEX", "RGB", "HSV")

    Column {
        // Input Mode Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.forEach { mode ->
                FilterChip(
                    selected = inputMode == mode,
                    onClick = { inputMode = mode },
                    label = { Text(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input Fields
        when (inputMode) {
            "HEX" -> HexInput(color, onColorChanged)
            "RGB" -> RGBInput(color, onColorChanged)
            "HSV" -> HSVInput(color, onColorChanged)
        }
    }
}

/**
 * HEX color input field
 */
@Composable
internal fun HexInput(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var hexValue by remember(color) {
        mutableStateOf(String.format("#%06X", (color.toArgb() and 0xFFFFFF)))
    }

    OutlinedTextField(
        value = hexValue,
        onValueChange = { newHex ->
            hexValue = newHex
            if (newHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                try {
                    val colorInt = newHex.toColorInt()
                    onColorChanged(Color(colorInt))
                } catch (e: Exception) { /* Invalid color */ }
            }
        },
        label = { Text("Hex Color") },
        placeholder = { Text("#FF0000") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

/**
 * RGB color input fields
 */
@Composable
internal fun RGBInput(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var red by remember(color) { mutableStateOf((color.red * 255).toInt().toString()) }
    var green by remember(color) { mutableStateOf((color.green * 255).toInt().toString()) }
    var blue by remember(color) { mutableStateOf((color.blue * 255).toInt().toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(
            Triple("R", red, { newVal: String -> red = newVal }),
            Triple("G", green, { newVal: String -> green = newVal }),
            Triple("B", blue, { newVal: String -> blue = newVal })
        ).forEach { (label, value, onValueChange) ->
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                    val r = red.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    val g = green.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    val b = blue.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    onColorChanged(Color(r, g, b))
                },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

/**
 * HSV color input fields
 */
@Composable
internal fun HSVInput(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var hue by remember(color) { mutableStateOf(getHue(color).toInt().toString()) }
    var saturation by remember(color) { mutableStateOf((getSaturation(color) * 100).toInt().toString()) }
    var value by remember(color) { mutableStateOf((getValue(color) * 100).toInt().toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(
            Triple("H", hue, { newVal: String -> hue = newVal }),
            Triple("S", saturation, { newVal: String -> saturation = newVal }),
            Triple("V", value, { newVal: String -> value = newVal })
        ).forEach { (label, inputValue, onValueChange) ->
            OutlinedTextField(
                value = inputValue,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                    val h = hue.toFloatOrNull()?.coerceIn(0f, 360f) ?: 0f
                    val s = (saturation.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f) / 100f
                    val v = (value.toFloatOrNull()?.coerceIn(0f, 100f) ?: 100f) / 100f
                    onColorChanged(Color.hsv(h, s, v))
                },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

// ==================== COMPACT VERSIONS ====================

/**
 * Compact color input methods (HEX/RGB only)
 */
@Composable
internal fun CompactColorInputMethods(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var inputMode by remember { mutableStateOf("HEX") }
    val modes = listOf("HEX", "RGB")

    Column {
        // Compact Input Mode Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            modes.forEach { mode ->
                FilterChip(
                    selected = inputMode == mode,
                    onClick = { inputMode = mode },
                    label = {
                        Text(
                            mode,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Compact Input Fields
        when (inputMode) {
            "HEX" -> CompactHexInput(color, onColorChanged)
            "RGB" -> CompactRGBInput(color, onColorChanged)
        }
    }
}

/**
 * Compact HEX input field
 */
@Composable
internal fun CompactHexInput(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var hexValue by remember(color) {
        mutableStateOf(String.format("#%06X", (color.toArgb() and 0xFFFFFF)))
    }

    OutlinedTextField(
        value = hexValue,
        onValueChange = { newHex ->
            hexValue = newHex
            if (newHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                try {
                    val colorInt = newHex.toColorInt()
                    onColorChanged(Color(colorInt))
                } catch (e: Exception) { /* Invalid color */ }
            }
        },
        label = {
            Text(
                "Hex Color",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        placeholder = {
            Text(
                "#FF0000",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp), // Standard height for better visibility
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium // Larger text for better readability
    )
}

/**
 * Compact RGB input fields
 */
@Composable
internal fun CompactRGBInput(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var red by remember(color) { mutableStateOf((color.red * 255).toInt().toString()) }
    var green by remember(color) { mutableStateOf((color.green * 255).toInt().toString()) }
    var blue by remember(color) { mutableStateOf((color.blue * 255).toInt().toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple("R", red, { newVal: String -> red = newVal }),
            Triple("G", green, { newVal: String -> green = newVal }),
            Triple("B", blue, { newVal: String -> blue = newVal })
        ).forEach { (label, value, onValueChange) ->
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    onValueChange(newValue)
                    val r = red.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    val g = green.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    val b = blue.toIntOrNull()?.coerceIn(0, 255) ?: 0
                    onColorChanged(Color(r, g, b))
                },
                label = {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp), // Standard height for better visibility
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
