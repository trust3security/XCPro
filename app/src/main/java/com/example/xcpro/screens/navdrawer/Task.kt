package com.example.ui1.screens

import android.net.Uri
import android.util.Log
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
import com.example.xcpro.FlightMode
import com.example.xcpro.copyFileToInternalStorage
import com.example.xcpro.loadAirspaceFiles
import com.example.xcpro.loadAndApplyAirspace
import com.example.xcpro.loadAndApplyWaypoints
import com.example.xcpro.loadSelectedClasses
import com.example.xcpro.loadWaypointFiles
import com.example.xcpro.parseAirspaceClasses
import com.example.xcpro.saveAirspaceFiles
import com.example.xcpro.saveSelectedClasses
import com.example.xcpro.saveWaypointFiles
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
        val (airspaceFiles, airspaceChecks) = loadAirspaceFiles(context)
        val (waypointFiles, waypointChecks) = loadWaypointFiles(context)
        selectedAirspaceFiles.clear()
        selectedAirspaceFiles.addAll(airspaceFiles)
        airspaceCheckedStates.value = airspaceChecks
        selectedWaypointFiles.clear()
        selectedWaypointFiles.addAll(waypointFiles)
        waypointCheckedStates.value = waypointChecks
        selectedClasses.value = loadSelectedClasses(context) ?: mutableMapOf()
    }

    val airspaceFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val fileName = copyFileToInternalStorage(context, it)
                    if (!fileName.endsWith(".txt", ignoreCase = true)) {
                        errorMessage = "Only .txt files are supported for airspace files."
                        Log.e(TAG, "Selected file is not a .txt file: $fileName")
                        return@launch
                    }
                    if (!selectedAirspaceFiles.any { file -> file.lastPathSegment?.substringAfterLast("/") == fileName }) {
                        selectedAirspaceFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                        airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                            put(fileName, false)
                        }
                        saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
                        val newClasses = parseAirspaceClasses(context, selectedAirspaceFiles)
                        selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                            newClasses.forEach { put(it, it == "R" || it == "D") }
                        }
                        saveSelectedClasses(context, selectedClasses.value)
                        loadAndApplyAirspace(context, mapLibreMap)
                        errorMessage = null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying file: ${e.message}")
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
                        Log.e(TAG, "Selected file is not a .cup file: $fileName")
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
                        Log.d(TAG, "Waypoint file added and saved: $fileName")
                    } else {
                        errorMessage = "File already selected: $fileName"
                        Log.d(TAG, "Duplicate waypoint file ignored: $fileName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying waypoint file: ${e.message}")
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
                Log.d(TAG, "Camera moved to lat=${latLng.latitude}, lon=${latLng.longitude}, zoom=$animatedZoom")
            } catch (e: Exception) {
                Log.e(TAG, "Error moving camera: ${e.message}")
            }
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (selectedNavItem == "Files") 0.dp else 0.dp,
        sheetContent = {
            if (selectedNavItem == "Files") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color(0xFFF5F5F5)) // Very light grey, similar to system bottom bar
                        .padding(bottom = 54.dp)
                ) {
                    // Add drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .padding(top = 8.dp)
                            .background(Color.Gray, shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                    )
                    when (selectedItem) {
                        "Airspace" -> {
                            Button(
                                onClick = { airspaceFilePickerLauncher.launch("text/plain") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Select Airspace Files")
                            }
                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                            val listState = rememberLazyListState()
                            val isScrollable = listState.canScrollForward || listState.canScrollBackward
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                                    .drawWithContent {
                                        drawContent()
                                        if (isScrollable) {
                                            val firstVisibleItemIndex = listState.firstVisibleItemIndex
                                            val visibleItemCount = listState.layoutInfo.visibleItemsInfo.size
                                            val totalItemCount = listState.layoutInfo.totalItemsCount
                                            val scrollFraction = if (totalItemCount > 0) {
                                                firstVisibleItemIndex.toFloat() / (totalItemCount - visibleItemCount).coerceAtLeast(1)
                                            } else 0f
                                            val scrollbarHeight = size.height / totalItemCount.coerceAtLeast(1) * visibleItemCount
                                            val scrollbarOffsetY = scrollFraction * (size.height - scrollbarHeight)
                                            drawRect(
                                                color = Color.Gray.copy(alpha = 0.5f),
                                                topLeft = Offset(size.width - 8.dp.toPx(), scrollbarOffsetY),
                                                size = Size(8.dp.toPx(), scrollbarHeight.coerceAtLeast(8.dp.toPx()))
                                            )
                                        }
                                    }
                            ) {
                                items(selectedAirspaceFiles, key = { it.toString() }) { fileUri ->
                                    val fileName = fileUri.lastPathSegment?.substringAfterLast("/") ?: "Unknown file"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = airspaceCheckedStates.value[fileName] ?: false,
                                            onCheckedChange = {
                                                airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                                    put(fileName, it)
                                                }
                                                saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
                                                val newClasses = parseAirspaceClasses(context, selectedAirspaceFiles)
                                                selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                                    keys.retainAll(newClasses)
                                                }
                                                saveSelectedClasses(context, selectedClasses.value)
                                                loadAndApplyAirspace(context, mapLibreMap)
                                            },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = fileName.take(20).let {
                                                if (it.length >= 20) "$it..." else it
                                            },
                                            modifier = Modifier.weight(1f),
                                            color = Color.Black
                                        )
                                        IconButton(onClick = {
                                            selectedAirspaceFiles.remove(fileUri)
                                            airspaceCheckedStates.value = airspaceCheckedStates.value.toMutableMap().apply {
                                                remove(fileName)
                                            }
                                            saveAirspaceFiles(context, selectedAirspaceFiles, airspaceCheckedStates.value)
                                            val newClasses = parseAirspaceClasses(context, selectedAirspaceFiles)
                                            selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                                keys.retainAll(newClasses)
                                            }
                                            saveSelectedClasses(context, selectedClasses.value)
                                            loadAndApplyAirspace(context, mapLibreMap)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove file",
                                                tint = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "Waypoints" -> {
                            Button(
                                onClick = { waypointFilePickerLauncher.launch("application/octet-stream") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Select Waypoint Files")
                            }
                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                            val listState = rememberLazyListState()
                            val isScrollable = listState.canScrollForward || listState.canScrollBackward
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                                    .drawWithContent {
                                        drawContent()
                                        if (isScrollable) {
                                            val firstVisibleItemIndex = listState.firstVisibleItemIndex
                                            val visibleItemCount = listState.layoutInfo.visibleItemsInfo.size
                                            val totalItemCount = listState.layoutInfo.totalItemsCount
                                            val scrollFraction = if (totalItemCount > 0) {
                                                firstVisibleItemIndex.toFloat() / (totalItemCount - visibleItemCount).coerceAtLeast(1)
                                            } else 0f
                                            val scrollbarHeight = size.height / totalItemCount.coerceAtLeast(1) * visibleItemCount
                                            val scrollbarOffsetY = scrollFraction * (size.height - scrollbarHeight)
                                            drawRect(
                                                color = Color.Gray.copy(alpha = 0.5f),
                                                topLeft = Offset(size.width - 8.dp.toPx(), scrollbarOffsetY),
                                                size = Size(8.dp.toPx(), scrollbarHeight.coerceAtLeast(8.dp.toPx()))
                                            )
                                        }
                                    }
                            ) {
                                items(selectedWaypointFiles, key = { it.toString() }) { fileUri ->
                                    val fileName = fileUri.lastPathSegment?.substringAfterLast("/") ?: "Unknown file"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = waypointCheckedStates.value[fileName] ?: false,
                                            onCheckedChange = {
                                                waypointCheckedStates.value = waypointCheckedStates.value.toMutableMap().apply {
                                                    put(fileName, it)
                                                }
                                                saveWaypointFiles(context, selectedWaypointFiles, waypointCheckedStates.value)
                                                loadAndApplyWaypoints(context, mapLibreMap, selectedWaypointFiles, waypointCheckedStates.value)
                                            },
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(
                                            text = fileName.take(20).let {
                                                if (it.length >= 20) "$it..." else it
                                            },
                                            modifier = Modifier.weight(1f),
                                            color = Color.Black
                                        )
                                        IconButton(onClick = {
                                            selectedWaypointFiles.remove(fileUri)
                                            waypointCheckedStates.value = waypointCheckedStates.value.toMutableMap().apply {
                                                remove(fileName)
                                            }
                                            saveWaypointFiles(context, selectedWaypointFiles, waypointCheckedStates.value)
                                            loadAndApplyWaypoints(context, mapLibreMap, selectedWaypointFiles, waypointCheckedStates.value)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove file",
                                                tint = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "Classes" -> {
                            val classes = parseAirspaceClasses(context, selectedAirspaceFiles)
                            if (classes.isEmpty()) {
                                Text(
                                    text = "No airspace classes available. Please add airspace files.",
                                    style = MaterialTheme.typography.body2,
                                    textAlign = TextAlign.Center,
                                    color = Color.Black,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                )
                            } else {
                                // Convert to AirspaceClassItem format
                                val airspaceClassItems = classes.map { className ->
                                    AirspaceClassItem(
                                        className = className,
                                        enabled = selectedClasses.value[className] ?: false,
                                        color = when (className) {
                                            "A" -> "#FF0000"
                                            "C" -> "#FF6600"
                                            "D" -> "#0066FF"
                                            "R" -> "#FF0000"
                                            "G" -> "#00AA00"
                                            "CTR" -> "#9900FF"
                                            "TMZ" -> "#FFFF00"
                                            else -> "#888888"
                                        },
                                        description = when (className) {
                                            "A" -> "Controlled - IFR only"
                                            "C" -> "Controlled - Radio req"
                                            "D" -> "Controlled - Radio req"
                                            "R" -> "Restricted"
                                            "G" -> "General - Uncontrolled"
                                            "CTR" -> "Control Zone"
                                            "TMZ" -> "Transponder Mandatory"
                                            else -> "Unknown class"
                                        }
                                    )
                                }

                                val listState = rememberLazyListState()
                                val isScrollable = listState.canScrollForward || listState.canScrollBackward
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 16.dp)
                                        .drawWithContent {
                                            drawContent()
                                            if (isScrollable) {
                                                val firstVisibleItemIndex = listState.firstVisibleItemIndex
                                                val visibleItemCount = listState.layoutInfo.visibleItemsInfo.size
                                                val totalItemCount = listState.layoutInfo.totalItemsCount
                                                val scrollFraction = if (totalItemCount > 0) {
                                                    firstVisibleItemIndex.toFloat() / (totalItemCount - visibleItemCount).coerceAtLeast(1)
                                                } else 0f
                                                val scrollbarHeight = size.height / totalItemCount.coerceAtLeast(1) * visibleItemCount
                                                val scrollbarOffsetY = scrollFraction * (size.height - scrollbarHeight)
                                                drawRect(
                                                    color = Color.Gray.copy(alpha = 0.5f),
                                                    topLeft = Offset(size.width - 8.dp.toPx(), scrollbarOffsetY),
                                                    size = Size(8.dp.toPx(), scrollbarHeight.coerceAtLeast(8.dp.toPx()))
                                                )
                                            }
                                        }
                                ) {
                                    items(airspaceClassItems, key = { it.className }) { airspaceClass ->
                                        TaskAirspaceClassCard(
                                            airspaceClass = airspaceClass,
                                            onToggle = { className ->
                                                selectedClasses.value = selectedClasses.value.toMutableMap().apply {
                                                    put(className, !(get(className) ?: false))
                                                }
                                                saveSelectedClasses(context, selectedClasses.value)
                                                loadAndApplyAirspace(context, mapLibreMap)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = selectedItem ?: "No Item Selected",
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                color = Color.Black
                            )
                        }
                    }
                }
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
                        Log.d(TAG, "🏗️ Creating MapView for Task screen")
                        getMapAsync { map: MapLibreMap ->
                            mapLibreMap = map
                            map.setStyle("https://api.maptiler.com/maps/streets-v2/style.json?key=nYDScLfnBm52GAc3jXEZ") {
                                // Configure UI settings AFTER style is loaded
                                map.uiSettings.isZoomGesturesEnabled = true   // ✅ ENABLE zoom gestures (pinch to zoom)
                                map.uiSettings.isRotateGesturesEnabled = false
                                map.uiSettings.isTiltGesturesEnabled = false
                                map.uiSettings.isScrollGesturesEnabled = true  // ✅ ENABLE pan gestures (drag to pan)
                                map.uiSettings.isQuickZoomGesturesEnabled = false

                                // Add gesture listeners to debug
                                map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                                    override fun onMoveBegin(detector: MoveGestureDetector) {
                                        Log.d(TAG, "🖐️ TASK MAP MOVE DETECTED - Task screen gesture working!")
                                    }
                                    override fun onMove(detector: MoveGestureDetector) {}
                                    override fun onMoveEnd(detector: MoveGestureDetector) {
                                        Log.d(TAG, "🖐️ TASK MAP MOVE ENDED")
                                    }
                                })

                                map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
                                    override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                                        Log.d(TAG, "🔍 TASK MAP ZOOM DETECTED - Task screen zoom working!")
                                    }
                                    override fun onScale(detector: StandardScaleGestureDetector) {}
                                    override fun onScaleEnd(detector: StandardScaleGestureDetector) {
                                        Log.d(TAG, "🔍 TASK MAP ZOOM ENDED")
                                    }
                                })

                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(-30.8700, 150.5200))
                                    .zoom(8.0)
                                    .build()
                                loadAndApplyAirspace(ctx, map)
                                loadAndApplyWaypoints(ctx, map, selectedWaypointFiles, waypointCheckedStates.value)

                                Log.d(TAG, "✅ MapLibre gestures configured: scroll=${map.uiSettings.isScrollGesturesEnabled}, zoom=${map.uiSettings.isZoomGesturesEnabled}")
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
                                Log.d(TAG, "👆 Parent received touch event: ${event.type}, pointers: ${event.changes.size}")
                            }
                        }
                    }
            )
            if (selectedNavItem == "Files") {
                BottomAppBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(54.dp),
                    elevation = 8.dp, // Add elevation to show it's above the sheet
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        listOf("Airspace", "Waypoints", "Classes").forEach { itemName ->
                            Column(
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .clickable {
                                        selectedItem = itemName
                                        scope.launch {
                                            scaffoldState.bottomSheetState.expand()
                                            onShowBottomSheet()
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Map,
                                    contentDescription = itemName,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                                Text(
                                    text = itemName,
                                    style = MaterialTheme.typography.caption,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskAirspaceClassCard(
    airspaceClass: AirspaceClassItem,
    onToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE5E5E5) // Light grey instead of dark grey
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = Color(android.graphics.Color.parseColor(airspaceClass.color))
                            .copy(alpha = 0.7f), // Slightly more opaque for better visibility on light background
                        shape = RoundedCornerShape(4.dp)
                    )
                    .border(
                        2.dp,
                        Color.Black.copy(alpha = 0.2f), // Dark border for light background
                        RoundedCornerShape(4.dp)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Class info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Class ${airspaceClass.className}",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black, // Black text for light background
                    fontSize = 16.sp
                )
                Text(
                    text = airspaceClass.description,
                    color = Color(0xFF666666), // Dark grey text for light background
                    fontSize = 12.sp
                )
            }

            // Toggle button
            IconButton(
                onClick = { onToggle(airspaceClass.className) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (airspaceClass.enabled) Color(0xFF059669) else Color(0xFF9CA3AF),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (airspaceClass.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (airspaceClass.enabled) "Hide" else "Show",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

