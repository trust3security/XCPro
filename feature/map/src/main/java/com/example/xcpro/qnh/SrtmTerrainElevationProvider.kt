package com.example.xcpro.qnh

import android.content.Context
import com.example.dfcards.dfcards.calculations.SrtmTerrainDatabase
import com.example.xcpro.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Singleton
class SrtmTerrainElevationProvider @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TerrainElevationProvider {
    private val terrainDatabase = SrtmTerrainDatabase(context)

    override suspend fun getElevationMeters(lat: Double, lon: Double): Double? =
        withContext(ioDispatcher) {
            terrainDatabase.getElevation(lat, lon)
        }
}

