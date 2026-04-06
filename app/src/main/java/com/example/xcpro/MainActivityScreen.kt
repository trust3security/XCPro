package com.example.xcpro

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.appshell.settings.requestOpenGeneralSettingsOnMap
import com.example.xcpro.profiles.ProfileSelectionScreen
import com.example.xcpro.profiles.ProfileViewModel
import com.example.xcpro.startup.ensureMapRoute
import kotlinx.coroutines.launch

private const val TAG = "MainActivityScreen"

val LocalNavigationBarHeight = staticCompositionLocalOf<Dp> { 56.dp }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainActivityScreen(
    navController: NavHostController = rememberNavController(),
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
) {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val configViewModel: AppConfigViewModel = hiltViewModel()
    val context = LocalContext.current
    
    //  Status bar is automatically transparent (#00000000) on this device
    //  This allows map to show behind status bar as requested by user
    //  The applyUserStatusBarStyle function ensures dark icons for visibility
    val configUiState by configViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        configViewModel.loadConfig()
    }
    var navigationBarHeight by remember { mutableStateOf(56.dp) }
    val density = LocalDensity.current
    val currentRoute by navController.currentBackStackEntryAsState()
    val scope = rememberCoroutineScope()
    var isBottomSheetVisible by remember { mutableStateOf(false) }
    var selectedNavItem by remember { mutableStateOf<String?>(null) }
    var allowFlightSensorStart by rememberSaveable { mutableStateOf(false) }

    // Check if we need to show profile selection based on state
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    
    // Apply saved status bar style when profile changes
    LaunchedEffect(profileUiState.activeProfile?.id) {
        AppLogger.d(TAG, "Applying saved status bar style for active profile change")
        val activity = context as? MainActivity
        if (activity != null) {
            AppLogger.d(TAG, "MainActivity reference obtained for status bar style update")
            activity.applyUserStatusBarStyle(profileUiState.activeProfile?.id)
        } else {
            AppLogger.e(TAG, "Failed to get MainActivity reference from context")
        }
    }

    AppLogger.d(TAG, "MainActivity route=${currentRoute?.destination?.route}")

    if (!profileUiState.isHydrated) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val showProfileSelection =
        profileUiState.profiles.isEmpty() || profileUiState.activeProfile == null
    if (showProfileSelection) {
        AppLogger.i(
            TAG,
            "Profile selection required: profileCount=${profileUiState.profiles.size}, " +
                "hasActive=${profileUiState.activeProfile != null}, " +
                "bootstrapError=${profileUiState.bootstrapError}"
        )
        ProfileSelectionScreen(
            onProfileSelected = {},
            onEditProfile = { profile ->
                navController.navigate("profile_settings/${profile.id}")
            }
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
                config = configUiState.config,
                profileUiState = profileUiState,
                allowFlightSensorStart = allowFlightSensorStart,
                setAllowFlightSensorStart = { allowFlightSensorStart = it },
                getSelectedNavItem = { selectedNavItem },
                setSelectedNavItem = { selectedNavItem = it },
                setBottomSheetVisible = { isBottomSheetVisible = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
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
                        AppLogger.d(TAG, "NavigationBar height=$navigationBarHeight")
                    },
                tonalElevation = 0.dp
            ) {
                AppLogger.d(TAG, "NavigationBar composing for '${currentRoute?.destination?.route}' route")
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
                                AppLogger.d(TAG, "NavigationBar item clicked: $name")
                                scope.launch {
                                    selectedNavItem = name
                                    if (name == "Task") {
                                        AppLogger.d(TAG, "Task item clicked; no action performed")
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
                            AppLogger.d(TAG, "NavigationBar item clicked: Home")
                            selectedNavItem = "Home"
                            scope.launch {
                                if (currentRoute?.destination?.route == "support" && isBottomSheetVisible) {
                                    isBottomSheetVisible = false
                                } else if (currentRoute?.destination?.route == "about") {
                                    ensureMapRoute(navController)
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
                            AppLogger.d(TAG, "NavigationBar item clicked: Waypoints")
                            selectedNavItem = "Waypoints"
                            scope.launch {
                                if (currentRoute?.destination?.route == "support") {
                                    isBottomSheetVisible = true
                                } else {
                                    ensureMapRoute(navController)
                                    requestOpenGeneralSettingsOnMap(navController)
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
