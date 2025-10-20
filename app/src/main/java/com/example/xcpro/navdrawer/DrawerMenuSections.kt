package com.example.xcpro.navdrawer

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.ui1.icons.ActivityLog
import com.example.ui1.icons.Hangglider
import com.example.ui1.icons.Task
import com.example.xcpro.loadHomeWaypoint
import com.example.xcpro.profiles.ProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Profile section with aircraft selection and logbook
 */
@Composable
fun ProfileSection(
    profileViewModel: ProfileViewModel,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    val uiState by profileViewModel.uiState.collectAsState()
    val activeProfile = uiState.activeProfile
    val profileTitle = activeProfile?.let { "${it.name} (${it.aircraftType.displayName})" } ?: "No Profile Selected"

    var selectedAircraftModel by remember { mutableStateOf<String?>(null) }
    var aircraftExpanded by remember { mutableStateOf(false) }

    ModernExpandableSection(
        title = profileTitle,
        icon = activeProfile?.aircraftType?.icon ?: Icons.Outlined.Person,
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        // Quick Profile Actions
        ModernNavItem(
            title = "Switch Profile",
            icon = Icons.Outlined.SwitchAccount,
            indentLevel = 1,
            onClick = {
                scope.launch {
                    drawerState.close()
                    navController.navigate("profile_selection")
                }
            }
        )

        ModernNavItem(
            title = "Manage Profiles",
            icon = Icons.Outlined.Person,
            indentLevel = 1,
            onClick = {
                scope.launch {
                    drawerState.close()
                    navController.navigate("profiles")
                }
            }
        )

        // Aircraft Subsection
        ModernExpandableSubItem(
            title = selectedAircraftModel ?: "Aircraft",
            icon = Icons.Outlined.Flight,
            isExpanded = aircraftExpanded,
            onToggle = { aircraftExpanded = !aircraftExpanded },
            isSubItem = true
        ) {
            listOf(
                "Sailplanes" to Icons.Outlined.Flight,
                "Paraglider" to Icons.Outlined.Paragliding,
                "Hangglider" to Hangglider
            ).forEach { (aircraftType, icon) ->
                ModernNavItem(
                    title = aircraftType,
                    icon = icon,
                    isSelected = selectedAircraftModel?.contains(aircraftType) == true,
                    indentLevel = 2,
                    onClick = {
                        scope.launch {
                            selectedAircraftModel = aircraftType
                            aircraftExpanded = false
                            drawerState.close()
                            when (aircraftType) {
                                "Sailplanes" -> navController.navigate("sailplanes")
                                "Paraglider" -> navController.navigate("paragliders")
                                "Hangglider" -> navController.navigate("hanggliders")
                            }
                        }
                    }
                )
            }
        }

        // Logbook Item
        ModernNavItem(
            title = "Logbook",
            icon = ActivityLog,
            indentLevel = 1,
            onClick = {
                scope.launch {
                    drawerState.close()
                    navController.navigate("logbook")
                }
            }
        )
    }
}

/**
 * Task section with home waypoint and add task
 */
@Composable
fun TaskSection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope,
    onItemSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current

    // Home waypoint state tracking (NO ANIMATION)
    var homeWaypointName by remember { mutableStateOf<String?>(null) }

    // Load home waypoint name on first composition
    LaunchedEffect(Unit) {
        val savedHome = loadHomeWaypoint(context)
        homeWaypointName = savedHome?.name
    }

    // Listen for home waypoint updates
    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("HomeWaypointPrefs", Context.MODE_PRIVATE)

        // Poll for changes every second
        while (true) {
            val currentName = sharedPrefs.getString("current_home_waypoint", null)
            if (currentName != homeWaypointName) {
                homeWaypointName = currentName
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "home_pulse")

    val homeIconColor = if (homeWaypointName == null) {
        // Strong pulsing red when no home waypoint
        infiniteTransition.animateColor(
            initialValue = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
            targetValue = MaterialTheme.colorScheme.error,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "home_pulse_color"
        ).value
    } else {
        // Static gray when home waypoint is set
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    ModernExpandableSection(
        title = "Task",
        icon = Task,
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        ModernNavItem(
            title = homeWaypointName?.let { name ->
                if (name.length > 15) "${name.take(15)}..." else name
            } ?: "Select Home",
            icon = Icons.Outlined.Home,
            indentLevel = 1,
            onClick = {
                scope.launch {
                    drawerState.close()
                    if (homeWaypointName == null) {
                        navController.navigate("flight_data/waypoints?autoFocusHome=true")
                    } else {
                        navController.navigate("flight_data/waypoints?autoFocusHome=false")
                    }
                }
            },
            iconTint = homeIconColor
        )

        ModernNavItem(
            title = "Add Task",
            icon = Icons.Outlined.Add,
            indentLevel = 1,
            onClick = {
                android.util.Log.d("TaskSection", "🎯 Add Task clicked!")
                scope.launch {
                    drawerState.close()
                    android.util.Log.d("TaskSection", "🎯 Calling onItemSelected with 'add_task'")
                    onItemSelected("add_task")
                }
            }
        )
    }
}

/**
 * Map style section with radio selection
 */
@Composable
fun MapStyleSection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    selectedMapStyle: String,
    onMapStyleSelected: (String) -> Unit
) {
    ModernExpandableSection(
        title = "Map Style",
        icon = Icons.Outlined.Map,
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        listOf(
            "Topo" to Icons.Outlined.Terrain,
            "Satellite" to Icons.Outlined.Satellite,
            "Terrain" to Icons.Outlined.Landscape
        ).forEach { (style, icon) ->
            ModernRadioItem(
                title = style,
                icon = icon,
                isSelected = selectedMapStyle == style,
                onClick = { onMapStyleSelected(style) }
            )
        }
    }
}

/**
 * Settings section with flight data, general, and look & feel
 */
@Composable
fun SettingsSection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    ModernExpandableSection(
        title = "Settings",
        icon = Icons.Outlined.Settings,
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        ModernNavItem(
            title = "Flight Data",
            icon = Icons.Outlined.CloudUpload,
            indentLevel = 1,
            onClick = {
                scope.launch {
                    drawerState.close()
                    navController.navigate("flight_data")
                }
            }
        )
        ModernNavItem(
            title = "General",
            icon = Icons.Outlined.Tune,
            indentLevel = 1,
            onClick = {
                scope.launch {
                    drawerState.close()
                    navController.navigate("settings")
                }
            }
        )
        ModernNavItem(
            title = "Vario Audio",
            icon = Icons.Outlined.VolumeUp,
            indentLevel = 2,
            onClick = {
                scope.launch {
                    drawerState.close()
                    navController.navigate("vario_audio_settings")
                }
            }
        )
    }
}

/**
 * Bottom items (Account, Support, About)
 */
@Composable
fun BottomMenuItems(
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )

    listOf(
        "Manage Account" to Icons.Outlined.AccountCircle to "manage_account",
        "Support" to Icons.Outlined.HelpOutline to "support",
        "About" to Icons.Outlined.Info to "about"
    ).forEach { (titleIcon, route) ->
        ModernNavItem(
            title = titleIcon.first,
            icon = titleIcon.second,
            onClick = {
                scope.launch {
                    drawerState.close()
                    navController.navigate(route)
                }
            }
        )
    }
}
