package com.example.xcpro.tasks.racing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import org.maplibre.android.maps.MapLibreMap
// Fixed: Now using Racing-specific manager - maintains task separation
import com.example.xcpro.tasks.racing.RacingTaskManager

/**
 * Racing Task Map Overlay - Completely separate from AAT tasks
 * Handles only racing task visualization and validation
 */
@Composable
fun RacingTaskMapOverlay(
    racingTaskManager: RacingTaskManager,
    mapLibreMap: MapLibreMap?,
    modifier: Modifier = Modifier
) {
    val currentRacingTask = racingTaskManager.currentRacingTask

    // Plot racing task on map when task changes
    LaunchedEffect(currentRacingTask) {

        if (currentRacingTask.waypoints.isNotEmpty()) {
            racingTaskManager.plotRacingOnMap(mapLibreMap)

            // COURSE LINE VERIFICATION: Check that red line touches turn points
            if (currentRacingTask.waypoints.size >= 2) {
                val validation = racingTaskManager.validateRacingCourse()


                if (!validation.isValid) {
                    validation.touchPointResults.forEachIndexed { index, result ->
                        if (!result.isValid) {
                        }
                    }
                } else {
                }
            }
        } else {
        }
    }

    Box(modifier = modifier) {
        // Racing task visualization UI could go here
        // (distance display, task info, etc.)
    }
}


