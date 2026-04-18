package com.trust3.xcpro

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
import com.trust3.xcpro.appshell.settings.requestOpenGeneralSettingsOnMap
import com.trust3.xcpro.livefollow.LiveFollowRoutes
import com.trust3.xcpro.livefollow.account.ManageAccount
import com.trust3.xcpro.livefollow.friends.FriendsFlyingPilotSelection
import com.trust3.xcpro.livefollow.friends.FriendsFlyingScreen
import com.trust3.xcpro.livefollow.friends.FriendsFlyingWatchTargetType
import com.trust3.xcpro.livefollow.pilot.LiveFollowPilotScreen
import com.trust3.xcpro.livefollow.pilot.LiveFollowPilotViewModel
import com.trust3.xcpro.livefollow.watch.LiveFollowWatchEntryRoute
import com.trust3.xcpro.livefollow.watch.LiveFollowWatchSelectionHint
import com.trust3.xcpro.livefollow.watch.LiveFollowWatchShareCodeScreen
import com.trust3.xcpro.livefollow.watch.LiveFollowWatchShareEntryRoute
import com.trust3.xcpro.livefollow.watch.LiveFollowWatchViewModel
import com.trust3.xcpro.appshell.navdrawer.MyAbout
import com.trust3.xcpro.appshell.navdrawer.MySupport
import com.trust3.xcpro.map.MapScreenViewModel
import com.trust3.xcpro.screens.navdrawer.lookandfeel.LookAndFeelScreen
import com.trust3.xcpro.screens.navdrawer.LayoutScreen
import com.trust3.xcpro.profiles.ProfileSelectionScreen
import com.trust3.xcpro.profiles.ProfileUiState
import com.trust3.xcpro.screens.navdrawer.ColorsScreen
import com.trust3.xcpro.screens.navdrawer.LevoVarioSettingsScreen
import com.trust3.xcpro.screens.navdrawer.HawkVarioSettingsScreen
import com.trust3.xcpro.screens.navdrawer.PolarSettingsScreen
import com.trust3.xcpro.screens.navdrawer.ThermallingSettingsScreen
import com.trust3.xcpro.screens.navdrawer.UnitsSettingsScreen
import com.trust3.xcpro.screens.navdrawer.OrientationSettingsScreen
import com.trust3.xcpro.screens.navdrawer.AdsbSettingsScreen
import com.trust3.xcpro.screens.navdrawer.ForecastSettingsScreen
import com.trust3.xcpro.screens.navdrawer.HotspotsSettingsScreen
import com.trust3.xcpro.screens.navdrawer.OgnSettingsScreen
import com.trust3.xcpro.screens.navdrawer.WeatherSettingsScreen
import com.trust3.xcpro.screens.diagnostics.VarioDiagnosticsScreen
import com.trust3.xcpro.screens.replay.IgcReplayScreen
import com.trust3.xcpro.navigation.SettingsRoutes
import com.trust3.xcpro.navigation.TrafficSettingsRoutes
import com.trust3.xcpro.startup.AUTO_START_LIVEFOLLOW_SHARING_ON_MAP
import com.trust3.xcpro.startup.STARTUP_CHOOSER_ROUTE
import com.trust3.xcpro.startup.StartupChooserScreen
import com.trust3.xcpro.startup.consumeAutoStartLiveFollowSharingOnMap
import com.trust3.xcpro.startup.ensureMapRoute
import com.trust3.xcpro.startup.navigateFromStartupToFlyingMap
import com.trust3.xcpro.startup.navigateFromStartupToFriendsFlying
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    navController: NavHostController,
    drawerState: DrawerState,
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
                flightDataMgmtPort = mapViewModel.flightDataMgmtPort
            )
        }
        composable("flight_data") { backStackEntry ->
            val mapEntry = remember(backStackEntry) { navController.getBackStackEntry("map") }
            val mapViewModel: MapScreenViewModel = hiltViewModel(mapEntry)
            FlightMgmt(
                navController = navController,
                drawerState = drawerState,
                activeProfile = profileUiState.activeProfile,
                flightDataMgmtPort = mapViewModel.flightDataMgmtPort
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
            com.trust3.xcpro.profiles.ProfileSettingsScreen(profileId = profileId, navController = navController)
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

