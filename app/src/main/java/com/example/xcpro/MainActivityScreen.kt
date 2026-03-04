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
import com.example.xcpro.navigation.MapNavigationSignals
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
    val initialMapStyle = configUiState.config?.optJSONObject("app")?.optString("mapStyle") ?: "Topo"
    var navigationBarHeight by remember { mutableStateOf(56.dp) }
    val density = LocalDensity.current
    val currentRoute by navController.currentBackStackEntryAsState()
    val scope = rememberCoroutineScope()
    var isBottomSheetVisible by remember { mutableStateOf(false) }
    var selectedNavItem by remember { mutableStateOf<String?>(null) }

    // Check if we need to show profile selection based on state
    val profileUiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    
    // Apply saved status bar style when profile changes
    LaunchedEffect(profileUiState.activeProfile?.id) {
        Log.d("MainActivity", " LaunchedEffect triggered for profile: ${profileUiState.activeProfile?.id} (${profileUiState.activeProfile?.name})")
        val activity = context as? MainActivity
        if (activity != null) {
            Log.d("MainActivity", " MainActivity reference obtained, calling applyUserStatusBarStyle")
            activity.applyUserStatusBarStyle(profileUiState.activeProfile?.id)
        } else {
            Log.e("MainActivity", " Failed to get MainActivity reference from context")
        }
    }

    Log.d(TAG, "MainActivity: Current route=${currentRoute?.destination?.route}")

    if (!profileUiState.isHydrated) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (profileUiState.activeProfile == null && !profileUiState.bootstrapError.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Profile Storage Error",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = profileUiState.bootstrapError ?: "Failed to load stored profiles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val showProfileSelection =
        profileUiState.profiles.isEmpty() || profileUiState.activeProfile == null
    if (showProfileSelection) {
        Log.i(
            TAG,
            "Profile selection required: package=${context.packageName}, " +
                "profileCount=${profileUiState.profiles.size}, " +
                "hasActive=${profileUiState.activeProfile != null}, " +
                "bootstrapError=${profileUiState.bootstrapError}"
        )
        ProfileSelectionScreen(
            onProfileSelected = {}
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
                config = configUiState.config,
                profileUiState = profileUiState,
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
                                    val requestOpenGeneralOnMap = {
                                        runCatching {
                                            navController.getBackStackEntry("map")
                                                .savedStateHandle[MapNavigationSignals.OPEN_GENERAL_SETTINGS_ON_MAP] = true
                                        }
                                    }
                                    requestOpenGeneralOnMap()
                                    navController.navigate("map") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                    requestOpenGeneralOnMap()
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
