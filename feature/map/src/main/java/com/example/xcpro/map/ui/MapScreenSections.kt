package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import android.util.Log
import android.view.MotionEvent
import com.example.xcpro.map.BuildConfig
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.CardContainer
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.tasks.TaskMapOverlay
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.FlightDataManager
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.dfcards.dfcards.CardVisualStyles
import com.example.xcpro.qnh.QnhCalibrationState

@Composable
fun MapMainLayers(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (org.maplibre.android.maps.MapLibreMap) -> Unit,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskManager: TaskManagerCoordinator,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onContainerSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit,
    cardSafeTopOffsetPx: Float = 0f,
    modifier: Modifier = Modifier,
    onCardLayerPositioned: (Rect) -> Unit = {},
    cardStyle: CardStyle
) {
    Box(modifier = modifier.fillMaxSize()) {
        MapViewHost(
            mapState = mapState,
            mapInitializer = mapInitializer,
            onMapReady = onMapReady
        )

        CardGridLayer(
            mapState = mapState,
            flightViewModel = flightViewModel,
            flightDataManager = flightDataManager,
            isUiEditMode = isUiEditMode,
            onEditModeChange = onEditModeChange,
            onContainerSizeChanged = onContainerSizeChanged,
            cardSafeTopOffsetPx = cardSafeTopOffsetPx,
            onCardLayerPositioned = onCardLayerPositioned,
            cardStyle = cardStyle
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
        ) {
            TaskMapOverlay(
                taskManager = taskManager,
                mapLibreMap = mapState.mapLibreMap,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QnhDialog(
    visible: Boolean,
    qnhInput: String,
    qnhError: String?,
    unitsPreferences: com.example.xcpro.common.units.UnitsPreferences,
    liveData: RealTimeFlightData?,
    calibrationState: QnhCalibrationState,
    onQnhInputChange: (String) -> Unit,
    onConfirm: (Double) -> Unit,
    onInvalidInput: (String) -> Unit,
    onAutoCalibrate: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val pressureLabel = unitsPreferences.pressure.abbreviation
    val pressureDecimals = com.example.xcpro.pressurePrecision(unitsPreferences)
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Manual QNH") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the local QNH in $pressureLabel.")
                OutlinedTextField(
                    value = qnhInput,
                    onValueChange = { onQnhInputChange(it) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    label = { Text("QNH ($pressureLabel)") },
                    isError = qnhError != null
                )
                qnhError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                when (calibrationState) {
                    is QnhCalibrationState.Collecting -> {
                        Text(
                            text = "Auto Cal: ${calibrationState.samplesCollected}/${calibrationState.samplesRequired}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    is QnhCalibrationState.TimedOut -> {
                        Text(
                            text = "Auto Cal timed out",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is QnhCalibrationState.Failed -> {
                        Text(
                            text = "Auto Cal failed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Unit
                }
                liveData?.let { data ->
                    val status = if (data.isQNHCalibrated) "Calibrated" else "Standard"
                    Text("Current: ${com.example.xcpro.formatQnhDisplay(data.qnh, unitsPreferences, pressureDecimals)} ($status)")
                    data.baroGpsDelta?.let { delta ->
                        Text("Baro vs GPS: ${com.example.xcpro.formatBaroGpsDelta(delta, unitsPreferences)}")
                    }
                    val ageSeconds = data.qnhCalibrationAgeSeconds
                    if (ageSeconds >= 0) {
                        val ageLabel = when {
                            ageSeconds >= 3600 -> "${ageSeconds / 3600}h"
                            ageSeconds >= 60 -> "${ageSeconds / 60}m"
                            else -> "${ageSeconds}s"
                        }
                        Text("Last calibration: $ageLabel ago")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = qnhInput.trim().toDoubleOrNull()
                    if (parsed != null) {
                        onConfirm(parsed)
                    } else {
                        onInvalidInput("Enter a numeric value")
                    }
                }
            ) {
                Text("Set QNH")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAutoCalibrate) {
                    Text("Auto Cal")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}



@Composable
private fun MapViewHost(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    onMapReady: (org.maplibre.android.maps.MapLibreMap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                mapState.mapView = this
                getMapAsync { map: MapLibreMap ->
                    scope.launch {
                        try {
                            mapInitializer.initializeMap(map)
                            onMapReady(map)
                        } catch (_: Exception) {}
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun CardGridLayer(
    mapState: MapScreenState,
    flightViewModel: FlightDataViewModel,
    flightDataManager: FlightDataManager,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onContainerSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit,
    cardSafeTopOffsetPx: Float,
    onCardLayerPositioned: (Rect) -> Unit,
    cardStyle: CardStyle,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isUiEditMode) 11f else 2f)
            .pointerInteropFilter { motionEvent ->
                val action = when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> "DOWN"
                    MotionEvent.ACTION_POINTER_DOWN -> "POINTER_DOWN"
                    MotionEvent.ACTION_MOVE -> "MOVE"
                    MotionEvent.ACTION_POINTER_UP -> "POINTER_UP"
                    MotionEvent.ACTION_UP -> "UP"
                    MotionEvent.ACTION_CANCEL -> "CANCEL"
                    else -> motionEvent.actionMasked.toString()
                }
                if (BuildConfig.DEBUG) {
                    val pointerSummary = buildString {
                        for (i in 0 until motionEvent.pointerCount) {
                            if (i > 0) append("; ")
                            append("#")
                            append(motionEvent.getPointerId(i))
                            append("@")
                            append(String.format("%.1f,%.1f", motionEvent.getX(i), motionEvent.getY(i)))
                        }
                    }
                    Log.d(
                        "GESTURE_CARD_BOX",
                        "action= edit= pointers=[] consumed="
                    )
                }
                false
            }
    ) {
        DisposableEffect(Unit) {
            onDispose { onCardLayerPositioned(Rect.Zero) }
        }

        val cardVisualStyle = when (cardStyle) {
            CardStyle.TRANSPARENT -> CardVisualStyles.transparent()
            CardStyle.STANDARD,
            CardStyle.COMPACT,
            CardStyle.LARGE -> CardVisualStyles.standard()
        }

        CardContainer(
            onContainerSizeChanged = onContainerSizeChanged,
            onCardBoundsChanged = onCardLayerPositioned,
            statusBarOffset = cardSafeTopOffsetPx,
            onFlightTemplateClick = { flightDataManager.showCardLibrary() },
            isEditMode = isUiEditMode,
            onEditModeChanged = onEditModeChange,
            viewModel = flightViewModel,
            modifier = Modifier.fillMaxSize(),
            cardVisualStyle = cardVisualStyle
        )
    }
}
