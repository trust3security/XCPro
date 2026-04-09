package com.example.xcpro.appshell.settings

import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.example.ui1.screens.AdsbSettingsSubSheet
import com.example.ui1.screens.DFNavboxes
import com.example.ui1.screens.FilesScreen
import com.example.ui1.screens.HotspotsSettingsSubSheet
import com.example.ui1.screens.OgnSettingsSubSheet
import com.example.ui1.screens.ProfilesScreen
import com.example.ui1.screens.WeGlideSettingsSubSheet
import com.example.xcpro.screens.navdrawer.BluetoothVarioSettingsScreen
import com.example.xcpro.screens.navdrawer.ForecastSettingsScreen
import com.example.xcpro.screens.navdrawer.HawkVarioSettingsScreen
import com.example.xcpro.screens.navdrawer.LayoutScreen
import com.example.xcpro.screens.navdrawer.LevoVarioSettingsScreen
import com.example.xcpro.screens.navdrawer.PolarSettingsScreen
import com.example.xcpro.screens.navdrawer.UnitsSettingsScreen
import com.example.xcpro.screens.navdrawer.lookandfeel.LookAndFeelScreen
import com.example.xcpro.screens.replay.IgcFilesScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRouteSubSheetContainer(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        content()
    }
}

@Composable
internal fun FilesSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        FilesScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun ProfilesSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        ProfilesScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap,
            onClose = onNavigateToMap
        )
    }
}

@Composable
internal fun LookAndFeelSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        LookAndFeelScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun UnitsSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        UnitsSettingsScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun PolarSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        PolarSettingsScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun LevoVarioSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        LevoVarioSettingsScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun HawkVarioSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        HawkVarioSettingsScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun BluetoothVarioSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        BluetoothVarioSettingsScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun LayoutSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        LayoutScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun ForecastSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        ForecastSettingsScreen(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap
        )
    }
}

@Composable
internal fun NavboxesSettingsSubSheet(
    navController: NavHostController,
    drawerState: DrawerState,
    onDismiss: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onNavigateToMap: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        DFNavboxes(
            navController = navController,
            drawerState = drawerState,
            onNavigateUp = onDismiss,
            onSecondaryNavigate = onNavigateToDrawer,
            onNavigateToMap = onNavigateToMap,
            onClose = onNavigateToMap
        )
    }
}

@Composable
internal fun IgcFilesSettingsSubSheet(
    navController: NavHostController,
    onDismiss: () -> Unit
) {
    SettingsRouteSubSheetContainer(onDismiss = onDismiss) {
        IgcFilesScreen(
            navController = navController,
            onNavigateBack = onDismiss
        )
    }
}
