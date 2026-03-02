package com.example.xcpro.forecast

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext


@Singleton
class ForecastOverlayRepository @Inject constructor(
    preferencesRepository: ForecastPreferencesRepository,
    catalogPort: ForecastCatalogPort,
    tilesPort: ForecastTilesPort,
    legendPort: ForecastLegendPort,
    valuePort: ForecastValuePort,
    clock: Clock,
    @IoDispatcher dispatcher: CoroutineDispatcher
) {
    private val runtime = ForecastOverlayRuntime(
        preferencesRepository = preferencesRepository,
        catalogPort = catalogPort,
        tilesPort = tilesPort,
        legendPort = legendPort,
        valuePort = valuePort,
        clock = clock,
        dispatcher = dispatcher
    )

    val overlayState: Flow<ForecastOverlayUiState>
        get() = runtime.overlayState

    fun loadingOverlayState(): Flow<ForecastOverlayUiState> = runtime.loadingOverlayState()

    suspend fun queryPointValue(
        latitude: Double,
        longitude: Double
    ): ForecastPointQueryResult = runtime.queryPointValue(
        latitude = latitude,
        longitude = longitude
    )
}

private fun isWindParameterMeta(meta: ForecastParameterMeta): Boolean =
    isForecastWindCategory(meta.category) || isForecastWindParameterId(meta.id)
