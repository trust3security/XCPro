package com.trust3.xcpro.map

import com.trust3.xcpro.common.di.IoDispatcher
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.common.waypoint.WaypointLoader
import com.trust3.xcpro.flightdata.WaypointFilesUseCase
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@ViewModelScoped
class RealWaypointLoader @Inject constructor(
    private val waypointFilesUseCase: WaypointFilesUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : WaypointLoader {
    override suspend fun load(): List<WaypointData> = withContext(ioDispatcher) {
        val (waypointFiles, checks) = waypointFilesUseCase.loadWaypointFiles()
        waypointFilesUseCase.loadAllWaypoints(waypointFiles, checks)
    }
}
