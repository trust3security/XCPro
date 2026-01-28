package com.example.xcpro.screens.flightdata

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.WaypointData

private const val TAG = "HomeWaypointSelector"

/**
 * Home Waypoint Selector Module
 *
 * Dedicated component for selecting and managing the home waypoint.
 * Extracted from FlightDataWaypointsTab.kt for better modularity.
 */

@Composable
fun HomeWaypointSelector(
    availableWaypoints: List<WaypointData>,
    context: Context,
    autoFocus: Boolean = false,
    onAutoFocusConsumed: () -> Unit = {},
    onSearchFocused: () -> Unit = {}
) {
    var homeWaypointQuery by remember { mutableStateOf("") }
    var selectedHomeWaypoint by remember { mutableStateOf<WaypointData?>(null) }
    var showHomeWaypointDropdown by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // Handle auto-focus
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            Log.d(TAG, " AUTO-FOCUS TRIGGERED! Focusing home waypoint input...")
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            showHomeWaypointDropdown = true
            Log.d(TAG, " FOCUS REQUESTED AND DROPDOWN OPENED")
            onAutoFocusConsumed()
        }
    }

    // Load saved home waypoint on first composition
    LaunchedEffect(Unit) {
        val savedHome = loadHomeWaypoint(context)
        selectedHomeWaypoint = savedHome
    }

    // Filter waypoints - show first 5 alphabetically when empty
    val filteredHomeWaypoints = remember(availableWaypoints, homeWaypointQuery) {
        if (homeWaypointQuery.isBlank()) {
            availableWaypoints.sortedBy { it.name }.take(5)
        } else {
            availableWaypoints.filter { waypoint ->
                waypoint.name.contains(homeWaypointQuery, ignoreCase = true) ||
                        waypoint.code.contains(homeWaypointQuery, ignoreCase = true)
            }.take(5)
        }
    }

    // Auto-dismiss keyboard when only 1 result
    LaunchedEffect(filteredHomeWaypoints.size, homeWaypointQuery) {
        if (filteredHomeWaypoints.size == 1 && homeWaypointQuery.isNotBlank()) {
            focusManager.clearFocus()
        }
    }

    // Determine card color based on whether home waypoint is set
    val cardColor = if (selectedHomeWaypoint != null) {
        Color(0xFF34C759).copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        if (selectedHomeWaypoint != null) {
            // SELECTED STATE: Display selected home waypoint
            SelectedHomeWaypointDisplay(
                waypoint = selectedHomeWaypoint!!,
                onDelete = {
                    selectedHomeWaypoint = null
                    homeWaypointQuery = ""
                    saveHomeWaypoint(context, null)
                    Log.d(TAG, " Home waypoint deleted")
                }
            )
        } else {
            // SEARCH STATE: Home waypoint selection interface
            HomeWaypointSearchInterface(
                homeWaypointQuery = homeWaypointQuery,
                onQueryChange = { query ->
                    homeWaypointQuery = query
                    showHomeWaypointDropdown = true
                },
                filteredWaypoints = filteredHomeWaypoints,
                showDropdown = showHomeWaypointDropdown,
                onDropdownDismiss = { showHomeWaypointDropdown = false },
                onWaypointSelected = { selectedWaypoint ->
                    selectedHomeWaypoint = selectedWaypoint
                    homeWaypointQuery = ""
                    showHomeWaypointDropdown = false
                    saveHomeWaypoint(context, selectedWaypoint)
                    focusManager.clearFocus()
                    Log.d(TAG, " Home waypoint set to: ${selectedWaypoint.name}")
                },
                focusRequester = focusRequester,
                focusManager = focusManager,
                onSearchFocused = {
                    onSearchFocused()
                    showHomeWaypointDropdown = true
                }
            )
        }
    }
}

@Composable
private fun SelectedHomeWaypointDisplay(
    waypoint: WaypointData,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Home,
            contentDescription = "Home waypoint set",
            tint = Color(0xFF34C759),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (waypoint.name.length > 20) {
                    "${waypoint.name.take(20)}..."
                } else {
                    waypoint.name
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = "${waypoint.getStyleDescription()}  ${waypoint.elevation}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete home waypoint",
            modifier = Modifier
                .size(20.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDelete() },
            tint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun HomeWaypointSearchInterface(
    homeWaypointQuery: String,
    onQueryChange: (String) -> Unit,
    filteredWaypoints: List<WaypointData>,
    showDropdown: Boolean,
    onDropdownDismiss: () -> Unit,
    onWaypointSelected: (WaypointData) -> Unit,
    focusRequester: FocusRequester,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onSearchFocused: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = "Home waypoint",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Home Waypoint",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input
        Column {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicTextField(
                        value = homeWaypointQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onSearchFocused()
                                }
                            },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (homeWaypointQuery.isEmpty()) {
                                Text(
                                    text = "Enter home waypoint here...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (homeWaypointQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onQueryChange("")
                                onDropdownDismiss()
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Dropdown Results
            AnimatedVisibility(
                visible = showDropdown && filteredWaypoints.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column {
                        filteredWaypoints.forEach { waypoint ->
                            HomeWaypointItem(
                                waypoint = waypoint,
                                onWaypointSelected = onWaypointSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWaypointItem(
    waypoint: WaypointData,
    onWaypointSelected: (WaypointData) -> Unit
) {
    Surface(
        onClick = { onWaypointSelected(waypoint) },
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = waypoint.getTypeIcon(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = waypoint.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (waypoint.code.isNotBlank()) {
                        Text(
                            text = waypoint.code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "${waypoint.getStyleDescription()}  ${waypoint.elevation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
