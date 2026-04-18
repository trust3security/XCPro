package com.trust3.xcpro

import com.trust3.xcpro.orientation.OrientationClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class MapOrientationManagerFactory @Inject constructor(
    private val orientationDataSourceFactory: OrientationDataSourceFactory,
    private val settingsRepository: MapOrientationSettingsRepository,
    private val clock: OrientationClock
) {
    fun create(scope: CoroutineScope): MapOrientationManager =
        MapOrientationManager(
            scope = scope,
            orientationDataSourceFactory = orientationDataSourceFactory,
            settingsRepository = settingsRepository,
            clock = clock
        )
}
