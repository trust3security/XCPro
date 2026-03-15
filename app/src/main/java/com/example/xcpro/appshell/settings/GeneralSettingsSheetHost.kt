package com.example.xcpro.appshell.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun SettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onShowAirspaceOverlay: () -> Unit
) {
    val scope = rememberCoroutineScope()
    GeneralSettingsSheetHost(
        navController = navController,
        drawerState = drawerState,
        onDismissRequest = {
            scope.launch { closeGeneralToMap(navController, drawerState) }
        },
        onNavigateUp = {
            scope.launch { closeGeneralToDrawer(navController, drawerState) }
        },
        onNavigateToMap = {
            scope.launch { closeGeneralToMap(navController, drawerState) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeneralSettingsSheetHost(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismissRequest: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            SettingsTopAppBar(
                title = "General",
                onNavigateUp = onNavigateUp,
                onSecondaryNavigate = null,
                onNavigateToMap = onNavigateToMap
            )
            GeneralSettingsContent(
                navController = navController,
                drawerState = drawerState,
                onNavigateToMap = onNavigateToMap,
                onNavigateToDrawer = onNavigateUp
            )
        }
    }
}

@Composable
private fun GeneralSettingsContent(
    navController: NavHostController,
    drawerState: DrawerState,
    onNavigateToMap: () -> Unit,
    onNavigateToDrawer: () -> Unit
) {
    var activeSubSheet by remember { mutableStateOf(GeneralSubSheet.NONE) }

    GeneralSettingsCategoryGrid(
        onSubSheetSelected = { activeSubSheet = it }
    )

    GeneralSettingsSubSheetContent(
        activeSubSheet = activeSubSheet,
        navController = navController,
        drawerState = drawerState,
        onNavigateToMap = onNavigateToMap,
        onNavigateToDrawer = onNavigateToDrawer,
        onSubSheetChange = { activeSubSheet = it }
    )
}
