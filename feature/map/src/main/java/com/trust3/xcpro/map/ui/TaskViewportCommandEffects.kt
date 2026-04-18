package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.trust3.xcpro.map.MapCommand
import com.trust3.xcpro.map.MapScreenViewModel
import com.trust3.xcpro.tasks.TaskSheetViewModel
import com.trust3.xcpro.tasks.TaskSheetViewportEffect

@Composable
internal fun TaskViewportCommandEffects(
    mapViewModel: MapScreenViewModel,
    taskViewModel: TaskSheetViewModel = hiltViewModel()
) {
    LaunchedEffect(mapViewModel, taskViewModel) {
        taskViewModel.viewportEffects.collect { effect ->
            when (effect) {
                TaskSheetViewportEffect.RequestFitCurrentTask -> {
                    mapViewModel.emitMapCommand(MapCommand.FitCurrentTask)
                }
            }
        }
    }
}
