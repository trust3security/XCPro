package com.example.xcpro.map.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.dfcards.dfcards.CardContainer
import com.example.dfcards.dfcards.FlightDataViewModel
import com.example.xcpro.tasks.TaskMapOverlay
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.map.MapScreenState
import com.example.xcpro.map.MapInitializer
import com.example.xcpro.map.FlightDataManager
import com.example.xcpro.map.MapLocationRenderFrameBinder
import com.example.xcpro.map.MapOverlayManager
import com.example.xcpro.map.MapRenderSurfaceDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
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
    onMapViewBound: () -> Unit,
    locationRenderFrameBinder: MapLocationRenderFrameBinder,
    renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics,
    flightDataManager: FlightDataManager,
    flightViewModel: FlightDataViewModel,
    overlayManager: MapOverlayManager,
    isUiEditMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onContainerSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit,
    cardSafeTopOffsetPx: Float = 0f,
    modifier: Modifier = Modifier,
    onCardLayerPositioned: (Rect) -> Unit = {},
    cardStyle: CardStyle,
    hiddenCardIds: Set<String> = emptySet()
) {
    Box(modifier = modifier.fillMaxSize()) {
        MapViewHost(
            mapState = mapState,
            mapInitializer = mapInitializer,
            onMapReady = onMapReady,
            onMapViewBound = onMapViewBound,
            locationRenderFrameBinder = locationRenderFrameBinder,
            renderSurfaceDiagnostics = renderSurfaceDiagnostics
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
            cardStyle = cardStyle,
            hiddenCardIds = hiddenCardIds
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
        ) {
            TaskMapOverlay(
                onTaskStateChanged = overlayManager::onTaskStateChanged,
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
    onMapViewBound: () -> Unit,
    locationRenderFrameBinder: MapLocationRenderFrameBinder,
    renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val latestOnMapReady = rememberUpdatedState(onMapReady)
    val latestMapInitializer = rememberUpdatedState(mapInitializer)
    val latestOnMapViewBound = rememberUpdatedState(onMapViewBound)
    val bindingController = remember(mapState, locationRenderFrameBinder, renderSurfaceDiagnostics) {
        MapViewHostBindingController(
            mapState = mapState,
            locationRenderFrameBinder = locationRenderFrameBinder,
            renderSurfaceDiagnostics = renderSurfaceDiagnostics
        )
    }
    val hostedMapView = remember(mapState, context) {
        mapState.mapView ?: MapView(context).apply {
            // Do not publish this MapView into mapState until the host binding
            // controller actually attaches it. Lifecycle sync can run before the
            // AndroidView host is attached, and the runtime lifecycle owner must
            // not see a non-attached MapView as ready.
            getMapAsync { map: MapLibreMap ->
                if (!scope.isActive) {
                    return@getMapAsync
                }
                scope.launch {
                    runCatching { latestMapInitializer.value.initializeMap(map) }
                        .onFailure { error ->
                            if (error is CancellationException) {
                                throw error
                            }
                            AppLogger.e("MapViewHost", "Map initialization failed: ${error.message}", error)
                        }
                    if (!isActive) {
                        return@launch
                    }
                    runCatching { latestOnMapReady.value(map) }
                        .onFailure { callbackError ->
                            AppLogger.e(
                                "MapViewHost",
                                "onMapReady callback failed after initialization: ${callbackError.message}",
                                callbackError
                            )
                        }
                }
            }
        }
    }

    DisposableEffect(locationRenderFrameBinder) {
        onDispose {
            locationRenderFrameBinder.unbindRenderFrameListener()
        }
    }

    DisposableEffect(hostedMapView, bindingController) {
        bindingController.attach(hostedMapView, latestOnMapViewBound.value)
        onDispose {
            bindingController.clear(hostedMapView)
        }
    }

    AndroidView(
        factory = { hostedMapView },
        update = { view ->
            bindingController.updateMapViewReference(view)
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
    hiddenCardIds: Set<String>,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isUiEditMode) 11f else 2f)
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
            hiddenCardIds = hiddenCardIds,
            viewModel = flightViewModel,
            modifier = Modifier.fillMaxSize(),
            cardVisualStyle = cardVisualStyle
        )
    }
}
