package com.example.xcpro.appshell.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.xcpro.igc.ui.IGC_FILES_LABEL
import com.example.xcpro.map.R

internal const val GENERAL_SETTINGS_GRID_TAG = "general_settings_grid"

@Composable
internal fun GeneralSettingsCategoryGrid(
    onSubSheetSelected: (GeneralSubSheet) -> Unit
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag(GENERAL_SETTINGS_GRID_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Files",
                            icon = Icons.Default.Folder,
                            onClick = { onSubSheetSelected(GeneralSubSheet.FILES) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Profiles",
                            icon = Icons.Default.Map,
                            onClick = { onSubSheetSelected(GeneralSubSheet.PROFILES) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Look & Feel",
                            icon = Icons.Outlined.Style,
                            onClick = { onSubSheetSelected(GeneralSubSheet.LOOK_AND_FEEL) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Units",
                            icon = Icons.Default.Straighten,
                            onClick = { onSubSheetSelected(GeneralSubSheet.UNITS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Polar",
                            icon = Icons.Default.Flight,
                            onClick = { onSubSheetSelected(GeneralSubSheet.POLAR) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Levo Vario",
                            icon = Icons.Default.Speed,
                            onClick = { onSubSheetSelected(GeneralSubSheet.LEVO_VARIO) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "HAWK Vario",
                            icon = Icons.Default.Speed,
                            onClick = { onSubSheetSelected(GeneralSubSheet.HAWK_VARIO) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Orientation",
                            icon = Icons.Default.Explore,
                            onClick = { onSubSheetSelected(GeneralSubSheet.ORIENTATION) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Layouts",
                            icon = Icons.Default.GridView,
                            onClick = { onSubSheetSelected(GeneralSubSheet.LAYOUTS) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "WeGlide",
                            icon = Icons.Default.Map,
                            onClick = { onSubSheetSelected(GeneralSubSheet.WEGLIDE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItemDrawable(
                            title = "SkySight",
                            iconResId = R.drawable.ic_skysight,
                            iconSize = 26.4.dp,
                            onClick = { onSubSheetSelected(GeneralSubSheet.SKYSIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Hotspots",
                            icon = Icons.Default.Speed,
                            onClick = { onSubSheetSelected(GeneralSubSheet.HOTSPOTS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItemDrawable(
                            title = "RainViewer",
                            iconResId = R.drawable.rainviewer,
                            iconSize = 29.04.dp,
                            onClick = { onSubSheetSelected(GeneralSubSheet.WEATHER) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "OGN",
                            icon = Icons.Default.Flight,
                            onClick = { onSubSheetSelected(GeneralSubSheet.OGN) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Thermalling",
                            icon = Icons.Default.Explore,
                            onClick = { onSubSheetSelected(GeneralSubSheet.THERMALLING) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "ADS-b",
                            icon = Icons.Default.AirplanemodeActive,
                            onClick = { onSubSheetSelected(GeneralSubSheet.ADSB) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Navboxes",
                            icon = Icons.Default.Dashboard,
                            onClick = { onSubSheetSelected(GeneralSubSheet.NAVBOXES) },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = IGC_FILES_LABEL,
                            icon = Icons.Default.PlayArrow,
                            onClick = { onSubSheetSelected(GeneralSubSheet.IGC_REPLAY) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
