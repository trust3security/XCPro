warning: in the working copy of 'CODING_POLICY.md', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'dfcards-library/src/main/java/com/example/dfcards/dfcards/EnhancedFlightDataCard.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'feature/map/src/main/java/com/example/xcpro/map/ui/widgets/MapUIWidgets.kt', LF will be replaced by CRLF the next time Git touches it
warning: in the working copy of 'feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt', LF will be replaced by CRLF the next time Git touches it
[1mdiff --git a/CODING_POLICY.md b/CODING_POLICY.md[m
[1mindex 3725423..0e94db9 100644[m
[1m--- a/CODING_POLICY.md[m
[1m+++ b/CODING_POLICY.md[m
[36m@@ -190,6 +190,7 @@[m [mExample:[m
 - Provide `@Preview` for each screen and complex component.[m
 - Hoist state; pass events as lambdas.[m
 - Keep files under **500 lines** (prefer <= 350). Split when larger.[m
[32m+[m[32m- Pointer input consumption: `consumePositionChange()` is deprecated. When touching pointer handlers, switch to `change.consume()` (or the newer helpers) and remove the warnings as part of the refactor.[m
 [m
 ### Live Telemetry & Recomposition Discipline[m
 - **Collect lifecycle-aware**: UI layers must use `collectAsStateWithLifecycle()` (or `stateIn(viewModelScope, SharingStarted.WhileSubscribed)` inside the ViewModel) for every sensor/telemetry flow. No raw `collectAsState()` on `gpsFlow`, `orientationFlow`, etc.it keeps emitting while backgrounded, wastes battery, and re-triggers full-screen recompositions.[m
[1mdiff --git a/dfcards-library/src/main/java/com/example/dfcards/dfcards/EnhancedFlightDataCard.kt b/dfcards-library/src/main/java/com/example/dfcards/dfcards/EnhancedFlightDataCard.kt[m
[1mindex d783f90..e0b38a7 100644[m
[1m--- a/dfcards-library/src/main/java/com/example/dfcards/dfcards/EnhancedFlightDataCard.kt[m
[1m+++ b/dfcards-library/src/main/java/com/example/dfcards/dfcards/EnhancedFlightDataCard.kt[m
[36m@@ -7,6 +7,7 @@[m [mimport androidx.compose.animation.core.tween[m
 import androidx.compose.foundation.background[m
 import androidx.compose.foundation.border[m
 import androidx.compose.foundation.layout.Box[m
[32m+[m[32mimport androidx.compose.foundation.layout.BoxWithConstraints[m
 import androidx.compose.foundation.layout.fillMaxSize[m
 import androidx.compose.foundation.layout.fillMaxWidth[m
 import androidx.compose.foundation.layout.padding[m
[36m@@ -22,6 +23,7 @@[m [mimport androidx.compose.ui.graphics.Brush[m
 import androidx.compose.ui.graphics.Color[m
 import androidx.compose.ui.graphics.RectangleShape[m
 import androidx.compose.ui.graphics.graphicsLayer[m
[32m+[m[32mimport androidx.compose.ui.platform.LocalDensity[m
 import androidx.compose.ui.text.SpanStyle[m
 import androidx.compose.ui.text.buildAnnotatedString[m
 import androidx.compose.ui.text.font.FontWeight[m
[36m@@ -90,94 +92,113 @@[m [mfun EnhancedFlightDataCard([m
         baseModifier[m
     }[m
 [m
[31m-    Box([m
[31m-        modifier = borderedModifier[m
[31m-    ) {[m
[31m-        Text([m
[31m-            text = flightData.label,[m
[31m-            fontSize = stableFontSizes.labelSize.sp,[m
[31m-            fontWeight = FontWeight.Bold,[m
[31m-            color = visualStyle.labelColor.copy(alpha = 0.7f * editModeAlpha),[m
[31m-            textAlign = TextAlign.Center,[m
[31m-            maxLines = 1,[m
[31m-            overflow = TextOverflow.Ellipsis,[m
[31m-            modifier = Modifier[m
[31m-                .fillMaxWidth()[m
[31m-                .padding(horizontal = 2.dp, vertical = 2.dp)[m
[31m-                .align(Alignment.TopCenter)[m
[31m-        )[m
[32m+[m[32m    BoxWithConstraints(modifier = borderedModifier) {[m
[32m+[m[32m        val maxH = maxHeight[m
[32m+[m[32m        val minEdge = 0.dp[m
[32m+[m
[32m+[m[32m        // Hug the edges: zero padding, rely on tiny nudges to visually touch edges.[m
[32m+[m[32m        val desiredTop = 0.dp[m
[32m+[m[32m        val desiredBottom = 0.dp[m
[32m+[m
[32m+[m[32m        val topPad = desiredTop.coerceIn(minEdge, maxH / 2)[m
[32m+[m[32m        val bottomPad = desiredBottom.coerceIn(minEdge, maxH / 2)[m
[32m+[m
[32m+[m[32m        val labelNudge = with(LocalDensity.current) { (-3).dp.toPx() }[m
[32m+[m[32m        val footerNudge = with(LocalDensity.current) { 3.dp.toPx() }[m
 [m
         val primaryColor = flightData.primaryColorOverride ?: visualStyle.primaryColor[m
[32m+[m[32m        val primarySize = stableFontSizes.primarySize * 0.8f[m
[32m+[m
[32m+[m[32m        Box([m
[32m+[m[32m            modifier = Modifier[m
[32m+[m[32m                .fillMaxSize()[m
[32m+[m[32m                .padding(start = 2.dp, end = 2.dp, top = topPad, bottom = bottomPad)[m
[32m+[m[32m        ) {[m
[32m+[m[32m            Text([m
[32m+[m[32m                text = flightData.label,[m
[32m+[m[32m                fontSize = stableFontSizes.labelSize.sp,[m
[32m+[m[32m                fontWeight = FontWeight.Bold,[m
[32m+[m[32m                color = Color.Black.copy(alpha = 0.9f * editModeAlpha),[m
[32m+[m[32m                textAlign = TextAlign.Center,[m
[32m+[m[32m                maxLines = 1,[m
[32m+[m[32m                overflow = TextOverflow.Ellipsis,[m
[32m+[m[32m                modifier = Modifier[m
[32m+[m[32m                    .fillMaxWidth()[m
[32m+[m[32m                    .align(Alignment.TopCenter)[m
[32m+[m[32m                    .graphicsLayer { translationY = labelNudge }[m
[32m+[m[32m            )[m
 [m
[31m-        Text([m
[31m-            text = buildAnnotatedString {[m
[31m-                val primaryNumber = flightData.primaryValueNumber[m
[31m-                val primaryUnit = flightData.primaryValueUnit[m
[31m-                if (primaryNumber != null) {[m
[31m-                    withStyle([m
[32m+[m[32m            Text([m
[32m+[m[32m                text = buildAnnotatedString {[m
[32m+[m[32m                    val primaryNumber = flightData.primaryValueNumber[m
[32m+[m[32m                    val primaryUnit = flightData.primaryValueUnit[m
[32m+[m[32m                    if (primaryNumber != null) {[m
[32m+[m[32m                        withStyle([m
                             style = SpanStyle([m
[31m-                                fontSize = stableFontSizes.primarySize.sp,[m
[32m+[m[32m                                fontSize = primarySize.sp,[m
                                 fontWeight = FontWeight.Bold,[m
                                 color = primaryColor[m
                             )[m
                         ) {[m
                             append(primaryNumber)[m
[31m-                    }[m
[31m-                    primaryUnit?.let { unit ->[m
[31m-                        append(" ")[m
[32m+[m[32m                        }[m
[32m+[m[32m                        primaryUnit?.let { unit ->[m
[32m+[m[32m                            append(" ")[m
[32m+[m[32m                            withStyle([m
[32m+[m[32m                                style = SpanStyle([m
[32m+[m[32m                                    fontSize = (primarySize * 0.55f).sp,[m
[32m+[m[32m                                    fontWeight = FontWeight.Medium,[m
[32m+[m[32m                                    color = visualStyle.unitColor[m
[32m+[m[32m                                )[m
[32m+[m[32m                            ) {[m
[32m+[m[32m                                append(unit)[m
[32m+[m[32m                            }[m
[32m+[m[32m                        }[m
[32m+[m[32m                    } else {[m
                         withStyle([m
                             style = SpanStyle([m
[31m-                                fontSize = (stableFontSizes.primarySize * 0.55f).sp,[m
[31m-                                fontWeight = FontWeight.Medium,[m
[31m-                                color = visualStyle.unitColor[m
[32m+[m[32m                                fontSize = primarySize.sp,[m
[32m+[m[32m                                fontWeight = FontWeight.Bold,[m
[32m+[m[32m                                color = primaryColor[m
                             )[m
                         ) {[m
[31m-                            append(unit)[m
[32m+[m[32m                            append(flightData.primaryValue)[m
                         }[m
                     }[m
[31m-                } else {[m
[31m-                    withStyle([m
[31m-                        style = SpanStyle([m
[31m-                            fontSize = stableFontSizes.primarySize.sp,[m
[31m-                            fontWeight = FontWeight.Bold,[m
[31m-                            color = primaryColor[m
[31m-                        )[m
[31m-                    ) {[m
[31m-                        append(flightData.primaryValue)[m
[31m-                    }[m
[31m-                }[m
[31m-            },[m
[31m-            textAlign = TextAlign.Center,[m
[31m-            maxLines = 1,[m
[31m-            overflow = TextOverflow.Ellipsis,[m
[31m-            modifier = Modifier[m
[31m-                .fillMaxWidth()[m
[31m-                .align(Alignment.Center)[m
[31m-                .graphicsLayer {[m
[31m-                    scaleX = primaryScale[m
[31m-                    scaleY = primaryScale[m
[31m-                }[m
[31m-        )[m
[31m-[m
[31m-        flightData.secondaryValue?.let {[m
[31m-            val secondarySize = if (flightData.id == "wind_spd") {[m
[31m-                (stableFontSizes.secondarySize * 2f).sp[m
[31m-            } else {[m
[31m-                stableFontSizes.secondarySize.sp[m
[31m-            }[m
[31m-            Text([m
[31m-                text = it,[m
[31m-                fontSize = secondarySize,[m
[31m-                fontWeight = FontWeight.Bold,[m
[31m-                color = Color.Black,[m
[32m+[m[32m                },[m
                 textAlign = TextAlign.Center,[m
                 maxLines = 1,[m
                 overflow = TextOverflow.Ellipsis,[m
                 modifier = Modifier[m
                     .fillMaxWidth()[m
[31m-                    .padding(horizontal = 2.dp, vertical = 2.dp)[m
[31m-                    .align(Alignment.BottomCenter)[m
[32m+[m[32m                    .align(Alignment.Center)[m
[32m+[m[32m                    .graphicsLayer {[m
[32m+[m[32m                        scaleX = primaryScale[m
[32m+[m[32m                        scaleY = primaryScale[m
[32m+[m[32m                    }[m
             )[m
[32m+[m
[32m+[m[32m            flightData.secondaryValue?.let { secondaryText ->[m
[32m+[m[32m                val secondaryMultiplier = when {[m
[32m+[m[32m                    flightData.id == "wind_spd" && secondaryText.equals("NO WIND", ignoreCase = true) -> 1f[m
[32m+[m[32m                    flightData.id == "wind_spd" -> 2f[m
[32m+[m[32m                    else -> 1f[m
[32m+[m[32m                }[m
[32m+[m[32m                val secondarySize = (stableFontSizes.secondarySize * secondaryMultiplier).sp[m
[32m+[m[32m                Text([m
[32m+[m[32m                    text = secondaryText,[m
[32m+[m[32m                    fontSize = secondarySize,[m
[32m+[m[32m                    fontWeight = FontWeight.Bold,[m
[32m+[m[32m                    color = Color.Black,[m
[32m+[m[32m                    textAlign = TextAlign.Center,[m
[32m+[m[32m                    maxLines = 1,[m
[32m+[m[32m                    overflow = TextOverflow.Ellipsis,[m
[32m+[m[32m                    modifier = Modifier[m
[32m+[m[32m                        .fillMaxWidth()[m
[32m+[m[32m                        .align(Alignment.BottomCenter)[m
[32m+[m[32m                        .graphicsLayer { translationY = footerNudge }[m
[32m+[m[32m                )[m
[32m+[m[32m            }[m
         }[m
     }[m
 }[m
[1mdiff --git a/feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayWidgets.kt b/feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayWidgets.kt[m
[1mdeleted file mode 100644[m
[1mindex a17f3d4..0000000[m
[1m--- a/feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayWidgets.kt[m
[1m+++ /dev/null[m
[36m@@ -1,679 +0,0 @@[m
[31m-package com.example.xcpro.map.ui[m
[31m-[m
[31m-import androidx.compose.animation.AnimatedVisibility[m
[31m-import androidx.compose.animation.fadeIn[m
[31m-import androidx.compose.animation.fadeOut[m
[31m-import androidx.compose.animation.scaleIn[m
[31m-import androidx.compose.animation.scaleOut[m
[31m-import androidx.compose.animation.core.Spring[m
[31m-import androidx.compose.animation.core.animateFloatAsState[m
[31m-import androidx.compose.animation.core.spring[m
[31m-import androidx.compose.foundation.layout.Arrangement[m
[31m-import androidx.compose.foundation.layout.Box[m
[31m-import androidx.compose.foundation.layout.BoxScope[m
[31m-import androidx.compose.foundation.layout.Column[m
[31m-import androidx.compose.foundation.layout.Row[m
[31m-import androidx.compose.foundation.layout.fillMaxSize[m
[31m-import androidx.compose.foundation.layout.fillMaxWidth[m
[31m-import androidx.compose.foundation.layout.offset[m
[31m-import androidx.compose.foundation.layout.padding[m
[31m-import androidx.compose.foundation.layout.size[m
[31m-import androidx.compose.foundation.gestures.Orientation[m
[31m-import androidx.compose.runtime.Composable[m
[31m-import androidx.compose.runtime.LaunchedEffect[m
[31m-import androidx.compose.runtime.DisposableEffect[m
[31m-import androidx.compose.runtime.MutableState[m
[31m-import androidx.compose.runtime.derivedStateOf[m
[31m-import androidx.lifecycle.compose.collectAsStateWithLifecycle[m
[31m-import androidx.compose.runtime.getValue[m
[31m-import androidx.compose.runtime.remember[m
[31m-import androidx.compose.runtime.mutableStateOf[m
[31m-import androidx.compose.runtime.setValue[m
[31m-import androidx.compose.runtime.rememberCoroutineScope[m
[31m-import androidx.compose.ui.layout.onGloballyPositioned[m
[31m-import androidx.compose.ui.Alignment[m
[31m-import androidx.compose.ui.Modifier[m
[31m-import androidx.compose.ui.geometry.Offset[m
[31m-import androidx.compose.ui.geometry.Rect[m
[31m-import androidx.compose.ui.unit.Density[m
[31m-import androidx.compose.ui.unit.IntSize[m
[31m-import androidx.compose.ui.unit.IntOffset[m
[31m-import androidx.compose.ui.unit.dp[m
[31m-import androidx.compose.ui.zIndex[m
[31m-import android.util.Log[m
[31m-import com.example.dfcards.dfcards.FlightDataViewModel[m
[31m-import com.example.xcpro.CompassWidget[m
[31m-import com.example.xcpro.MapOrientationManager[m
[31m-import com.example.xcpro.common.orientation.MapOrientationMode[m
[31m-import com.example.xcpro.common.orientation.OrientationData[m
[31m-import com.example.xcpro.map.DistanceCirclesCanvas[m
[31m-import com.example.xcpro.map.FlightDataManager[m
[31m-import com.example.xcpro.map.LocationManager[m
[31m-import com.example.xcpro.map.MapCameraManager[m
[31m-import com.example.xcpro.map.MapGestureSetup[m
[31m-import com.example.xcpro.map.MapInitializer[m
[31m-import com.example.xcpro.map.MapModalManager[m
[31m-import com.example.xcpro.map.MapModalUI[m
[31m-import com.example.xcpro.map.MapOverlayGestureTarget[m
[31m-import com.example.xcpro.map.MapScreenState[m
[31m-import com.example.xcpro.map.MapTaskIntegration[m
[31m-import com.example.xcpro.map.ui.widgets.MapUIWidgetManager[m
[31m-import com.example.xcpro.map.ui.widgets.MapUIWidgets[m
[31m-import com.example.xcpro.map.ballast.BallastCommand[m
[31m-import com.example.xcpro.map.ballast.BallastUiState[m
[31m-import com.example.xcpro.common.units.UnitsFormatter[m
[31m-import com.example.xcpro.common.units.VerticalSpeedMs[m
[31m-import com.example.xcpro.sensors.GPSData[m
[31m-import com.example.xcpro.tasks.TaskManagerCoordinator[m
[31m-import kotlinx.coroutines.flow.StateFlow[m
[31m-import com.example.xcpro.variometer.layout.VariometerUiState[m
[31m-import com.example.xcpro.screens.navdrawer.lookandfeel.CardStyle[m
[31m-import com.example.xcpro.replay.IgcReplayController[m
[31m-import androidx.compose.material.icons.Icons[m
[31m-import androidx.compose.material.icons.filled.Pause[m
[31m-import androidx.compose.material.icons.filled.PlayArrow[m
[31m-import androidx.compose.material.icons.filled.Stop[m
[31m-import androidx.compose.material3.AssistChip[m
[31m-import androidx.compose.material3.Card[m
[31m-import androidx.compose.material3.CardDefaults[m
[31m-import androidx.compose.material3.FloatingActionButton[m
[31m-import androidx.compose.material3.Icon[m
[31m-import androidx.compose.material3.IconButton[m
[31m-import androidx.compose.material3.MaterialTheme[m
[31m-import androidx.compose.material3.Slider[m
[31m-import androidx.compose.material3.Text[m
[31m-import kotlinx.coroutines.launch[m
[31m-import com.example.xcpro.map.BuildConfig[m
[31m-import androidx.compose.runtime.rememberCoroutineScope[m
[31m-import androidx.compose.material.ExperimentalMaterialApi[m
[31m-import androidx.compose.material.rememberSwipeableState[m
[31m-import androidx.compose.material.swipeable[m
[31m-import androidx.compose.material.FractionalThreshold[m
[31m-[m
[31m-@Composable[m
[31m-@Suppress("LongParameterList")[m
[31m-internal fun MapOverlayStack([m
[31m-    mapState: MapScreenState,[m
[31m-    mapInitializer: MapInitializer,[m
[31m-    locationManager: LocationManager,[m
[31m-    flightDataManager: FlightDataManager,[m
[31m-    flightViewModel: FlightDataViewModel,[m
[31m-    currentFlightModeSelection: com.example.dfcards.FlightModeSelection,[m
[31m-    taskManager: TaskManagerCoordinator,[m
[31m-    orientationManager: MapOrientationManager,[m
[31m-    orientationData: OrientationData,[m
[31m-    cameraManager: MapCameraManager,[m
[31m-    currentLocation: GPSData?,[m
[31m-    showReturnButton: Boolean,[m
[31m-    isAATEditMode: Boolean,[m
[31m-    isUiEditMode: Boolean,[m
[31m-    onEditModeChange: (Boolean) -> Unit,[m
[31m-    onSetAATEditMode: (Boolean) -> Unit,[m
[31m-    onExitAATEditMode: () -> Unit,[m
[31m-    safeContainerSize: MutableState<IntSize>,[m
[31m-    variometerUiState: VariometerUiState,[m
[31m-    minVariometerSizePx: Float,[m
[31m-    maxVariometerSizePx: Float,[m
[31m-    onVariometerOffsetChange: (Offset) -> Unit,[m
[31m-    onVariometerSizeChange: (Float) -> Unit,[m
[31m-    onVariometerLongPress: () -> Unit,[m
[31m-    onVariometerEditFinished: () -> Unit,[m
[31m-    hamburgerOffset: MutableState<Offset>,[m
[31m-    flightModeOffset: MutableState<Offset>,[m
[31m-    ballastOffset: MutableState<Offset>,[m
[31m-    widgetManager: MapUIWidgetManager,[m
[31m-    screenWidthPx: Float,[m
[31m-    screenHeightPx: Float,[m
[31m-    density: Density,[m
[31m-    modalManager: MapModalManager,[m
[31m-    ballastUiState: StateFlow<BallastUiState>,[m
[31m-    hideBallastPill: Boolean,[m
[31m-    onBallastCommand: (BallastCommand) -> Unit,[m
[31m-    onHamburgerTap: () -> Unit,[m
[31m-    onHamburgerLongPress: () -> Unit,[m
[31m-    cardStyle: CardStyle,[m
[31m-    replayState: StateFlow<IgcReplayController.SessionState>,[m
[31m-    onReplayPlayPause: () -> Unit,[m
[31m-    onReplayStop: () -> Unit,[m
[31m-    onReplaySpeedChange: (Double) -> Unit,[m
[31m-    onReplaySeek: (Float) -> Unit,[m
[31m-    showReplayDevFab: Boolean,[m
[31m-    onReplayDevFabClick: () -> Unit[m
[31m-) {[m
[31m-    val currentMode by mapState.currentModeFlow.collectAsStateWithLifecycle()[m
[31m-    val showDistanceCircles by mapState.showDistanceCirclesFlow.collectAsStateWithLifecycle()[m
[31m-    val gestureRegions by widgetManager.gestureRegions.collectAsStateWithLifecycle()[m
[31m-[m
[31m-    LaunchedEffect(gestureRegions) {[m
[31m-        if (BuildConfig.DEBUG) Log.d("GESTURE_REGIONS", gestureRegions.joinToString(prefix = "[", postfix = "]") { region ->[m
[31m-            "${region.target}:${region.bounds}"[m
[31m-        })[m
[31m-    }[m
[31m-[m
[31m-    DisposableEffect(Unit) {[m
[31m-        onDispose {[m
[31m-            widgetManager.clearGestureRegion(MapOverlayGestureTarget.CARD_GRID)[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    Box([m
[31m-        modifier = Modifier[m
[31m-            .fillMaxSize()[m
[31m-            .zIndex(3f)[m
[31m-    ) {[m
[31m-        MapMainLayers([m
[31m-            mapState = mapState,[m
[31m-            mapInitializer = mapInitializer,[m
[31m-            locationManager = locationManager,[m
[31m-            flightDataManager = flightDataManager,[m
[31m-            flightViewModel = flightViewModel,[m
[31m-[m
[31m-            taskManager = taskManager,[m
[31m-            orientationManager = orientationManager,[m
[31m-            orientationData = orientationData,[m
[31m-            cameraManager = cameraManager,[m
[31m-            currentLocation = currentLocation,[m
[31m-            showReturnButton = showReturnButton,[m
[31m-            isAATEditMode = isAATEditMode,[m
[31m-            isUiEditMode = isUiEditMode,[m
[31m-            onEditModeChange = onEditModeChange,[m
[31m-            onSetAATEditMode = onSetAATEditMode,[m
[31m-            onContainerSizeChanged = { size ->[m
[31m-                if (size.width > 0 && size.height > 0) {[m
[31m-                    safeContainerSize.value = size[m
[31m-                }[m
[31m-            },[m
[31m-            modifier = Modifier.fillMaxSize(),[m
[31m-            onCardLayerPositioned = { bounds ->[m
[31m-                if (bounds == Rect.Zero) {[m
[31m-                    widgetManager.clearGestureRegion(MapOverlayGestureTarget.CARD_GRID)[m
[31m-                } else {[m
[31m-                    widgetManager.updateGestureRegion([m
[31m-                        target = MapOverlayGestureTarget.CARD_GRID,[m
[31m-                        bounds = bounds,[m
[31m-                        consumeGestures = isUiEditMode[m
[31m-                    )[m
[31m-                }[m
[31m-            },[m
[31m-            cardStyle = cardStyle[m
[31m-        )[m
[31m-[m
[31m-        if (!isUiEditMode) {[m
[31m-            MapGestureSetup.GestureHandlerOverlay([m
[31m-                mapState = mapState,[m
[31m-                taskManager = taskManager,[m
[31m-                flightDataManager = flightDataManager,[m
[31m-                locationManager = locationManager,[m
[31m-                cameraManager = cameraManager,[m
[31m-                currentLocation = currentLocation,[m
[31m-                showReturnButton = showReturnButton,[m
[31m-                isAATEditMode = isAATEditMode,[m
[31m-                onAATEditModeChange = onSetAATEditMode,[m
[31m-                gestureRegions = gestureRegions,[m
[31m-                modifier = Modifier.zIndex(3.6f)[m
[31m-            )[m
[31m-        }[m
[31m-[m
[31m-        val replaySession by replayState.collectAsStateWithLifecycle()[m
[31m-        ReplayControlsSheet([m
[31m-            session = replaySession,[m
[31m-            modifier = Modifier[m
[31m-                .align(Alignment.BottomCenter)[m
[31m-                .zIndex(20f),[m
[31m-            onPlayPause = onReplayPlayPause,[m
[31m-            onStop = onReplayStop,[m
[31m-            onSpeedChanged = onReplaySpeedChange,[m
[31m-            onSeek = onReplaySeek[m
[31m-        )[m
[31m-[m
[31m-        MapUIWidgets.FlightModeMenu([m
[31m-            widgetManager = widgetManager,[m
[31m-            currentMode = currentMode,[m
[31m-            visibleModes = flightDataManager.visibleModes,[m
[31m-            onModeChange = { newMode -> mapState.updateFlightMode(newMode) },[m
[31m-            flightModeOffset = flightModeOffset.value,[m
[31m-            screenWidthPx = screenWidthPx,[m
[31m-            screenHeightPx = screenHeightPx,[m
[31m-            onOffsetChange = { offset -> flightModeOffset.value = offset },[m
[31m-            isEditMode = isUiEditMode,[m
[31m-            modifier = Modifier[m
[31m-                .align(Alignment.TopStart)[m
[31m-                .zIndex(12f)[m
[31m-        )[m
[31m-[m
[31m-        CompassPanel([m
[31m-            orientationData = orientationData,[m
[31m-            orientationManager = orientationManager,[m
[31m-            modifier = Modifier[m
[31m-                .align(Alignment.TopEnd)[m
[31m-                .padding(top = 80.dp, end = 16.dp)[m
[31m-                .zIndex(5f)[m
[31m-        )[m
[31m-[m
[31m-        BallastPanel([m
[31m-            ballastUiState = ballastUiState,[m
[31m-            hideBallastPill = hideBallastPill,[m
[31m-            widgetManager = widgetManager,[m
[31m-            ballastOffset = ballastOffset,[m
[31m-            screenWidthPx = screenWidthPx,[m
[31m-            screenHeightPx = screenHeightPx,[m
[31m-            onBallastCommand = onBallastCommand,[m
[31m-            isUiEditMode = isUiEditMode,[m
[31m-            modifier = Modifier[m
[31m-                .align(Alignment.TopStart)[m
[31m-                .zIndex(12f)[m
[31m-        )[m
[31m-[m
[31m-        VariometerPanel([m
[31m-            flightDataManager = flightDataManager,[m
[31m-            widgetManager = widgetManager,[m
[31m-            variometerUiState = variometerUiState,[m
[31m-            minVariometerSizePx = minVariometerSizePx,[m
[31m-            maxVariometerSizePx = maxVariometerSizePx,[m
[31m-            onVariometerOffsetChange = onVariometerOffsetChange,[m
[31m-            onVariometerSizeChange = onVariometerSizeChange,[m
[31m-            onVariometerLongPress = onVariometerLongPress,[m
[31m-            onVariometerEditFinished = onVariometerEditFinished,[m
[31m-            screenWidthPx = screenWidthPx,[m
[31m-            screenHeightPx = screenHeightPx,[m
[31m-            isUiEditMode = isUiEditMode[m
[31m-        )[m
[31m-[m
[31m-        DistanceCirclesLayer([m
[31m-            mapState = mapState,[m
[31m-            flightDataManager = flightDataManager,[m
[31m-            showDistanceCircles = showDistanceCircles[m
[31m-        )[m
[31m-[m
[31m-        AatEditFab([m
[31m-            isAATEditMode = isAATEditMode,[m
[31m-            taskManager = taskManager,[m
[31m-            cameraManager = cameraManager,[m
[31m-            onExitAATEditMode = onExitAATEditMode[m
[31m-        )[m
[31m-[m
[31m-        if (showReplayDevFab) {[m
[31m-            ReplayDevFab(onReplayDevFabClick = onReplayDevFabClick)[m
[31m-        }[m
[31m-[m
[31m-        HamburgerMenu([m
[31m-            widgetManager = widgetManager,[m
[31m-            hamburgerOffset = hamburgerOffset,[m
[31m-            screenWidthPx = screenWidthPx,[m
[31m-            screenHeightPx = screenHeightPx,[m
[31m-            onHamburgerTap = onHamburgerTap,[m
[31m-            onHamburgerLongPress = onHamburgerLongPress,[m
[31m-            isUiEditMode = isUiEditMode[m
[31m-        )[m
[31m-[m
[31m-        MapModalUI.AirspaceSettingsModalOverlay(modalManager = modalManager)[m
[31m-    }[m
[31m-}[m
[31m-[m
[31m-@Composable[m
[31m-private fun CompassPanel([m
[31m-    orientationData: OrientationData,[m
[31m-    orientationManager: MapOrientationManager,[m
[31m-    modifier: Modifier = Modifier[m
[31m-) {[m
[31m-    AnimatedVisibility([m
[31m-        visible = true,[m
[31m-        enter = fadeIn() + scaleIn(),[m
[31m-        exit = fadeOut() + scaleOut(),[m
[31m-        modifier = modifier[m
[31m-    ) {[m
[31m-        CompassWidget([m
[31m-            orientation = orientationData,[m
[31m-            onModeToggle = {[m
[31m-                val nextMode = when (orientationManager.getCurrentMode()) {[m
[31m-                    MapOrientationMode.NORTH_UP -> MapOrientationMode.TRACK_UP[m
[31m-                    MapOrientationMode.TRACK_UP -> MapOrientationMode.HEADING_UP[m
[31m-                    MapOrientationMode.HEADING_UP -> MapOrientationMode.WIND_UP[m
[31m-                    MapOrientationMode.WIND_UP -> MapOrientationMode.NORTH_UP[m
[31m-                }[m
[31m-                orientationManager.setOrientationMode(nextMode)[m
[31m-            }[m
[31m-        )[m
[31m-    }[m
[31m-}[m
[31m-[m
[31m-@Composable[m
[31m-private fun BallastPanel([m
[31m-    ballastUiState: StateFlow<BallastUiState>,[m
[31m-    hideBallastPill: Boolean,[m
[31m-    widgetManager: MapUIWidgetManager,[m
[31m-    ballastOffset: MutableState<Offset>,[m
[31m-    screenWidthPx: Float,[m
[31m-    screenHeightPx: Float,[m
[31m-    onBallastCommand: (BallastCommand) -> Unit,[m
[31m-    isUiEditMode: Boolean,[m
[31m-    modifier: Modifier = Modifier[m
[31m-) {[m
[31m-    val ballastState by ballastUiState.collectAsStateWithLifecycle()[m
[31m-    val showBallastPill =[m
[31m-        !hideBallastPill && ([m
[31m-            ballastState.isAnimating ||[m
[31m-                ballastState.snapshot.hasBallast ||[m
[31m-                ballastState.snapshot.currentKg > 0.0[m
[31m-            )[m
[31m-[m
[31m-    AnimatedVisibility([m
[31m-        visible = showBallastPill,[m
[31m-        enter = fadeIn() + scaleIn(),[m
[31m-        exit = fadeOut() + scaleOut(),[m
[31m-        modifier = modifier[m
[31m-    ) {[m
[31m-        MapUIWidgets.BallastWidget([m
[31m-            widgetManager = widgetManager,[m
[31m-            ballastState = ballastState,[m
[31m-            onCommand = onBallastCommand,[m
[31m-            ballastOffset = ballastOffset.value,[m
[31m-            screenWidthPx = screenWidthPx,[m
[31m-            screenHeightPx = screenHeightPx,[m
[31m-            onOffsetChange = { offset -> ballastOffset.value = offset },[m
[31m-            isEditMode = isUiEditMode[m
[31m-        )[m
[31m-    }[m
[31m-}[m
[31m-[m
[31m-@Composable[m
[31m-private fun VariometerPanel([m
[31m-    flightDataManager: FlightDataManager,[m
[31m-    widgetManager: MapUIWidgetManager,[m
[31m-    variometerUiState: VariometerUiState,[m
[31m-    minVariometerSizePx: Float,[m
[31m-    maxVariometerSizePx: Float,[m
[31m-    onVariometerOffsetChange: (Offset) -> Unit,[m
[31m-    onVariometerSizeChange: (Float) -> Unit,[m
[31m-    onVariometerLongPress: () -> Unit,[m
[31m-    onVariometerEditFinished: () -> Unit,[m
[31m-    screenWidthPx: Float,[m
[31m-    screenHeightPx: Float,[m
[31m-    isUiEditMode: Boolean[m
[31m-) {[m
[31m-    val displayNumericVario by flightDataManager.displayVarioFlow.collectAsStateWithLifecycle()[m
[31m-    val animatedVario by animateFloatAsState([m
[31m-        targetValue = displayNumericVario,[m
[31m-        animationSpec = spring([m
[31m-            dampingRatio = Spring.DampingRatioMediumBouncy,[m
[31m-            stiffness = Spring.StiffnessMedium[m
[31m-        ),[m
[31m-        label = "vario"[m
[31m-    )[m
[31m-    val unitsPreferences = flightDataManager.unitsPreferences[m
[31m-    val varioFormatted by remember(displayNumericVario, unitsPreferences) {[m
[31m-        derivedStateOf {[m
[31m-            UnitsFormatter.verticalSpeed([m
[31m-                VerticalSpeedMs(displayNumericVario.toDouble()),[m
[31m-                unitsPreferences[m
[31m-            )[m
[31m-        }[m
[31m-    }[m
[31m-    MapUIWidgets.VariometerWidget([m
[31m-        widgetManager = widgetManager,[m
[31m-        variometerState = variometerUiState,[m
[31m-        needleValue = animatedVario,[m
[31m-        displayValue = displayNumericVario,[m
[31m-        displayLabel = varioFormatted.text,[m
[31m-        screenWidthPx = screenWidthPx,[m
[31m-        screenHeightPx = screenHeightPx,[m
[31m-        minSizePx = minVariometerSizePx,[m
[31m-        maxSizePx = maxVariometerSizePx,[m
[31m-        isEditMode = isUiEditMode,[m
[31m-        onOffsetChange = onVariometerOffsetChange,[m
[31m-        onSizeChange = onVariometerSizeChange,[m
[31m-        onLongPress = onVariometerLongPress,[m
[31m-        onEditFinished = onVariometerEditFinished,[m
[31m-        modifier = Modifier.zIndex(if (isUiEditMode) 12f else 3f)[m
[31m-    )[m
[31m-}[m
[31m-[m
[31m-@Composable[m
[31m-private fun DistanceCirclesLayer([m
[31m-    mapState: MapScreenState,[m
[31m-    flightDataManager: FlightDataManager,[m
[31m-    showDistanceCircles: Boolean[m
[31m-) {[m
[31m-    val mapLatitude by flightDataManager.latitudeFlow.collectAsStateWithLifecycle()[m
[31m-    val mapZoom by mapState.currentZoomFlow.collectAsStateWithLifecycle()[m
[31m-    DistanceCirclesCanvas([m
[31m-        mapZoom = mapZoom,[m
[31m-        mapLatitude = mapLatitude,[m
[31m-        isVisible = showDistanceCircles,[m
[31m-        modifier = Modifier.zIndex(3.7f)[m
[31m-    )[m
[31m-}[m
[31m-[m
[31m-@Composable[m
[31m-private fun BoxScope.AatEditFab([m
[31m-    isAATEditMode: Boolean,[m
[31m-    taskManager: TaskManagerCoordinator,[m
[31m-    cameraManager: MapCameraManager,[m
[31m-    onExitAATEditMode: () -> Unit[m
[31m-) {[m
[31m-    MapTaskIntegration.AATEditModeFAB([m
[31m-        isAATEditMode = isAATEditMode,[m
[31m-        taskManager = taskManager,[m
[31m-        cameraManager = cameraManager,[m
[31m-        onExitEditMode = onExitAATEditMode,[m
[31m-        modifier = Modifier[m
[31m-            .align(Alignment.BottomEnd)[m
[31m-            .zIndex(11f)[m
[31m-    )[m
[31m-}[m
[31m-[m
[31m-@Composable[m
[31m-private fun BoxScope.ReplayDevFab([m
[31m-    onReplayDevFabClick: () -> Unit[m
[31m-) {[m
[31m-    FloatingActionButton([m
[31m-        onClick = onReplayDevFabClick,[m
[31m-        modifier = Modifier[m
[31m-            .align(Alignment.BottomEnd)[m
[31m-            .padding(bottom = 96.dp, end = 16.dp)[m
[31m-            .zIndex(15f)[m
[31m-    ) {[m
[31m-        Icon([m
[31m-            imageVector = Icons.Default.PlayArrow,[m
[31m-            contentDescription = "Start sample replay"[m
[31m-        )[m
[31m-    }[m
[31m-}[m
[31m-[m
[31m-@Composable[m
[31m-private fun BoxScope.HamburgerMenu([m
[31m-    widgetManager: MapUIWidgetManager,[m
[31m-    hamburgerOffset: MutableState<Offset>,[m
[31m-    screenWidthPx: Float,[m
[31m-    screenHeightPx: Float,[m
[31m-    onHamburgerTap: () -> Unit,[m
[31m-    onHamburgerLongPress: () -> Unit,[m
[31m-    isUiEditMode: Boolean[m
[31m-) {[m
[31m-    MapUIWidgets.SideHamburgerMenu([m
[31m-        widgetManager = widgetManager,[m
[31m-        hamburgerOffset = hamburgerOffset.value,[m
[31m-        screenWidthPx = screenWidthPx,[m
[31m-        screenHeightPx = screenHeightPx,[m
[31m-        onHamburgerTap = onHamburgerTap,[m
[31m-        onHamburgerLongPress = onHamburgerLongPress,[m
[31m-        onOffsetChange = { offset -> hamburgerOffset.value = offset },[m
[31m-        isEditMode = isUiEditMode,[m
[31m-        modifier = Modifier[m
[31m-            .align(Alignment.TopStart)[m
[31m-            .zIndex(12f)[m
[31m-    )[m
[31m-}[m
[31m-[m
[31m-@OptIn(ExperimentalMaterialApi::class)[m
[31m-@Composable[m
[31m-private fun BoxScope.ReplayControlsSheet([m
[31m-    session: IgcReplayController.SessionState,[m
[31m-    modifier: Modifier = Modifier,[m
[31m-    onPlayPause: () -> Unit,[m
[31m-    onStop: () -> Unit,[m
[31m-    onSpeedChanged: (Double) -> Unit,[m
[31m-    onSeek: (Float) -> Unit[m
[31m-) {[m
[31m-    val scope = rememberCoroutineScope()[m
[31m-    var sheetHeightPx by remember { mutableStateOf(1f) }[m
[31m-    val swipeableState = rememberSwipeableState(initialValue = ReplaySheetValue.Hidden)[m
[31m-    val anchors = remember(sheetHeightPx) {[m
[31m-        if (sheetHeightPx <= 0f) emptyMap()[m
[31m-        else mapOf([m
[31m-            0f to ReplaySheetValue.Visible,[m
[31m-            sheetHeightPx to ReplaySheetValue.Hidden[m
[31m-        )[m
[31m-    }[m
[31m-[m
[31m-    LaunchedEffect(session.selection) {[m
[31m-        if (session.hasSelection && anchors.isNotEmpty()) {[m
[31m-            swipeableState.animateTo(ReplaySheetValue.Visible)[m
[31m-        } else {[m
[31m-            swipeableState.snapTo(ReplaySheetValue.Hidden)[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    val rawOffset = swipeableState.offset.value[m
[31m-    val offsetPx = if (rawOffset.isNaN()) {[m
[31m-        if (session.hasSelection) 0f else sheetHeightPx[m
[31m-    } else {[m
[31m-        rawOffset[m
[31m-    }[m
[31m-[m
[31m-    val cardModifier = modifier[m
[31m-        .fillMaxWidth()[m
[31m-        .padding(horizontal = 16.dp, vertical = 24.dp)[m
[31m-        .offset {[m
[31m-            IntOffset(0, offsetPx.coerceIn(0f, sheetHeightPx).toInt())[m
[31m-        }[m
[31m-        .let { base ->[m
[31m-            if (anchors.isEmpty()) {[m
[31m-                base[m
[31m-            } else {[m
[31m-                base[m
[31m-                    .swipeable([m
[31m-                        state = swipeableState,[m
[31m-                        anchors = anchors,[m
[31m-                        thresholds = { _, _ -> FractionalThreshold(0.3f) },[m
[31m-                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical[m
[31m-                    )[m
[31m-                    .onGloballyPositioned { coords ->[m
[31m-                        sheetHeightPx = coords.size.height.toFloat()[m
[31m-                    }[m
[31m-            }[m
[31m-        }[m
[31m-[m
[31m-    if (session.hasSelection || swipeableState.currentValue == ReplaySheetValue.Visible) {[m
[31m-        Card([m
[31m-            modifier = cardModifier,[m
[31m-            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),[m
[31m-            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)[m
[31m-        ) {[m
[31m-            ReplayControlsContent([m
[31m-                state = session,[m
[31m-                onPlayPause = onPlayPause,[m
[31m-                onStop = onStop,[m
[31m-                onSpeedChanged = onSpeedChanged,[m
[31m-                onSeek = onSeek[m
[31m-            )[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    if (session.hasSelection && swipeableState.currentValue == ReplaySheetValue.Hidden) {[m
[31m-        AssistChip([m
[31m-            onClick = { scope.launch { swipeableState.animateTo(ReplaySheetValue.Visible) } },[m
[31m-            label = { Text("Replay controls") },[m
[31m-            modifier = Modifier[m
[31m-                .align(Alignment.BottomCenter)[m
[31m-                .padding(bottom = 24.dp)[m
[31m-        )[m
[31m-    }[m
[31m-[m
[31m-}[m
[31m-[m
[31m-@Composable[m
[31m-private fun ReplayControlsContent([m
[31m-    state: IgcReplayController.SessionState,[m
[31m-    onPlayPause: () -> Unit,[m
[31m-    onStop: () -> Unit,[m
[31m-    onSpeedChanged: (Double) -> Unit,[m
[31m-    onSeek: (Float) -> Unit[m
[31m-) {[m
[31m-    if (!state.hasSelection) return[m
[31m-    val isPlaying = state.status == IgcReplayController.SessionStatus.PLAYING[m
[31m-    val title = state.selection?.displayName ?: "IGC Replay"[m
[31m-    val elapsed = state.elapsedMillis[m
[31m-    val duration = state.durationMillis[m
[31m-    val progress = state.progressFraction[m
[31m-    val speed = state.speedMultiplier[m
[31m-[m
[31m-    Column([m
[31m-        modifier = Modifier.padding(16.dp),[m
[31m-        verticalArrangement = Arrangement.spacedBy(12.dp)[m
[31m-    ) {[m
[31m-        Text(text = title, style = MaterialTheme.typography.titleMedium)[m
[31m-        Text([m
[31m-            text = "${formatDuration(elapsed)} / ${formatDuration(duration)} • ${"%.1f".format(speed)}x",[m
[31m-            style = MaterialTheme.typography.bodyMedium[m
[31m-        )[m
[31m-        Text(text = "Timeline", style = MaterialTheme.typography.bodySmall)[m
[31m-        Slider([m
[31m-            value = progress,[m
[31m-            onValueChange = onSeek,[m
[31m-            modifier = Modifier.fillMaxWidth()[m
[31m-        )[m
[31m-        Text(text = "Speed", style = MaterialTheme.typography.bodySmall)[m
[31m-            Slider([m
[31m-                value = speed.toFloat(),[m
[31m-                onValueChange = { onSpeedChanged(it.toDouble()) },[m
[31m-                valueRange = 1f..10f,[m
[31m-                modifier = Modifier.fillMaxWidth()[m
[31m-            )[m
[31m-        Row([m
[31m-            modifier = Modifier.fillMaxWidth(),[m
[31m-            horizontalArrangement = Arrangement.spacedBy(12.dp)[m
[31m-        ) {[m
[31m-            IconButton(onClick = onPlayPause) {[m
[31m-                if (isPlaying) {[m
[31m-                    Icon([m
[31m-                        imageVector = Icons.Default.Pause,[m
[31m-                        contentDescription = "Pause replay"[m
[31m-                    )[m
[31m-                } else {[m
[31m-                    Icon([m
[31m-                        imageVector = Icons.Default.PlayArrow,[m
[31m-                        contentDescription = "Play replay"[m
[31m-                    )[m
[31m-                }[m
[31m-            }[m
[31m-            IconButton(onClick = onStop) {[m
[31m-                Icon([m
[31m-                    imageVector = Icons.Default.Stop,[m
[31m-                    contentDescription = "Stop replay"[m
[31m-                )[m
[31m-            }[m
[31m-        }[m
[31m-    }[m
[31m-}[m
[31m-[m
[31m-private enum class ReplaySheetValue {[m
[31m-    Hidden,[m
[31m-    Visible[m
[31m-}[m
[31m-[m
[31m-private fun formatDuration(millis: Long): String {[m
[31m-    if (millis <= 0L) return "00:00"[m
[31m-    val totalSeconds = millis / 1000[m
[31m-    val seconds = (totalSeconds % 60).toInt()[m
[31m-    val minutes = ((totalSeconds / 60) % 60).toInt()[m
[31m-    val hours = (totalSeconds / 3600).toInt()[m
[31m-    return if (hours > 0) {[m
[31m-        "%d:%02d:%02d".format(hours, minutes, seconds)[m
[31m-    } else {[m
[31m-        "%02d:%02d".format(minutes, seconds)[m
[31m-    }[m
[31m-}[m
[31m-[m
[31m-[m
[31m-[m
[1mdiff --git a/feature/map/src/main/java/com/example/xcpro/map/ui/widgets/MapUIWidgets.kt b/feature/map/src/main/java/com/example/xcpro/map/ui/widgets/MapUIWidgets.kt[m
[1mindex bc5b7a1..008eedc 100644[m
[1m--- a/feature/map/src/main/java/com/example/xcpro/map/ui/widgets/MapUIWidgets.kt[m
[1m+++ b/feature/map/src/main/java/com/example/xcpro/map/ui/widgets/MapUIWidgets.kt[m
[36m@@ -1,83 +1,19 @@[m
 package com.example.xcpro.map.ui.widgets[m
 [m
[31m-import android.util.Log[m
[31m-import androidx.compose.animation.AnimatedVisibility[m
[31m-import androidx.compose.animation.fadeIn[m
[31m-import androidx.compose.animation.fadeOut[m
[31m-import androidx.compose.foundation.ExperimentalFoundationApi[m
[31m-import androidx.compose.foundation.background[m
[31m-import androidx.compose.foundation.border[m
[31m-import androidx.compose.foundation.combinedClickable[m
[31m-import androidx.compose.foundation.gestures.awaitEachGesture[m
[31m-import androidx.compose.foundation.gestures.awaitFirstDown[m
[31m-import androidx.compose.foundation.gestures.awaitLongPressOrCancellation[m
[31m-import androidx.compose.foundation.gestures.detectDragGestures[m
[31m-import androidx.compose.foundation.gestures.detectTapGestures[m
[31m-import androidx.compose.foundation.gestures.detectVerticalDragGestures[m
[31m-import androidx.compose.foundation.layout.Arrangement[m
[31m-import androidx.compose.foundation.layout.Box[m
[31m-import androidx.compose.foundation.layout.Column[m
[31m-import androidx.compose.foundation.layout.Row[m
[31m-import androidx.compose.foundation.layout.fillMaxHeight[m
[31m-import androidx.compose.foundation.layout.fillMaxSize[m
[31m-import androidx.compose.foundation.layout.height[m
[31m-import androidx.compose.foundation.layout.offset[m
[31m-import androidx.compose.foundation.layout.padding[m
[31m-import androidx.compose.foundation.layout.size[m
[31m-import androidx.compose.foundation.layout.width[m
[31m-import androidx.compose.foundation.layout.widthIn[m
[31m-import androidx.compose.foundation.layout.wrapContentSize[m
[31m-import androidx.compose.foundation.layout.wrapContentWidth[m
[31m-import androidx.compose.foundation.shape.CircleShape[m
[31m-import androidx.compose.foundation.shape.RoundedCornerShape[m
[31m-import androidx.compose.material.icons.Icons[m
[31m-import androidx.compose.material.icons.filled.Menu[m
[31m-import androidx.compose.material3.DropdownMenu[m
[31m-import androidx.compose.material3.DropdownMenuItem[m
[31m-import androidx.compose.material3.Icon[m
[31m-import androidx.compose.material3.MaterialTheme[m
[31m-import androidx.compose.material3.Surface[m
[31m-import androidx.compose.material3.Text[m
 import androidx.compose.runtime.Composable[m
[31m-import androidx.compose.runtime.DisposableEffect[m
[31m-import androidx.compose.runtime.LaunchedEffect[m
[31m-import androidx.compose.runtime.getValue[m
[31m-import androidx.compose.runtime.mutableStateOf[m
[31m-import androidx.compose.runtime.saveable.rememberSaveable[m
[31m-import androidx.compose.runtime.remember[m
[31m-import androidx.compose.runtime.rememberUpdatedState[m
[31m-import androidx.compose.runtime.setValue[m
[31m-import androidx.compose.ui.Alignment[m
 import androidx.compose.ui.Modifier[m
[31m-import androidx.compose.ui.draw.clip[m
 import androidx.compose.ui.geometry.Offset[m
[31m-import androidx.compose.ui.graphics.Color[m
[31m-import androidx.compose.ui.graphics.RectangleShape[m
[31m-import androidx.compose.ui.input.pointer.consumePositionChange[m
[31m-import androidx.compose.ui.input.pointer.pointerInput[m
[31m-import androidx.compose.ui.layout.boundsInRoot[m
[31m-import androidx.compose.ui.layout.onGloballyPositioned[m
[31m-import androidx.compose.ui.platform.LocalDensity[m
[31m-import androidx.compose.ui.unit.IntOffset[m
[31m-import androidx.compose.ui.unit.dp[m
[31m-import com.example.ui1.UIVariometer[m
 import com.example.xcpro.common.flight.FlightMode[m
[31m-import com.example.xcpro.map.MapOverlayGestureTarget[m
 import com.example.xcpro.map.ballast.BallastCommand[m
[31m-import com.example.xcpro.map.ballast.BallastPill[m
 import com.example.xcpro.map.ballast.BallastUiState[m
 import com.example.xcpro.variometer.layout.VariometerUiState[m
[31m-import kotlin.math.roundToInt[m
[31m-import kotlinx.coroutines.delay[m
[31m-import kotlinx.coroutines.launch[m
 [m
[32m+[m[32m/**[m
[32m+[m[32m * Thin façade over individual widget implementations.[m
[32m+[m[32m * Public API remains the same; implementations live in separate files for readability.[m
[32m+[m[32m */[m
 object MapUIWidgets {[m
 [m
[31m-    /**[m
[31m-     * Variometer widget that mirrors the hamburger/flight-mode drag plumbing.[m
[31m-     * Relies on [MapUIWidgetManager] for gesture region updates so map gestures yield correctly.[m
[31m-     */[m
[31m-    @OptIn(ExperimentalFoundationApi::class)[m
     @Composable[m
     fun VariometerWidget([m
         widgetManager: MapUIWidgetManager,[m
[36m@@ -95,443 +31,51 @@[m [mobject MapUIWidgets {[m
         onLongPress: () -> Unit,[m
         onEditFinished: () -> Unit,[m
         modifier: Modifier = Modifier[m
[31m-    ) {[m
[31m-        if (!variometerState.isInitialized) {[m
[31m-            Log.d("VARIO_GESTURE", "render skipped; state not initialized")[m
[31m-            return[m
[31m-        }[m
[31m-        Log.d("VARIO_GESTURE", "render editMode=$isEditMode offset=${variometerState.offset}")[m
[32m+[m[32m    ) = VariometerWidgetImpl([m
[32m+[m[32m        widgetManager = widgetManager,[m
[32m+[m[32m        variometerState = variometerState,[m
[32m+[m[32m        needleValue = needleValue,[m
[32m+[m[32m        displayValue = displayValue,[m
[32m+[m[32m        displayLabel = displayLabel,[m
[32m+[m[32m        screenWidthPx = screenWidthPx,[m
[32m+[m[32m        screenHeightPx = screenHeightPx,[m
[32m+[m[32m        minSizePx = minSizePx,[m
[32m+[m[32m        maxSizePx = maxSizePx,[m
[32m+[m[32m        isEditMode = isEditMode,[m
[32m+[m[32m        onOffsetChange = onOffsetChange,[m
[32m+[m[32m        onSizeChange = onSizeChange,[m
[32m+[m[32m        onLongPress = onLongPress,[m
[32m+[m[32m        onEditFinished = onEditFinished,[m
[32m+[m[32m        modifier = modifier[m
[32m+[m[32m    )[m
 [m
[31m-        DisposableEffect(Unit) {[m
[31m-            onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.VARIOMETER) }[m
[31m-        }[m
[31m-[m
[31m-        val density = LocalDensity.current[m
[31m-        val displayOffset = remember { mutableStateOf(variometerState.offset) }[m
[31m-        val displaySize = remember { mutableStateOf(variometerState.sizePx) }[m
[31m-        var isUserInteracting by remember { mutableStateOf(false) }[m
[31m-[m
[31m-        LaunchedEffect(variometerState.offset, variometerState.sizePx, isUserInteracting) {[m
[31m-            if (!isUserInteracting) {[m
[31m-                displayOffset.value = variometerState.offset[m
[31m-                displaySize.value = variometerState.sizePx[m
[31m-                Log.d([m
[31m-                    "VARIO_GESTURE",[m
[31m-                    "sync from state offset=${variometerState.offset} size=${variometerState.sizePx}"[m
[31m-                )[m
[31m-            }[m
[31m-        }[m
[31m-[m
[31m-        val latestVariometerState = rememberUpdatedState(variometerState)[m
[31m-[m
[31m-        fun applyDragDelta(dragAmount: Offset) {[m
[31m-            val sizePx = displaySize.value[m
[31m-            val maxX = (screenWidthPx - sizePx).coerceAtLeast(0f)[m
[31m-            val maxY = (screenHeightPx - sizePx).coerceAtLeast(0f)[m
[31m-            val newOffset = Offset([m
[31m-                x = (displayOffset.value.x + dragAmount.x).coerceIn(0f, maxX),[m
[31m-                y = (displayOffset.value.y + dragAmount.y).coerceIn(0f, maxY)[m
[31m-            )[m
[31m-            if (newOffset != displayOffset.value) {[m
[31m-                displayOffset.value = newOffset[m
[31m-                Log.d("VARIO_GESTURE", "dragging -> $newOffset (bounds=[0,$maxX]x[0,$maxY])")[m
[31m-            }[m
[31m-        }[m
[31m-[m
[31m-        val baseModifier = modifier[m
[31m-            .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }[m
[31m-            .size(with(density) { displaySize.value.toDp() })[m
[31m-            .background(Color.Transparent, RoundedCornerShape(12.dp))[m
[31m-            .onGloballyPositioned { coordinates ->[m
[31m-                widgetManager.updateGestureRegion([m
[31m-                    target = MapOverlayGestureTarget.VARIOMETER,[m
[31m-                    bounds = coordinates.boundsInRoot(),[m
[31m-                    consumeGestures = true[m
[31m-                )[m
[31m-            }[m
[31m-            .editModeBorder(isEditMode, RoundedCornerShape(12.dp))[m
[31m-[m
[31m-        val tapModifier = Modifier.pointerInput(isEditMode) {[m
[31m-            awaitEachGesture {[m
[31m-                val down = awaitFirstDown(requireUnconsumed = false)[m
[31m-                val longPress = awaitLongPressOrCancellation(down.id)[m
[31m-                if (longPress != null) {[m
[31m-                    Log.d("VARIO_GESTURE", "longPress detected -> toggling edit mode")[m
[31m-                    onLongPress()[m
[31m-                }[m
[31m-            }[m
[31m-        }[m
[31m-[m
[31m-        val dragModifier = if (isEditMode) {[m
[31m-            Modifier.pointerInput(screenWidthPx, screenHeightPx, displaySize.value) {[m
[31m-                detectDragGestures([m
[31m-                    onDragStart = {[m
[31m-                        isUserInteracting = true[m
[31m-                        Log.d("VARIO_GESTURE", "dragStart offset=${displayOffset.value}")[m
[31m-                    },[m
[31m-                    onDrag = { change, dragAmount ->[m
[31m-                        applyDragDelta(dragAmount)[m
[31m-                        change.consumePositionChange()[m
[31m-                    },[m
[31m-                    onDragEnd = {[m
[31m-                        isUserInteracting = false[m
[31m-                        Log.d("VARIO_GESTURE", "dragEnd offset=${displayOffset.value}")[m
[31m-                        onOffsetChange(displayOffset.value)[m
[31m-                        onEditFinished()[m
[31m-                    },[m
[31m-                    onDragCancel = {[m
[31m-                        isUserInteracting = false[m
[31m-                        Log.d("VARIO_GESTURE", "dragCancel restoring ${latestVariometerState.value.offset}")[m
[31m-                        displayOffset.value = latestVariometerState.value.offset[m
[31m-                        onEditFinished()[m
[31m-                    }[m
[31m-                )[m
[31m-            }[m
[31m-        } else {[m
[31m-            Modifier[m
[31m-        }[m
[31m-[m
[31m-        Box([m
[31m-            modifier = baseModifier[m
[31m-                .then(tapModifier)[m
[31m-                .then(dragModifier)[m
[31m-        ) {[m
[31m-            UIVariometer([m
[31m-                needleValue = needleValue,[m
[31m-                displayValue = displayValue,[m
[31m-                valueLabel = displayLabel,[m
[31m-                modifier = Modifier.fillMaxSize()[m
[31m-            )[m
[31m-[m
[31m-            if (isEditMode) {[m
[31m-                VariometerResizeHandle([m
[31m-                    onResizeStart = { isUserInteracting = true },[m
[31m-                    onResize = { dragAmount ->[m
[31m-                        val newSize = (displaySize.value + (dragAmount.x + dragAmount.y) / 2f)[m
[31m-                            .coerceIn(minSizePx, maxSizePx)[m
[31m-                        if (newSize != displaySize.value) {[m
[31m-                            displaySize.value = newSize[m
[31m-                            Log.d("VARIO_GESTURE", "resize -> size=$newSize")[m
[31m-                        }[m
[31m-                    },[m
[31m-                    onResizeEnd = {[m
[31m-                        isUserInteracting = false[m
[31m-                        Log.d("VARIO_GESTURE", "resizeEnd size=${displaySize.value}")[m
[31m-                        onSizeChange(displaySize.value)[m
[31m-                        onEditFinished()[m
[31m-                    }[m
[31m-                )[m
[31m-            }[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    @OptIn(ExperimentalFoundationApi::class)[m
     @Composable[m
     fun BallastWidget([m
[31m-[m
         widgetManager: MapUIWidgetManager,[m
[31m-[m
         ballastState: BallastUiState,[m
[31m-[m
         onCommand: (BallastCommand) -> Unit,[m
[31m-[m
         ballastOffset: Offset,[m
[31m-[m
         screenWidthPx: Float,[m
[31m-[m
         screenHeightPx: Float,[m
[31m-[m
         onOffsetChange: (Offset) -> Unit,[m
[31m-[m
         isEditMode: Boolean,[m
[31m-[m
         modifier: Modifier = Modifier,[m
[31m-[m
         widthDp: Float = 40f,[m
[31m-[m
         heightDp: Float = 120f[m
[32m+[m[32m    ) = BallastWidgetImpl([m
[32m+[m[32m        widgetManager = widgetManager,[m
[32m+[m[32m        ballastState = ballastState,[m
[32m+[m[32m        onCommand = onCommand,[m
[32m+[m[32m        ballastOffset = ballastOffset,[m
[32m+[m[32m        screenWidthPx = screenWidthPx,[m
[32m+[m[32m        screenHeightPx = screenHeightPx,[m
[32m+[m[32m        onOffsetChange = onOffsetChange,[m
[32m+[m[32m        isEditMode = isEditMode,[m
[32m+[m[32m        modifier = modifier,[m
[32m+[m[32m        widthDp = widthDp,[m
[32m+[m[32m        heightDp = heightDp[m
[32m+[m[32m    )[m
 [m
[31m-    ) {[m
[31m-[m
[31m-        val density = LocalDensity.current[m
[31m-[m
[31m-        val widthPx = with(density) { widthDp.dp.toPx() }[m
[31m-[m
[31m-        val heightPx = with(density) { heightDp.dp.toPx() }[m
[31m-[m
[31m-        val swipeThresholdPx = with(density) { 32.dp.toPx() }[m
[31m-[m
[31m-        val displayOffset = remember(isEditMode) { mutableStateOf(ballastOffset) }[m
[31m-        var showSwipeHint by rememberSaveable { mutableStateOf(true) }[m
[31m-        var dragAccumulation by remember { mutableStateOf(0f) }[m
[31m-        val latestBallastState by rememberUpdatedState(ballastState)[m
[31m-[m
[31m-        LaunchedEffect(ballastOffset, isEditMode) {[m
[31m-[m
[31m-            if (!isEditMode) {[m
[31m-[m
[31m-                displayOffset.value = ballastOffset[m
[31m-[m
[31m-            }[m
[31m-[m
[31m-        }[m
[31m-[m
[31m-        DisposableEffect(Unit) {[m
[31m-[m
[31m-            onDispose {[m
[31m-[m
[31m-                widgetManager.clearGestureRegion(MapOverlayGestureTarget.BALLAST)[m
[31m-[m
[31m-            }[m
[31m-[m
[31m-        }[m
[31m-[m
[31m-        Box([m
[31m-[m
[31m-            modifier = modifier[m
[31m-[m
[31m-                .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }[m
[31m-[m
[31m-                .height(heightDp.dp)[m
[31m-[m
[31m-                .wrapContentWidth(Alignment.Start)[m
[31m-[m
[31m-                .widthIn(min = widthDp.dp)[m
[31m-[m
[31m-                .editModeBorder(isEditMode, RoundedCornerShape(18.dp))[m
[31m-[m
[31m-                .onGloballyPositioned { coordinates ->[m
[31m-[m
[31m-                    widgetManager.updateGestureRegion([m
[31m-[m
[31m-                        target = MapOverlayGestureTarget.BALLAST,[m
[31m-[m
[31m-                        bounds = coordinates.boundsInRoot()[m
[31m-[m
[31m-                    )[m
[31m-[m
[31m-                }[m
[31m-[m
[31m-                .then([m
[31m-[m
[31m-                    if (isEditMode) {[m
[31m-[m
[31m-                        Modifier.pointerInput(screenWidthPx, screenHeightPx) {[m
[31m-[m
[31m-                            detectDragGestures([m
[31m-[m
[31m-                                onDrag = { change, dragAmount ->[m
[31m-[m
[31m-                                    displayOffset.value = Offset([m
[31m-[m
[31m-                                        x = (displayOffset.value.x + dragAmount.x).coerceIn([m
[31m-[m
[31m-                                            0f,[m
[31m-[m
[31m-                                            (screenWidthPx - widthPx).coerceAtLeast(0f)[m
[31m-[m
[31m-                                        ),[m
[31m-[m
[31m-                                        y = (displayOffset.value.y + dragAmount.y).coerceIn([m
[31m-[m
[31m-                                            0f,[m
[31m-[m
[31m-                                            (screenHeightPx - heightPx).coerceAtLeast(0f)[m
[31m-[m
[31m-                                        )[m
[31m-[m
[31m-                                    )[m
[31m-[m
[31m-                                    change.consumePositionChange()[m
[31m-[m
[31m-                                },[m
[31m-[m
[31m-                                onDragEnd = {[m
[31m-[m
[31m-                                    widgetManager.saveWidgetPosition("ballast_pill", displayOffset.value)[m
[31m-[m
[31m-                                    onOffsetChange(displayOffset.value)[m
[31m-[m
[31m-                                }[m
[31m-[m
[31m-                            )[m
[31m-[m
[31m-                        }[m
[31m-[m
[31m-                    } else {[m
[31m-[m
[31m-                        Modifier[m
[31m-[m
[31m-                    }[m
[31m-[m
[31m-                )[m
[31m-[m
[31m-                .pointerInput(isEditMode) {[m
[31m-[m
[31m-                    if (!isEditMode) {[m
[31m-[m
[31m-                        detectTapGestures(onTap = {[m
[31m-[m
[31m-                            if (latestBallastState.isAnimating) {[m
[31m-[m
[31m-                                onCommand(BallastCommand.Cancel)[m
[31m-[m
[31m-                            } else {[m
[31m-[m
[31m-                                showSwipeHint = false[m
[31m-[m
[31m-                            }[m
[31m-[m
[31m-                        })[m
[31m-[m
[31m-                    }[m
[31m-[m
[31m-                }[m
[31m-[m
[31m-                .pointerInput(isEditMode) {[m
[31m-[m
[31m-                    if (!isEditMode) {[m
[31m-[m
[31m-                        detectVerticalDragGestures([m
[31m-[m
[31m-                            onDragStart = {[m
[31m-[m
[31m-                                dragAccumulation = 0f[m
[31m-[m
[31m-                                showSwipeHint = false[m
[31m-[m
[31m-                            },[m
[31m-[m
[31m-                            onVerticalDrag = { change, dragAmount ->[m
[31m-[m
[31m-                                dragAccumulation += dragAmount[m
[31m-[m
[31m-                                change.consumePositionChange()[m
[31m-[m
[31m-                            },[m
[31m-[m
[31m-                            onDragEnd = {[m
[31m-[m
[31m-                                when {[m
[31m-[m
[31m-                                    dragAccumulation <= -swipeThresholdPx -> onCommand(BallastCommand.StartFill)[m
[31m-[m
[31m-                                    dragAccumulation >= swipeThresholdPx -> onCommand(BallastCommand.StartDrain)[m
[31m-[m
[31m-                                }[m
[31m-[m
[31m-                                dragAccumulation = 0f[m
[31m-[m
[31m-                            },[m
[31m-[m
[31m-                            onDragCancel = {[m
[31m-[m
[31m-                                dragAccumulation = 0f[m
[31m-[m
[31m-                            }[m
[31m-[m
[31m-                        )[m
[31m-[m
[31m-                    }[m
[31m-[m
[31m-                }[m
[31m-[m
[31m-        ) {[m
[31m-[m
[31m-            Row([m
[31m-[m
[31m-                modifier = Modifier[m
[31m-[m
[31m-                    .fillMaxHeight()[m
[31m-[m
[31m-                    .padding(horizontal = 4.dp),[m
[31m-[m
[31m-                verticalAlignment = Alignment.CenterVertically,[m
[31m-[m
[31m-                horizontalArrangement = Arrangement.spacedBy(8.dp)[m
[31m-[m
[31m-            ) {[m
[31m-[m
[31m-                BallastPill([m
[31m-[m
[31m-                    state = ballastState,[m
[31m-[m
[31m-                    onCommand = onCommand,[m
[31m-[m
[31m-                    modifier = Modifier[m
[31m-[m
[31m-                        .width(widthDp.dp)[m
[31m-[m
[31m-                        .height(heightDp.dp)[m
[31m-[m
[31m-                )[m
[31m-[m
[31m-[m
[31m-[m
[31m-                AnimatedVisibility([m
[31m-[m
[31m-                    visible = showSwipeHint && !isEditMode,[m
[31m-[m
[31m-                    enter = fadeIn(),[m
[31m-[m
[31m-                    exit = fadeOut()[m
[31m-[m
[31m-                ) {[m
[31m-[m
[31m-                    Column([m
[31m-[m
[31m-                        modifier = Modifier.padding(end = 6.dp),[m
[31m-[m
[31m-                        horizontalAlignment = Alignment.Start,[m
[31m-[m
[31m-                        verticalArrangement = Arrangement.spacedBy(6.dp)[m
[31m-[m
[31m-                    ) {[m
[31m-[m
[31m-                        Text([m
[31m-[m
[31m-                            text = "Swipe Up Fill",[m
[31m-[m
[31m-                            style = MaterialTheme.typography.labelSmall,[m
[31m-[m
[31m-                            color = MaterialTheme.colorScheme.error,[m
[31m-[m
[31m-                            maxLines = 1,[m
[31m-[m
[31m-                            softWrap = false[m
[31m-[m
[31m-                        )[m
[31m-[m
[31m-                        Text([m
[31m-[m
[31m-                            text = "Swipe Down Drain",[m
[31m-[m
[31m-                            style = MaterialTheme.typography.labelSmall,[m
[31m-[m
[31m-                            color = MaterialTheme.colorScheme.error,[m
[31m-[m
[31m-                            maxLines = 1,[m
[31m-[m
[31m-                            softWrap = false[m
[31m-[m
[31m-                        )[m
[31m-[m
[31m-                    }[m
[31m-[m
[31m-                }[m
[31m-[m
[31m-            }[m
[31m-[m
[31m-        }[m
[31m-[m
[31m-    }[m
[31m-[m
[31m-[m
[31m-    /**[m
[31m-     * Draggable hamburger button docked along the left edge.[m
[31m-     * Users can reposition it while edit mode is active.[m
[31m-     */[m
[31m-    @OptIn(ExperimentalFoundationApi::class)[m
     @Composable[m
     fun SideHamburgerMenu([m
         widgetManager: MapUIWidgetManager,[m
[36m@@ -544,109 +88,19 @@[m [mobject MapUIWidgets {[m
         isEditMode: Boolean,[m
         modifier: Modifier = Modifier,[m
         sizeDp: Float = 90f[m
[31m-    ) {[m
[31m-        val density = LocalDensity.current[m
[31m-        val iconSizeDp = sizeDp * 0.6f[m
[31m-        val containerSizeDp = if (isEditMode) sizeDp * 0.8f else sizeDp[m
[31m-        val sizePx = with(density) { containerSizeDp.dp.toPx() }[m
[31m-[m
[31m-        DisposableEffect(Unit) {[m
[31m-            onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.SIDE_HAMBURGER) }[m
[31m-        }[m
[31m-[m
[31m-        val displayOffset = remember(isEditMode) {[m
[31m-            mutableStateOf(hamburgerOffset)[m
[31m-        }[m
[32m+[m[32m    ) = SideHamburgerMenuImpl([m
[32m+[m[32m        widgetManager = widgetManager,[m
[32m+[m[32m        hamburgerOffset = hamburgerOffset,[m
[32m+[m[32m        screenWidthPx = screenWidthPx,[m
[32m+[m[32m        screenHeightPx = screenHeightPx,[m
[32m+[m[32m        onHamburgerTap = onHamburgerTap,[m
[32m+[m[32m        onHamburgerLongPress = onHamburgerLongPress,[m
[32m+[m[32m        onOffsetChange = onOffsetChange,[m
[32m+[m[32m        isEditMode = isEditMode,[m
[32m+[m[32m        modifier = modifier,[m
[32m+[m[32m        sizeDp = sizeDp[m
[32m+[m[32m    )[m
 [m
[31m-        LaunchedEffect(hamburgerOffset, isEditMode) {[m
[31m-            if (!isEditMode) {[m
[31m-                displayOffset.value = hamburgerOffset[m
[31m-            }[m
[31m-        }[m
[31m-[m
[31m-        Surface([m
[31m-            modifier = modifier[m
[31m-                .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }[m
[31m-                .size(containerSizeDp.dp)[m
[31m-                .editModeBorder(isEditMode, RectangleShape)[m
[31m-                .onGloballyPositioned { coordinates ->[m
[31m-                    widgetManager.updateGestureRegion([m
[31m-                        target = MapOverlayGestureTarget.SIDE_HAMBURGER,[m
[31m-                        bounds = coordinates.boundsInRoot()[m
[31m-                    )[m
[31m-                }[m
[31m-                .then([m
[31m-                    if (isEditMode) {[m
[31m-                        Modifier.pointerInput(Unit) {[m
[31m-                            detectTapGestures([m
[31m-                                onLongPress = { onHamburgerLongPress() }[m
[31m-                            )[m
[31m-                        }[m
[31m-                    } else {[m
[31m-                        Modifier.combinedClickable([m
[31m-                            onClick = onHamburgerTap,[m
[31m-                            onLongClick = onHamburgerLongPress[m
[31m-                        )[m
[31m-                    }[m
[31m-                )[m
[31m-                .pointerInput(isEditMode, screenWidthPx, screenHeightPx) {[m
[31m-                    if (isEditMode) {[m
[31m-                        detectDragGestures([m
[31m-                            onDragStart = {[m
[31m-                                Log.d("MapUIWidgetManager", "Hamburger drag started from ${displayOffset.value}")[m
[31m-                            },[m
[31m-                            onDrag = { change, dragAmount ->[m
[31m-                                displayOffset.value = Offset([m
[31m-                                    x = (displayOffset.value.x + dragAmount.x).coerceIn(0f, (screenWidthPx - sizePx).coerceAtLeast(0f)),[m
[31m-                                    y = (displayOffset.value.y + dragAmount.y).coerceIn(0f, (screenHeightPx - sizePx).coerceAtLeast(0f))[m
[31m-                                )[m
[31m-                                change.consumePositionChange()[m
[31m-                            },[m
[31m-                            onDragEnd = {[m
[31m-                                Log.d("MapUIWidgetManager", "Hamburger drag ended at ${displayOffset.value}")[m
[31m-                                widgetManager.saveWidgetPosition("side_hamburger", displayOffset.value)[m
[31m-                                onOffsetChange(displayOffset.value)[m
[31m-                            }[m
[31m-                        )[m
[31m-                    }[m
[31m-                },[m
[31m-            shape = RectangleShape,[m
[31m-            color = Color.Transparent,[m
[31m-            contentColor = MaterialTheme.colorScheme.onSurface,[m
[31m-            tonalElevation = 0.dp,[m
[31m-            shadowElevation = 0.dp[m
[31m-        ) {[m
[31m-            Box([m
[31m-                modifier = Modifier.fillMaxSize(),[m
[31m-                contentAlignment = Alignment.Center[m
[31m-            ) {[m
[31m-                val lineWidth = (iconSizeDp * 0.72f).dp[m
[31m-                val lineHeight = (iconSizeDp * 0.08f).dp[m
[31m-                val columnHeight = iconSizeDp.dp + lineHeight * 2[m
[31m-                val lineShape = RoundedCornerShape(percent = 50)[m
[31m-                Column([m
[31m-                    verticalArrangement = Arrangement.spacedBy(lineHeight, Alignment.CenterVertically),[m
[31m-                    horizontalAlignment = Alignment.CenterHorizontally,[m
[31m-                    modifier = Modifier.height(columnHeight)[m
[31m-                ) {[m
[31m-                    repeat(3) {[m
[31m-                        Box([m
[31m-                            modifier = Modifier[m
[31m-                                .width(lineWidth)[m
[31m-                                .height(lineHeight)[m
[31m-                                .clip(lineShape)[m
[31m-                                .background(Color.Black)[m
[31m-                        )[m
[31m-                    }[m
[31m-                }[m
[31m-            }[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Flight mode selector that mirrors the hamburger widget's gesture plumbing so taps land reliably.[m
[31m-     */[m
[31m-    @OptIn(ExperimentalFoundationApi::class)[m
     @Composable[m
     fun FlightModeMenu([m
         widgetManager: MapUIWidgetManager,[m
[36m@@ -661,230 +115,18 @@[m [mobject MapUIWidgets {[m
         modifier: Modifier = Modifier,[m
         widthDp: Float = 96f,[m
         heightDp: Float = 36f[m
[31m-    ) {[m
[31m-        val tag = "FlightModeMenu"[m
[31m-        val density = LocalDensity.current[m
[31m-        val widthPx = with(density) { widthDp.dp.toPx() }[m
[31m-        val heightPx = with(density) { heightDp.dp.toPx() }[m
[31m-[m
[31m-        DisposableEffect(Unit) {[m
[31m-            onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.FLIGHT_MODE) }[m
[31m-        }[m
[31m-[m
[31m-        val displayOffset = remember(isEditMode) { mutableStateOf(flightModeOffset) }[m
[31m-        LaunchedEffect(flightModeOffset, isEditMode) {[m
[31m-            if (!isEditMode) {[m
[31m-                displayOffset.value = flightModeOffset[m
[31m-            }[m
[31m-        }[m
[31m-[m
[31m-        var isExpanded by remember { mutableStateOf(false) }[m
[31m-        LaunchedEffect(isEditMode) {[m
[31m-            if (isEditMode) {[m
[31m-                isExpanded = false[m
[31m-            }[m
[31m-        }[m
[31m-[m
[31m-        Box([m
[31m-            modifier = modifier[m
[31m-                .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }[m
[31m-                .width(widthDp.dp)[m
[31m-                .height(heightDp.dp)[m
[31m-                .editModeBorder(isEditMode, RoundedCornerShape(18.dp))[m
[31m-                .onGloballyPositioned { coordinates ->[m
[31m-                    widgetManager.updateGestureRegion([m
[31m-                        target = MapOverlayGestureTarget.FLIGHT_MODE,[m
[31m-                        bounds = coordinates.boundsInRoot()[m
[31m-                    )[m
[31m-                }[m
[31m-                .then([m
[31m-                    if (isEditMode) {[m
[31m-                        Modifier.pointerInput(Unit) {[m
[31m-                            detectTapGestures(onLongPress = { isExpanded = false })[m
[31m-                        }[m
[31m-                    } else {[m
[31m-                        Modifier.combinedClickable([m
[31m-                            onClick = {[m
[31m-                                isExpanded = true[m
[31m-                                Log.d(tag, "Surface clicked; opening dropdown")[m
[31m-                            }[m
[31m-                        )[m
[31m-                    }[m
[31m-                )[m
[31m-                .pointerInput(isEditMode, screenWidthPx, screenHeightPx) {[m
[31m-                    if (isEditMode) {[m
[31m-                        detectDragGestures([m
[31m-                            onDragStart = {[m
[31m-                                Log.d(tag, "Drag started from ${displayOffset.value}")[m
[31m-                            },[m
[31m-                            onDrag = { change, dragAmount ->[m
[31m-                                displayOffset.value = Offset([m
[31m-                                    x = (displayOffset.value.x + dragAmount.x).coerceIn([m
[31m-                                        0f,[m
[31m-                                        (screenWidthPx - widthPx).coerceAtLeast(0f)[m
[31m-                                    ),[m
[31m-                                    y = (displayOffset.value.y + dragAmount.y).coerceIn([m
[31m-                                        0f,[m
[31m-                                        (screenHeightPx - heightPx).coerceAtLeast(0f)[m
[31m-                                    )[m
[31m-                                )[m
[31m-                                change.consumePositionChange()[m
[31m-                            },[m
[31m-                            onDragEnd = {[m
[31m-                                Log.d(tag, "Drag ended at ${displayOffset.value}")[m
[31m-                                widgetManager.saveWidgetPosition("flight_mode_menu", displayOffset.value)[m
[31m-                                onOffsetChange(displayOffset.value)[m
[31m-                            }[m
[31m-                        )[m
[31m-                    }[m
[31m-                }[m
[31m-        ) {[m
[31m-            Surface([m
[31m-                shape = RoundedCornerShape(18.dp),[m
[31m-                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),[m
[31m-                tonalElevation = 0.dp,[m
[31m-                shadowElevation = 0.dp,[m
[31m-                modifier = Modifier.fillMaxSize()[m
[31m-            ) {[m
[31m-                Row([m
[31m-                    modifier = Modifier[m
[31m-                        .fillMaxSize()[m
[31m-                        .padding(horizontal = 12.dp, vertical = 8.dp),[m
[31m-                    verticalAlignment = Alignment.CenterVertically,[m
[31m-                    horizontalArrangement = Arrangement.spacedBy(8.dp)[m
[31m-                ) {[m
[31m-                    Box([m
[31m-                        modifier = Modifier[m
[31m-                            .size(10.dp)[m
[31m-                            .clip(CircleShape)[m
[31m-                            .background(colorForMode(currentMode))[m
[31m-                    )[m
[31m-                    Text([m
[31m-                        text = currentMode.displayName,[m
[31m-                        style = MaterialTheme.typography.labelMedium[m
[31m-                    )[m
[31m-                }[m
[31m-            }[m
[31m-[m
[31m-            DropdownMenu([m
[31m-                expanded = isExpanded,[m
[31m-                onDismissRequest = {[m
[31m-                    isExpanded = false[m
[31m-                    Log.d(tag, "Dropdown dismissed")[m
[31m-                },[m
[31m-                shape = RoundedCornerShape(20.dp)[m
[31m-            ) {[m
[31m-                visibleModes.forEach { mode ->[m
[31m-                    DropdownMenuItem([m
[31m-                        onClick = {[m
[31m-                            onModeChange(mode)[m
[31m-                            isExpanded = false[m
[31m-                            Log.d(tag, "Mode selected ${mode.displayName}")[m
[31m-                        },[m
[31m-                        text = {[m
[31m-                            Row([m
[31m-                                verticalAlignment = Alignment.CenterVertically,[m
[31m-                                horizontalArrangement = Arrangement.spacedBy(12.dp)[m
[31m-                            ) {[m
[31m-                                Box([m
[31m-                                    modifier = Modifier[m
[31m-                                        .size(10.dp)[m
[31m-                                        .clip(CircleShape)[m
[31m-                                        .background([m
[31m-                                            colorForMode(mode).copy([m
[31m-                                                alpha = if (mode == currentMode) 1f else 0.4f[m
[31m-                                            )[m
[31m-                                        )[m
[31m-                                )[m
[31m-                                Text([m
[31m-                                    text = mode.displayName,[m
[31m-                                    style = if (mode == currentMode) {[m
[31m-                                        MaterialTheme.typography.labelMedium.copy([m
[31m-                                            color = MaterialTheme.colorScheme.onSurface[m
[31m-                                        )[m
[31m-                                    } else {[m
[31m-                                        MaterialTheme.typography.labelMedium.copy([m
[31m-                                            color = MaterialTheme.colorScheme.onSurfaceVariant[m
[31m-                                        )[m
[31m-                                    }[m
[31m-                                )[m
[31m-                            }[m
[31m-                        }[m
[31m-                    )[m
[31m-                }[m
[31m-            }[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    @Composable[m
[31m-    private fun VariometerResizeHandle([m
[31m-        onResizeStart: () -> Unit,[m
[31m-        onResize: (dragAmount: Offset) -> Unit,[m
[31m-        onResizeEnd: () -> Unit[m
[31m-    ) {[m
[31m-        Box([m
[31m-            modifier = Modifier[m
[31m-                .fillMaxSize()[m
[31m-                .wrapContentSize(Alignment.BottomEnd)[m
[31m-        ) {[m
[31m-            Box([m
[31m-                modifier = Modifier[m
[31m-                    .size(24.dp)[m
[31m-                    .background(Color(0xB3FF1744), RoundedCornerShape(12.dp))[m
[31m-                    .pointerInput(Unit) {[m
[31m-                        detectDragGestures([m
[31m-                            onDragStart = {[m
[31m-                                Log.d("VARIO_GESTURE", "resize start")[m
[31m-                                onResizeStart()[m
[31m-                            },[m
[31m-                            onDrag = { change, dragAmount ->[m
[31m-                                onResize(dragAmount)[m
[31m-                                change.consumePositionChange()[m
[31m-                            },[m
[31m-                            onDragEnd = {[m
[31m-                                Log.d("VARIO_GESTURE", "resize end")[m
[31m-                                onResizeEnd()[m
[31m-                            },[m
[31m-                            onDragCancel = {[m
[31m-                                Log.d("VARIO_GESTURE", "resize cancel")[m
[31m-                                onResizeEnd()[m
[31m-                            }[m
[31m-                        )[m
[31m-                    }[m
[31m-            )[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    private fun colorForMode(mode: FlightMode): Color {[m
[31m-        return when (mode) {[m
[31m-            FlightMode.CRUISE -> Color(0xFF2196F3)[m
[31m-            FlightMode.THERMAL -> Color(0xFF9C27B0)[m
[31m-            FlightMode.FINAL_GLIDE -> Color(0xFFF44336)[m
[31m-        }[m
[31m-    }[m
[31m-[m
[32m+[m[32m    ) = FlightModeMenuImpl([m
[32m+[m[32m        widgetManager = widgetManager,[m
[32m+[m[32m        currentMode = currentMode,[m
[32m+[m[32m        visibleModes = visibleModes,[m
[32m+[m[32m        onModeChange = onModeChange,[m
[32m+[m[32m        flightModeOffset = flightModeOffset,[m
[32m+[m[32m        screenWidthPx = screenWidthPx,[m
[32m+[m[32m        screenHeightPx = screenHeightPx,[m
[32m+[m[32m        onOffsetChange = onOffsetChange,[m
[32m+[m[32m        isEditMode = isEditMode,[m
[32m+[m[32m        modifier = modifier,[m
[32m+[m[32m        widthDp = widthDp,[m
[32m+[m[32m        heightDp = heightDp[m
[32m+[m[32m    )[m
 }[m
[31m-[m
[31m-private fun Modifier.editModeBorder([m
[31m-    isEditMode: Boolean,[m
[31m-    shape: androidx.compose.ui.graphics.Shape = RectangleShape[m
[31m-): Modifier {[m
[31m-    return if (isEditMode) {[m
[31m-        border([m
[31m-            width = 2.dp,[m
[31m-            color = Color.Red,[m
[31m-            shape = shape[m
[31m-        )[m
[31m-    } else {[m
[31m-        this[m
[31m-    }[m
[31m-}[m
[31m-[m
[31m-[m
[31m-[m
[31m-[m
[31m-[m
[31m-[m
[31m-[m
[31m-[m
[31m-[m
[1mdiff --git a/feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt b/feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt[m
[1mindex 1d692a8..fa1f10d 100644[m
[1m--- a/feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt[m
[1m+++ b/feature/map/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt[m
[36m@@ -1,75 +1,18 @@[m
[31m-﻿package com.example.xcpro.sensors[m
[32m+[m[32mpackage com.example.xcpro.sensors[m
 [m
[31m-import android.Manifest[m
[31m-import android.annotation.SuppressLint[m
 import android.content.Context[m
[31m-import android.content.pm.PackageManager[m
[31m-import android.hardware.Sensor[m
[31m-import android.hardware.SensorEvent[m
[31m-import android.hardware.SensorEventListener[m
 import android.hardware.SensorManager[m
[31m-import android.location.Location[m
[31m-import android.location.LocationListener[m
 import android.location.LocationManager[m
[31m-import android.util.Log[m
[31m-import androidx.core.app.ActivityCompat[m
[31m-import com.example.xcpro.common.units.AltitudeM[m
[31m-import com.example.xcpro.common.units.PressureHpa[m
[31m-import com.example.xcpro.common.units.SpeedMs[m
 import kotlinx.coroutines.flow.MutableStateFlow[m
 import kotlinx.coroutines.flow.StateFlow[m
 import kotlinx.coroutines.flow.asStateFlow[m
[31m-import org.maplibre.android.geometry.LatLng[m
 [m
 /**[m
[31m- * Unified Sensor Manager - Single Source of Truth for all sensors[m
[31m- *[m
[31m- * RESPONSIBILITIES:[m
[31m- * - Manage LocationManager (GPS + Network providers)[m
[31m- * - Manage SensorManager (Pressure + Magnetic sensors)[m
[31m- * - Emit raw sensor data via StateFlows[m
[31m- * - NO calculations (only sensor management)[m
[31m- *[m
[31m- * SSOT PRINCIPLE:[m
[31m- * - ONE StateFlow per sensor type (GPS, Barometer, Compass)[m
[31m- * - ALL consumers read from these flows[m
[31m- * - ZERO duplicate listeners[m
[31m- *[m
[31m- * INDUSTRY STANDARDS:[m
[31m- * - GPS: 1Hz (1000ms) - standard for gliding apps, battery efficient[m
[31m- * - Barometer: ~20Hz (SENSOR_DELAY_GAME) - for smooth variometer[m
[31m- * - Magnetometer: ~60Hz (SENSOR_DELAY_UI) - for compass display[m
[32m+[m[32m * Public facade exposing raw sensor flows.[m
[32m+[m[32m * Delegates registration and updates to [SensorRegistry].[m
  */[m
[31m-class UnifiedSensorManager(private val context: Context) : SensorEventListener, SensorDataSource {[m
[32m+[m[32mclass UnifiedSensorManager(private val context: Context) : SensorDataSource {[m
 [m
[31m-    companion object {[m
[31m-        private const val TAG = "UnifiedSensorManager"[m
[31m-[m
[31m-        // GPS update rate (industry standard for gliding apps)[m
[31m-        private const val GPS_UPDATE_INTERVAL_MS = 1000L  // 1Hz (NOT 10Hz battery killer!)[m
[31m-        private const val GPS_MIN_DISTANCE_M = 0f         // Get all updates[m
[31m-[m
[31m-        // Barometer delay (for smooth variometer)[m
[31m-        private const val BARO_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME  // ~20Hz[m
[31m-[m
[31m-        // Magnetometer delay (for compass display)[m
[31m-        private const val COMPASS_SENSOR_DELAY = SensorManager.SENSOR_DELAY_UI  // ~60Hz[m
[31m-[m
[31m-        // Accelerometer delay (for variometer fusion)[m
[31m-        private const val ACCEL_SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME  // ~200Hz[m
[31m-    }[m
[31m-[m
[31m-    // Android system services[m
[31m-    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager[m
[31m-    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager[m
[31m-[m
[31m-    // Sensors[m
[31m-    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)[m
[31m-    private val magneticSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)[m
[31m-    private val linearAccelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)[m
[31m-    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)[m
[31m-[m
[31m-    // StateFlows - Single Source of Truth for each sensor[m
     private val _gpsFlow = MutableStateFlow<GPSData?>(null)[m
     override val gpsFlow: StateFlow<GPSData?> = _gpsFlow.asStateFlow()[m
 [m
[36m@@ -85,454 +28,33 @@[m [mclass UnifiedSensorManager(private val context: Context) : SensorEventListener,[m
     private val _attitudeFlow = MutableStateFlow<AttitudeData?>(null)[m
     override val attitudeFlow: StateFlow<AttitudeData?> = _attitudeFlow.asStateFlow()[m
 [m
[31m-    // Service state[m
[31m-    private var isGpsStarted = false[m
[31m-    private var isBaroStarted = false[m
[31m-    private var isCompassStarted = false[m
[31m-    private var isAccelStarted = false[m
[31m-    private var isRotationStarted = false[m
[31m-[m
     private val orientationProcessor = OrientationProcessor()[m
 [m
[31m-    // GPS location listener[m
[31m-    private val gpsListener = object : LocationListener {[m
[31m-        override fun onLocationChanged(location: Location) {[m
[31m-            Log.d(TAG, "GPS update: lat=${location.latitude}, lon=${location.longitude}, " +[m
[31m-                    "alt=${location.altitude}m, speed=${location.speed}m/s, " +[m
[31m-                    "bearing=${location.bearing}Â°, accuracy=${location.accuracy}m")[m
[31m-[m
[31m-            val gpsData = GPSData([m
[31m-                latLng = LatLng(location.latitude, location.longitude),[m
[31m-                altitude = AltitudeM(if (location.hasAltitude()) location.altitude else 0.0),[m
[31m-                speed = SpeedMs(if (location.hasSpeed()) location.speed.toDouble() else 0.0),[m
[31m-                bearing = if (location.hasBearing()) location.bearing.toDouble() else 0.0,[m
[31m-                accuracy = location.accuracy,[m
[31m-                timestamp = location.time[m
[31m-            )[m
[31m-[m
[31m-            _gpsFlow.value = gpsData[m
[31m-        }[m
[31m-[m
[31m-        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {[m
[31m-            Log.d(TAG, "GPS status changed: provider=$provider, status=$status")[m
[31m-        }[m
[31m-[m
[31m-        override fun onProviderEnabled(provider: String) {[m
[31m-            Log.d(TAG, "GPS provider enabled: $provider")[m
[31m-        }[m
[31m-[m
[31m-        override fun onProviderDisabled(provider: String) {[m
[31m-            Log.d(TAG, "GPS provider disabled: $provider")[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    // Barometer and compass sensor listener[m
[31m-    override fun onSensorChanged(event: SensorEvent?) {[m
[31m-        if (event == null) return[m
[31m-[m
[31m-        when (event.sensor.type) {[m
[31m-            Sensor.TYPE_PRESSURE -> {[m
[31m-                val pressureHPa = event.values[0].toDouble()[m
[31m-                val baroData = BaroData([m
[31m-                    pressureHPa = PressureHpa(pressureHPa),[m
[31m-                    timestamp = System.currentTimeMillis()[m
[31m-                )[m
[31m-                _baroFlow.value = baroData[m
[31m-[m
[31m-                if (baroData.timestamp % 5000 < 50) {[m
[31m-                    Log.d(TAG, "Barometer update: pressure=${"%.1f".format(pressureHPa)} hPa")[m
[31m-                }[m
[31m-            }[m
[31m-[m
[31m-            Sensor.TYPE_MAGNETIC_FIELD -> {[m
[31m-                val x = event.values[0][m
[31m-                val y = event.values[1][m
[31m-                val z = event.values[2][m
[31m-[m
[31m-                val heading = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble()))[m
[31m-                val normalizedHeading = (heading + 360) % 360[m
[31m-[m
[31m-                val compassData = CompassData([m
[31m-                    heading = normalizedHeading,[m
[31m-                    accuracy = event.accuracy,[m
[31m-                    timestamp = System.currentTimeMillis()[m
[31m-                )[m
[31m-                _compassFlow.value = compassData[m
[31m-[m
[31m-                if (compassData.timestamp % 5000 < 50) {[m
[31m-                    Log.d(TAG, "Compass update: heading=${"%.1f".format(normalizedHeading)}°")[m
[31m-                }[m
[31m-            }[m
[31m-[m
[31m-            Sensor.TYPE_ROTATION_VECTOR -> {[m
[31m-                orientationProcessor.updateRotationVector(event.values)[m
[31m-                orientationProcessor.attitude()?.let { attitude ->[m
[31m-                    _attitudeFlow.value = AttitudeData([m
[31m-                        headingDeg = attitude.headingDeg,[m
[31m-                        pitchDeg = attitude.pitchDeg,[m
[31m-                        rollDeg = attitude.rollDeg,[m
[31m-                        timestamp = System.currentTimeMillis(),[m
[31m-                        isReliable = attitude.isReliable[m
[31m-                    )[m
[31m-                }[m
[31m-            }[m
[31m-[m
[31m-            Sensor.TYPE_LINEAR_ACCELERATION -> {[m
[31m-                val sample = orientationProcessor.projectVerticalAcceleration(event.values)[m
[31m-                val accelData = AccelData([m
[31m-                    verticalAcceleration = sample.verticalAcceleration,[m
[31m-                    timestamp = System.currentTimeMillis(),[m
[31m-                    isReliable = sample.isReliable[m
[31m-                )[m
[31m-                _accelFlow.value = accelData[m
[31m-[m
[31m-                if (accelData.timestamp % 5000 < 50) {[m
[31m-                    Log.d([m
[31m-                        TAG,[m
[31m-                        "Accelerometer update: verticalAccel=${"%.3f".format(accelData.verticalAcceleration)} m/s^2, reliable=${accelData.isReliable}"[m
[31m-                    )[m
[31m-                }[m
[31m-            }[m
[31m-        }[m
[31m-    }[m
[31m-    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {[m
[31m-        sensor?.let {[m
[31m-            Log.d(TAG, "Sensor accuracy changed: ${it.name}, accuracy=$accuracy")[m
[31m-        }[m
[31m-    }[m
[32m+[m[32m    private val registry = SensorRegistry([m
[32m+[m[32m        context = context,[m
[32m+[m[32m        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,[m
[32m+[m[32m        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,[m
[32m+[m[32m        orientationProcessor = orientationProcessor,[m
[32m+[m[32m        updateGps = { _gpsFlow.value = it },[m
[32m+[m[32m        updateBaro = { _baroFlow.value = it },[m
[32m+[m[32m        updateCompass = { _compassFlow.value = it },[m
[32m+[m[32m        updateAttitude = { _attitudeFlow.value = it },[m
[32m+[m[32m        updateAccel = { _accelFlow.value = it }[m
[32m+[m[32m    )[m
 [m
     /**[m
[31m-     * Start all sensors[m
[31m-     * MUST be called after location permissions are granted[m
[32m+[m[32m     * Start all sensors. Call after permissions are granted.[m
      */[m
[31m-    fun startAllSensors(): Boolean {[m
[31m-        Log.d(TAG, "Starting all sensors...")[m
[31m-        val gpsStarted = startGPS()[m
[31m-        val baroStarted = startBarometer()[m
[31m-        val compassStarted = startCompass()[m
[31m-        val rotationStarted = startRotationVector()[m
[31m-        val accelStarted = startAccelerometer()[m
[31m-        Log.d([m
[31m-            TAG,[m
[31m-            "Sensor start status -> gps=$gpsStarted, baro=$baroStarted, compass=$compassStarted, rotation=$rotationStarted, accel=$accelStarted"[m
[31m-        )[m
[31m-        return gpsStarted[m
[31m-    }[m
[32m+[m[32m    fun startAllSensors(): Boolean = registry.startAll()[m
 [m
     /**[m
[31m-     * Stop all sensors[m
[31m-     * MUST be called when app is backgrounded or destroyed[m
[32m+[m[32m     * Stop all sensors. Call when app backgrounds or shuts down.[m
      */[m
[31m-    fun stopAllSensors() {[m
[31m-        Log.d(TAG, "Stopping all sensors...")[m
[31m-        stopGPS()[m
[31m-        stopBarometer()[m
[31m-        stopCompass()[m
[31m-        stopRotationVector()[m
[31m-        stopAccelerometer()[m
[31m-        Log.d(TAG, "All sensors stopped")[m
[31m-    }[m
[32m+[m[32m    fun stopAllSensors() = registry.stopAll()[m
 [m
[31m-    /**[m
[31m-     * Start GPS tracking (1Hz - industry standard)[m
[31m-     */[m
[31m-    @SuppressLint("MissingPermission")[m
[31m-    private fun startGPS(): Boolean {[m
[31m-        if (isGpsStarted) {[m
[31m-            Log.d(TAG, "GPS already started")[m
[31m-            return true[m
[31m-        }[m
[31m-[m
[31m-        if (!hasLocationPermissions()) {[m
[31m-            Log.e(TAG, "No location permissions - cannot start GPS")[m
[31m-            return false[m
[31m-        }[m
[31m-[m
[31m-        var gpsProviderStarted = false[m
[31m-        try {[m
[31m-            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {[m
[31m-                locationManager.requestLocationUpdates([m
[31m-                    LocationManager.GPS_PROVIDER,[m
[31m-                    GPS_UPDATE_INTERVAL_MS,[m
[31m-                    GPS_MIN_DISTANCE_M,[m
[31m-                    gpsListener[m
[31m-                )[m
[31m-                gpsProviderStarted = true[m
[31m-                Log.d(TAG, "GPS provider started (1Hz)")[m
[31m-            } else {[m
[31m-                Log.w(TAG, "GPS provider not enabled")[m
[31m-            }[m
[31m-[m
[31m-            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {[m
[31m-                locationManager.requestLocationUpdates([m
[31m-                    LocationManager.NETWORK_PROVIDER,[m
[31m-                    GPS_UPDATE_INTERVAL_MS,[m
[31m-                    GPS_MIN_DISTANCE_M,[m
[31m-                    gpsListener[m
[31m-                )[m
[31m-                Log.d(TAG, "Network provider started for fast initial fix")[m
[31m-            }[m
[31m-[m
[31m-            if (gpsProviderStarted) {[m
[31m-                getLastKnownLocation()?.let { location ->[m
[31m-                    Log.d(TAG, "Using last known location: ${location.latitude}, ${location.longitude}")[m
[31m-                    gpsListener.onLocationChanged(location)[m
[31m-                }[m
[31m-                isGpsStarted = true[m
[31m-            } else {[m
[31m-                Log.w(TAG, "Unable to register GPS listener - will retry later")[m
[31m-            }[m
[31m-        } catch (e: SecurityException) {[m
[31m-            Log.e(TAG, "Security exception starting GPS: ${e.message}")[m
[31m-            gpsProviderStarted = false[m
[31m-        } catch (e: Exception) {[m
[31m-            Log.e(TAG, "Error starting GPS: ${e.message}", e)[m
[31m-            gpsProviderStarted = false[m
[31m-        }[m
[31m-[m
[31m-        return gpsProviderStarted[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Start barometer sensor (~20Hz for smooth variometer)[m
[31m-     */[m
[31m-    private fun startBarometer(): Boolean {[m
[31m-        if (isBaroStarted) {[m
[31m-            Log.d(TAG, "Barometer already started")[m
[31m-            return true[m
[31m-        }[m
[31m-[m
[31m-        val sensor = pressureSensor ?: run {[m
[31m-            Log.w(TAG, "No barometer sensor available on this device")[m
[31m-            return false[m
[31m-        }[m
[31m-[m
[31m-        val success = sensorManager.registerListener(this, sensor, BARO_SENSOR_DELAY)[m
[31m-        if (success) {[m
[31m-            isBaroStarted = true[m
[31m-            Log.d(TAG, "Barometer started (~20Hz): ${sensor.name}")[m
[31m-        } else {[m
[31m-            Log.e(TAG, "Failed to register barometer listener")[m
[31m-        }[m
[31m-        return success[m
[31m-    }[m
[32m+[m[32m    fun isGpsEnabled(): Boolean = registry.isGpsEnabled()[m
 [m
[31m-    /**[m
[31m-     * Start compass sensor (~60Hz for display)[m
[31m-     */[m
[31m-    /**[m
[31m-     * Start compass sensor (~60Hz for display)[m
[31m-     */[m
[31m-        private fun startCompass(): Boolean {[m
[31m-        if (isCompassStarted) {[m
[31m-            Log.d(TAG, "Compass already started")[m
[31m-            return true[m
[31m-        }[m
[31m-[m
[31m-        val sensor = magneticSensor ?: run {[m
[31m-            Log.w(TAG, "No compass sensor available on this device")[m
[31m-            return false[m
[31m-        }[m
[31m-[m
[31m-        val success = sensorManager.registerListener(this, sensor, COMPASS_SENSOR_DELAY)[m
[31m-        if (success) {[m
[31m-            isCompassStarted = true[m
[31m-            Log.d(TAG, "Compass started (~60Hz): ${sensor.name}")[m
[31m-        } else {[m
[31m-            Log.e(TAG, "Failed to register compass listener")[m
[31m-        }[m
[31m-        return success[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Start rotation vector sensor[m
[31m-     */[m
[31m-    /**[m
[31m-     * Stop GPS tracking[m
[31m-     */[m
[31m-    private fun stopGPS() {[m
[31m-        if (!isGpsStarted) return[m
[32m+[m[32m    fun hasLocationPermissions(): Boolean = registry.hasLocationPermissions()[m
 [m
[31m-        try {[m
[31m-            locationManager.removeUpdates(gpsListener)[m
[31m-            isGpsStarted = false[m
[31m-            Log.d(TAG, "ðŸ›‘ GPS stopped")[m
[31m-        } catch (e: Exception) {[m
[31m-            Log.e(TAG, "âŒ Error stopping GPS: ${e.message}")[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Stop barometer sensor[m
[31m-     */[m
[31m-    private fun stopBarometer() {[m
[31m-        if (!isBaroStarted) return[m
[31m-[m
[31m-        pressureSensor?.let {[m
[31m-            sensorManager.unregisterListener(this, it)[m
[31m-            isBaroStarted = false[m
[31m-            Log.d(TAG, "ðŸ›‘ Barometer stopped")[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Stop compass sensor[m
[31m-     */[m
[31m-    private fun stopCompass() {[m
[31m-        if (!isCompassStarted) return[m
[31m-[m
[31m-        magneticSensor?.let {[m
[31m-            sensorManager.unregisterListener(this, it)[m
[31m-            isCompassStarted = false[m
[31m-            Log.d(TAG, "ðŸ›‘ Compass stopped")[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Start rotation vector sensor (provides device orientation)[m
[31m-     */[m
[31m-        private fun startRotationVector(): Boolean {[m
[31m-        if (isRotationStarted) {[m
[31m-            Log.d(TAG, "Rotation vector already started")[m
[31m-            return true[m
[31m-        }[m
[31m-[m
[31m-        val sensor = rotationVectorSensor ?: run {[m
[31m-            Log.w(TAG, "No rotation vector sensor available on this device")[m
[31m-            return false[m
[31m-        }[m
[31m-[m
[31m-        val success = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)[m
[31m-        if (success) {[m
[31m-            isRotationStarted = true[m
[31m-            Log.d(TAG, "Rotation vector started: ${sensor.name}")[m
[31m-        } else {[m
[31m-            Log.e(TAG, "Failed to register rotation vector listener")[m
[31m-        }[m
[31m-        return success[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Start accelerometer sensor (~200Hz for variometer fusion)[m
[31m-     */[m
[31m-    /**[m
[31m-     * Start accelerometer sensor (~200Hz for variometer fusion)[m
[31m-     */[m
[31m-        private fun startAccelerometer(): Boolean {[m
[31m-        if (isAccelStarted) {[m
[31m-            Log.d(TAG, "Accelerometer already started")[m
[31m-            return true[m
[31m-        }[m
[31m-[m
[31m-        val sensor = linearAccelerometerSensor ?: run {[m
[31m-            Log.w(TAG, "No linear accelerometer sensor available on this device")[m
[31m-            return false[m
[31m-        }[m
[31m-[m
[31m-        val success = sensorManager.registerListener(this, sensor, ACCEL_SENSOR_DELAY)[m
[31m-        if (success) {[m
[31m-            isAccelStarted = true[m
[31m-            Log.d(TAG, "Accelerometer started (~200Hz): ${sensor.name}")[m
[31m-        } else {[m
[31m-            Log.e(TAG, "Failed to register accelerometer listener")[m
[31m-        }[m
[31m-        return success[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Stop rotation vector sensor[m
[31m-     */[m
[31m-    /**[m
[31m-     * Stop rotation vector sensor[m
[31m-     */[m
[31m-    private fun stopRotationVector() {[m
[31m-        if (!isRotationStarted) return[m
[31m-[m
[31m-        rotationVectorSensor?.let {[m
[31m-            sensorManager.unregisterListener(this, it)[m
[31m-        }[m
[31m-        isRotationStarted = false[m
[31m-        orientationProcessor.reset()[m
[31m-        Log.d(TAG, "Rotation vector stopped")[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Stop accelerometer sensor[m
[31m-     */[m
[31m-    private fun stopAccelerometer() {[m
[31m-        if (!isAccelStarted) return[m
[31m-[m
[31m-        linearAccelerometerSensor?.let {[m
[31m-            sensorManager.unregisterListener(this, it)[m
[31m-            isAccelStarted = false[m
[31m-            Log.d(TAG, "ðŸ›‘ Accelerometer stopped")[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Get last known location from any provider[m
[31m-     */[m
[31m-    @SuppressLint("MissingPermission")[m
[31m-    private fun getLastKnownLocation(): Location? {[m
[31m-        if (!hasLocationPermissions()) return null[m
[31m-[m
[31m-        return try {[m
[31m-            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)[m
[31m-            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)[m
[31m-[m
[31m-            // Return the most recent and accurate location[m
[31m-            when {[m
[31m-                gpsLocation != null && networkLocation != null -> {[m
[31m-                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation[m
[31m-                }[m
[31m-                gpsLocation != null -> gpsLocation[m
[31m-                networkLocation != null -> networkLocation[m
[31m-                else -> null[m
[31m-            }[m
[31m-        } catch (e: SecurityException) {[m
[31m-            Log.e(TAG, "âŒ Security exception getting last known location: ${e.message}")[m
[31m-            null[m
[31m-        }[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Check if location permissions are granted[m
[31m-     */[m
[31m-    private fun hasLocationPermissions(): Boolean {[m
[31m-        return ActivityCompat.checkSelfPermission([m
[31m-            context,[m
[31m-            Manifest.permission.ACCESS_FINE_LOCATION[m
[31m-        ) == PackageManager.PERMISSION_GRANTED ||[m
[31m-                ActivityCompat.checkSelfPermission([m
[31m-                    context,[m
[31m-                    Manifest.permission.ACCESS_COARSE_LOCATION[m
[31m-                ) == PackageManager.PERMISSION_GRANTED[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Check if GPS is enabled on device[m
[31m-     */[m
[31m-    fun isGpsEnabled(): Boolean {[m
[31m-        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)[m
[31m-    }[m
[31m-[m
[31m-    /**[m
[31m-     * Get sensor availability status[m
[31m-     */[m
[31m-    fun getSensorStatus(): SensorStatus {[m
[31m-        return SensorStatus([m
[31m-            gpsAvailable = isGpsEnabled(),[m
[31m-            gpsStarted = isGpsStarted,[m
[31m-            baroAvailable = pressureSensor != null,[m
[31m-            baroStarted = isBaroStarted,[m
[31m-            compassAvailable = magneticSensor != null,[m
[31m-            compassStarted = isCompassStarted,[m
[31m-            accelAvailable = linearAccelerometerSensor != null,[m
[31m-            accelStarted = isAccelStarted,[m
[31m-            rotationAvailable = rotationVectorSensor != null,[m
[31m-            rotationStarted = isRotationStarted,[m
[31m-            hasLocationPermissions = hasLocationPermissions()[m
[31m-        )[m
[31m-    }[m
[32m+[m[32m    fun getSensorStatus(): SensorStatus = registry.status()[m
 }[m
[31m-[m
