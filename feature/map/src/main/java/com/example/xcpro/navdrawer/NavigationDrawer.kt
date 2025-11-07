package com.example.xcpro.navdrawer

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.xcpro.profiles.ProfileViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import kotlinx.coroutines.launch

private const val TAG = "NavigationDrawer"

/**
 * Main navigation drawer component
 * Refactored into modular sections for maintainability
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDrawer(
    modifier: Modifier = Modifier,
    drawerState: DrawerState,
    navController: NavHostController,
    profileExpanded: MutableState<Boolean>,
    mapStyleExpanded: MutableState<Boolean>,
    settingsExpanded: MutableState<Boolean>,
    initialMapStyle: String,
    onItemSelected: (String) -> Unit = {},
    onMapStyleSelected: (String) -> Unit = {},
    content: @Composable () -> Unit
) {
    Log.d(TAG, "🚪 NavigationDrawer composable started")
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedMapStyle by remember { mutableStateOf(initialMapStyle) }

    val mapStyleSelectedCallback: (String) -> Unit = { style ->
        selectedMapStyle = style
        onMapStyleSelected(style)
        Log.d(TAG, "Map style updated: $style")
    }

    Log.d(TAG, "🚪 DismissibleNavigationDrawer rendering with state: ${drawerState.currentValue}")
    DismissibleNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            DismissibleDrawerSheet(
                modifier = Modifier.width(210.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    DrawerHeader()

                    Spacer(modifier = Modifier.height(8.dp))

                    // XCPro V1 quick entry
                    // Profile Section
                    ProfileSection(
                        profileViewModel = profileViewModel,
                        isExpanded = profileExpanded.value,
                        onToggle = {
                            profileExpanded.value = !profileExpanded.value
                            saveNavDrawerConfig(context, profileExpanded.value, mapStyleExpanded.value, settingsExpanded.value)
                        },
                        navController = navController,
                        drawerState = drawerState,
                        scope = scope
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Task Section
                    TaskSection(
                        isExpanded = true, // Always expanded for task section
                        onToggle = { /* Not toggleable */ },
                        navController = navController,
                        drawerState = drawerState,
                        scope = scope,
                        onItemSelected = onItemSelected
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Map Style Section
                    MapStyleSection(
                        isExpanded = mapStyleExpanded.value,
                        onToggle = {
                            mapStyleExpanded.value = !mapStyleExpanded.value
                            saveNavDrawerConfig(context, profileExpanded.value, mapStyleExpanded.value, settingsExpanded.value)
                        },
                        selectedMapStyle = selectedMapStyle,
                        onMapStyleSelected = mapStyleSelectedCallback
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Settings Section
                    Log.d(TAG, "🔧 Rendering Settings section: expanded=${settingsExpanded.value}")
                    SettingsSection(
                        isExpanded = settingsExpanded.value,
                        onToggle = {
                            settingsExpanded.value = !settingsExpanded.value
                            saveNavDrawerConfig(context, profileExpanded.value, mapStyleExpanded.value, settingsExpanded.value)
                        },
                        navController = navController,
                        drawerState = drawerState,
                        scope = scope
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Items
                    BottomMenuItems(
                        navController = navController,
                        drawerState = drawerState,
                        scope = scope
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        },
        modifier = modifier
    ) {
        content()
    }
}

/**
 * Drawer header with app title
 */
@Composable
private fun DrawerHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "XC Pro",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
