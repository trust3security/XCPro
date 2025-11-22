package com.example.xcpro.tasks


import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.TaskCategory
import com.example.xcpro.tasks.RulesBTTab
import com.example.xcpro.tasks.ManageBTTabRouter
import com.example.xcpro.tasks.core.TaskType
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.MultiFormatReader
import com.google.zxing.BinaryBitmap
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.RGBLuminanceSource
import android.Manifest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// Calculate waypoint item height (approximate)
private val WAYPOINT_ITEM_HEIGHT = 72.dp // Estimated height of ReorderableWaypointItem
private val CLEARANCE_MULTIPLIER = 1.5f

// Function to calculate optimal bottom sheet height
private fun calculateOptimalHeight(
    density: Density,
    waypointCount: Int,
    screenHeight: Dp
): Float {
    return with(density) {
        val clearance = WAYPOINT_ITEM_HEIGHT * CLEARANCE_MULTIPLIER
        val maxAllowedHeight = screenHeight - clearance
        maxAllowedHeight.toPx()
    }
}

// ---------- MAIN BOTTOM SHEET ----------
@Composable
fun SwipeableTaskBottomSheet(
    taskManager: TaskManagerCoordinator,
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    mapLibreMap: MapLibreMap?,
    allWaypoints: List<WaypointData> = emptyList(),
    isSearchActive: Boolean = false,
    currentQNH: String? = null,
    initialHeight: BottomSheetState = BottomSheetState.HALF_EXPANDED,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val focusManager = LocalFocusManager.current

    val minimizedHeight = 120.dp
    val halfExpandedHeight = 400.dp
    val fullyExpandedHeight = screenHeight * 0.95f

    val minimizedPx = with(density) { minimizedHeight.toPx() }
    val halfExpandedPx = with(density) { halfExpandedHeight.toPx() }
    val fullyExpandedPx = with(density) { fullyExpandedHeight.toPx() }

    // Search offset to shift sheet down when search is active
    val searchOffset = if (isSearchActive) WAYPOINT_ITEM_HEIGHT else 0.dp

    // ViewModel wiring (placed near data consumers to avoid scope confusion)
    val taskViewModel: TaskSheetViewModel = viewModel(factory = TaskSheetViewModel.factory(taskManager))
    LaunchedEffect(mapLibreMap) { taskViewModel.setMap(mapLibreMap) }
    val uiState by taskViewModel.uiState.collectAsStateWithLifecycle()

    // ✅ SSOT FIX: Make task reactive so distance/UI updates when radius changes
    // CRITICAL: Without derivedStateOf, task captures once and never updates!
    val task by remember { derivedStateOf { uiState.task } }
    var taskType by remember { mutableStateOf(uiState.taskType) }

    var currentHeightPx by remember(initialHeight) {
        val height = when (initialHeight) {
            BottomSheetState.MINIMIZED -> minimizedPx
            BottomSheetState.HALF_EXPANDED -> halfExpandedPx
            BottomSheetState.FULLY_EXPANDED -> fullyExpandedPx
        }
        mutableStateOf(height)
    }
    var isDragging by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(TaskCategory.MANAGE) }

    var swipeDownDistance by remember { mutableStateOf(0f) }
    var swipeUpDistance by remember { mutableStateOf(0f) }
    var initialHeightPx by remember { mutableStateOf(0f) }

    val draggableState = rememberDraggableState { delta ->
        isDragging = true
        val newHeight = currentHeightPx - delta

        // Track swipe direction and distance
        if (delta > 0) { // Swiping down
            swipeDownDistance += delta
            swipeUpDistance = 0f
        } else { // Swiping up
            swipeUpDistance += kotlin.math.abs(delta)
            swipeDownDistance = 0f
        }

        currentHeightPx = newHeight.coerceIn(minimizedPx - 200f, fullyExpandedPx)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = searchOffset)
            .height(with(density) { currentHeightPx.toDp() })
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { focusManager.clearFocus() }
                )
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    isDragging = true
                    initialHeightPx = currentHeightPx
                },
                onDragStopped = {
                    isDragging = false

                    val swipeThreshold = 50f

                    val newHeight = when {
                        swipeDownDistance > 150f || currentHeightPx < minimizedPx -> {
                            if (isSearchActive) {
                                minimizedPx
                            } else {
                                onDismiss()
                                currentHeightPx
                            }
                        }
                        swipeUpDistance > swipeThreshold && initialHeightPx < halfExpandedPx -> {
                            halfExpandedPx
                        }
                        swipeUpDistance > swipeThreshold && initialHeightPx >= halfExpandedPx -> {
                            fullyExpandedPx
                        }
                        swipeDownDistance > swipeThreshold && initialHeightPx > halfExpandedPx -> {
                            halfExpandedPx
                        }
                        swipeDownDistance > swipeThreshold && initialHeightPx <= halfExpandedPx -> {
                            minimizedPx
                        }
                        else -> {
                            // Snap to nearest state based on current position
                            when {
                                currentHeightPx < (minimizedPx + halfExpandedPx) / 2 -> minimizedPx
                                currentHeightPx < (halfExpandedPx + fullyExpandedPx) / 2 -> halfExpandedPx
                                else -> fullyExpandedPx
                            }
                        }
                    }

                    currentHeightPx = newHeight
                    swipeDownDistance = 0f
                    swipeUpDistance = 0f
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            when {
                currentHeightPx <= minimizedPx + 50f -> {
                    // Minimized content
                    MinimizedContent(task = task, taskManager = taskManager)
                }
                currentHeightPx >= fullyExpandedPx - 50f -> {
                    // Fully expanded content with categories
                    ExpandedContent(
                        uiState = uiState,
                        task = task,
                        taskManager = taskManager,
                        taskViewModel = taskViewModel,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it },
                        currentQNH = currentQNH,
                        allWaypoints = allWaypoints,
                        onClearTask = onClearTask,
                        onSaveTask = onSaveTask,
                        onDismiss = onDismiss,
                        mapLibreMap = mapLibreMap
                    )
                }
                else -> {
                    // Half expanded - show same content as fully expanded
                    ExpandedContent(
                        uiState = uiState,
                        task = task,
                        taskManager = taskManager,
                        taskViewModel = taskViewModel,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it },
                        currentQNH = currentQNH,
                        allWaypoints = allWaypoints,
                        onClearTask = onClearTask,
                        onSaveTask = onSaveTask,
                        onDismiss = onDismiss,
                        mapLibreMap = mapLibreMap
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedContent(
    uiState: TaskUiState,
    task: Task,
    taskManager: TaskManagerCoordinator,
    taskViewModel: TaskSheetViewModel,
    selectedCategory: TaskCategory,
    onCategorySelect: (TaskCategory) -> Unit,
    currentQNH: String?,
    allWaypoints: List<WaypointData>,
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    mapLibreMap: MapLibreMap?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Category selector
        ScrollableTabRow(
            selectedTabIndex = TaskCategory.values().indexOf(selectedCategory),
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 0.dp
        ) {
            TaskCategory.values().forEach { category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelect(category) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (category) {
                                    TaskCategory.MANAGE -> Icons.Default.Settings
                                    TaskCategory.RULES -> Icons.Default.Policy
                                    TaskCategory.FILES -> Icons.Default.Folder
                                    TaskCategory.FOUR -> Icons.Default.Star
                                    TaskCategory.FIVE -> Icons.Default.Favorite
                                },
                                contentDescription = null
                            )
                            Text(category.label)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category content
        when (selectedCategory) {
            TaskCategory.MANAGE -> {
                ManageBTTabRouter(
                    uiState = uiState,
                    task = task,
                    taskManager = taskManager,
                    taskViewModel = taskViewModel,
                    mapLibreMap = mapLibreMap,
                    allWaypoints = allWaypoints,
                    onClearTask = onClearTask,
                    onSaveTask = onSaveTask,
                    onDismiss = onDismiss,
                    currentQNH = currentQNH,
                    taskType = taskManager.taskType
                )
            }
            TaskCategory.RULES -> {
                RulesBTTab(
                    selected = taskManager.taskType,
                    onSelect = { taskType ->
                        taskManager.setTaskType(taskType)
                    },
                    taskManager = taskManager,
                    taskViewModel = taskViewModel
                )
            }
            TaskCategory.FILES -> {
                FilesBTTab(
                    taskManager = taskManager,
                    taskViewModel = taskViewModel,
                    currentQNH = currentQNH
                )
            }
            else -> {
                // Placeholder for other categories
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${selectedCategory.label} - Coming Soon",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// QR Code Dialog Component
@Composable
fun QRCodeDialog(
    taskManager: TaskManagerCoordinator,
    uiState: TaskUiState? = null,
    onDismiss: () -> Unit,
    onImportJson: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) {
            importError = "No image captured"
            return@rememberLauncherForActivityResult
        }
        val decoded = runCatching { decodeQRCode(bitmap) }.getOrNull()
        if (decoded != null) {
            importError = null
            onImportJson(decoded)
            onDismiss()
        } else {
            importError = "Could not read QR code from camera preview"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanLauncher.launch(null)
        } else {
            importError = "Camera permission denied"
        }
    }

    // Generate QR code when dialog opens
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val taskData = TaskPersistSerializer.serialize(
                    task = taskManager.currentTask,
                    taskType = taskManager.taskType,
                    targets = uiState?.targets.orEmpty()
                )
                qrBitmap = generateQRCode(taskData)
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Failed to generate QR code: ${e.message}"
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Share Task via QR Code",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Generating QR Code...")
                    }
                    errorMessage != null -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    qrBitmap != null -> {
                        Card(
                            modifier = Modifier.size(200.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "Task QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scan this QR code with another pilot's app to share the task",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Task: ${taskManager.currentTask.waypoints.size} waypoints",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Import from JSON text (fallback to QR scan)
                OutlinedTextField(
                    value = importText,
                    onValueChange = {
                        importText = it
                        importError = null
                    },
                    label = { Text("Paste task JSON to import") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (importError != null) {
                    Text(
                        text = importError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            importError = null
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                scanLauncher.launch(null)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan QR")
                    }
                    Button(
                        onClick = {
                            try {
                                onImportJson(importText)
                                importText = ""
                                importError = null
                                onDismiss()
                            } catch (e: Exception) {
                                importError = e.message ?: "Import failed"
                            }
                        },
                        enabled = importText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// Generate QR code bitmap
private suspend fun generateQRCode(data: String): Bitmap = withContext(Dispatchers.Default) {
    val writer = QRCodeWriter()
    val bitMatrix: BitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 512, 512)

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }

    return@withContext bitmap
}

private fun decodeQRCode(bitmap: Bitmap): String? {
    val intArray = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    return runCatching { MultiFormatReader().decode(binaryBitmap).text }.getOrNull()
}

