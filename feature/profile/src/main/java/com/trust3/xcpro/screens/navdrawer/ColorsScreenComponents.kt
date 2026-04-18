package com.trust3.xcpro.screens.navdrawer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.ui.theme.AppColorTheme

/**
 * Colors Screen Components
 *
 * Extracted from ColorsScreen.kt for file size compliance.
 * Contains all preview and card components for color theme selection.
 */

/**
 * Current theme preview card with color selection and edit button
 */
@Composable
internal fun CurrentThemePreview(
    theme: AppColorTheme,
    primaryColor: Color,
    secondaryColor: Color,
    selectedColorType: String,
    onColorSelected: (String) -> Unit,
    onEditColors: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Current Theme: ${theme.displayName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = theme.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Color Preview Row - Clickable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SelectableColorPreviewCircle(
                    color = primaryColor,
                    label = "Primary",
                    colorType = "primary",
                    isSelected = selectedColorType == "primary",
                    onColorSelected = onColorSelected
                )
                SelectableColorPreviewCircle(
                    color = secondaryColor,
                    label = "Secondary",
                    colorType = "secondary",
                    isSelected = selectedColorType == "secondary",
                    onColorSelected = onColorSelected
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Edit Selected Color Button
            Button(
                onClick = onEditColors,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit ${selectedColorType.replaceFirstChar { it.uppercase() }} Color")
            }
        }
    }
}

/**
 * Color theme selection card with colors preview
 */
@Composable
internal fun ColorThemeCard(
    theme: AppColorTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primary = theme.primaryColor
    val secondary = theme.secondaryColor

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Color circles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(primary, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(secondary, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
    }
}

/**
 * Selectable color preview circle (primary/secondary selector)
 */
@Composable
internal fun SelectableColorPreviewCircle(
    color: Color,
    label: String,
    colorType: String,
    isSelected: Boolean,
    onColorSelected: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onColorSelected(colorType) }
    ) {
        Box(
            modifier = Modifier
                .size(if (isSelected) 44.dp else 40.dp) // Larger when selected
                .background(color, CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = if (isSelected) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Simple color preview circle with label
 */
@Composable
internal fun ColorPreviewCircle(
    color: Color,
    label: String,
    isLarge: Boolean = false
) {
    val size = if (isLarge) 50.dp else 40.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}
