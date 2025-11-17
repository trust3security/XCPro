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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.example.xcpro.map.BuildConfig
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
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.LocationManager
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapCameraManager
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle
import com.example.dfcards.dfcards.CardVisualStyles

@Composable
fun MapMainLayers(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
    locationManager: LocationManager,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    taskManager: TaskManagerCoordinator,
    orientationManager: MapOrientationManager,
    orientationData: OrientationData,
    cameraManager: MapCameraManager,
    currentLocation: GPSData?,
    showReturnButton: Boolean,
    isAATEditMode: Boolean,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onSetAATEditMode: (Boolean) -> Unit,
    onContainerSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit,
    cardSafeTopOffsetPx: Float = 0f,
    modifier: Modifier = Modifier,
    onCardLayerPositioned: (Rect) -> Unit = {},
    cardStyle: CardStyle
) {
    Box(modifier = modifier.fillMaxSize()) {
        MapViewHost(
            mapState = mapState,
            mapInitializer = mapInitializer
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

        val showCompass = orientationData.mode != MapOrientationMode.NORTH_UP
        val toggleOrientation = {
            val nextMode = when (orientationManager.getCurrentMode()) {
                MapOrientationMode.NORTH_UP -> MapOrientationMode.TRACK_UP
                MapOrientationMode.TRACK_UP -> MapOrientationMode.HEADING_UP
                MapOrientationMode.HEADING_UP -> MapOrientationMode.NORTH_UP
                else -> MapOrientationMode.NORTH_UP
            }
            orientationManager.setOrientationMode(nextMode)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
                .zIndex(5f)
        ) {
            AnimatedVisibility(
                visible = showCompass,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300))
            ) {
                CompassWidget(
                    orientation = orientationData,
                    onModeToggle = toggleOrientation
                )
            }

            AnimatedVisibility(
                visible = !showCompass,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                AssistChip(
                    onClick = toggleOrientation,
                    label = { Text("Change orientation") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = "Change orientation"
                        )
                    }
                )
            }
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



@Composable
private fun MapViewHost(
    mapState: MapScreenState,
    mapInitializer: MapInitializer,
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
