package com.trust3.xcpro.livefollow.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun FriendsFlyingBrowseTabContent(
    tabUiState: FriendsFlyingTabUiState,
    selectedWatchKey: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPilotSelected: (FriendsFlyingPilotSelection) -> Unit
) {
    val filteredPilots = filterFriendsFlyingPilots(
        pilots = tabUiState.pilots,
        rawQuery = searchQuery
    )
    val filteredActivePilots = filteredPilots.filterNot(FriendsFlyingPilotRowUiModel::isStale)
    val filteredStalePilots = filteredPilots.filter(FriendsFlyingPilotRowUiModel::isStale)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item("search") {
            FriendsFlyingSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClearSearch = onClearSearch
            )
        }
        item("actions") {
            FriendsFlyingSheetActions(
                message = tabUiState.message,
                isError = tabUiState.isError,
                canRefresh = tabUiState.canRefresh,
                onRefresh = onRefresh
            )
        }

        when {
            tabUiState.isLoading && tabUiState.pilots.isEmpty() -> {
                item("loading") {
                    FriendsFlyingLoadingState(
                        message = tabUiState.message ?: "Loading pilots..."
                    )
                }
            }

            tabUiState.pilots.isEmpty() -> {
                item("empty") {
                    FriendsFlyingMessageState(
                        message = tabUiState.message ?: "No pilots available right now.",
                        isError = tabUiState.isError
                    )
                }
            }

            filteredPilots.isEmpty() -> {
                item("no-results") {
                    FriendsFlyingMessageState(
                        message = "No pilots match \"$searchQuery\".",
                        isError = false
                    )
                }
            }

            else -> {
                if (tabUiState.isLoading) {
                    item("refreshing") {
                        FriendsFlyingLoadingState(
                            message = "Refreshing pilots...",
                            compact = true
                        )
                    }
                }
                if (filteredActivePilots.isNotEmpty()) {
                    item("active-header") {
                        FriendsFlyingSectionHeader("Active pilots")
                    }
                    items(
                        items = filteredActivePilots,
                        key = { it.watchKey }
                    ) { pilot ->
                        FriendsFlyingPilotRow(
                            pilot = pilot,
                            isSelected = pilot.watchKey == selectedWatchKey,
                            onClick = { onPilotSelected(pilot.toSelection()) }
                        )
                        HorizontalDivider()
                    }
                }
                if (filteredStalePilots.isNotEmpty()) {
                    item("stale-header") {
                        FriendsFlyingSectionHeader("Recently active")
                    }
                    items(
                        items = filteredStalePilots,
                        key = { it.watchKey }
                    ) { pilot ->
                        FriendsFlyingPilotRow(
                            pilot = pilot,
                            isSelected = pilot.watchKey == selectedWatchKey,
                            onClick = { onPilotSelected(pilot.toSelection()) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsFlyingSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        singleLine = true,
        label = { Text("Search pilots") },
        placeholder = { Text("Name, share code, or session") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search pilots"
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        }
    )
}

@Composable
private fun FriendsFlyingSheetActions(
    message: String?,
    isError: Boolean,
    canRefresh: Boolean,
    onRefresh: () -> Unit
) {
    if (message == null && !canRefresh) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        message?.let { helperText ->
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f)
            )
        }
        if (canRefresh) {
            TextButton(onClick = onRefresh) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun FriendsFlyingLoadingState(
    message: String,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun FriendsFlyingMessageState(
    message: String,
    isError: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FriendsFlyingSectionHeader(
    label: String
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}
