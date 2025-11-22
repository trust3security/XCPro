package com.example.xcpro.screens.navdrawer.tasks

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomAppBar
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.android.maps.MapLibreMap

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TaskFilesBottomSheetContent(
    selectedItem: String?,
    onSelectItem: (String) -> Unit,
    context: Context,
    mapLibreMap: MapLibreMap?,
    airspaceFilePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    waypointFilePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    errorMessage: String?,
    onErrorMessage: (String?) -> Unit,
    selectedAirspaceFiles: MutableList<Uri>,
    airspaceCheckedStates: MutableState<MutableMap<String, Boolean>>,
    selectedWaypointFiles: MutableList<Uri>,
    waypointCheckedStates: MutableState<MutableMap<String, Boolean>>,
    selectedClasses: MutableState<MutableMap<String, Boolean>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color(0xFFF5F5F5))
            .padding(bottom = 54.dp)
    ) {
        TaskFilesSheetHandle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            when (selectedItem) {
                "Airspace" -> AirspaceSection(
                    context = context,
                    mapLibreMap = mapLibreMap,
                    airspaceFilePickerLauncher = airspaceFilePickerLauncher,
                    errorMessage = errorMessage,
                    onErrorMessage = onErrorMessage,
                    selectedAirspaceFiles = selectedAirspaceFiles,
                    airspaceCheckedStates = airspaceCheckedStates
                )

                "Waypoints" -> WaypointSection(
                    context = context,
                    mapLibreMap = mapLibreMap,
                    waypointFilePickerLauncher = waypointFilePickerLauncher,
                    errorMessage = errorMessage,
                    onErrorMessage = onErrorMessage,
                    selectedWaypointFiles = selectedWaypointFiles,
                    waypointCheckedStates = waypointCheckedStates
                )

                "Classes" -> AirspaceClassesSection(
                    context = context,
                    mapLibreMap = mapLibreMap,
                    airspaceFiles = selectedAirspaceFiles,
                    selectedClasses = selectedClasses
                )

                else -> Text(
                    text = selectedItem ?: "No Item Selected",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 32.dp),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun TaskFilesBottomBar(
    selectedItem: String?,
    onItemClick: (String) -> Unit
) {
    BottomAppBar(
        modifier = Modifier.height(54.dp),
        elevation = 8.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            listOf("Airspace", "Waypoints", "Classes").forEach { itemName ->
                Column(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clickable { onItemClick(itemName) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Map,
                        contentDescription = itemName,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Text(
                        text = itemName,
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TaskFilesSheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .padding(top = 8.dp)
            .background(
                color = Color.Black.copy(alpha = 0.2f),
                shape = RoundedCornerShape(2.dp)
            )
    )
}
