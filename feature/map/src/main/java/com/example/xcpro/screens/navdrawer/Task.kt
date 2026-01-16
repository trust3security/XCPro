package com.example.ui1.screens

import android.net.Uri
import com.example.xcpro.core.common.logging.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.AirspaceRepository
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.loadWaypointFiles
import com.example.xcpro.saveWaypointFiles
import com.example.xcpro.screens.navdrawer.tasks.TaskAirspaceClassCard
import com.example.xcpro.screens.navdrawer.tasks.TaskFilesBottomBar
import com.example.xcpro.screens.navdrawer.tasks.TaskFilesBottomSheetContent
import com.example.ui1.icons.Reply_all
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.StandardScaleGestureDetector
import java.io.File

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val TAG = "TaskScreen"
private const val SWIPE_COOLDOWN_MS = 300L
private const val ZOOM_SENSITIVITY = 0.005f
private const val DOUBLE_TAP_ZOOM_DELTA = 1.0
private const val INITIAL_LATITUDE = -30.87
private const val INITIAL_LONGITUDE = 150.52
private const val INITIAL_ZOOM = 7.5


@OptIn(ExperimentalMaterialApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Task(
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
    val airspaceRepository = remember(context) { AirspaceRepository(context) }
    val selectedAirspaceFiles = remember { mutableStateListOf<Uri>() }
    val airspaceCheckedStates = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    val selectedWaypointFiles = remember { mutableStateListOf<Uri>() }
    val waypointCheckedStates = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    val selectedClasses = remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentMode by remember { mutableStateOf(FlightMode.CRUISE) }
    val modes = FlightMode.values()
    var swipeDirection by remember { mutableStateOf<String?>(null) }
    var lastSwipeTime by remember { mutableLongStateOf(0L) }
    var targetZoom by remember { mutableStateOf<Float?>(null) }
    var targetLatLng by remember { mutableStateOf<LatLng?>(null) }

    LaunchedEffect(Unit) {
        val (airspaceFiles, airspaceChecks) = airspaceRepository.loadAirspaceFiles()
        val (waypointFiles, waypointChecks) = loadWaypointFiles(context)
        selectedAirspaceFiles.clear()
        selectedAirspaceFiles.addAll(airspaceFiles)
        airspaceCheckedStates.value = airspaceChecks
        selectedWaypointFiles.clear()
        selectedWaypointFiles.addAll(waypointFiles)
        waypointCheckedStates.value = waypointChecks
        selectedClasses.value = airspaceRepository.loadSelectedClasses() ?: mutableMapOf()
    }

    val airspaceFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val fileName = copyFileToInternalStorage(context, it)
                    if (!fileName.endsWith(".txt", ignoreCase = true)) {
                        errorMessage = "Only .txt files are supported for airspace files."
                        AppLogger.e(TAG, "Selected file is not a .txt file: $fileName")
                        return@launch
                    }
                    if (!selectedAirspaceFiles.any { file -> file.lastPathSegment?.substringAfterLast("/") == fileName }) {
                        selectedAirspaceFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                        airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                            put(fileName, false)
                        }
                        airspaceRepository.saveAirspaceFiles(selectedAirspaceFiles, airspaceCheckedStates.value)
                        val newClasses = airspaceRepository.parseClasses(selectedAirspaceFiles)
                        selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                            newClasses.forEach { put(it, it == "R" || it == "D") }
                        }
                        airspaceRepository.saveSelectedClasses(selectedClasses.value)
                        loadAndApplyAirspace(context, mapLibreMap, airspaceRepository)
                        errorMessage = null
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error copying file: ${e.message}")
                    errorMessage = "Error copying file: ${e.message}"
                }
            }
        }
    }

    val waypointFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val fileName = copyFileToInternalStorage(context, it)
                    if (!fileName.endsWith(".cup", ignoreCase = true)) {
                        errorMessage = "Only .cup files are supported for waypoint files."
                        AppLogger.e(TAG, "Selected file is not a .cup file: $fileName")
                        return@launch
                    }
                    if (!selectedWaypointFiles.any { file -> file.lastPathSegment?.substringAfterLast("/") == fileName }) {
                        selectedWaypointFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                        waypointCheckedStates.value = waypointCheckedStates.value.toMutableMap().apply {
                            put(fileName, true) // Default to checked
                        }
                        saveWaypointFiles(context, selectedWaypointFiles, waypointCheckedStates.value)
                        errorMessage = null
                        loadAndApplyWaypoints(context, mapLibreMap, selectedWaypointFiles, waypointCheckedStates.value)
                        AppLogger.d(TAG, "Waypoint file added and saved: $fileName")
                    } else {
                        errorMessage = "File already selected: $fileName"
                        AppLogger.d(TAG, "Duplicate waypoint file ignored: $fileName")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error copying waypoint file: ${e.message}")
                    errorMessage = "Error copying file: ${e.message}"
                }
            }
        }
    }

    val animatedZoom by animateFloatAsState(
        targetValue = targetZoom ?: INITIAL_ZOOM.toFloat(),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
    )

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

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (selectedNavItem == "Files") 0.dp else 0.dp,
        sheetContent = {
            if (selectedNavItem == "Files") {
                TaskFilesBottomSheetContent(
                    selectedItem = selectedItem,
                    onSelectItem = { selectedItem = it },
                    context = context,
                    mapLibreMap = mapLibreMap,
                    airspaceFilePickerLauncher = airspaceFilePickerLauncher,
                    waypointFilePickerLauncher = waypointFilePickerLauncher,
                    errorMessage = errorMessage,
                    onErrorMessage = { errorMessage = it },
                    selectedAirspaceFiles = selectedAirspaceFiles,
                    airspaceCheckedStates = airspaceCheckedStates,
                    selectedWaypointFiles = selectedWaypointFiles,
                    waypointCheckedStates = waypointCheckedStates,
                    selectedClasses = selectedClasses
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
                        IconButton(onClick = {
                            scope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back and Open Drawer")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                navController.popBackStack("map", inclusive = false)
                                drawerState.open()
                            }
                        }) {
                            Icon(
                                imageVector = Reply_all,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        navController.popBackStack("map", inclusive = false)
                    }) {
                        Icon(Icons.Default.Map, contentDescription = "Home")
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
                            map.setStyle("https://api.maptiler.com/maps/streets-v2/style.json?key=nYDScLfnBm52GAc3jXEZ") {
                                // Configure UI settings AFTER style is loaded
                                map.uiSettings.isZoomGesturesEnabled = true   // ENABLE zoom gestures (pinch to zoom)
                                map.uiSettings.isRotateGesturesEnabled = false
                                map.uiSettings.isTiltGesturesEnabled = false
                                map.uiSettings.isScrollGesturesEnabled = true  // ENABLE pan gestures (drag to pan)
                                map.uiSettings.isQuickZoomGesturesEnabled = false

                                // Add gesture listeners to debug
                                map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                                    override fun onMoveBegin(detector: MoveGestureDetector) {
                                        AppLogger.d(TAG, "TASK MAP MOVE DETECTED - Task screen gesture working!")
                                    }
                                    override fun onMove(detector: MoveGestureDetector) {}
                                    override fun onMoveEnd(detector: MoveGestureDetector) {
                                        AppLogger.d(TAG, "TASK MAP MOVE ENDED")
                                    }
                                })

                                map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
                                    override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                                        AppLogger.d(TAG, "TASK MAP ZOOM DETECTED - Task screen zoom working!")
                                    }
                                    override fun onScale(detector: StandardScaleGestureDetector) {}
                                    override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                                        AppLogger.d(TAG, "TASK MAP ZOOM ENDED")
                                    }
                                })

                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(-30.8700, 150.5200))
                                    .zoom(8.0)
                                    .build()
                                scope.launch {
                                    loadAndApplyAirspace(ctx, map, airspaceRepository)
                                }
                                scope.launch {
                                    loadAndApplyWaypoints(ctx, map, selectedWaypointFiles, waypointCheckedStates.value)
                                }
                                AppLogger.d(TAG, "MapLibre gestures configured: scroll=${map.uiSettings.isScrollGesturesEnabled}, zoom=${map.uiSettings.isZoomGesturesEnabled}")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        // Add debug logging for touch events at parent level
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (AppLogger.rateLimit(TAG, "task_pointer", 1_000L)) {
                                    AppLogger.d(TAG, "Parent received touch event: ${event.type}, pointers: ${event.changes.size}")
                                }
                            }
                        }
                    }
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


