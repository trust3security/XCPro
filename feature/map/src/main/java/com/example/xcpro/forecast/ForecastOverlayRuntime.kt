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


@OptIn(ExperimentalCoroutinesApi::class)
internal class ForecastOverlayRuntime(
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
        var cachedPrimaryTileKey: OverlayTileKey? = null
        var cachedPrimaryTileSpec: ForecastTileSpec? = null
        var primaryTileErrorKey: OverlayTileKey? = null
        var primaryTileErrorMessage: String? = null
        var lastPrimaryTileAttemptMs: Long = 0L

        var cachedPrimaryLegendKey: OverlayLegendKey? = null
        var cachedPrimaryLegend: ForecastLegendSpec? = null
        var primaryLegendErrorKey: OverlayLegendKey? = null
        var primaryLegendErrorMessage: String? = null
        var lastPrimaryLegendAttemptMs: Long = 0L

        var cachedWindTileKey: OverlayTileKey? = null
        var cachedWindTileSpec: ForecastTileSpec? = null
        var windTileErrorKey: OverlayTileKey? = null
        var windTileErrorMessage: String? = null
        var lastWindTileAttemptMs: Long = 0L

        var cachedWindLegendKey: OverlayLegendKey? = null
        var cachedWindLegend: ForecastLegendSpec? = null
        var windLegendErrorKey: OverlayLegendKey? = null
        var windLegendErrorMessage: String? = null
        var lastWindLegendAttemptMs: Long = 0L

        selectionInputFlow().collect { input ->
            val preferences = input.preferences
            val nowUtcMs = input.nowUtcMs
            val selection = if (preferences.overlayEnabled || preferences.windOverlayEnabled) {
                resolveSelection(
                    preferences = preferences,
                    nowUtcMs = nowUtcMs
                )
            } else {
                null
            }
            val normalizedRegionCode = normalizeForecastRegionCode(preferences.selectedRegion)
            val primaryEnabled = preferences.overlayEnabled && selection != null
            val windEnabled = preferences.windOverlayEnabled &&
                selection?.windParameters?.isNotEmpty() == true
            val anyOverlayEnabled = primaryEnabled || windEnabled
            val selectedTimeForState = selection?.selectedTimeSlot?.validTimeUtcMs
                ?: if (preferences.skySightSatelliteOverlayEnabled) nowUtcMs else null

            val baseState = ForecastOverlayUiState(
                enabled = primaryEnabled,
                opacity = preferences.opacity,
                windOverlayScale = preferences.windOverlayScale,
                windOverlayEnabled = windEnabled,
                windDisplayMode = preferences.windDisplayMode,
                skySightSatelliteOverlayEnabled = preferences.skySightSatelliteOverlayEnabled,
                skySightSatelliteImageryEnabled = preferences.skySightSatelliteImageryEnabled,
                skySightSatelliteRadarEnabled = preferences.skySightSatelliteRadarEnabled,
                skySightSatelliteLightningEnabled = preferences.skySightSatelliteLightningEnabled,
                skySightSatelliteAnimateEnabled = preferences.skySightSatelliteAnimateEnabled,
                skySightSatelliteHistoryFrames = preferences.skySightSatelliteHistoryFrames,
                selectedRegionCode = normalizedRegionCode,
                autoTimeEnabled = preferences.autoTimeEnabled,
                followTimeOffsetMinutes = preferences.followTimeOffsetMinutes,
                primaryParameters = selection?.primaryParameters ?: emptyList(),
                selectedPrimaryParameterId = selection?.selectedPrimaryParameterId
                    ?: preferences.selectedPrimaryParameterId,
                windParameters = selection?.windParameters ?: emptyList(),
                selectedWindParameterId = selection?.selectedWindParameterId
                    ?: preferences.selectedWindParameterId,
                timeSlots = selection?.timeSlots ?: emptyList(),
                selectedTimeUtcMs = selectedTimeForState
            )
            if (!primaryEnabled) {
                cachedPrimaryTileKey = null
                cachedPrimaryTileSpec = null
                primaryTileErrorKey = null
                primaryTileErrorMessage = null
                lastPrimaryTileAttemptMs = 0L

                cachedPrimaryLegendKey = null
                cachedPrimaryLegend = null
                primaryLegendErrorKey = null
                primaryLegendErrorMessage = null
                lastPrimaryLegendAttemptMs = 0L
            }

            if (!anyOverlayEnabled) {
                cachedWindTileKey = null
                cachedWindTileSpec = null
                windTileErrorKey = null
                windTileErrorMessage = null
                lastWindTileAttemptMs = 0L

                cachedWindLegendKey = null
                cachedWindLegend = null
                windLegendErrorKey = null
                windLegendErrorMessage = null
                lastWindLegendAttemptMs = 0L

                emit(baseState)
                return@collect
            }

            val resolvedSelection = selection ?: run {
                emit(baseState)
                return@collect
            }

            val selectedTimeUtcMs = resolvedSelection.selectedTimeSlot.validTimeUtcMs
            val dayBucket = forecastRegionLocalDayBucket(
                utcMs = selectedTimeUtcMs,
                regionCode = preferences.selectedRegion
            )

            val primaryTileKey = OverlayTileKey(
                regionCode = normalizedRegionCode,
                parameterId = resolvedSelection.selectedPrimaryParameterId,
                timeUtcMs = selectedTimeUtcMs
            )
            val primaryLegendKey = OverlayLegendKey(
                regionCode = normalizedRegionCode,
                parameterId = resolvedSelection.selectedPrimaryParameterId,
                dayBucket = dayBucket
            )

            val windTileKey = if (windEnabled) {
                OverlayTileKey(
                    regionCode = normalizedRegionCode,
                    parameterId = resolvedSelection.selectedWindParameterId,
                    timeUtcMs = selectedTimeUtcMs
                )
            } else {
                null
            }
            val windLegendKey = if (windEnabled) {
                OverlayLegendKey(
                    regionCode = normalizedRegionCode,
                    parameterId = resolvedSelection.selectedWindParameterId,
                    dayBucket = dayBucket
                )
            } else {
                null
            }

            if (!windEnabled) {
                cachedWindTileKey = null
                cachedWindTileSpec = null
                windTileErrorKey = null
                windTileErrorMessage = null
                lastWindTileAttemptMs = 0L

                cachedWindLegendKey = null
                cachedWindLegend = null
                windLegendErrorKey = null
                windLegendErrorMessage = null
                lastWindLegendAttemptMs = 0L
            }

            val currentPrimaryTile = if (cachedPrimaryTileKey == primaryTileKey) cachedPrimaryTileSpec else null
            val currentPrimaryLegend = if (cachedPrimaryLegendKey == primaryLegendKey) cachedPrimaryLegend else null
            val currentWindTile = if (windTileKey != null && cachedWindTileKey == windTileKey) {
                cachedWindTileSpec
            } else {
                null
            }
            val currentWindLegend = if (windLegendKey != null && cachedWindLegendKey == windLegendKey) {
                cachedWindLegend
            } else {
                null
            }

            val shouldFetchPrimaryTile = primaryEnabled && (
                currentPrimaryTile == null ||
                    shouldRetry(
                        errorKeyMatches = primaryTileErrorKey == primaryTileKey,
                        lastAttemptMs = lastPrimaryTileAttemptMs,
                        nowUtcMs = nowUtcMs
                    )
                )
            val shouldFetchPrimaryLegend = primaryEnabled && (
                currentPrimaryLegend == null ||
                    shouldRetry(
                        errorKeyMatches = primaryLegendErrorKey == primaryLegendKey,
                        lastAttemptMs = lastPrimaryLegendAttemptMs,
                        nowUtcMs = nowUtcMs
                    )
                )
            val shouldFetchWindTile = windTileKey != null && (
                currentWindTile == null ||
                    shouldRetry(
                        errorKeyMatches = windTileErrorKey == windTileKey,
                        lastAttemptMs = lastWindTileAttemptMs,
                        nowUtcMs = nowUtcMs
                    )
                )
            val shouldFetchWindLegend = windLegendKey != null && (
                currentWindLegend == null ||
                    shouldRetry(
                        errorKeyMatches = windLegendErrorKey == windLegendKey,
                        lastAttemptMs = lastWindLegendAttemptMs,
                        nowUtcMs = nowUtcMs
                    )
                )

            if (
                shouldFetchPrimaryTile ||
                shouldFetchPrimaryLegend ||
                shouldFetchWindTile ||
                shouldFetchWindLegend
            ) {
                emit(
                    baseState.copy(
                        isLoading = true,
                        primaryTileSpec = if (primaryEnabled) currentPrimaryTile else null,
                        primaryLegend = if (primaryEnabled) currentPrimaryLegend else null,
                        windTileSpec = currentWindTile,
                        windLegend = currentWindLegend
                    )
                )
            }

            if (shouldFetchPrimaryTile) {
                lastPrimaryTileAttemptMs = nowUtcMs
                try {
                    val tileSpec = tilesPort.getTileSpec(
                        parameterId = resolvedSelection.selectedPrimaryParameterId,
                        timeSlot = resolvedSelection.selectedTimeSlot,
                        regionCode = preferences.selectedRegion
                    )
                    cachedPrimaryTileKey = primaryTileKey
                    cachedPrimaryTileSpec = tileSpec
                    if (primaryTileErrorKey == primaryTileKey) {
                        primaryTileErrorKey = null
                        primaryTileErrorMessage = null
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    primaryTileErrorKey = primaryTileKey
                    primaryTileErrorMessage = t.message ?: "Failed to load forecast tiles"
                }
            }

            if (shouldFetchPrimaryLegend) {
                lastPrimaryLegendAttemptMs = nowUtcMs
                try {
                    val legend = legendPort.getLegend(
                        parameterId = resolvedSelection.selectedPrimaryParameterId,
                        timeSlot = resolvedSelection.selectedTimeSlot,
                        regionCode = preferences.selectedRegion
                    )
                    cachedPrimaryLegendKey = primaryLegendKey
                    cachedPrimaryLegend = legend
                    if (primaryLegendErrorKey == primaryLegendKey) {
                        primaryLegendErrorKey = null
                        primaryLegendErrorMessage = null
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    primaryLegendErrorKey = primaryLegendKey
                    primaryLegendErrorMessage = t.message ?: "Failed to load forecast legend"
                }
            }

            if (shouldFetchWindTile) {
                val requiredWindTileKey = requireNotNull(windTileKey)
                lastWindTileAttemptMs = nowUtcMs
                try {
                    val tileSpec = tilesPort.getTileSpec(
                        parameterId = resolvedSelection.selectedWindParameterId,
                        timeSlot = resolvedSelection.selectedTimeSlot,
                        regionCode = preferences.selectedRegion
                    )
                    cachedWindTileKey = requiredWindTileKey
                    cachedWindTileSpec = tileSpec
                    if (windTileErrorKey == requiredWindTileKey) {
                        windTileErrorKey = null
                        windTileErrorMessage = null
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    windTileErrorKey = requiredWindTileKey
                    windTileErrorMessage = t.message ?: "Failed to load wind overlay tiles"
                }
            }

            if (shouldFetchWindLegend) {
                val requiredWindLegendKey = requireNotNull(windLegendKey)
                lastWindLegendAttemptMs = nowUtcMs
                try {
                    val legend = legendPort.getLegend(
                        parameterId = resolvedSelection.selectedWindParameterId,
                        timeSlot = resolvedSelection.selectedTimeSlot,
                        regionCode = preferences.selectedRegion
                    )
                    cachedWindLegendKey = requiredWindLegendKey
                    cachedWindLegend = legend
                    if (windLegendErrorKey == requiredWindLegendKey) {
                        windLegendErrorKey = null
                        windLegendErrorMessage = null
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    windLegendErrorKey = requiredWindLegendKey
                    windLegendErrorMessage = t.message ?: "Failed to load wind overlay legend"
                }
            }

            val primaryTileForState = if (cachedPrimaryTileKey == primaryTileKey) cachedPrimaryTileSpec else null
            val primaryLegendForState = if (cachedPrimaryLegendKey == primaryLegendKey) {
                cachedPrimaryLegend
            } else {
                null
            }
            val windTileForState = if (windTileKey != null && cachedWindTileKey == windTileKey) {
                cachedWindTileSpec
            } else {
                null
            }
            val windLegendForState = if (windLegendKey != null && cachedWindLegendKey == windLegendKey) {
                cachedWindLegend
            } else {
                null
            }

            val warningMessage = joinMessages(
                if (primaryEnabled && primaryLegendErrorKey == primaryLegendKey) {
                    primaryLegendErrorMessage
                } else {
                    null
                },
                if (primaryEnabled && primaryTileErrorKey == primaryTileKey && primaryTileForState != null) {
                    primaryTileErrorMessage
                } else {
                    null
                },
                if (windLegendKey != null && windLegendErrorKey == windLegendKey) windLegendErrorMessage else null,
                if (
                    windTileKey != null &&
                    windTileErrorKey == windTileKey &&
                    windTileForState != null
                ) {
                    windTileErrorMessage
                } else {
                    null
                }
            )
            val primaryFatalError = if (
                primaryEnabled &&
                primaryTileForState == null &&
                primaryTileErrorKey == primaryTileKey
            ) {
                primaryTileErrorMessage ?: "Failed to load forecast tiles"
            } else {
                null
            }
            val windFatalError = if (
                windTileKey != null &&
                windTileForState == null &&
                windTileErrorKey == windTileKey
            ) {
                windTileErrorMessage ?: "Failed to load wind overlay tiles"
            } else {
                null
            }
            val fatalError = joinMessages(primaryFatalError, windFatalError)
            val warningWithoutFatalDupes = subtractMessages(
                source = warningMessage,
                toRemove = fatalError
            )

            emit(
                baseState.copy(
                    isLoading = false,
                    primaryTileSpec = if (primaryEnabled) primaryTileForState else null,
                    primaryLegend = if (primaryEnabled) primaryLegendForState else null,
                    windTileSpec = if (windEnabled) windTileForState else null,
                    windLegend = if (windEnabled) windLegendForState else null,
                    errorMessage = fatalError,
                    warningMessage = warningWithoutFatalDupes
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
        val selectedParameterMeta = selection.primaryParameters.firstOrNull { meta ->
            meta.id.value.equals(selection.selectedPrimaryParameterId.value, ignoreCase = true)
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
                parameterId = selection.selectedPrimaryParameterId,
                timeSlot = selection.selectedTimeSlot,
                regionCode = preferences.selectedRegion
            )
            ForecastPointQueryResult.Success(
                latitude = latitude,
                longitude = longitude,
                pointValue = pointValue
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ForecastPointQueryResult.Error(
                message = t.message ?: "Failed to query forecast value"
            )
        }
    }

    private fun selectionInputFlow(): Flow<SelectionInput> =
        preferencesRepository.preferencesFlow.flatMapLatest { preferences ->
            val tickFlow: Flow<Long> = if (
                preferences.overlayEnabled ||
                    preferences.windOverlayEnabled ||
                    preferences.skySightSatelliteOverlayEnabled
            ) {
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
        val parameters = catalogPort.getParameters().ifEmpty { defaultPrimaryParameters() }
        val primaryParameters = parameters.filterNot(::isWindMeta)
            .ifEmpty { parameters }
        val windParameters = parameters.filter(::isWindMeta)

        val selectedPrimaryParameterId = selectParameterId(
            requested = preferences.selectedPrimaryParameterId,
            parameters = primaryParameters,
            fallback = DEFAULT_FORECAST_PARAMETER_ID
        )
        val selectedWindParameterId = selectParameterId(
            requested = preferences.selectedWindParameterId,
            parameters = windParameters,
            fallback = DEFAULT_FORECAST_WIND_PARAMETER_ID
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
            primaryParameters = primaryParameters,
            selectedPrimaryParameterId = selectedPrimaryParameterId,
            windParameters = windParameters,
            selectedWindParameterId = selectedWindParameterId,
            timeSlots = timeSlots,
            selectedTimeSlot = selectedTimeSlot
        )
    }

    private fun selectParameterId(
        requested: ForecastParameterId,
        parameters: List<ForecastParameterMeta>,
        fallback: ForecastParameterId
    ): ForecastParameterId {
        val matched = parameters.firstOrNull { meta ->
            meta.id.value.equals(requested.value, ignoreCase = true)
        }?.id
        if (matched != null) return matched
        return parameters.firstOrNull()?.id ?: fallback
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

    private fun defaultPrimaryParameters(): List<ForecastParameterMeta> = listOf(
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

    private fun subtractMessages(source: String?, toRemove: String?): String? {
        val sourceMessages = splitMessages(source)
        if (sourceMessages.isEmpty()) return null
        val removedMessages = splitMessages(toRemove)
        if (removedMessages.isEmpty()) return sourceMessages.joinToString(separator = " | ")
        val filtered = sourceMessages.filter { sourceMessage ->
            removedMessages.none { removedMessage ->
                sourceMessage.equals(removedMessage, ignoreCase = true)
            }
        }
        if (filtered.isEmpty()) return null
        return filtered.joinToString(separator = " | ")
    }

    private fun splitMessages(message: String?): List<String> = message
        ?.split("|")
        ?.mapNotNull { part -> part.trim().takeIf { it.isNotEmpty() } }
        ?: emptyList()

    private fun isWindMeta(meta: ForecastParameterMeta): Boolean =
        isForecastWindCategory(meta.category) || isForecastWindParameterId(meta.id)

    private fun roundDownToHour(wallUtcMs: Long): Long = (wallUtcMs / HOUR_MS) * HOUR_MS

    private data class ResolvedSelection(
        val primaryParameters: List<ForecastParameterMeta>,
        val selectedPrimaryParameterId: ForecastParameterId,
        val windParameters: List<ForecastParameterMeta>,
        val selectedWindParameterId: ForecastParameterId,
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
