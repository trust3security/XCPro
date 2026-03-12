package com.example.ui1.screens

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

@Composable
internal fun GeneralSettingsSubSheetContent(
    activeSubSheet: GeneralSubSheet,
    navController: NavHostController,
    drawerState: DrawerState,
    onNavigateToMap: () -> Unit,
    onNavigateToDrawer: () -> Unit,
    onSubSheetChange: (GeneralSubSheet) -> Unit
) {
    when (activeSubSheet) {
        GeneralSubSheet.NONE -> Unit
        GeneralSubSheet.WEATHER -> {
            WeatherSettingsSubSheet(
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.FILES -> {
            FilesSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.PROFILES -> {
            ProfilesSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.LOOK_AND_FEEL -> {
            LookAndFeelSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.UNITS -> {
            UnitsSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.POLAR -> {
            PolarSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.LEVO_VARIO -> {
            LevoVarioSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.HAWK_VARIO -> {
            HawkVarioSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.LAYOUTS -> {
            LayoutSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.SKYSIGHT -> {
            ForecastSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.OGN -> {
            OgnSettingsSubSheet(
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.WEGLIDE -> {
            WeGlideSettingsSubSheet(
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.ADSB -> {
            AdsbSettingsSubSheet(
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.HOTSPOTS -> {
            HotspotsSettingsSubSheet(
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.NAVBOXES -> {
            NavboxesSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.IGC_REPLAY -> {
            IgcFilesSettingsSubSheet(
                navController = navController,
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) }
            )
        }
        GeneralSubSheet.ORIENTATION -> {
            OrientationSettingsSubSheet(
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.THERMALLING -> {
            ThermallingSettingsSubSheet(
                onDismiss = { onSubSheetChange(GeneralSubSheet.NONE) },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
    }
}
