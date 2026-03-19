package com.example.xcpro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui1.screens.DFNavboxes
import com.example.ui1.screens.FilesScreen
import com.example.ui1.screens.FlightMgmt
import com.example.ui1.screens.Hangglider
import com.example.ui1.screens.Paragliders
import com.example.ui1.screens.ProfilesScreen
import com.example.ui1.screens.Sailplanes
import com.example.ui1.screens.Task
import com.example.xcpro.appshell.settings.GeneralSettingsSheetHost
import com.example.xcpro.appshell.settings.consumeOpenGeneralSettingsOnMap
import com.example.xcpro.appshell.settings.requestOpenGeneralSettingsOnMap
import com.example.xcpro.livefollow.LiveFollowRoutes
import com.example.xcpro.livefollow.pilot.LiveFollowPilotScreen
import com.example.xcpro.livefollow.watch.LiveFollowWatchEntryRoute
import com.example.xcpro.appshell.navdrawer.MyAbout
import com.example.xcpro.appshell.navdrawer.MySupport
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelScreen
import com.example.xcpro.screens.navdrawer.LayoutScreen
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.ui.MapScreen
import com.example.xcpro.profiles.ProfileSelectionScreen
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.screens.navdrawer.ColorsScreen
import com.example.xcpro.screens.navdrawer.LevoVarioSettingsScreen
import com.example.xcpro.screens.navdrawer.HawkVarioSettingsScreen
import com.example.xcpro.screens.navdrawer.PolarSettingsScreen
import com.example.xcpro.screens.navdrawer.ThermallingSettingsScreen
import com.example.xcpro.screens.navdrawer.UnitsSettingsScreen
import com.example.xcpro.screens.navdrawer.OrientationSettingsScreen
import com.example.xcpro.screens.navdrawer.AdsbSettingsScreen
import com.example.xcpro.screens.navdrawer.ForecastSettingsScreen
import com.example.xcpro.screens.navdrawer.HotspotsSettingsScreen
import com.example.xcpro.screens.navdrawer.OgnSettingsScreen
import com.example.xcpro.screens.navdrawer.WeatherSettingsScreen
import com.example.xcpro.screens.diagnostics.VarioDiagnosticsScreen
import com.example.xcpro.screens.replay.IgcReplayScreen
import com.example.xcpro.navigation.SettingsRoutes
import com.example.xcpro.navigation.TrafficSettingsRoutes
import com.example.xcpro.profiles.ManageAccount
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    navController: NavHostController,
    drawerState: DrawerState,
    initialMapStyle: String,
    config: org.json.JSONObject?,
    profileUiState: ProfileUiState,
    getSelectedNavItem: () -> String?,
    setSelectedNavItem: (String) -> Unit,
    setBottomSheetVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "map",
        modifier = modifier
    ) {
        composable("map") { backStackEntry ->
            val mapViewModel: MapScreenViewModel = hiltViewModel(backStackEntry)
            val openGeneralSettingsOnMap by backStackEntry.savedStateHandle
                .getStateFlow(
                    com.example.xcpro.navigation.MapNavigationSignals.OPEN_GENERAL_SETTINGS_ON_MAP,
                    false
                )
                .collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var showGeneralSettings by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(openGeneralSettingsOnMap) {
                if (openGeneralSettingsOnMap && consumeOpenGeneralSettingsOnMap(backStackEntry)) {
                    showGeneralSettings = true
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                MapScreen(
                    navController = navController,
                    drawerState = drawerState,
                    profileExpanded = remember(config) {
                        mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("profileExpanded", true) ?: true)
                    },
                    mapStyleExpanded = remember(config) {
                        mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("mapStyleExpanded", false) ?: false)
                    },
                    settingsExpanded = remember(config) {
                        mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("settingsExpanded", true) ?: true)
                    },
                    initialMapStyle = initialMapStyle,
                    onOpenGeneralSettings = {
                        showGeneralSettings = true
                    },
                    mapViewModel = mapViewModel
                )
                if (showGeneralSettings) {
                    GeneralSettingsSheetHost(
                        navController = navController,
                        drawerState = drawerState,
                        onDismissRequest = {
                            showGeneralSettings = false
                        },
                        onNavigateUp = {
                            showGeneralSettings = false
                            scope.launch {
                                if (!drawerState.isOpen) {
                                    drawerState.open()
                                }
                            }
                        },
                        onNavigateToMap = {
                            showGeneralSettings = false
                            scope.launch {
                                if (drawerState.isOpen) {
                                    drawerState.close()
                                }
                            }
                        }
                    )
                }
            }
        }
        composable(SettingsRoutes.GENERAL) {
            // Compatibility shim for legacy callers still navigating to "settings".
            LaunchedEffect(Unit) {
                requestOpenGeneralSettingsOnMap(navController)
                val poppedToMap = navController.popBackStack("map", inclusive = false)
                if (!poppedToMap) {
                    navController.navigate("map") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                    requestOpenGeneralSettingsOnMap(navController)
                }
            }
        }
        composable("look_and_feel") { LookAndFeelScreen(navController = navController, drawerState = drawerState) }
        composable("units_settings") { UnitsSettingsScreen(navController = navController, drawerState = drawerState) }
        composable("polar_settings") { PolarSettingsScreen(navController = navController, drawerState = drawerState) }
        composable("levo_vario_settings") { LevoVarioSettingsScreen(navController = navController, drawerState = drawerState) }
        composable("hawk_vario_settings") { HawkVarioSettingsScreen(navController = navController, drawerState = drawerState) }
        composable("vario_diagnostics") { VarioDiagnosticsScreen(navController = navController, drawerState = drawerState) }
        composable("colors") { ColorsScreen(navController = navController) }
        composable("task") {
            Task(
                navController = navController,
                drawerState = drawerState,
                selectedNavItem = getSelectedNavItem() ?: "Task",
                onShowBottomSheet = { setBottomSheetVisible(true) },
                onHideBottomSheet = { setBottomSheetVisible(false) }
            )
            setSelectedNavItem("Task")
        }
        composable(
            route = "flight_data/waypoints?autoFocusHome={autoFocusHome}",
            arguments = listOf(navArgument("autoFocusHome") { type = NavType.BoolType; defaultValue = false })
        ) { backStackEntry ->
            val autoFocusHome = backStackEntry.arguments?.getBoolean("autoFocusHome") ?: false
            val mapEntry = remember(backStackEntry) { navController.getBackStackEntry("map") }
            val mapViewModel: MapScreenViewModel = hiltViewModel(mapEntry)
            FlightMgmt(
                navController = navController,
                drawerState = drawerState,
                initialTab = "waypoints",
                autoFocusHome = autoFocusHome,
                activeProfile = profileUiState.activeProfile,
                flightDataManager = mapViewModel.runtimeDependencies.flightDataManager
            )
        }
        composable("flight_data") { backStackEntry ->
            val mapEntry = remember(backStackEntry) { navController.getBackStackEntry("map") }
            val mapViewModel: MapScreenViewModel = hiltViewModel(mapEntry)
            FlightMgmt(
                navController = navController,
                drawerState = drawerState,
                activeProfile = profileUiState.activeProfile,
                flightDataManager = mapViewModel.runtimeDependencies.flightDataManager
            )
        }
        composable(SettingsRoutes.FILES) { FilesScreen(navController, drawerState) }
        composable(SettingsRoutes.PROFILES) { ProfilesScreen(navController, drawerState) }
        composable("profile_selection") {
            ProfileSelectionScreen(
                onProfileSelected = { navController.popBackStack() },
                onEditProfile = { profile ->
                    navController.navigate("profile_settings/${profile.id}")
                }
            )
        }
        composable("profile_settings/{profileId}") { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            com.example.xcpro.profiles.ProfileSettingsScreen(profileId = profileId, navController = navController)
        }
        composable("sailplanes") { Sailplanes(navController, drawerState) }
        composable("paragliders") { Paragliders(navController, drawerState) }
        composable("hanggliders") { Hangglider(navController, drawerState) }
        composable("manage_account") { ManageAccount(navController, drawerState) }
        composable("logbook") { Logbook(navController, drawerState) }
        composable("layouts") { LayoutScreen(navController, drawerState) }
        composable(TrafficSettingsRoutes.ADSB_SETTINGS) { AdsbSettingsScreen(navController, drawerState) }
        composable(TrafficSettingsRoutes.OGN_SETTINGS) { OgnSettingsScreen(navController, drawerState) }
        composable("forecast_settings") { ForecastSettingsScreen(navController, drawerState) }
        composable(SettingsRoutes.WEATHER_SETTINGS) { WeatherSettingsScreen(navController, drawerState) }
        composable(TrafficSettingsRoutes.HOTSPOTS_SETTINGS) { HotspotsSettingsScreen(navController, drawerState) }
        composable(SettingsRoutes.THERMALLING_SETTINGS) {
            ThermallingSettingsScreen(navController, drawerState)
        }
        composable("dfnavboxes") { DFNavboxes(navController, drawerState) }
        composable(SettingsRoutes.ORIENTATION_SETTINGS) {
            OrientationSettingsScreen(
                navController = navController,
                drawerState = drawerState
            )
        }
        composable("igcReplay") { IgcReplayScreen(navController = navController) }
        composable("support") { MySupport(navController = navController, drawerState = drawerState, onShowBottomSheet = { setBottomSheetVisible(true) }, onHideBottomSheet = { setBottomSheetVisible(false) }) }
        composable("about") { MyAbout(navController, drawerState) }
        composable(LiveFollowRoutes.PILOT) {
            LiveFollowPilotScreen(
                onNavigateBack = {
                    val popped = navController.popBackStack(route = "map", inclusive = false)
                    if (!popped) {
                        navController.navigate("map") {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(
            route = LiveFollowRoutes.WATCH_ENTRY,
            arguments = listOf(
                navArgument(LiveFollowRoutes.WATCH_SESSION_ID_ARG) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            LiveFollowWatchEntryRoute(
                navController = navController,
                rawSessionId = backStackEntry.arguments?.getString(
                    LiveFollowRoutes.WATCH_SESSION_ID_ARG
                )
            )
        }
    }
}

