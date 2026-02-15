package com.example.xcpro.forecast

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastOverlayRepository @Inject constructor(
    private val preferencesRepository: ForecastPreferencesRepository,
    private val catalogPort: ForecastCatalogPort,
    private val tilesPort: ForecastTilesPort,
    private val legendPort: ForecastLegendPort,
    private val valuePort: ForecastValuePort,
    private val clock: Clock,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    val overlayState: Flow<ForecastOverlayUiState> = preferencesRepository.preferencesFlow
        .mapLatest { preferences ->
            val selection = resolveSelection(
                preferences = preferences,
                nowUtcMs = clock.nowWallMs()
            )
            val baseState = ForecastOverlayUiState(
                enabled = preferences.overlayEnabled,
                opacity = preferences.opacity,
                autoTimeEnabled = preferences.autoTimeEnabled,
                parameters = selection.parameters,
                selectedParameterId = selection.selectedParameterId,
                timeSlots = selection.timeSlots,
                selectedTimeUtcMs = selection.selectedTimeSlot.validTimeUtcMs
            )
            if (!preferences.overlayEnabled) {
                return@mapLatest baseState
            }
            try {
                val legend = legendPort.getLegend(selection.selectedParameterId)
                val tileSpec = tilesPort.getTileSpec(
                    parameterId = selection.selectedParameterId,
                    timeSlot = selection.selectedTimeSlot
                )
                return@mapLatest baseState.copy(
                    legend = legend,
                    tileSpec = tileSpec
                )
            } catch (t: Throwable) {
                return@mapLatest baseState.copy(
                    errorMessage = t.message ?: "Failed to load forecast overlay"
                )
            }
        }
        .flowOn(dispatcher)

    fun loadingOverlayState(): Flow<ForecastOverlayUiState> = flow {
        var lastFetchKey: OverlayFetchKey? = null
        var lastFetchPayload: OverlayFetchPayload? = null
        var lastFetchError: String? = null

        selectionInputFlow().collect { input ->
            val preferences = input.preferences
            val selection = resolveSelection(
                preferences = preferences,
                nowUtcMs = input.nowUtcMs
            )
            val baseState = ForecastOverlayUiState(
                enabled = preferences.overlayEnabled,
                opacity = preferences.opacity,
                autoTimeEnabled = preferences.autoTimeEnabled,
                parameters = selection.parameters,
                selectedParameterId = selection.selectedParameterId,
                timeSlots = selection.timeSlots,
                selectedTimeUtcMs = selection.selectedTimeSlot.validTimeUtcMs
            )
            if (!preferences.overlayEnabled) {
                lastFetchKey = null
                lastFetchPayload = null
                lastFetchError = null
                emit(baseState)
                return@collect
            }

            val fetchKey = OverlayFetchKey(
                regionCode = preferences.selectedRegion,
                parameterId = selection.selectedParameterId,
                timeUtcMs = selection.selectedTimeSlot.validTimeUtcMs
            )
            if (fetchKey == lastFetchKey) {
                val cachedPayload = lastFetchPayload
                val cachedError = lastFetchError
                when {
                    cachedPayload != null -> {
                        emit(
                            baseState.copy(
                                legend = cachedPayload.legend,
                                tileSpec = cachedPayload.tileSpec
                            )
                        )
                    }

                    cachedError != null -> {
                        emit(baseState.copy(errorMessage = cachedError))
                    }

                    else -> emit(baseState)
                }
                return@collect
            }

            emit(baseState.copy(isLoading = true))
            lastFetchKey = fetchKey
            lastFetchPayload = null
            lastFetchError = null
            try {
                val legend = legendPort.getLegend(selection.selectedParameterId)
                val tileSpec = tilesPort.getTileSpec(
                    parameterId = selection.selectedParameterId,
                    timeSlot = selection.selectedTimeSlot
                )
                lastFetchPayload = OverlayFetchPayload(
                    legend = legend,
                    tileSpec = tileSpec
                )
                emit(
                    baseState.copy(
                        isLoading = false,
                        legend = legend,
                        tileSpec = tileSpec
                    )
                )
            } catch (t: Throwable) {
                lastFetchError = t.message ?: "Failed to load forecast overlay"
                emit(
                    baseState.copy(
                        isLoading = false,
                        errorMessage = lastFetchError
                    )
                )
            }
        }
    }.flowOn(dispatcher)

    suspend fun queryPointValue(
        latitude: Double,
        longitude: Double
    ): ForecastPointQueryResult = withContext(dispatcher) {
        val preferences = preferencesRepository.currentPreferences()
        if (!preferences.overlayEnabled) {
            return@withContext ForecastPointQueryResult.Unavailable(
                reason = "Forecast overlay is disabled"
            )
        }

        val selection = resolveSelection(
            preferences = preferences,
            nowUtcMs = clock.nowWallMs()
        )
        return@withContext try {
            val pointValue = valuePort.getValue(
                latitude = latitude,
                longitude = longitude,
                parameterId = selection.selectedParameterId,
                timeSlot = selection.selectedTimeSlot
            )
            ForecastPointQueryResult.Success(
                latitude = latitude,
                longitude = longitude,
                pointValue = pointValue
            )
        } catch (t: Throwable) {
            ForecastPointQueryResult.Error(
                message = t.message ?: "Failed to query forecast value"
            )
        }
    }

    private fun selectionInputFlow(): Flow<SelectionInput> =
        preferencesRepository.preferencesFlow.flatMapLatest { preferences ->
            if (preferences.autoTimeEnabled) {
                autoTimeTickerFlow().map { nowUtcMs ->
                    SelectionInput(
                        preferences = preferences,
                        nowUtcMs = nowUtcMs
                    )
                }
            } else {
                flowOf(
                    SelectionInput(
                        preferences = preferences,
                        nowUtcMs = clock.nowWallMs()
                    )
                )
            }
        }

    private fun autoTimeTickerFlow(): Flow<Long> = flow {
        emit(clock.nowWallMs())
        while (true) {
            delay(AUTO_TIME_TICK_MS)
            emit(clock.nowWallMs())
        }
    }

    private suspend fun resolveSelection(
        preferences: ForecastPreferences,
        nowUtcMs: Long
    ): ResolvedSelection {
        val parameters = catalogPort.getParameters().ifEmpty { defaultParameters() }
        val selectedParameterId = selectParameterId(
            requested = preferences.selectedParameterId,
            parameters = parameters
        )
        val timeSlots = catalogPort.getTimeSlots(
            nowUtcMs = nowUtcMs,
            regionCode = preferences.selectedRegion
        ).ifEmpty {
            listOf(ForecastTimeSlot(roundDownToHour(nowUtcMs)))
        }
        val requestedTimeUtcMs = if (preferences.autoTimeEnabled) {
            null
        } else {
            preferences.selectedTimeUtcMs
        }
        val selectedTimeSlot = selectTimeSlot(
            requestedUtcMs = requestedTimeUtcMs,
            timeSlots = timeSlots,
            nowUtcMs = nowUtcMs
        )
        return ResolvedSelection(
            parameters = parameters,
            selectedParameterId = selectedParameterId,
            timeSlots = timeSlots,
            selectedTimeSlot = selectedTimeSlot
        )
    }

    private fun selectParameterId(
        requested: ForecastParameterId,
        parameters: List<ForecastParameterMeta>
    ): ForecastParameterId {
        val matched = parameters.firstOrNull { meta ->
            meta.id.value.equals(requested.value, ignoreCase = true)
        }?.id
        if (matched != null) return matched
        return parameters.firstOrNull()?.id ?: DEFAULT_FORECAST_PARAMETER_ID
    }

    private fun selectTimeSlot(
        requestedUtcMs: Long?,
        timeSlots: List<ForecastTimeSlot>,
        nowUtcMs: Long
    ): ForecastTimeSlot {
        if (timeSlots.isEmpty()) {
            return ForecastTimeSlot(roundDownToHour(nowUtcMs))
        }
        val targetUtcMs = requestedUtcMs ?: nowUtcMs
        if (requestedUtcMs == null) {
            return timeSlots.lastOrNull { it.validTimeUtcMs <= nowUtcMs } ?: timeSlots.first()
        }
        return timeSlots.minByOrNull { slot ->
            abs(slot.validTimeUtcMs - targetUtcMs)
        } ?: timeSlots.first()
    }

    private fun defaultParameters(): List<ForecastParameterMeta> = listOf(
        ForecastParameterMeta(
            id = DEFAULT_FORECAST_PARAMETER_ID,
            name = "Thermal",
            category = "Thermal",
            unitLabel = "m/s"
        )
    )

    private fun roundDownToHour(wallUtcMs: Long): Long = (wallUtcMs / HOUR_MS) * HOUR_MS

    private data class ResolvedSelection(
        val parameters: List<ForecastParameterMeta>,
        val selectedParameterId: ForecastParameterId,
        val timeSlots: List<ForecastTimeSlot>,
        val selectedTimeSlot: ForecastTimeSlot
    )

    private data class SelectionInput(
        val preferences: ForecastPreferences,
        val nowUtcMs: Long
    )

    private data class OverlayFetchKey(
        val regionCode: String,
        val parameterId: ForecastParameterId,
        val timeUtcMs: Long
    )

    private data class OverlayFetchPayload(
        val legend: ForecastLegendSpec,
        val tileSpec: ForecastTileSpec
    )

    private companion object {
        private const val HOUR_MS = 3_600_000L
        private const val AUTO_TIME_TICK_MS = 60_000L
    }
}
