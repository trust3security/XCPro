package com.trust3.xcpro.map

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class MapScreenUiEventHandler(
    private val uiState: MutableStateFlow<MapUiState>,
    private val uiEffects: MutableSharedFlow<MapUiEffect>,
    private val onRefreshWaypoints: () -> Unit
) {

    fun onEvent(event: MapUiEvent) {
        when (event) {
            MapUiEvent.RefreshWaypoints -> onRefreshWaypoints()
            MapUiEvent.ToggleUiEditMode -> setUiEditMode(!uiState.value.isUiEditMode)
            is MapUiEvent.SetUiEditMode -> setUiEditMode(event.enabled)
            MapUiEvent.ToggleDrawer -> toggleDrawer()
            MapUiEvent.OpenDrawer -> openDrawer()
            is MapUiEvent.SetDrawerOpen -> setDrawerOpen(event.isOpen)
        }
    }

    private fun setUiEditMode(enabled: Boolean) {
        if (uiState.value.isUiEditMode == enabled) {
            return
        }
        uiState.update { it.copy(isUiEditMode = enabled) }
    }

    private fun toggleDrawer() {
        val shouldOpen = !uiState.value.isDrawerOpen
        uiState.update { it.copy(isDrawerOpen = shouldOpen) }
        uiEffects.tryEmit(if (shouldOpen) MapUiEffect.OpenDrawer else MapUiEffect.CloseDrawer)
    }

    private fun setDrawerOpen(isOpen: Boolean) {
        if (uiState.value.isDrawerOpen == isOpen) {
            return
        }
        uiState.update { it.copy(isDrawerOpen = isOpen) }
    }

    private fun openDrawer() {
        if (uiState.value.isDrawerOpen) {
            return
        }
        uiState.update { it.copy(isDrawerOpen = true) }
        uiEffects.tryEmit(MapUiEffect.OpenDrawer)
    }
}
