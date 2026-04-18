package com.trust3.xcpro.terrain

import javax.inject.Qualifier

interface TerrainElevationDataSource {
    suspend fun getElevationMeters(lat: Double, lon: Double): Double?
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OfflineTerrainSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnlineTerrainSource
