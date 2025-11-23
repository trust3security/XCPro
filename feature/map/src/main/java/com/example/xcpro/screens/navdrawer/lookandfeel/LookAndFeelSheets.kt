package com.example.xcpro.screens.navdrawer.lookandfeel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.ui.theme.AppColorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusBarStyleSheet(
    showSheet: MutableState<Boolean>,
    currentStyle: StatusBarStyle,
    onStyleSelected: (StatusBarStyle) -> Unit
) {
    if (showSheet.value) {
        ModalBottomSheet(onDismissRequest = { showSheet.value = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Status Bar Style",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                StatusBarStyle.values().forEachIndexed { index, style ->
                    StatusBarStyleOption(
                        style = style,
                        isSelected = currentStyle == style,
                        onSelect = {
                            onStyleSelected(style)
                            showSheet.value = false
                        }
                    )
                    if (index != StatusBarStyle.values().lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardStyleSheet(
    showSheet: MutableState<Boolean>,
    currentStyle: CardStyle,
    onStyleSelected: (CardStyle) -> Unit
) {
    if (showSheet.value) {
        ModalBottomSheet(onDismissRequest = { showSheet.value = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Card Style",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                CardStyle.values().forEachIndexed { index, style ->
                    CardStyleOption(
                        style = style,
                        isSelected = currentStyle == style,
                        onSelect = {
                            onStyleSelected(style)
                            showSheet.value = false
                        }
                    )
                    if (index != CardStyle.values().lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorThemeSheet(
    showSheet: MutableState<Boolean>,
    currentTheme: AppColorTheme,
    onThemeSelected: (AppColorTheme) -> Unit,
    onNavigateToColors: () -> Unit
) {
    if (showSheet.value) {
        ModalBottomSheet(onDismissRequest = { showSheet.value = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Quick Color Themes",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                val quickThemes = listOf(
                    AppColorTheme.DEFAULT,
                    AppColorTheme.AVIATION,
                    AppColorTheme.FOREST,
                    AppColorTheme.SUNSET,
                    AppColorTheme.OCEAN,
                    AppColorTheme.PURPLE
                )
                LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    items(quickThemes) { theme ->
                        ColorThemeRow(
                            theme = theme,
                            selected = theme == currentTheme,
                            onClick = {
                                onThemeSelected(theme)
                                showSheet.value = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        showSheet.value = false
                        onNavigateToColors()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                ) {
                    Icon(imageVector = Icons.Filled.Palette, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("More Color Options")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ColorThemeRow(
    theme: AppColorTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected) ButtonDefaults.outlinedButtonBorder(enabled = true) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = theme.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                text = theme.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
