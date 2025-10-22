package com.example.xcpro

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.xcpro.profiles.ProfileSelectionScreen
import com.example.xcpro.profiles.ProfileViewModel
import kotlinx.coroutines.launch

private const val TAG = "MainActivityScreen"

val LocalNavigationBarHeight = staticCompositionLocalOf<Dp> { 56.dp }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainActivityScreen(
    navController: NavHostController = rememberNavController(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = viewModel()
    
    // ✅ Status bar is automatically transparent (#00000000) on this device
    // ✅ This allows map to show behind status bar as requested by user
    // ✅ The applyUserStatusBarStyle function ensures dark icons for visibility
    val config = loadConfig(context)
    val initialMapStyle = config?.optJSONObject("app")?.optString("mapStyle") ?: "Topo"
    var navigationBarHeight by remember { mutableStateOf(56.dp) }
    val density = LocalDensity.current
    val currentRoute by navController.currentBackStackEntryAsState()
    val scope = rememberCoroutineScope()
    var isBottomSheetVisible by remember { mutableStateOf(false) }
    var selectedNavItem by remember { mutableStateOf<String?>(null) }
    var showProfileSelection by remember { mutableStateOf(false) }

    // Check if we need to show profile selection based on state
    val profileUiState by profileViewModel.uiState.collectAsState()
    
    LaunchedEffect(profileUiState) {
        if (profileUiState.profiles.isEmpty()) {
            // No profiles exist, show profile selection to create first one
            showProfileSelection = true
        } else if (profileUiState.activeProfile == null) {
            // Profiles exist but none is active, show selection
            showProfileSelection = true
        } else {
            // We have an active profile, can proceed to main app
            showProfileSelection = false
        }
    }
    
    // Apply saved status bar style when profile changes
    LaunchedEffect(profileUiState.activeProfile?.id) {
        Log.d("MainActivity", "📱 LaunchedEffect triggered for profile: ${profileUiState.activeProfile?.id} (${profileUiState.activeProfile?.name})")
        val activity = context as? MainActivity
        if (activity != null) {
            Log.d("MainActivity", "✅ MainActivity reference obtained, calling applyUserStatusBarStyle")
            activity.applyUserStatusBarStyle(profileUiState.activeProfile?.id)
        } else {
            Log.e("MainActivity", "❌ Failed to get MainActivity reference from context")
        }
    }

    Log.d(TAG, "MainActivity: Current route=${currentRoute?.destination?.route}")

    if (showProfileSelection) {
        ProfileSelectionScreen(
            onProfileSelected = { showProfileSelection = false }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CompositionLocalProvider(LocalNavigationBarHeight provides navigationBarHeight) {
            AppNavGraph(
                navController = navController,
                drawerState = drawerState,
                initialMapStyle = initialMapStyle,
                config = config,
                profileUiState = profileUiState,
                getSelectedNavItem = { selectedNavItem },
                setSelectedNavItem = { selectedNavItem = it },
                setBottomSheetVisible = { isBottomSheetVisible = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            /*
            NavHost(
                navController = navController,
                startDestination = "map",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                composable("map") {
                    MapScreen(
                        navController = navController,
                        drawerState = drawerState,
                        profileExpanded = remember { mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("profileExpanded", true) ?: true) },
                        mapStyleExpanded = remember { mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("mapStyleExpanded", false) ?: false) },
                        settingsExpanded = remember { mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("settingsExpanded", true) ?: true) },
                        initialMapStyle = initialMapStyle,
                        showTaskScreen = remember { mutableStateOf(false) }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        navController = navController,
                        drawerState = drawerState,
                        onShowAirspaceOverlay = { }
                    )
                }
                composable("look_and_feel") {
                    LookAndFeelScreen(
                        navController = navController,
                        drawerState = drawerState
                    )
                }
                composable("units_settings") {
                    UnitsSettingsScreen(
                        navController = navController
                    )
                }
                composable("polar_settings") {
                    PolarSettingsScreen(
                        navController = navController,
                        drawerState = drawerState
                    )
                }
                composable("skysight_settings") {
                    com.example.xcpro.skysight.SkysightSettingsScreen(
                        drawerState = drawerState,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToMap = { navController.popBackStack("map", inclusive = false) }
                    )
                }
                composable("vario_audio_settings") {
                    VarioAudioSettingsScreen(
                        navController = navController,
                        drawerState = drawerState
                    )
                }
                composable("colors") {
                    ColorsScreen(
                        navController = navController
                    )
                }
                composable("task") {
                    Task(
                        navController = navController,
                        drawerState = drawerState,
                        selectedNavItem = selectedNavItem,
                        onShowBottomSheet = { isBottomSheetVisible = true },
                        onHideBottomSheet = { isBottomSheetVisible = false }
                    )
                    LaunchedEffect(Unit) {
                        selectedNavItem = "Task"
                    }
                }
                composable("task_creation") {
                    TaskCreation(
                        navController = navController,
                        drawerState = drawerState
                    )
                }
                composable(
                    route = "flight_data/waypoints?autoFocusHome={autoFocusHome}",
                    arguments = listOf(navArgument("autoFocusHome") { 
                        type = NavType.BoolType
                        defaultValue = false 
                    })
                ) { backStackEntry ->
                    val autoFocusHome = backStackEntry.arguments?.getBoolean("autoFocusHome") ?: false
                    FlightMgmt(
                        navController = navController,
                        drawerState = drawerState,
                        initialTab = "waypoints", // This will open directly to waypoints tab
                        autoFocusHome = autoFocusHome,
                        activeProfile = profileUiState.activeProfile // ✅ ADD: Pass active profile
                    )
                }
                composable("flight_data") { FlightMgmt( navController = navController, drawerState = drawerState, activeProfile = profileUiState.activeProfile) }
                composable("files") { FilesScreen(navController, drawerState) }
                composable("profiles") { ProfilesScreen (navController, drawerState) }
                composable("profile_selection") { 
                    ProfileSelectionScreen(
                        onProfileSelected = { navController.popBackStack() }
                    ) 
                }
                composable("profile_settings/{profileId}") { backStackEntry ->
                    val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
                    com.example.xcpro.profiles.ProfileSettingsScreen(
                        profileId = profileId,
                        navController = navController
                    )
                }
                composable("sailplanes") { Sailplanes(navController, drawerState) }
                composable("paragliders") { Paragliders(navController, drawerState) }
                composable("hanggliders") { Hangglider(navController, drawerState) }
                composable("manage_account") { ManageAccount(navController, drawerState) }
                composable("logbook") { Logbook(navController, drawerState) }
                composable("layouts") { LayoutScreen(navController, drawerState) }
                composable("dfnavboxes") { DFNavboxes(navController, drawerState) }
                composable("support") {
                    MySupport(
                        navController = navController,
                        drawerState = drawerState,
                        onShowBottomSheet = { isBottomSheetVisible = true },
                        onHideBottomSheet = { isBottomSheetVisible = false }
                    )
                }
                composable("about") { MyAbout(navController, drawerState) }
            }
            */
        }

        if (currentRoute?.destination?.route in listOf("about", "support", "task")) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets(0.dp))
                    .clipToBounds()
                    .onGloballyPositioned { coordinates ->
                        val heightPx = coordinates.size.height
                        navigationBarHeight = with(density) { heightPx.toDp() }
                        Log.d(TAG, "NavigationBar: Height measured as $navigationBarHeight")
                    },
                tonalElevation = 0.dp
            ) {
                Log.d(TAG, "NavigationBar: Composing for '${currentRoute?.destination?.route}' route")
                if (currentRoute?.destination?.route == "task") {
                    val taskIcons = listOf(
                        Pair("Task", Icons.Default.Add),
                        Pair("Favorite", Icons.Default.Favorite),
                        Pair("Places", Icons.Default.Place),
                        Pair("Files", Icons.Default.AttachFile)
                    )
                    taskIcons.forEach { (name, icon) ->
                        NavigationBarItem(
                            selected = selectedNavItem == name,
                            onClick = {
                                Log.d(TAG, "NavigationBar: $name item clicked")
                                scope.launch {
                                    selectedNavItem = name
                                    if (name == "Task") {
                                        Log.d(TAG, "Task item clicked, no action performed")
                                    } else if (name == "Files") {
                                        isBottomSheetVisible = true
                                    } else if (name == "Favorite" || name == "Places") {
                                        isBottomSheetVisible = false
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name
                                )
                            },
                            label = {
                                Text(text = name)
                            }
                        )
                    }
                } else {
                    NavigationBarItem(
                        selected = selectedNavItem == "Home",
                        onClick = {
                            Log.d(TAG, "NavigationBar: Home item clicked")
                            selectedNavItem = "Home"
                            scope.launch {
                                if (currentRoute?.destination?.route == "support" && isBottomSheetVisible) {
                                    isBottomSheetVisible = false
                                } else if (currentRoute?.destination?.route == "about") {
                                    navController.navigate("map") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                    }
                                }
                            }
                        },
                        icon = {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = {
                            Text("Home")
                        }
                    )
                    NavigationBarItem(
                        selected = selectedNavItem == "Waypoints",
                        onClick = {
                            Log.d(TAG, "NavigationBar: Waypoints item clicked")
                            selectedNavItem = "Waypoints"
                            scope.launch {
                                if (currentRoute?.destination?.route == "support") {
                                    isBottomSheetVisible = true
                                } else {
                                    navController.navigate("settings") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                    }
                                }
                            }
                        },
                        icon = {
                            Icon(
                                Icons.Default.Place,
                                contentDescription = "Waypoints"
                            )
                        },
                        label = {
                            Text("Waypoints")
                        }
                    )
                }
            }
        }
    }
}
