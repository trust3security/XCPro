package com.example.ui1.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.xcpro.map.R
import com.example.xcpro.navigation.SettingsRoutes
import com.example.xcpro.screens.navdrawer.HotspotsSettingsContent
import com.example.xcpro.screens.navdrawer.HotspotsSettingsViewModel
import com.example.xcpro.screens.navdrawer.OgnSettingsContent
import com.example.xcpro.screens.navdrawer.OgnSettingsViewModel
import com.example.xcpro.screens.navdrawer.SettingsTopAppBar
import com.example.xcpro.screens.navdrawer.ThermallingSettingsContent
import com.example.xcpro.screens.navdrawer.ThermallingSettingsViewModel
import com.example.xcpro.screens.navdrawer.WeatherSettingsSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    onShowAirspaceOverlay: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val closeToDrawer: () -> Unit = {
        scope.launch {
            closeGeneralToDrawer(
                navController = navController,
                drawerState = drawerState
            )
        }
    }
    val closeToMap: () -> Unit = {
        scope.launch {
            closeGeneralToMap(
                navController = navController,
                drawerState = drawerState
            )
        }
    }
    GeneralSettingsSheetHost(
        navController = navController,
        drawerState = drawerState,
        onDismissRequest = closeToMap,
        onNavigateUp = closeToDrawer,
        onNavigateToMap = closeToMap
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsSheetHost(
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
            modifier = Modifier
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

internal suspend fun closeGeneralToMap(
    navController: NavHostController,
    drawerState: DrawerState
) {
    drawerState.close()
    val poppedToMap = navController.popBackStack("map", inclusive = false)
    if (!poppedToMap) {
        navController.navigateUp()
    }
}

internal suspend fun closeGeneralToDrawer(
    navController: NavHostController,
    drawerState: DrawerState
) {
    val poppedToMap = navController.popBackStack("map", inclusive = false)
    if (!poppedToMap) {
        navController.navigateUp()
    }
    drawerState.open()
}

@Composable
private fun GeneralSettingsContent(
    navController: NavHostController,
    drawerState: DrawerState,
    onNavigateToMap: () -> Unit,
    onNavigateToDrawer: () -> Unit
) {
    val listState = rememberLazyListState()
    var activeSubSheet by remember { mutableStateOf(GeneralSubSheet.NONE) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Files",
                            icon = Icons.Default.Folder,
                            onClick = { activeSubSheet = GeneralSubSheet.FILES },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Profiles",
                            icon = Icons.Default.Map,
                            onClick = { activeSubSheet = GeneralSubSheet.PROFILES },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Look & Feel",
                            icon = Icons.Outlined.Style,
                            onClick = { activeSubSheet = GeneralSubSheet.LOOK_AND_FEEL },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Units",
                            icon = Icons.Default.Straighten,
                            onClick = { activeSubSheet = GeneralSubSheet.UNITS },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Polar",
                            icon = Icons.Default.Flight,
                            onClick = { activeSubSheet = GeneralSubSheet.POLAR },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Levo Vario",
                            icon = Icons.Default.Speed,
                            onClick = { activeSubSheet = GeneralSubSheet.LEVO_VARIO },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "HAWK Vario",
                            icon = Icons.Default.Speed,
                            onClick = { activeSubSheet = GeneralSubSheet.HAWK_VARIO },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Orientation",
                            icon = Icons.Default.Explore,
                            onClick = { activeSubSheet = GeneralSubSheet.ORIENTATION },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Layouts",
                            icon = Icons.Default.GridView,
                            onClick = { activeSubSheet = GeneralSubSheet.LAYOUTS },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Proximity",
                            icon = Icons.Default.AirplanemodeActive,
                            onClick = { activeSubSheet = GeneralSubSheet.PROXIMITY },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItemDrawable(
                            title = "SkySight",
                            iconResId = R.drawable.ic_skysight,
                            iconSize = 26.4.dp,
                            onClick = { activeSubSheet = GeneralSubSheet.SKYSIGHT },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "Hotspots",
                            icon = Icons.Default.Speed,
                            onClick = { activeSubSheet = GeneralSubSheet.HOTSPOTS },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItemDrawable(
                            title = "RainViewer",
                            iconResId = R.drawable.rainviewer,
                            iconSize = 29.04.dp,
                            onClick = { activeSubSheet = GeneralSubSheet.WEATHER },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "OGN",
                            icon = Icons.Default.Flight,
                            onClick = { activeSubSheet = GeneralSubSheet.OGN },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = stringResource(R.string.thermalling_title),
                            icon = Icons.Default.Explore,
                            onClick = { activeSubSheet = GeneralSubSheet.THERMALLING },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "ADS-b",
                            icon = Icons.Default.AirplanemodeActive,
                            onClick = { activeSubSheet = GeneralSubSheet.ADSB },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryItem(
                            title = "Navboxes",
                            icon = Icons.Default.Dashboard,
                            onClick = { activeSubSheet = GeneralSubSheet.NAVBOXES },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryItem(
                            title = "IGC Replay",
                            icon = Icons.Default.PlayArrow,
                            onClick = { activeSubSheet = GeneralSubSheet.IGC_REPLAY },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    when (activeSubSheet) {
        GeneralSubSheet.NONE -> Unit
        GeneralSubSheet.PROXIMITY -> {
            ProximitySettingsSheet(
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap,
                onOpenOgn = {
                    activeSubSheet = GeneralSubSheet.OGN
                },
                onOpenHotspots = {
                    activeSubSheet = GeneralSubSheet.HOTSPOTS
                },
                onOpenLookAndFeel = {
                    activeSubSheet = GeneralSubSheet.NONE
                    navController.navigate("look_and_feel")
                },
                onOpenColors = {
                    activeSubSheet = GeneralSubSheet.NONE
                    navController.navigate("colors")
                }
            )
        }
        GeneralSubSheet.WEATHER -> {
            WeatherSettingsSubSheet(
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.FILES -> {
            FilesSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.PROFILES -> {
            ProfilesSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.LOOK_AND_FEEL -> {
            LookAndFeelSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.UNITS -> {
            UnitsSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.POLAR -> {
            PolarSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.LEVO_VARIO -> {
            LevoVarioSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.HAWK_VARIO -> {
            HawkVarioSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.LAYOUTS -> {
            LayoutSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.SKYSIGHT -> {
            ForecastSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.OGN -> {
            OgnSettingsSubSheet(
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.ADSB -> {
            AdsbSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.HOTSPOTS -> {
            HotspotsSettingsSubSheet(
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.NAVBOXES -> {
            NavboxesSettingsSubSheet(
                navController = navController,
                drawerState = drawerState,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.IGC_REPLAY -> {
            IgcReplaySettingsSubSheet(
                navController = navController,
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE }
            )
        }
        GeneralSubSheet.ORIENTATION -> {
            OrientationSettingsSubSheet(
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
        GeneralSubSheet.THERMALLING -> {
            ThermallingSettingsSubSheet(
                onDismiss = { activeSubSheet = GeneralSubSheet.NONE },
                onNavigateToDrawer = onNavigateToDrawer,
                onNavigateToMap = onNavigateToMap
            )
        }
    }
}

private enum class GeneralSubSheet {
    NONE,
    PROXIMITY,
    WEATHER,
    FILES,
    PROFILES,
    LOOK_AND_FEEL,
    UNITS,
    POLAR,
    LEVO_VARIO,
    HAWK_VARIO,
    LAYOUTS,
    SKYSIGHT,
    OGN,
    ADSB,
    HOTSPOTS,
    NAVBOXES,
    IGC_REPLAY,
    ORIENTATION,
    THERMALLING
}

@Composable
fun CategoryItem(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun CategoryItemDrawable(
    title: String,
    iconResId: Int,
    iconSize: Dp = 24.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = Color.Black
            )
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

