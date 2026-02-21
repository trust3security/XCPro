package com.example.xcpro.tasks.aat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.xcpro.core.time.Clock
import com.example.xcpro.tasks.aat.calculations.AATInteractiveTaskDistance
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.ui.AATOverlayFactory
import org.maplibre.android.maps.MapLibreMap

@Composable
fun rememberAATInteractiveTurnpointManager(
    aatWaypoints: List<AATWaypoint>,
    clock: Clock,
    callbacks: AATManagerCallbacks = AATManagerCallbacks()
): AATInteractiveTurnpointManager {
    return remember(clock) {
        AATInteractiveTurnpointManager(callbacks, clock)
    }.apply {
        updateWaypoints(aatWaypoints)
    }
}

@Composable
fun AATInteractiveTurnpointIntegration(
    aatWaypoints: List<AATWaypoint>,
    mapLibreMap: MapLibreMap?,
    clock: Clock,
    onWaypointUpdated: (Int, AATWaypoint) -> Unit,
    onDistanceUpdated: (AATInteractiveTaskDistance) -> Unit = {},
    onEditModeChanged: (Boolean, Int) -> Unit = { _, _ -> },
    onMapCameraUpdate: (Double, Double, Float) -> Unit = { _, _, _ -> },
    onCheckTargetPointHit: (Float, Float) -> Int? = { _, _ -> null },
    modifier: Modifier = Modifier
) {
    val manager = rememberAATInteractiveTurnpointManager(
        aatWaypoints = aatWaypoints,
        clock = clock,
        callbacks = AATManagerCallbacks(
            onWaypointUpdated = onWaypointUpdated,
            onDistanceUpdated = onDistanceUpdated,
            onEditModeChanged = onEditModeChanged,
            onMapCameraUpdate = onMapCameraUpdate,
            onCheckTargetPointHit = onCheckTargetPointHit
        )
    )

    LaunchedEffect(mapLibreMap) {
        manager.attachToMap(mapLibreMap)
    }

    LaunchedEffect(aatWaypoints) {
        manager.updateWaypoints(aatWaypoints)
    }

    if (mapLibreMap != null) {
        AATOverlayFactory.CreateInteractiveOverlay(
            aatWaypoints = aatWaypoints,
            mapLibreMap = mapLibreMap,
            clock = clock,
            onTargetPointUpdated = { index: Int, newTargetPoint: AATLatLng ->
                if (index < aatWaypoints.size) {
                    val updatedWaypoint = aatWaypoints[index].copy(
                        targetPoint = newTargetPoint,
                        isTargetPointCustomized = true
                    )
                    onWaypointUpdated(index, updatedWaypoint)
                }
            },
            onEditModeChanged = onEditModeChanged,
            onCheckTargetPointHit = onCheckTargetPointHit,
            modifier = modifier
        )
    }
}

object AATInteractiveTurnpointManagerFactory {

    fun createInteractiveManager(
        callbacks: AATManagerCallbacks,
        clock: Clock
    ): AATInteractiveTurnpointManager {
        return AATInteractiveTurnpointManager(callbacks, clock)
    }

    fun createDisplayManager(
        clock: Clock,
        onDistanceUpdated: (AATInteractiveTaskDistance) -> Unit = {}
    ): AATInteractiveTurnpointManager {
        return AATInteractiveTurnpointManager(
            AATManagerCallbacks(onDistanceUpdated = onDistanceUpdated),
            clock
        )
    }
}
