package com.example.ui1

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.ui1.icons.ActivityLog
import com.example.ui1.icons.Hangglider
import com.example.ui1.icons.Task
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import com.example.xcpro.*
import com.example.ui1.screens.Hangglider
import com.example.xcpro.profiles.ProfileViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

private const val TAG = "NavigationDrawer"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDrawer(
    modifier: Modifier = Modifier,
    drawerState: DrawerState,
    navController: NavHostController,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    initialMapStyle: String,
    onItemSelected: (String) -> Unit = {},
    onMapStyleSelected: (String) -> Unit = {},
    content: @Composable () -> Unit
) {
    Log.d(TAG, "🚪 NavigationDrawer composable started")
    val profileViewModel: ProfileViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedAircraftModel by remember { mutableStateOf<String?>(null) }
    var aircraftExpanded by remember { mutableStateOf(false) }
    var taskExpanded by remember { mutableStateOf(true) }
    var selectedMapStyle by remember { mutableStateOf(initialMapStyle) }

    val mapStyleSelectedCallback: (String) -> Unit = { style ->
        selectedMapStyle = style
        onMapStyleSelected(style)
        Log.d(TAG, "Map style updated: $style")
    }

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

// ✅ CORRECTED VERSION:
    val infiniteTransition = rememberInfiniteTransition(label = "home_pulse")

    val homeIconColor = if (homeWaypointName == null) {
        // Strong pulsing red when no home waypoint
        infiniteTransition.animateColor(
            initialValue = MaterialTheme.colorScheme.error.copy(alpha = 0.3f), // Very light red
            targetValue = MaterialTheme.colorScheme.error, // Full bright red
            animationSpec = infiniteRepeatable(
                animation = tween(1500), // Faster pulse
                repeatMode = RepeatMode.Reverse
            ),
            label = "home_pulse_color"
        ).value // ✅ Get the .value directly
    } else {
        // Static gray when home waypoint is set
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Log.d(TAG, "🚪 DismissibleNavigationDrawer rendering with state: ${drawerState.currentValue}")
    DismissibleNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DismissibleDrawerSheet(
                modifier = Modifier.width(210.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "XC Pro",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Profile Section
                    val uiState by profileViewModel.uiState.collectAsState()
                    val activeProfile = uiState.activeProfile
                    val profileTitle = activeProfile?.let { "${it.name} (${it.aircraftType.displayName})" } ?: "No Profile Selected"
                    
                    ModernExpandableSection(
                        title = profileTitle,
                        icon = activeProfile?.aircraftType?.icon ?: Icons.Outlined.Person,
                        isExpanded = profileExpanded.value,
                        onToggle = {
                            profileExpanded.value = !profileExpanded.value
                            saveNavDrawerConfig(context, profileExpanded.value, mapStyleExpanded.value, settingsExpanded.value)
                        }
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
                                    onItemSelected("profile_selection")
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
                                    onItemSelected("profiles")
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
                                            onItemSelected("aircraft/$aircraftType")
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
                                    onItemSelected("logbook")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Task Section
                    ModernExpandableSection(
                        title = "Task",
                        icon = Task,
                        isExpanded = taskExpanded,
                        onToggle = { taskExpanded = !taskExpanded }
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
                                        // No home waypoint - use auto-focus to help select one
                                        navController.navigate("flight_data/waypoints?autoFocusHome=true")
                                    } else {
                                        // Home waypoint exists - just open waypoints tab normally
                                        navController.navigate("flight_data/waypoints?autoFocusHome=false")
                                    }
                                    onItemSelected("flight_data_waypoints")
                                }
                            },
                            iconTint = homeIconColor
                        )

                        ModernNavItem(
                            title = "Add Task",
                            icon = Icons.Outlined.Add,
                            indentLevel = 1,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onItemSelected("add_task")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Map Style Section
                    ModernExpandableSection(
                        title = "Map Style",
                        icon = Icons.Outlined.Map,
                        isExpanded = mapStyleExpanded.value,
                        onToggle = {
                            mapStyleExpanded.value = !mapStyleExpanded.value
                            saveNavDrawerConfig(context, profileExpanded.value, mapStyleExpanded.value, settingsExpanded.value)
                        }
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
                                onClick = { mapStyleSelectedCallback(style) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Settings Section
                    Log.d(TAG, "🔧 Rendering Settings section: expanded=${settingsExpanded.value}")
                    ModernExpandableSection(
                        title = "Settings",
                        icon = Icons.Outlined.Settings,
                        isExpanded = settingsExpanded.value,
                        onToggle = {
                            settingsExpanded.value = !settingsExpanded.value
                            saveNavDrawerConfig(context, profileExpanded.value, mapStyleExpanded.value, settingsExpanded.value)
                        }
                    ) {
                        ModernNavItem(
                            title = "Flight Data",
                            icon = Icons.Outlined.CloudUpload,
                            indentLevel = 1,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate("flight_data")
                                    onItemSelected("flight_data")
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
                                    onItemSelected("settings")
                                }
                            }
                        )
                        ModernNavItem(
                            title = "Look & Feel",
                            icon = Icons.Outlined.Palette,
                            indentLevel = 1,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate("look_and_feel")
                                    onItemSelected("look_and_feel")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Divider
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Bottom Items
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
                                    onItemSelected(route)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        },
        modifier = modifier
    ) {
        content()
    }
}

@Composable
private fun ModernExpandableSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "arrow_rotation"
    )

    Column {
        Surface(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer
            else Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Expand",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle),
                    tint = if (isExpanded) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ModernExpandableSubItem(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isSubItem: Boolean = false,
    content: @Composable () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "subarrow_rotation"
    )

    Column {
        Surface(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isSubItem) 16.dp else 0.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Expand",
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 2.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ModernNavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean = false,
    indentLevel: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    iconTint: Color? = null
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val defaultContentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (indentLevel * 16).dp)
            .clip(RoundedCornerShape(8.dp)),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint ?: defaultContentColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor ?: if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ModernRadioItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .clip(RoundedCornerShape(8.dp)),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private fun saveNavDrawerConfig(
    context: Context,
    profileExpanded: Boolean,
    mapStyleExpanded: Boolean,
    settingsExpanded: Boolean
) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val jsonObject = if (file.exists()) {
            JSONObject(file.readText())
        } else {
            JSONObject()
        }
        val navDrawerObject = jsonObject.optJSONObject("navDrawer") ?: JSONObject()
        navDrawerObject.put("profileExpanded", profileExpanded)
        navDrawerObject.put("mapStyleExpanded", mapStyleExpanded)
        navDrawerObject.put("settingsExpanded", settingsExpanded)
        jsonObject.put("navDrawer", navDrawerObject)
        file.writeText(jsonObject.toString(2))
        Log.d(TAG, "Saved nav drawer config: profileExpanded=$profileExpanded, mapStyleExpanded=$mapStyleExpanded, settingsExpanded=$settingsExpanded")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving nav drawer config to configuration.json: ${e.message}")
    }
}

private fun loadConfig(context: Context): JSONObject? {
    return try {
        val file = File(context.filesDir, "configuration.json")
        if (file.exists()) {
            val jsonString = file.readText()
            JSONObject(jsonString)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading config from configuration.json: ${e.message}")
        null
    }
}