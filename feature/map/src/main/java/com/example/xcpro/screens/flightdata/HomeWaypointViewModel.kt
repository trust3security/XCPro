package com.example.xcpro.screens.flightdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcpro.common.waypoint.HomeWaypointUseCase
import com.example.xcpro.common.waypoint.WaypointData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeWaypointViewModel @Inject constructor(
    private val useCase: HomeWaypointUseCase
) : ViewModel() {

    val homeWaypoint: StateFlow<WaypointData?> = useCase.observeHomeWaypoint()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = useCase.getHomeWaypoint()
        )

    val homeWaypointName: StateFlow<String?> = homeWaypoint
        .map { it?.name }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = useCase.getHomeWaypoint()?.name
        )

    fun setHomeWaypoint(waypoint: WaypointData?) {
        useCase.setHomeWaypoint(waypoint)
    }
}
