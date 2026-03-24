package com.example.xcpro

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.xcpro.appshell.settings.requestOpenGeneralSettingsOnMap
import com.example.xcpro.livefollow.LiveFollowRoutes
import com.example.xcpro.livefollow.account.ManageAccount
import com.example.xcpro.livefollow.friends.FriendsFlyingPilotSelection
import com.example.xcpro.livefollow.friends.FriendsFlyingScreen
import com.example.xcpro.livefollow.friends.FriendsFlyingWatchTargetType
import com.example.xcpro.livefollow.pilot.LiveFollowPilotScreen
import com.example.xcpro.livefollow.pilot.LiveFollowPilotViewModel
import com.example.xcpro.livefollow.watch.LiveFollowWatchEntryRoute
import com.example.xcpro.livefollow.watch.LiveFollowWatchSelectionHint
import com.example.xcpro.livefollow.watch.LiveFollowWatchShareCodeScreen
import com.example.xcpro.livefollow.watch.LiveFollowWatchShareEntryRoute
import com.example.xcpro.livefollow.watch.LiveFollowWatchViewModel
import com.example.xcpro.appshell.navdrawer.MyAbout
import com.example.xcpro.appshell.navdrawer.MySupport
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelScreen
import com.example.xcpro.screens.navdrawer.LayoutScreen
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
import com.example.xcpro.startup.AUTO_START_LIVEFOLLOW_SHARING_ON_MAP
import com.example.xcpro.startup.STARTUP_CHOOSER_ROUTE
import com.example.xcpro.startup.StartupChooserScreen
import com.example.xcpro.startup.consumeAutoStartLiveFollowSharingOnMap
import com.example.xcpro.startup.ensureMapRoute
import com.example.xcpro.startup.navigateFromStartupToFlyingMap
import com.example.xcpro.startup.navigateFromStartupToFriendsFlying
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    navController: NavHostController,
    drawerState: DrawerState,
    initialMapStyle: String,
    config: org.json.JSONObject?,
    profileUiState: ProfileUiState,
    allowFlightSensorStart: Boolean,
    setAllowFlightSensorStart: (Boolean) -> Unit,
    getSelectedNavItem: () -> String?,
    setSelectedNavItem: (String) -> Unit,
    setBottomSheetVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = STARTUP_CHOOSER_ROUTE,
        modifier = modifier
    ) {
        composable(STARTUP_CHOOSER_ROUTE) {
            StartupChooserScreen(
                onOpenFlying = {
                    setAllowFlightSensorStart(true)
                    navigateFromStartupToFlyingMap(navController)
                },
                onOpenFriendsFlying = {
                    setAllowFlightSensorStart(false)
                    navigateFromStartupToFriendsFlying(navController)
                }
            )
        }
        composable("map") { backStackEntry ->
            val liveFollowPilotViewModel: LiveFollowPilotViewModel = hiltViewModel(backStackEntry)
            val autoStartLiveFollowSharingOnMap by backStackEntry.savedStateHandle
                .getStateFlow(
                    AUTO_START_LIVEFOLLOW_SHARING_ON_MAP,
                    false
                )
                .collectAsStateWithLifecycle()
            LaunchedEffect(autoStartLiveFollowSharingOnMap) {
                if (
                    autoStartLiveFollowSharingOnMap &&
                    consumeAutoStartLiveFollowSharingOnMap(backStackEntry)
                ) {
                    liveFollowPilotViewModel.autoStartSharingWhenReady()
                }
            }
            SharedMapRouteHost(
                navController = navController,
                drawerState = drawerState,
                initialMapStyle = initialMapStyle,
                config = config,
                allowFlightSensorStart = allowFlightSensorStart,
                viewModelOwnerEntry = backStackEntry
            )
        }
        composable(SettingsRoutes.GENERAL) {
            // Compatibility shim for legacy callers still navigating to "settings".
            LaunchedEffect(Unit) {
                ensureMapRoute(navController)
                requestOpenGeneralSettingsOnMap(navController)
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
            LaunchedEffect(Unit) {
                setAllowFlightSensorStart(true)
            }
            LiveFollowPilotScreen(
                onNavigateBack = {
                    ensureMapRoute(navController)
                }
            )
        }
        composable(LiveFollowRoutes.FRIENDS_FLYING) {
            val mapEntry = remember(navController) {
                navController.getBackStackEntry(LiveFollowRoutes.MAP_ROUTE)
            }
            val liveFollowWatchViewModel: LiveFollowWatchViewModel = hiltViewModel(mapEntry)
            val watchUiState by liveFollowWatchViewModel.uiState.collectAsStateWithLifecycle()
            val selectedWatchKey = watchUiState.selectedShareCode ?: watchUiState.selectedSessionId
            val scope = rememberCoroutineScope()
            SharedMapRouteHost(
                navController = navController,
                drawerState = drawerState,
                initialMapStyle = initialMapStyle,
                config = config,
                allowFlightSensorStart = false,
                viewModelOwnerEntry = mapEntry
            ) {
                FriendsFlyingScreen(
                    onNavigateBack = {
                        ensureMapRoute(navController)
                    },
                    selectedWatchKey = selectedWatchKey,
                    onOpenWatch = { pilot ->
                        scope.launch {
                            when (pilot.watchTargetType) {
                                FriendsFlyingWatchTargetType.PUBLIC_SHARE_CODE -> {
                                    // AI-NOTE: Public Friends Flying keeps the
                                    // existing share-code watch lane on the shared
                                    // map owner while the sheet owns browse state.
                                    liveFollowWatchViewModel.handleWatchShareEntry(
                                        rawShareCode = pilot.shareCode,
                                        selectionHint = pilot.toWatchSelectionHint()
                                    )
                                }

                                FriendsFlyingWatchTargetType.AUTHENTICATED_SESSION_ID -> {
                                    liveFollowWatchViewModel.handleAuthorizedWatchEntry(
                                        rawSessionId = pilot.sessionId,
                                        selectionHint = pilot.toWatchSelectionHint()
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
        composable(LiveFollowRoutes.WATCH_SHARE_FORM) {
            LiveFollowWatchShareCodeScreen(
                onNavigateBack = {
                    ensureMapRoute(navController)
                },
                onOpenWatch = { shareCode ->
                    navController.navigate(LiveFollowRoutes.watchShareEntry(shareCode))
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
        composable(
            route = LiveFollowRoutes.WATCH_SHARE_ENTRY,
            arguments = listOf(
                navArgument(LiveFollowRoutes.WATCH_SHARE_CODE_ARG) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            LiveFollowWatchShareEntryRoute(
                navController = navController,
                rawShareCode = backStackEntry.arguments?.getString(
                    LiveFollowRoutes.WATCH_SHARE_CODE_ARG
                )
            )
        }
    }
}

private fun FriendsFlyingPilotSelection.toWatchSelectionHint(): LiveFollowWatchSelectionHint {
    return LiveFollowWatchSelectionHint(
        sessionId = sessionId,
        shareCode = shareCode,
        displayLabel = displayLabel,
        statusLabel = statusLabel,
        altitudeLabel = altitudeLabel,
        speedLabel = speedLabel,
        headingLabel = headingLabel,
        recencyLabel = recencyLabel,
        isStale = isStale
    )
}

