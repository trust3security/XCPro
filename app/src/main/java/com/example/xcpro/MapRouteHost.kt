package com.example.xcpro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.xcpro.appshell.settings.GeneralSettingsSheetHost
import com.example.xcpro.appshell.settings.consumeOpenGeneralSettingsOnMap
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.map.ui.MapScreen
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun SharedMapRouteHost(
    navController: NavHostController,
    drawerState: androidx.compose.material3.DrawerState,
    initialMapStyle: String,
    config: JSONObject?,
    allowFlightSensorStart: Boolean,
    viewModelOwnerEntry: NavBackStackEntry,
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    val mapViewModel: MapScreenViewModel = hiltViewModel(viewModelOwnerEntry)
    val openGeneralSettingsOnMap by viewModelOwnerEntry.savedStateHandle
        .getStateFlow(
            com.example.xcpro.navigation.MapNavigationSignals.OPEN_GENERAL_SETTINGS_ON_MAP,
            false
        )
        .collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showGeneralSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openGeneralSettingsOnMap) {
        if (openGeneralSettingsOnMap && consumeOpenGeneralSettingsOnMap(viewModelOwnerEntry)) {
            showGeneralSettings = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelOwnerEntry) {
            MapScreen(
                navController = navController,
                drawerState = drawerState,
                profileExpanded = remember(config) {
                    mutableStateOf(
                        config?.optJSONObject("navDrawer")
                            ?.optBoolean("profileExpanded", true)
                            ?: true
                    )
                },
                mapStyleExpanded = remember(config) {
                    mutableStateOf(
                        config?.optJSONObject("navDrawer")
                            ?.optBoolean("mapStyleExpanded", false)
                            ?: false
                    )
                },
                settingsExpanded = remember(config) {
                    mutableStateOf(
                        config?.optJSONObject("navDrawer")
                            ?.optBoolean("settingsExpanded", true)
                            ?: true
                    )
                },
                initialMapStyle = initialMapStyle,
                allowFlightSensorStart = allowFlightSensorStart,
                onOpenGeneralSettings = {
                    showGeneralSettings = true
                },
                mapViewModel = mapViewModel
            )
        }

        overlayContent()

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
