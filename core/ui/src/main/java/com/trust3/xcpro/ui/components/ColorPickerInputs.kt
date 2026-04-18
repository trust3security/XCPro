package com.trust3.xcpro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

@Composable
internal fun ColorInputMethods(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var inputMode by remember { mutableStateOf("HEX") }
    val modes = listOf("HEX", "RGB", "HSV")

    Column {
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

        when (inputMode) {
            "HEX" -> HexInput(color, onColorChanged)
            "RGB" -> RGBInput(color, onColorChanged)
            "HSV" -> HSVInput(color, onColorChanged)
        }
    }
}

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
                    onColorChanged(Color(newHex.toColorInt()))
                } catch (_: Exception) {
                }
            }
        },
        label = { Text("Hex Color") },
        placeholder = { Text("#FF0000") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

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

@Composable
internal fun HSVInput(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var hue by remember(color) { mutableStateOf(getHue(color).toInt().toString()) }
    var saturation by remember(color) {
        mutableStateOf((getSaturation(color) * 100).toInt().toString())
    }
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

@Composable
internal fun CompactColorInputMethods(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var inputMode by remember { mutableStateOf("HEX") }
    val modes = listOf("HEX", "RGB")

    Column {
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

        when (inputMode) {
            "HEX" -> CompactHexInput(color, onColorChanged)
            "RGB" -> CompactRGBInput(color, onColorChanged)
        }
    }
}

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
                    onColorChanged(Color(newHex.toColorInt()))
                } catch (_: Exception) {
                }
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
            .height(56.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

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
                    .height(56.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
