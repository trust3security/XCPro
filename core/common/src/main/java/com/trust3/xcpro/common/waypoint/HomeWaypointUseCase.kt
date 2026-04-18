package com.trust3.xcpro.common.waypoint

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class HomeWaypointUseCase @Inject constructor(
    private val repository: HomeWaypointRepository
) {
    fun getHomeWaypoint(): WaypointData? = repository.getHomeWaypoint()

    fun observeHomeWaypoint(): Flow<WaypointData?> = repository.observeHomeWaypoint()

    fun observeHomeWaypointName(): Flow<String?> = repository.observeHomeWaypointName()

    fun setHomeWaypoint(waypoint: WaypointData?) {
        repository.setHomeWaypoint(waypoint)
    }
}
