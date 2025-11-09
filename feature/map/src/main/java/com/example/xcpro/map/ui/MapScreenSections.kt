package com.example.xcpro.map.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import android.util.Log
import android.view.MotionEvent
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.CardContainer
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.CompassWidget
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.screens.overlays.getMapStyleUrl
import com.example.xcpro.tasks.TaskMapOverlay
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.xcprov1.ui.HawkGauge
import com.example.xcpro.xcprov1.ui.WindRibbon
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapCameraManager
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
fun MapMainLayers(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightDataRepository: FlightDataRepository,
    flightViewModel: FlightDataViewModel,
    taskManager: TaskManagerCoordinator,
    orientationManager: MapOrientationManager,
    orientationData: OrientationData,
    cameraManager: MapCameraManager,
    currentFlightModeSelection: FlightModeSelection,
    currentLocation: GPSData?,
    showReturnButton: Boolean,
    isAATEditMode: Boolean,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onSetAATEditMode: (Boolean) -> Unit,
    onContainerSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit,
    cardSafeTopOffsetPx: Float = 0f,
    modifier: Modifier = Modifier,
    convertToRealTime: (CompleteFlightData) -> RealTimeFlightData,
    onCardLayerPositioned: (Rect) -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    val hawkSnapshot = locationManager.xcproV1Controller.snapshotFlow.collectAsState(null).value

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    mapState.mapView = this
                    getMapAsync { map: MapLibreMap ->
                        scope.launch {
                            try {
                                mapInitializer.initializeMap(map)
                            } catch (_: Exception) {}
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(Unit) {
            flightDataRepository.flightData.collect { completeData ->
                if (completeData != null) {
                    val realTimeData = convertToRealTime(completeData)
                    Log.d("MapMainLayers", "Sample received: lat=${realTimeData.latitude}, lon=${realTimeData.longitude}, vs=${realTimeData.verticalSpeed}, agl=${realTimeData.agl}")
                    flightDataManager.updateLiveFlightData(realTimeData)
                }
            }
        }

        if (currentFlightModeSelection == FlightModeSelection.HAWK) {
            hawkSnapshot?.let { data ->
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 110.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .zIndex(4f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HawkGauge(
                        actualClimb = data.actualClimb,
                        potentialClimb = data.potentialClimb,
                        confidence = data.confidence,
                        gaugeSize = 220.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    WindRibbon(
                        windX = data.windX,
                        windY = data.windY,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                }
            }
        }

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
                        "action=$action edit=$isUiEditMode pointers=[$pointerSummary] consumed=${motionEvent.actionMasked == MotionEvent.ACTION_CANCEL}"
                    )
                    false
                }
        ) {
            DisposableEffect(Unit) {
                onDispose { onCardLayerPositioned(Rect.Zero) }
            }

            CardContainer(
                onContainerSizeChanged = onContainerSizeChanged,
                onCardBoundsChanged = onCardLayerPositioned,
                statusBarOffset = cardSafeTopOffsetPx,
                onFlightTemplateClick = { flightDataManager.showCardLibrary() },
                isEditMode = isUiEditMode,
                onEditModeChanged = onEditModeChange,
                viewModel = flightViewModel,
                modifier = Modifier.fillMaxSize()
            )
        }

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

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
                .zIndex(5f)
        ) {
            CompassWidget(
                orientation = orientationData,
                onModeToggle = {
                    val nextMode = when (orientationManager.getCurrentMode()) {
                        MapOrientationMode.NORTH_UP -> MapOrientationMode.TRACK_UP
                        MapOrientationMode.TRACK_UP -> MapOrientationMode.HEADING_UP
                        MapOrientationMode.HEADING_UP -> MapOrientationMode.NORTH_UP
                        else -> MapOrientationMode.NORTH_UP
                    }
                    orientationManager.setOrientationMode(nextMode)
                }
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
    onQnhInputChange: (String) -> Unit,
    onConfirm: (Double) -> Unit,
    onInvalidInput: (String) -> Unit,
    onResetToStandard: () -> Unit,
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
                TextButton(onClick = onResetToStandard) {
                    Text("Auto Cal")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}


