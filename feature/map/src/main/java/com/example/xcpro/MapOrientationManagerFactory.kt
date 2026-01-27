package com.example.xcpro

import android.content.Context
import com.example.xcpro.orientation.OrientationClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class MapOrientationManagerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orientationDataSourceFactory: OrientationDataSourceFactory,
    private val clock: OrientationClock
) {
    fun create(scope: CoroutineScope): MapOrientationManager =
        MapOrientationManager(
            context = context,
            scope = scope,
            orientationDataSourceFactory = orientationDataSourceFactory,
            clock = clock
        )
}
