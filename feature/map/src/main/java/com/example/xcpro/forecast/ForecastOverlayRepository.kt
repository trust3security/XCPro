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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val overlayState: Flow<ForecastOverlayUiState> = loadingOverlayState()

    fun loadingOverlayState(): Flow<ForecastOverlayUiState> = flow {
        var cachedTileKey: OverlayTileKey? = null
        var cachedTileSpec: ForecastTileSpec? = null
        var tileErrorKey: OverlayTileKey? = null
        var tileErrorMessage: String? = null
        var lastTileAttemptMs: Long = 0L

        var cachedLegendKey: OverlayLegendKey? = null
        var cachedLegend: ForecastLegendSpec? = null
        var legendErrorKey: OverlayLegendKey? = null
        var legendErrorMessage: String? = null
        var lastLegendAttemptMs: Long = 0L

        selectionInputFlow().collect { input ->
            val preferences = input.preferences
            val nowUtcMs = input.nowUtcMs
            val selection = resolveSelection(
                preferences = preferences,
                nowUtcMs = nowUtcMs
            )
            val baseState = ForecastOverlayUiState(
                enabled = preferences.overlayEnabled,
                opacity = preferences.opacity,
                windOverlayScale = preferences.windOverlayScale,
                windDisplayMode = preferences.windDisplayMode,
                selectedRegionCode = normalizeForecastRegionCode(preferences.selectedRegion),
                autoTimeEnabled = preferences.autoTimeEnabled,
                followTimeOffsetMinutes = preferences.followTimeOffsetMinutes,
                parameters = selection.parameters,
                selectedParameterId = selection.selectedParameterId,
                timeSlots = selection.timeSlots,
                selectedTimeUtcMs = selection.selectedTimeSlot.validTimeUtcMs
            )
            if (!preferences.overlayEnabled) {
                cachedTileKey = null
                cachedTileSpec = null
                tileErrorKey = null
                tileErrorMessage = null
                lastTileAttemptMs = 0L
                cachedLegendKey = null
                cachedLegend = null
                legendErrorKey = null
                legendErrorMessage = null
                lastLegendAttemptMs = 0L
                emit(baseState)
                return@collect
            }

            val tileKey = OverlayTileKey(
                regionCode = normalizeForecastRegionCode(preferences.selectedRegion),
                parameterId = selection.selectedParameterId,
                timeUtcMs = selection.selectedTimeSlot.validTimeUtcMs
            )
            val legendKey = OverlayLegendKey(
                regionCode = normalizeForecastRegionCode(preferences.selectedRegion),
                parameterId = selection.selectedParameterId,
                dayBucket = forecastRegionLocalDayBucket(
                    utcMs = selection.selectedTimeSlot.validTimeUtcMs,
                    regionCode = preferences.selectedRegion
                )
            )

            val currentTileSpec = if (cachedTileKey == tileKey) cachedTileSpec else null
            val currentLegend = if (cachedLegendKey == legendKey) cachedLegend else null

            val shouldFetchTile = currentTileSpec == null ||
                shouldRetry(
                    errorKeyMatches = tileErrorKey == tileKey,
                    lastAttemptMs = lastTileAttemptMs,
                    nowUtcMs = nowUtcMs
                )
            val shouldFetchLegend = currentLegend == null ||
                shouldRetry(
                    errorKeyMatches = legendErrorKey == legendKey,
                    lastAttemptMs = lastLegendAttemptMs,
                    nowUtcMs = nowUtcMs
                )

            if (shouldFetchTile || shouldFetchLegend) {
                emit(
                    baseState.copy(
                        isLoading = true,
                        tileSpec = currentTileSpec,
                        legend = currentLegend
                    )
                )
            }

            if (shouldFetchTile) {
                lastTileAttemptMs = nowUtcMs
                try {
                    val tileSpec = tilesPort.getTileSpec(
                        parameterId = selection.selectedParameterId,
                        timeSlot = selection.selectedTimeSlot,
                        regionCode = preferences.selectedRegion
                    )
                    cachedTileKey = tileKey
                    cachedTileSpec = tileSpec
                    if (tileErrorKey == tileKey) {
                        tileErrorKey = null
                        tileErrorMessage = null
                    }
                } catch (t: Throwable) {
                    tileErrorKey = tileKey
                    tileErrorMessage = t.message ?: "Failed to load forecast tiles"
                }
            }

            if (shouldFetchLegend) {
                lastLegendAttemptMs = nowUtcMs
                try {
                    val legend = legendPort.getLegend(
                        parameterId = selection.selectedParameterId,
                        timeSlot = selection.selectedTimeSlot,
                        regionCode = preferences.selectedRegion
                    )
                    cachedLegendKey = legendKey
                    cachedLegend = legend
                    if (legendErrorKey == legendKey) {
                        legendErrorKey = null
                        legendErrorMessage = null
                    }
                } catch (t: Throwable) {
                    legendErrorKey = legendKey
                    legendErrorMessage = t.message ?: "Failed to load forecast legend"
                }
            }

            val tileForState = if (cachedTileKey == tileKey) cachedTileSpec else null
            val legendForState = if (cachedLegendKey == legendKey) cachedLegend else null
            val warningMessage = joinMessages(
                if (legendErrorKey == legendKey) legendErrorMessage else null,
                if (tileErrorKey == tileKey && tileForState != null) tileErrorMessage else null
            )
            val fatalError = if (tileForState == null && tileErrorKey == tileKey) {
                tileErrorMessage ?: "Failed to load forecast tiles"
            } else {
                null
            }

            emit(
                baseState.copy(
                    isLoading = false,
                    tileSpec = tileForState,
                    legend = legendForState,
                    errorMessage = fatalError,
                    warningMessage = warningMessage
                )
            )
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
        val selectedParameterMeta = selection.parameters.firstOrNull { meta ->
            meta.id.value.equals(selection.selectedParameterId.value, ignoreCase = true)
        }
        if (selectedParameterMeta?.supportsPointValue == false) {
            return@withContext ForecastPointQueryResult.Unavailable(
                reason = "${selectedParameterMeta.name} point value is unavailable"
            )
        }
        return@withContext try {
            val pointValue = valuePort.getValue(
                latitude = latitude,
                longitude = longitude,
                parameterId = selection.selectedParameterId,
                timeSlot = selection.selectedTimeSlot,
                regionCode = preferences.selectedRegion
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
            val tickFlow: Flow<Long> = if (preferences.overlayEnabled) {
                autoTimeTickerFlow()
            } else {
                flowOf(clock.nowWallMs())
            }
            tickFlow.map { nowUtcMs ->
                SelectionInput(
                    preferences = preferences,
                    nowUtcMs = nowUtcMs
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
        val followOffsetMs = if (preferences.autoTimeEnabled) {
            preferences.followTimeOffsetMinutes * MINUTE_MS
        } else {
            0L
        }
        val followNowUtcMs = nowUtcMs + followOffsetMs
        val requestedTimeUtcMs = if (preferences.autoTimeEnabled) {
            null
        } else {
            preferences.selectedTimeUtcMs
        }
        val selectedTimeSlot = selectTimeSlot(
            requestedUtcMs = requestedTimeUtcMs,
            timeSlots = timeSlots,
            nowUtcMs = followNowUtcMs
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

    private fun shouldRetry(
        errorKeyMatches: Boolean,
        lastAttemptMs: Long,
        nowUtcMs: Long
    ): Boolean {
        if (!errorKeyMatches) return false
        if (lastAttemptMs <= 0L) return true
        return (nowUtcMs - lastAttemptMs) >= RETRY_AFTER_FAILURE_MS
    }

    private fun joinMessages(vararg messages: String?): String? {
        val filtered = messages
            .mapNotNull { message -> message?.trim()?.takeIf { it.isNotEmpty() } }
            .distinct()
        if (filtered.isEmpty()) return null
        return filtered.joinToString(separator = " | ")
    }

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

    private data class OverlayTileKey(
        val regionCode: String,
        val parameterId: ForecastParameterId,
        val timeUtcMs: Long
    )

    private data class OverlayLegendKey(
        val regionCode: String,
        val parameterId: ForecastParameterId,
        val dayBucket: Long
    )

    private companion object {
        private const val HOUR_MS = 3_600_000L
        private const val MINUTE_MS = 60_000L
        private const val AUTO_TIME_TICK_MS = 60_000L
        private const val RETRY_AFTER_FAILURE_MS = 30_000L
    }
}
