package com.example.xcpro.screens.navdrawer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.rememberBottomSheetScaffoldState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.ui1.icons.Reply_all
import com.example.xcpro.airspace.AirspaceViewModel
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.flightdata.WaypointsViewModel
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.map.MapStyleCatalog
import com.example.xcpro.map.MapStyleUrlResolver
import com.example.xcpro.map.ui.documentRefForUri
import com.example.xcpro.screens.navdrawer.tasks.TaskFilesBottomBar
import com.example.xcpro.screens.navdrawer.tasks.TaskFilesBottomSheetContent
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.StandardScaleGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

private const val TAG = "TaskScreen"
private const val INITIAL_LATITUDE = -30.87
private const val INITIAL_LONGITUDE = 150.52
private const val INITIAL_ZOOM = 7.5

@OptIn(ExperimentalMaterialApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TaskRouteScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    selectedNavItem: String?,
    onShowBottomSheet: () -> Unit = {},
    onHideBottomSheet: () -> Unit = {}
) {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val airspaceViewModel: AirspaceViewModel = hiltViewModel()
    val waypointsViewModel: WaypointsViewModel = hiltViewModel()
    val taskScreenUseCasesViewModel: TaskScreenUseCasesViewModel = hiltViewModel()
    val airspaceState by airspaceViewModel.uiState.collectAsStateWithLifecycle()
    val waypointsState by waypointsViewModel.uiState.collectAsStateWithLifecycle()
    val airspaceUseCase = taskScreenUseCasesViewModel.airspaceUseCase
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var targetZoom by remember { mutableStateOf<Float?>(null) }
    var targetLatLng by remember { mutableStateOf<LatLng?>(null) }

    val airspaceFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { airspaceViewModel.importFile(documentRefForUri(context, it)) }
    }

    val waypointFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { waypointsViewModel.importFile(documentRefForUri(context, it)) }
    }

    val animatedZoom by animateFloatAsState(targetValue = targetZoom ?: INITIAL_ZOOM.toFloat())

    LaunchedEffect(animatedZoom, targetLatLng) {
        mapLibreMap?.let { map ->
            try {
                val latLng = targetLatLng ?: LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, animatedZoom.toDouble()))
                AppLogger.d(TAG, "Camera moved to lat=${latLng.latitude}, lon=${latLng.longitude}, zoom=$animatedZoom")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error moving camera: ${e.message}")
            }
        }
    }

    LaunchedEffect(mapLibreMap, airspaceState.enabledFiles, airspaceState.classStates) {
        if (mapLibreMap != null) {
            loadAndApplyAirspace(mapLibreMap, airspaceUseCase)
        }
    }

    LaunchedEffect(mapLibreMap, waypointsState.files, waypointsState.checkedStates) {
        if (mapLibreMap != null) {
            loadAndApplyWaypoints(context, mapLibreMap, waypointsState.files, waypointsState.checkedStates)
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (selectedNavItem == "Files") 0.dp else 0.dp,
        sheetContent = {
            if (selectedNavItem == "Files") {
                TaskFilesBottomSheetContent(
                    selectedItem = selectedItem,
                    onSelectItem = { selectedItem = it },
                    airspaceFilePickerLauncher = airspaceFilePickerLauncher,
                    waypointFilePickerLauncher = waypointFilePickerLauncher,
                    airspaceViewModel = airspaceViewModel,
                    waypointsViewModel = waypointsViewModel
                )
            } else {
                Box {}
            }
        },
        topBar = {
            androidx.compose.material3.CenterAlignedTopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                ),
                title = { androidx.compose.material3.Text(text = "Task") },
                navigationIcon = {
                    Row {
                        androidx.compose.material3.IconButton(onClick = {
                            scope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }) {
                            androidx.compose.material.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back and Open Drawer"
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = {
                            scope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }) {
                            androidx.compose.material.Icon(
                                imageVector = Reply_all,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier
                            )
                        }
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(onClick = {
                        navController.popBackStack("map", inclusive = false)
                    }) {
                        androidx.compose.material.Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = "Home"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    top = innerPadding.calculateTopPadding(),
                    bottom = 0.dp
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        AppLogger.d(TAG, "Creating MapView for Task screen")
                        getMapAsync { map: MapLibreMap ->
                            mapLibreMap = map
                            map.setStyle(
                                MapStyleUrlResolver.resolve(MapStyleCatalog.defaultSelectableKey())
                            ) {
                                map.uiSettings.isZoomGesturesEnabled = true
                                map.uiSettings.isRotateGesturesEnabled = false
                                map.uiSettings.isTiltGesturesEnabled = false
                                map.uiSettings.isScrollGesturesEnabled = true
                                map.uiSettings.isQuickZoomGesturesEnabled = false

                                map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                                    override fun onMoveBegin(detector: MoveGestureDetector) {
                                        AppLogger.d(TAG, "TASK MAP MOVE DETECTED - Task screen gesture working!")
                                    }
                                    override fun onMove(detector: MoveGestureDetector) = Unit
                                    override fun onMoveEnd(detector: MoveGestureDetector) {
                                        AppLogger.d(TAG, "TASK MAP MOVE ENDED")
                                    }
                                })

                                map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
                                    override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                                        AppLogger.d(TAG, "TASK MAP ZOOM DETECTED - Task screen zoom working!")
                                    }
                                    override fun onScale(detector: StandardScaleGestureDetector) = Unit
                                    override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                                        AppLogger.d(TAG, "TASK MAP ZOOM ENDED")
                                    }
                                })

                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(INITIAL_LATITUDE, INITIAL_LONGITUDE))
                                    .zoom(8.0)
                                    .build()
                                scope.launch {
                                    loadAndApplyAirspace(map, airspaceUseCase)
                                }
                                scope.launch {
                                    loadAndApplyWaypoints(ctx, map, waypointsState.files, waypointsState.checkedStates)
                                }
                                AppLogger.d(
                                    TAG,
                                    "MapLibre gestures configured: scroll=${map.uiSettings.isScrollGesturesEnabled}, zoom=${map.uiSettings.isZoomGesturesEnabled}"
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (selectedNavItem == "Files") {
                TaskFilesBottomBar(
                    selectedItem = selectedItem,
                    onItemClick = { itemName ->
                        selectedItem = itemName
                        scope.launch {
                            scaffoldState.bottomSheetState.expand()
                            onShowBottomSheet()
                        }
                    }
                )
            }
        }
    }
}
