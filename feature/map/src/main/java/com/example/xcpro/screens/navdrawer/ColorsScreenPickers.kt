package com.example.xcpro.screens.navdrawer

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.ui.components.ModernColorPicker

/**
 * Colors Screen Pickers
 *
 * Extracted from ColorsScreen.kt for file size compliance.
 * Contains all color picker components and dialogs.
 */

/**
 * Single color picker (primary only)
 */
@Composable
internal fun SingleColorPicker(
    themeDisplayName: String,
    selectedColorType: String,
    selectedColor: Color,
    onColorChanged: (Color) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    val colorTypeLabel = selectedColorType.replaceFirstChar { it.uppercase() }
    Log.d("ColorsScreen", " SingleColorPicker opened for theme: $themeDisplayName, colorType: $selectedColorType")
    Log.d("ColorsScreen", " Initial color: $selectedColor")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 350.dp) // Ensure adequate height for color grid
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Edit $colorTypeLabel Color",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Theme: $themeDisplayName",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Single Color Row
        ColorPickerRow(
            label = "$colorTypeLabel Color",
            color = selectedColor,
            onColorChanged = onColorChanged
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onSave
            ) {
                Text("Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Custom color picker (full version with primary/secondary)
 */
@Composable
internal fun CustomColorPicker(
    themeDisplayName: String,
    primaryColor: Color,
    onPrimaryChanged: (Color) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit
) {
    Log.d("ColorsScreen", " CustomColorPicker opened for theme: $themeDisplayName")
    Log.d("ColorsScreen", " Initial primary color: $primaryColor")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Edit $themeDisplayName Colors",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Primary Color
        ColorPickerRow(
            label = "Primary Color",
            color = primaryColor,
            onColorChanged = onPrimaryChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Remove secondary color - only primary color

        Spacer(modifier = Modifier.height(16.dp))


        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Color picker row with preset colors and custom picker button
 */
@Composable
internal fun ColorPickerRow(
    label: String,
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    val predefinedColors = listOf(
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFF44336),
        Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFFEB3B), Color(0xFF795548),
        Color(0xFF607D8B), Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFFF5722),
        Color(0xFF673AB7), Color(0xFF009688), Color(0xFFFFC107), Color(0xFF212121)
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Predefined color options
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(80.dp)
        ) {
            items(predefinedColors) { presetColor ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(presetColor, CircleShape)
                        .border(
                            width = if (presetColor == color) 3.dp else 1.dp,
                            color = if (presetColor == color)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable {
                            Log.d("ColorsScreen", " Preset color clicked: $presetColor")
                            onColorChanged(presetColor)
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Color Button
        OutlinedButton(
            onClick = { showColorPicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Custom Color Picker")
        }
    }

    // Modern Color Picker Dialog
    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) {
                    Text("Done")
                }
            },
            title = {
                Text(
                    text = "Choose $label",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                ModernColorPicker(
                    initialColor = color,
                    onColorChanged = { newColor ->
                        Log.d("ColorsScreen", " ModernColorPicker color changed: $newColor")
                        onColorChanged(newColor)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
