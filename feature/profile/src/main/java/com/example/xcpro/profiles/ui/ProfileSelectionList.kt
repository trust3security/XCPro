package com.example.xcpro.profiles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xcpro.profiles.AircraftType
import com.example.xcpro.profiles.UserProfile

private const val FILTER_ALL = "All"

@Composable
internal fun ProfileListSection(
    profiles: List<UserProfile>,
    activeProfileId: String?,
    onSelectProfile: (UserProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onShowCreateDialog: () -> Unit,
    onEditProfile: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val groupedProfiles = remember(profiles) { profiles.groupBy { it.aircraftType } }
    val availableTypes = remember(groupedProfiles) {
        AircraftType.entries.filter { groupedProfiles[it].orEmpty().isNotEmpty() }
    }

    val filteredProfiles = remember(profiles, selectedFilter, searchQuery) {
        profiles
            .asSequence()
            .filter { profile ->
                selectedFilter == FILTER_ALL || profile.aircraftType.displayName == selectedFilter
            }
            .filter { profile ->
                searchQuery.isBlank() ||
                    profile.name.contains(searchQuery, ignoreCase = true) ||
                    (profile.aircraftModel?.contains(searchQuery, ignoreCase = true) == true)
            }
            .sortedWith(compareBy<UserProfile> { it.id != activeProfileId }.thenBy { it.name.lowercase() })
            .toList()
    }

    val groupedFilteredProfiles = remember(filteredProfiles) { filteredProfiles.groupBy { it.aircraftType } }
    val orderedFilteredTypes = remember(groupedFilteredProfiles) {
        AircraftType.entries.filter { groupedFilteredProfiles.containsKey(it) }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        item {
            ProfileFilterBar(
                query = searchQuery,
                onQueryChanged = { searchQuery = it },
                selectedFilter = selectedFilter,
                availableTypes = availableTypes,
                profileCounts = groupedProfiles,
                onFilterSelected = { selectedFilter = it }
            )
        }

        if (filteredProfiles.isEmpty()) {
            item {
                EmptyFilterState(
                    onReset = {
                        searchQuery = ""
                        selectedFilter = FILTER_ALL
                    }
                )
            }
        } else {
            orderedFilteredTypes.forEach { aircraftType ->
                val sectionProfiles = groupedFilteredProfiles[aircraftType].orEmpty()

                item {
                    AircraftTypeSectionHeader(
                        title = aircraftType.displayName,
                        profileCount = sectionProfiles.size
                    )
                }

                items(sectionProfiles, key = { it.id }) { profile ->
                    ProfileListItem(
                        profile = profile,
                        isActive = profile.id == activeProfileId,
                        onSelect = { onSelectProfile(profile) },
                        onDelete = { onDeleteProfile(profile.id) },
                        onEdit = { onEditProfile(profile) }
                    )
                }
            }
        }

        item {
            CreateProfileCallout(onClick = onShowCreateDialog)
        }
    }
}

@Composable
private fun ProfileFilterBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    selectedFilter: String,
    availableTypes: List<AircraftType>,
    profileCounts: Map<AircraftType, List<UserProfile>>,
    onFilterSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Search profile or aircraft model") },
            singleLine = true
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                FilterChip(
                    selected = selectedFilter == FILTER_ALL,
                    onClick = { onFilterSelected(FILTER_ALL) },
                    label = { Text("All (${profileCounts.values.sumOf { it.size }})") }
                )
            }
            items(availableTypes, key = { it.name }) { type ->
                val count = profileCounts[type].orEmpty().size
                FilterChip(
                    selected = selectedFilter == type.displayName,
                    onClick = { onFilterSelected(type.displayName) },
                    label = { Text("${type.displayName} ($count)") }
                )
            }
        }
    }
}

@Composable
private fun EmptyFilterState(
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No profiles match this filter", style = MaterialTheme.typography.titleSmall)
            Text(
                "Try a different aircraft type or clear search.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Reset filters",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = onReset)
            )
        }
    }
}

@Composable
private fun AircraftTypeSectionHeader(
    title: String,
    profileCount: Int
) {
    Text(
        text = "$title ($profileCount)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileListItem(
    profile: UserProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = profile.aircraftType.icon(),
                            contentDescription = null,
                            tint = if (isActive) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            text = profile.getDisplayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Profile",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val label = if (isActive) "ACTIVE NOW" else "TAP TO SWITCH"
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
        }
    }
}

@Composable
private fun CreateProfileCallout(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Profile",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Create New Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
