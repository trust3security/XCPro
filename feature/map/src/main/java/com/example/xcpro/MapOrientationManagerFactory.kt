package com.example.xcpro

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class MapOrientationManagerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val orientationDataSourceFactory: OrientationDataSourceFactory
) {
    fun create(scope: CoroutineScope): MapOrientationManager =
        MapOrientationManager(
            context = context,
            scope = scope,
            orientationDataSourceFactory = orientationDataSourceFactory
        )
}
