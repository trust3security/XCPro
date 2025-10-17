package com.example.xcpro

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.xcpro.ui.theme.Baseui1Theme
import com.example.ui1.screens.MySupport
import com.example.ui1.screens.FilesScreen
import com.example.ui1.screens.Hangglider
import com.example.ui1.screens.ManageAccount
import com.example.ui1.screens.Paragliders
import com.example.ui1.screens.ProfilesScreen
import com.example.ui1.screens.Sailplanes
import com.example.ui1.screens.SettingsScreen
import com.example.ui1.screens.DFNavboxes
import com.example.ui1.screens.LayoutScreen
import com.example.ui1.screens.MyAbout
import com.example.ui1.screens.Task
import com.example.ui1.screens.FlightMgmt
import com.example.ui1.screens.LookAndFeelScreen
import com.example.ui1.screens.StatusBarStyle
import com.example.xcpro.screens.navdrawer.ColorsScreen
import com.example.xcpro.screens.navdrawer.UnitsSettingsScreen
import com.example.xcpro.screens.navdrawer.VarioAudioSettingsScreen
import com.example.xcpro.profiles.ProfileSelectionScreen
import com.example.xcpro.profiles.ProfileViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

private const val TAG = "MainActivity"

val LocalNavigationBarHeight = staticCompositionLocalOf<Dp> { 56.dp }

class MainActivity : ComponentActivity() {
    private var currentProfileId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Only set basic window flags that are safe to set early
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ✅ Acquire PARTIAL wake lock for continuous sensor operation
        // PARTIAL_WAKE_LOCK keeps CPU running even when screen is off
        // This prevents GPS/sensor freezing during sleep mode
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "XCPro::SensorWakeLock"
        )
        // Acquire indefinitely - will be released in onDestroy()
        wakeLock?.acquire()

        MapLibre.getInstance(this, "nYDScLfnBm52GAc3jXEZ", WellKnownTileServer.MapTiler)
        
        // ✅ First-time setup - Clear cache and set defaults
        val setupManager = FirstTimeSetupManager.getInstance(this)
        if (setupManager.isFirstLaunch()) {
            Log.i(TAG, "🚀 First launch detected - performing setup...")
            setupManager.performFirstTimeSetup()
        }
        Log.d(TAG, "Setup Info: ${setupManager.getSetupInfo()}")
        
        // Test Skysight integration
        com.example.xcpro.skysight.SkysightAutoTest.runNetworkTests(this)
        
        // Run keyhole verification test
        // runKeyholeVerificationTest() // TODO: Re-enable after keyhole implementation is completed
        
        setContent {
            Baseui1Theme {
                MainActivityScreen()
            }
        }

        // ✅ Apply user's status bar style after setContent
        Log.d("MainActivity", "🚀 onCreate: Applying initial status bar style")
        applyUserStatusBarStyle(null) // No profile initially
    }

    /**
     * Run keyhole verification test to check calculator/display synchronization
     * TODO: Re-enable after keyhole implementation is completed
     */
    /*
    private fun runKeyholeVerificationTest() {
        try {
            Log.d(TAG, "🔑 Starting keyhole verification test...")
            println("🔑 KEYHOLE VERIFICATION TEST STARTING...")

            val verification = com.example.xcpro.tasks.KeyholeVerification()
            val results = verification.verifyKeyholeImplementation()

            println("🔑 KEYHOLE VERIFICATION RESULTS:")
            println(results)

            Log.d(TAG, "🔑 Keyhole verification completed")
            Log.d(TAG, results)

        } catch (e: Exception) {
            Log.e(TAG, "🔑 ERROR: Keyhole verification failed: ${e.message}", e)
            println("🔑 ERROR: Keyhole verification failed: ${e.message}")
            e.printStackTrace()
        }
    }
    */

    override fun onResume() {
        super.onResume()
        // Reapply status bar style when activity resumes
        Log.d("MainActivity", "📱 onResume: Reapplying status bar style for profile: $currentProfileId")
        applyUserStatusBarStyle(currentProfileId)

        // ✅ Restart sensors if they were suspended during sleep mode
        // This ensures GPS and other sensors resume after screen-off
        Log.d("MainActivity", "📱 onResume: Triggering sensor restart after possible sleep mode")
        // Note: The actual sensor restart is handled in MapScreen via LocationManager
        // This log helps track when the app returns from background/sleep
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // ✅ Release wake lock to prevent battery drain
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
            }
        }
    }

    // ✅ Apply user's selected status bar style with navigation bar handling
    fun applyUserStatusBarStyle(profileId: String?) {
        currentProfileId = profileId // Store for reapplication
        Log.d("MainActivity", "🔍 applyUserStatusBarStyle called with profileId: $profileId")
        
        try {
            val sharedPrefs = getSharedPreferences("LookAndFeelPrefs", Context.MODE_PRIVATE)
            val styleId = if (profileId != null) {
                val savedStyleId = sharedPrefs.getString("profile_${profileId}_status_bar_style", StatusBarStyle.TRANSPARENT.id)
                Log.d("MainActivity", "📋 Retrieved saved style ID: $savedStyleId for profile: $profileId")
                savedStyleId
            } else {
                Log.d("MainActivity", "⚠️ No profileId provided, using default TRANSPARENT")
                StatusBarStyle.TRANSPARENT.id
            }
            
            val selectedStyle = StatusBarStyle.values().find { it.id == styleId } ?: StatusBarStyle.TRANSPARENT
            Log.d("MainActivity", "🎨 Applying status bar style: ${selectedStyle.title} (${selectedStyle.id}) for profile: $profileId")
            Log.d("MainActivity", "📱 Android SDK: ${Build.VERSION.SDK_INT}")
            Log.d("MainActivity", "🪟 Current window attributes:")
            Log.d("MainActivity", "   - statusBarColor: ${String.format("#%08X", window.statusBarColor)}")
            Log.d("MainActivity", "   - navigationBarColor: ${String.format("#%08X", window.navigationBarColor)}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d("MainActivity", "   - decorFitsSystemWindows: ${window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars())}")
            }
            
            when (selectedStyle) {
                StatusBarStyle.TRANSPARENT -> {
                    Log.d("MainActivity", "🔧 Applying TRANSPARENT style")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // ✅ CRITICAL: Allow content to draw behind system bars
                        window.setDecorFitsSystemWindows(false)
                        
                        // ✅ Status bar will be automatically transparent on this device
                        // ✅ Configure system UI for optimal map visibility
                        window.insetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            
                            // ✅ CRITICAL: Use DARK icons/text (black) for visibility on transparent map background
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                            )
                            Log.d("MainActivity", "✅ TRANSPARENT: Dark icons enabled for map visibility")
                        }
                        
                        // ✅ Enable full edge-to-edge layout for map visibility
                        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        
                        Log.d("MainActivity", "✅ TRANSPARENT: Map displays behind transparent status bar with dark icons")
                    } else {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }
                StatusBarStyle.THEMED -> {
                    Log.d("MainActivity", "🔧 Applying THEMED style")
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // THEMED: Use decorFitsSystemWindows TRUE for dedicated status bar background
                        window.setDecorFitsSystemWindows(true)
                        
                        // DEBUG: Set to RED to test if colors are applying
                        val themedColor = android.graphics.Color.parseColor("#FF0000") // RED for debugging
                        window.statusBarColor = themedColor
                        window.navigationBarColor = themedColor
                        
                        // Apply immediately without delay
                        window.insetsController?.let { controller ->
                            // Use dark icons on light themed background
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            )
                            controller.setSystemBarsAppearance(
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                            )
                            Log.d("MainActivity", "✅ THEMED: Applied white background with dark icons")
                        }
                        
                        // Clear any conflicting system UI flags
                        window.decorView.systemUiVisibility = 0
                        
                        // Force refresh
                        window.decorView.invalidate()
                        
                        Log.d("MainActivity", "🎨 THEMED - Status bar: ${String.format("#%08X", window.statusBarColor)}")
                        Log.d("MainActivity", "🎨 THEMED - Navigation bar: ${String.format("#%08X", window.navigationBarColor)}")
                    } else {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = android.graphics.Color.parseColor("#FFFFFF")
                        window.navigationBarColor = android.graphics.Color.parseColor("#FFFFFF")
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        )
                    }
                }
                StatusBarStyle.EDGE_TO_EDGE -> {
                    Log.d("MainActivity", "🔧 Applying EDGE_TO_EDGE style")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.setDecorFitsSystemWindows(false)
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        
                        // Apply immediately without delay
                        window.insetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            // Edge-to-edge: Use light icons (white) on content background
                            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                            Log.d("MainActivity", "✅ EDGE_TO_EDGE: Light icons enabled")
                        }
                        
                        // Force refresh
                        window.decorView.invalidate()
                        Log.d("MainActivity", "📊 EDGE_TO_EDGE - Status bar: ${String.format("#%08X", window.statusBarColor)}")
                    } else {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }
                StatusBarStyle.OVERLAY -> {
                    Log.d("MainActivity", "🔧 Applying OVERLAY style")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.setDecorFitsSystemWindows(false)
                        
                        val overlayColor = android.graphics.Color.parseColor("#80000000") // Semi-transparent black
                        window.statusBarColor = overlayColor
                        window.navigationBarColor = android.graphics.Color.parseColor("#80000000")
                        
                        // Apply immediately without delay
                        window.insetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            // Overlay: Use light icons (white) on dark overlay
                            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                            Log.d("MainActivity", "✅ OVERLAY: Light icons on dark overlay")
                        }
                        
                        // Force refresh
                        window.decorView.invalidate()
                        Log.d("MainActivity", "📊 OVERLAY - Status bar: ${String.format("#%08X", window.statusBarColor)}")
                    } else {

                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }
            }
            
            Log.d("MainActivity", "✅ Status bar styling applied successfully")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ Error applying status bar style: ${e.message}", e)
            // Fallback to default behavior with light icons
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.decorView.post {
                    window.insetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.navigationBars())
                        controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    }
                }
            }
        }
    }
}

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
