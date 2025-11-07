package com.example.xcpro.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.toSearchWaypoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableWaypointField(
    currentWaypoint: SearchWaypoint,
    allWaypoints: List<WaypointData>,
    onWaypointSelected: (SearchWaypoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchTextFieldValue by remember { mutableStateOf(TextFieldValue(currentWaypoint.title)) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<SearchWaypoint>()) }
    val focusRequester = remember { FocusRequester() }

    // Update search text when currentWaypoint changes
    LaunchedEffect(currentWaypoint) {
        searchTextFieldValue = TextFieldValue(currentWaypoint.title)
    }

    Column(modifier = modifier) {
        // Search field
        OutlinedTextField(
            value = searchTextFieldValue,
            onValueChange = { newTextFieldValue: TextFieldValue ->
                searchTextFieldValue = newTextFieldValue
                val newText = newTextFieldValue.text
                isSearchExpanded = newText.isNotBlank() && newText != currentWaypoint.title

                // Filter waypoints based on search
                searchResults = if (newText.length >= 2) {
                    allWaypoints.filter { wp ->
                        wp.name.contains(newText, ignoreCase = true) ||
                        wp.code.contains(newText, ignoreCase = true)
                    }.take(5).map { it.toSearchWaypoint() }
                } else emptyList()
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        searchTextFieldValue = searchTextFieldValue.copy(
                            selection = TextRange(0, searchTextFieldValue.text.length)
                        )
                    }
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

        // Dropdown with search results
        if (isSearchExpanded && searchResults.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-4).dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(searchResults) { result ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (result.subtitle.isNotBlank()) {
                                        Text(
                                            text = result.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onWaypointSelected(result)
                                searchTextFieldValue = TextFieldValue(result.title)
                                isSearchExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
