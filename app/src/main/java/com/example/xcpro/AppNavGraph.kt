package com.example.xcpro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ui1.screens.*
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelScreen
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.ui.MapScreen
import com.example.xcpro.profiles.ProfileSelectionScreen
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.screens.navdrawer.ColorsScreen
import com.example.xcpro.screens.navdrawer.LevoVarioSettingsScreen
import com.example.xcpro.screens.navdrawer.PolarSettingsScreen
import com.example.xcpro.screens.navdrawer.UnitsSettingsScreen
import com.example.xcpro.screens.navdrawer.OrientationSettingsScreen
import com.example.xcpro.screens.diagnostics.VarioDiagnosticsScreen
import com.example.xcpro.screens.replay.IgcReplayScreen

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
            MapScreen(
                navController = navController,
                drawerState = drawerState,
                profileExpanded = remember { mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("profileExpanded", true) ?: true) },
                mapStyleExpanded = remember { mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("mapStyleExpanded", false) ?: false) },
                settingsExpanded = remember { mutableStateOf(config?.optJSONObject("navDrawer")?.optBoolean("settingsExpanded", true) ?: true) },
                initialMapStyle = initialMapStyle,
                mapViewModel = mapViewModel
            )
        }
        composable("settings") { backStackEntry ->
            val mapEntry = remember(backStackEntry) { navController.getBackStackEntry("map") }
            val mapViewModel: MapScreenViewModel = hiltViewModel(mapEntry)
            SettingsScreen(
                navController = navController,
                drawerState = drawerState,
                onShowAirspaceOverlay = { }
            )
        }
        composable("look_and_feel") { LookAndFeelScreen(navController = navController, drawerState = drawerState) }
        composable("units_settings") { UnitsSettingsScreen(navController = navController) }
        composable("polar_settings") { PolarSettingsScreen(navController = navController, drawerState = drawerState) }
        composable("levo_vario_settings") { LevoVarioSettingsScreen(navController = navController, drawerState = drawerState) }
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
        composable("task_creation") { TaskCreation(navController = navController, drawerState = drawerState) }
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
                flightDataManager = mapViewModel.flightDataManager
            )
        }
        composable("flight_data") { backStackEntry ->
            val mapEntry = remember(backStackEntry) { navController.getBackStackEntry("map") }
            val mapViewModel: MapScreenViewModel = hiltViewModel(mapEntry)
            FlightMgmt(
                navController = navController,
                drawerState = drawerState,
                activeProfile = profileUiState.activeProfile,
                flightDataManager = mapViewModel.flightDataManager
            )
        }
        composable("files") { FilesScreen(navController, drawerState) }
        composable("profiles") { ProfilesScreen(navController, drawerState) }
        composable("profile_selection") { ProfileSelectionScreen(onProfileSelected = { navController.popBackStack() }) }
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
        composable("dfnavboxes") { DFNavboxes(navController, drawerState) }
        composable("orientation_settings") { backStackEntry ->
            val mapEntry = remember(backStackEntry) { navController.getBackStackEntry("map") }
            val mapViewModel: MapScreenViewModel = hiltViewModel(mapEntry)
            OrientationSettingsScreen(
                navController = navController,
                drawerState = drawerState,
                orientationManager = mapViewModel.orientationManager
            )
        }
        composable("igcReplay") { IgcReplayScreen(navController = navController) }
        composable("support") { MySupport(navController = navController, drawerState = drawerState, onShowBottomSheet = { setBottomSheetVisible(true) }, onHideBottomSheet = { setBottomSheetVisible(false) }) }
        composable("about") { MyAbout(navController, drawerState) }
    }
}

