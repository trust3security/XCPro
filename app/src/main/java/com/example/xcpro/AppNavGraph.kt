package com.example.xcpro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui1.screens.*
import com.example.xcpro.profiles.ProfileUiState
import com.example.xcpro.screens.navdrawer.UnitsSettingsScreen
import com.example.xcpro.screens.navdrawer.PolarSettingsScreen
import com.example.xcpro.screens.navdrawer.VarioAudioSettingsScreen
import com.example.xcpro.screens.navdrawer.ColorsScreen
import com.example.xcpro.profiles.ProfileSelectionScreen
import com.example.xcpro.ServiceLocator
import com.example.xcpro.xcprov1.ui.HawkDashboardScreen
import com.example.xcpro.xcprov1.viewmodel.HawkDashboardViewModel

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
        composable("look_and_feel") { LookAndFeelScreen(navController = navController, drawerState = drawerState) }
        composable("units_settings") { UnitsSettingsScreen(navController = navController) }
        composable("polar_settings") { PolarSettingsScreen(navController = navController) }
        composable("skysight_settings") {
            com.example.xcpro.skysight.SkysightSettingsScreen(
                drawerState = drawerState,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMap = { navController.popBackStack("map", inclusive = false) }
            )
        }
        composable("vario_audio_settings") { VarioAudioSettingsScreen(navController = navController, drawerState = drawerState) }
        composable("colors") { ColorsScreen(navController = navController) }
        composable("hawk_dashboard") {
            val locationManager = ServiceLocator.locationManager
            if (locationManager == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "HAWK dashboard requires active flight sensors. Launch the Map first.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                val viewModel: HawkDashboardViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            if (modelClass.isAssignableFrom(HawkDashboardViewModel::class.java)) {
                                @Suppress("UNCHECKED_CAST")
                                return HawkDashboardViewModel(
                                    controller = locationManager.xcproV1Controller,
                                    garminStatusFlow = locationManager.garminStatusFlow,
                                    autoConnectGarmin = { locationManager.connectGarminGlo() },
                                    disconnectGarmin = { locationManager.disconnectGarminGlo() }
                                ) as T
                            }
                            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
                        }
                    }
                )
                HawkDashboardScreen(viewModel = viewModel)
                setSelectedNavItem("hawk_dashboard")
            }
        }
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
            FlightMgmt(
                navController = navController,
                drawerState = drawerState,
                initialTab = "waypoints",
                autoFocusHome = autoFocusHome,
                activeProfile = profileUiState.activeProfile
            )
        }
        composable("flight_data") { FlightMgmt(navController = navController, drawerState = drawerState, activeProfile = profileUiState.activeProfile) }
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
        composable("support") { MySupport(navController = navController, drawerState = drawerState, onShowBottomSheet = { setBottomSheetVisible(true) }, onHideBottomSheet = { setBottomSheetVisible(false) }) }
        composable("about") { MyAbout(navController, drawerState) }
    }
}
