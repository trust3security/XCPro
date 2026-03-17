package com.example.xcpro.terrain

import android.content.Context
import com.example.dfcards.dfcards.calculations.SrtmTerrainDatabase
import com.example.xcpro.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
class SrtmTerrainDataSource @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : TerrainElevationDataSource {

    private val terrainDatabase = SrtmTerrainDatabase(
        context = context,
        ioDispatcher = ioDispatcher
    )

    override suspend fun getElevationMeters(lat: Double, lon: Double): Double? =
        terrainDatabase.getElevation(lat, lon)
}
