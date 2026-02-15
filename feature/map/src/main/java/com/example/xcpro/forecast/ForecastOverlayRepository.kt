package com.example.xcpro.forecast

import com.example.xcpro.common.di.IoDispatcher
import com.example.xcpro.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

@Singleton
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
            val selection = resolveSelection(preferences)
            val baseState = ForecastOverlayUiState(
                enabled = preferences.overlayEnabled,
                opacity = preferences.opacity,
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
        preferencesRepository.preferencesFlow.collect { preferences ->
            val selection = resolveSelection(preferences)
            val baseState = ForecastOverlayUiState(
                enabled = preferences.overlayEnabled,
                opacity = preferences.opacity,
                parameters = selection.parameters,
                selectedParameterId = selection.selectedParameterId,
                timeSlots = selection.timeSlots,
                selectedTimeUtcMs = selection.selectedTimeSlot.validTimeUtcMs
            )
            if (!preferences.overlayEnabled) {
                emit(baseState)
                return@collect
            }

            emit(baseState.copy(isLoading = true))
            try {
                val legend = legendPort.getLegend(selection.selectedParameterId)
                val tileSpec = tilesPort.getTileSpec(
                    parameterId = selection.selectedParameterId,
                    timeSlot = selection.selectedTimeSlot
                )
                emit(
                    baseState.copy(
                        isLoading = false,
                        legend = legend,
                        tileSpec = tileSpec
                    )
                )
            } catch (t: Throwable) {
                emit(
                    baseState.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Failed to load forecast overlay"
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

        val selection = resolveSelection(preferences)
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

    private suspend fun resolveSelection(preferences: ForecastPreferences): ResolvedSelection {
        val parameters = catalogPort.getParameters().ifEmpty { defaultParameters() }
        val selectedParameterId = selectParameterId(
            requested = preferences.selectedParameterId,
            parameters = parameters
        )
        val nowUtcMs = clock.nowWallMs()
        val timeSlots = catalogPort.getTimeSlots(
            nowUtcMs = nowUtcMs,
            regionCode = preferences.selectedRegion
        ).ifEmpty {
            listOf(ForecastTimeSlot(roundDownToHour(nowUtcMs)))
        }
        val selectedTimeSlot = selectTimeSlot(
            requestedUtcMs = preferences.selectedTimeUtcMs,
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
        if (parameters.any { it.id == requested }) return requested
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
            return timeSlots.firstOrNull { it.validTimeUtcMs >= nowUtcMs } ?: timeSlots.last()
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

    private companion object {
        private const val HOUR_MS = 3_600_000L
    }
}
