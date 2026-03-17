package com.example.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.xcpro.map.MapCommand
import com.example.xcpro.map.MapScreenViewModel
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.TaskSheetViewportEffect

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
