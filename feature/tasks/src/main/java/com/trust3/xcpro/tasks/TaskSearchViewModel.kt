package com.trust3.xcpro.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trust3.xcpro.common.waypoint.SearchWaypoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TaskSearchViewModel @Inject constructor(
    private val useCase: RecentWaypointsUseCase
) : ViewModel() {

    val recentWaypoints: StateFlow<List<SearchWaypoint>> = useCase.observeRecentWaypoints()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = useCase.getRecentWaypoints()
        )

    fun recordRecent(waypoint: SearchWaypoint, maxItems: Int = 5) {
        val updated = (listOf(waypoint) + recentWaypoints.value.filter { it.id != waypoint.id })
            .take(maxItems)
        useCase.setRecentWaypoints(updated)
    }
}
