package com.trust3.xcpro.screens.navdrawer.lookandfeel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.map.trail.TrailLength
import com.trust3.xcpro.map.trail.TrailSettings
import com.trust3.xcpro.map.trail.TrailType
import com.trust3.xcpro.ui.theme.AppColorTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SnailTrailSheet(
    showSheet: MutableState<Boolean>,
    currentSettings: TrailSettings,
    onLengthSelected: (TrailLength) -> Unit,
    onTypeSelected: (TrailType) -> Unit,
    onWindDriftChanged: (Boolean) -> Unit,
    onScalingChanged: (Boolean) -> Unit
) {
    if (showSheet.value) {
        ModalBottomSheet(onDismissRequest = { showSheet.value = false }) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Snail Trail",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                item {
                    SectionHeader(title = "Trail Length")
                }
                items(
                    listOf(
                        TrailLength.FULL,
                        TrailLength.LONG,
                        TrailLength.MEDIUM,
                        TrailLength.SHORT,
                        TrailLength.OFF
                    )
                ) { length ->
                    RadioRow(
                        title = trailLengthLabel(length),
                        description = when (length) {
                            TrailLength.FULL -> "Show all recorded points."
                            TrailLength.LONG -> "Show the last 60 minutes."
                            TrailLength.MEDIUM -> "Show the last 30 minutes."
                            TrailLength.SHORT -> "Show the last 10 minutes."
                            TrailLength.OFF -> "Disable trail rendering."
                        },
                        selected = currentSettings.length == length,
                        onSelect = { onLengthSelected(length) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(title = "Trail Style")
                }

                items(trailTypeOptions) { option ->
                    RadioRow(
                        title = option.title,
                        description = option.description,
                        selected = currentSettings.type == option.type,
                        onSelect = { onTypeSelected(option.type) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(title = "Behavior")
                }

                item {
                    ToggleRow(
                        title = "Wind drift",
                        description = "Drift the trail with wind when circling.",
                        checked = currentSettings.windDriftEnabled,
                        onCheckedChange = onWindDriftChanged
                    )
                }

                item {
                    ToggleRow(
                        title = "Width scaling",
                        description = "Scale trail width based on lift.",
                        checked = currentSettings.scalingEnabled,
                        onCheckedChange = onScalingChanged
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private data class TrailTypeOption(
    val type: TrailType,
    val title: String,
    val description: String
)

private val trailTypeOptions = listOf(
    TrailTypeOption(
        type = TrailType.VARIO_1,
        title = "Vario 1",
        description = "Green lift, brown sink, gray near zero."
    ),
    TrailTypeOption(
        type = TrailType.VARIO_1_DOTS,
        title = "Vario 1 dots",
        description = "Dotted sink, solid lift."
    ),
    TrailTypeOption(
        type = TrailType.VARIO_2,
        title = "Vario 2",
        description = "Black/blue sink, yellow near zero, green -> orange -> red -> purple lift."
    ),
    TrailTypeOption(
        type = TrailType.VARIO_2_DOTS,
        title = "Vario 2 dots",
        description = "Dotted sink with the Vario 2 palette."
    ),
    TrailTypeOption(
        type = TrailType.VARIO_DOTS_AND_LINES,
        title = "Dots + lines",
        description = "Dots with lines using the Vario 2 palette."
    ),
    TrailTypeOption(
        type = TrailType.VARIO_EINK,
        title = "Vario E-ink",
        description = "Monochrome dots and lines."
    ),
    TrailTypeOption(
        type = TrailType.ALTITUDE,
        title = "Altitude",
        description = "Colors follow altitude."
    )
)

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun RadioRow(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
        border = if (selected) ButtonDefaults.outlinedButtonBorder(enabled = true) else null,
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
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

