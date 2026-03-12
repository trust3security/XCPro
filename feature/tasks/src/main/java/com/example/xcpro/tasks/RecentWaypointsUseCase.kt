package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class RecentWaypointsUseCase @Inject constructor(
    private val repository: RecentWaypointsRepository
) {
    fun getRecentWaypoints(): List<SearchWaypoint> = repository.getRecentWaypoints()

    fun observeRecentWaypoints(): Flow<List<SearchWaypoint>> =
        repository.observeRecentWaypoints()

    fun setRecentWaypoints(waypoints: List<SearchWaypoint>) {
        repository.setRecentWaypoints(waypoints)
    }
}
