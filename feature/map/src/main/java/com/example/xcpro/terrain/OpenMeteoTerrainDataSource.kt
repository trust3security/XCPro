package com.example.xcpro.terrain

import android.content.Context
import com.example.dfcards.dfcards.calculations.OpenMeteoElevationApi
import com.example.xcpro.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
class OpenMeteoTerrainDataSource @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : TerrainElevationDataSource {

    private val api = OpenMeteoElevationApi(
        context = context,
        ioDispatcher = ioDispatcher
    )

    override suspend fun getElevationMeters(lat: Double, lon: Double): Double? =
        api.fetchElevation(lat, lon)
}
