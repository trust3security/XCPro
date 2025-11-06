package com.example.xcpro.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.dfcards.FlightModeSelection
import com.example.xcpro.FlightMode

fun getFlightModeColor(mode: FlightMode): Color {
    return when (mode) {
        FlightMode.CRUISE -> Color(0xFF2196F3)      // Blue
        FlightMode.THERMAL -> Color(0xFF9C27B0)     // Purple  
        FlightMode.FINAL_GLIDE -> Color(0xFFF44336) // Red
        FlightMode.HAWK -> Color(0xFF00BCD4) // Teal
    }
}

@Composable
fun ProfileIndicator(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val uiState by profileViewModel.uiState.collectAsState()
    val activeProfile = uiState.activeProfile
    
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activeProfile != null) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = activeProfile?.aircraftType?.icon ?: Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (activeProfile != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
            
            Text(
                text = activeProfile?.name ?: "No Profile",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (activeProfile != null) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

@Composable
fun ProfileQuickSwitcher(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val uiState by profileViewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    
    if (uiState.profiles.size <= 1) return // Don't show if only one or no profiles
    
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Switch Profile",
                modifier = Modifier.size(20.dp)
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(200.dp)
        ) {
            Text(
                text = "Switch Profile",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider()
            
            uiState.profiles.forEach { profile ->
                val isCurrentlyActive = uiState.activeProfile?.id == profile.id
                DropdownMenuItem(
                    onClick = {
                        profileViewModel.selectProfile(profile)
                        expanded = false
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = profile.aircraftType.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrentlyActive) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = profile.aircraftType.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (isCurrentlyActive) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "•",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    },
                    enabled = !isCurrentlyActive
                )
            }
            
            HorizontalDivider()
            
            DropdownMenuItem(
                onClick = {
                    navController.navigate("profile_selection")
                    expanded = false
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Manage Profiles")
                    }
                }
            )
        }
    }
}

@Composable
fun FlightModeIndicator(
    currentMode: com.example.xcpro.FlightMode,
    onModeChange: (com.example.xcpro.FlightMode) -> Unit,
    modifier: Modifier = Modifier,
    availableModes: List<com.example.xcpro.FlightMode>? = null,
    expandedOverride: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val uiState by profileViewModel.uiState.collectAsState()
    val activeProfile = uiState.activeProfile

    var visibleModes by remember { mutableStateOf(availableModes ?: com.example.xcpro.FlightMode.values().toList()) }
    var internalExpanded by remember { mutableStateOf(false) }
    val expanded = expandedOverride ?: internalExpanded
    val setExpanded: (Boolean) -> Unit = onExpandedChange ?: { internalExpanded = it }

    LaunchedEffect(activeProfile?.id, availableModes) {
        when {
            availableModes != null -> {
                visibleModes = availableModes
            }
            activeProfile != null -> {
                try {
                    val cardPreferences = com.example.dfcards.CardPreferences(context)
                    val visibilities = cardPreferences.getProfileAllFlightModeVisibilities(activeProfile.id).first()

                    val filteredModes = mutableListOf<com.example.xcpro.FlightMode>().apply {
                        add(com.example.xcpro.FlightMode.CRUISE)
                        if (visibilities["THERMAL"] != false) add(com.example.xcpro.FlightMode.THERMAL)
                        if (visibilities["FINAL_GLIDE"] != false) add(com.example.xcpro.FlightMode.FINAL_GLIDE)
                        if (visibilities["HAWK"] != false) add(com.example.xcpro.FlightMode.HAWK)
                    }

                    visibleModes = filteredModes

                    if (currentMode !in filteredModes) {
                        onModeChange(com.example.xcpro.FlightMode.CRUISE)
                    }
                } catch (_: Exception) {
                    visibleModes = com.example.xcpro.FlightMode.values().toList()
                }
            }
            else -> {
                visibleModes = com.example.xcpro.FlightMode.values().toList()
            }
        }
    }

    Box(
        modifier = modifier
    ) {
        Card(
            onClick = {
                android.util.Log.d(
                    "FlightModeIndicator",
                    "Card tapped; expanded=$expanded -> true"
                )
                setExpanded(true)
            },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(getFlightModeColor(currentMode))
                )

                Text(
                    text = currentMode.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                android.util.Log.d(
                    "FlightModeIndicator",
                    "Dropdown dismissed; expanded=$expanded -> false"
                )
                setExpanded(false)
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Color.White.copy(alpha = 0.95f),
                    RoundedCornerShape(12.dp)
                )
        ) {
            visibleModes.forEach { mode ->
                DropdownMenuItem(
                    onClick = {
                        onModeChange(mode)
                        android.util.Log.d(
                            "FlightModeIndicator",
                            "Mode selected (${mode.displayName}); collapsing dropdown"
                        )
                        setExpanded(false)
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (mode == currentMode) {
                                            getFlightModeColor(mode)
                                        } else {
                                            getFlightModeColor(mode).copy(alpha = 0.4f)
                                        }
                                    )
                            )
                            Text(
                                text = mode.displayName,
                                fontWeight = if (mode == currentMode) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                )
            }
        }
    }
}
