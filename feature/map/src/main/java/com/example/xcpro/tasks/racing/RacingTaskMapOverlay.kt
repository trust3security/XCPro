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
        println(" RACING TASK DEBUG: RacingTaskMapOverlay LaunchedEffect triggered. Waypoints: ${currentRacingTask.waypoints.size}")
        println(" RACING TASK DEBUG: Waypoints isEmpty: ${currentRacingTask.waypoints.isEmpty()}")

        if (currentRacingTask.waypoints.isNotEmpty()) {
            println(" RACING TASK DEBUG: Plotting racing task on map - waypoints: ${currentRacingTask.waypoints.size}")
            racingTaskManager.plotRacingOnMap(mapLibreMap)

            // COURSE LINE VERIFICATION: Check that red line touches turn points
            if (currentRacingTask.waypoints.size >= 2) {
                println(" COURSE LINE VERIFICATION: Starting validation for ${currentRacingTask.waypoints.size} waypoints")
                val validation = racingTaskManager.validateRacingCourse()

                println(" COURSE LINE VALIDATION RESULT:")
                println("    Valid: ${validation.isValid}")
                println("    Message: ${validation.message}")

                if (!validation.isValid) {
                    println(" COURSE LINE VALIDATION FAILED!")
                    validation.touchPointResults.forEachIndexed { index, result ->
                        if (!result.isValid) {
                            println("    Waypoint $index: ${result.message}")
                        }
                    }
                } else {
                    println(" COURSE LINE VALIDATION PASSED - All waypoints properly touched")
                }
            }
        } else {
            println(" RACING TASK DEBUG: NOT plotting racing task - no waypoints")
        }
    }

    Box(modifier = modifier) {
        // Racing task visualization UI could go here
        // (distance display, task info, etc.)
    }
}


