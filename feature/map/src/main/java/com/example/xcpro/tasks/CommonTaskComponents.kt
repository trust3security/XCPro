package com.example.xcpro.tasks


import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.units.DistanceM
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.toSearchWaypoint
import androidx.compose.ui.focus.focusRequester

/**
 * Common UI components shared between task types.
 *  SEPARATION COMPLIANT: No task-specific imports or logic
 */

@Composable
fun TaskStatsSection(
    task: Task,
    taskType: TaskType,
    distanceMeters: Double,
    unitsPreferences: UnitsPreferences,
    onQRCodeClick: () -> Unit
) {
    val formattedDistance = UnitsFormatter.distance(
        distance = DistanceM(distanceMeters),
        preferences = unitsPreferences
    ).text

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TaskStatItem(
            label = "Distance",
            value = formattedDistance,
            icon = Icons.Default.Straighten
        )

        TaskQRCodeItem(
            onQRCodeClick = onQRCodeClick
        )

        TaskStatItem(
            label = "Task",
            value = when (taskType) {
                TaskType.RACING -> "Racing"
                TaskType.AAT -> "AAT"
            },
            icon = Icons.Default.CheckCircle
        )
    }
}

@Composable
private fun TaskStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TaskQRCodeItem(
    onQRCodeClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onQRCodeClick() }
    ) {
        Icon(
            Icons.Default.QrCode,
            contentDescription = "Share Task QR Code",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Share",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "QR Code",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AdvanceControls(
    snapshot: TaskAdvanceUiSnapshot,
    onModeChange: (TaskAdvanceUiSnapshot.Mode) -> Unit,
    onToggleArm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                modifier = Modifier.testTag(ADVANCE_MANUAL_CHIP_TAG),
                onClick = { onModeChange(TaskAdvanceUiSnapshot.Mode.MANUAL) },
                label = { Text("Manual") },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (snapshot.mode == TaskAdvanceUiSnapshot.Mode.MANUAL) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                )
            )
            AssistChip(
                modifier = Modifier.testTag(ADVANCE_AUTO_CHIP_TAG),
                onClick = { onModeChange(TaskAdvanceUiSnapshot.Mode.AUTO) },
                label = { Text("Auto") },
                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (snapshot.mode == TaskAdvanceUiSnapshot.Mode.AUTO) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                )
            )
            AssistChip(
                modifier = Modifier.testTag(ADVANCE_ARM_CHIP_TAG),
                onClick = onToggleArm,
                label = { Text(if (snapshot.isArmed) "Armed" else "Disarmed") },
                leadingIcon = {
                    Icon(
                        if (snapshot.isArmed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (snapshot.isArmed) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistentWaypointSearchBar(
    allWaypoints: List<WaypointData>,
    onWaypointSelected: (SearchWaypoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<SearchWaypoint>()) }
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier) {
        // Search field - always visible at bottom
        OutlinedTextField(
            value = searchTextFieldValue,
            onValueChange = { newTextFieldValue: TextFieldValue ->
                searchTextFieldValue = newTextFieldValue
                val newText = newTextFieldValue.text
                isSearchExpanded = newText.isNotBlank()

                // Filter waypoints based on search
                searchResults = if (newText.length >= 2) {
                    allWaypoints.filter { wp ->
                        wp.name.contains(newText, ignoreCase = true) ||
                        wp.code.contains(newText, ignoreCase = true)
                    }.take(5).map { it.toSearchWaypoint() }
                } else emptyList()
            },
            modifier = Modifier
                .testTag(PERSISTENT_WAYPOINT_SEARCH_FIELD_TAG)
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        searchTextFieldValue = searchTextFieldValue.copy(
                            selection = TextRange(0, searchTextFieldValue.text.length)
                        )
                    }
                },
            placeholder = {
                Text(
                    text = "Search waypoints to add to task...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            textStyle = MaterialTheme.typography.titleSmall,
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        )

        // Dropdown with search results - appears above search bar
        if (isSearchExpanded && searchResults.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-4).dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(searchResults) { result ->
                        DropdownMenuItem(
                        text = {
                                Text(
                                    text = result.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onWaypointSelected(result)
                                // Clear search after selection
                                searchTextFieldValue = TextFieldValue("")
                                isSearchExpanded = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

internal const val ADVANCE_MANUAL_CHIP_TAG = "advance_manual_chip"
internal const val ADVANCE_AUTO_CHIP_TAG = "advance_auto_chip"
internal const val ADVANCE_ARM_CHIP_TAG = "advance_arm_chip"
internal const val PERSISTENT_WAYPOINT_SEARCH_FIELD_TAG = "persistent_waypoint_search_field"
