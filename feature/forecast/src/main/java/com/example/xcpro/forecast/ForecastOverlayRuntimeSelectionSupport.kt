package com.example.xcpro.forecast

import com.example.xcpro.core.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
internal fun selectionInputFlow(
    preferencesFlow: Flow<ForecastPreferences>,
    clock: Clock
): Flow<SelectionInput> = preferencesFlow.flatMapLatest { preferences ->
    val tickFlow: Flow<Long> = if (
        preferences.overlayEnabled ||
            preferences.windOverlayEnabled ||
            preferences.skySightSatelliteOverlayEnabled
    ) {
        autoTimeTickerFlow(clock)
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

internal fun autoTimeTickerFlow(clock: Clock): Flow<Long> = flow {
    emit(clock.nowWallMs())
    while (true) {
        delay(AUTO_TIME_TICK_MS)
        emit(clock.nowWallMs())
    }
}

internal fun selectParameterId(
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

internal fun selectTimeSlot(
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

internal fun defaultPrimaryParameters(): List<ForecastParameterMeta> = listOf(
    ForecastParameterMeta(
        id = DEFAULT_FORECAST_PARAMETER_ID,
        name = "Thermal",
        category = "Thermal",
        unitLabel = "m/s"
    )
)

internal fun shouldRetry(
    errorKeyMatches: Boolean,
    lastAttemptMs: Long,
    nowUtcMs: Long
): Boolean {
    if (!errorKeyMatches) return false
    if (lastAttemptMs <= 0L) return true
    return (nowUtcMs - lastAttemptMs) >= RETRY_AFTER_FAILURE_MS
}

internal fun joinMessages(vararg messages: String?): String? {
    val filtered = messages
        .mapNotNull { message -> message?.trim()?.takeIf { it.isNotEmpty() } }
        .distinct()
    if (filtered.isEmpty()) return null
    return filtered.joinToString(separator = " | ")
}

internal fun subtractMessages(source: String?, toRemove: String?): String? {
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

internal fun splitMessages(message: String?): List<String> = message
    ?.split("|")
    ?.mapNotNull { part -> part.trim().takeIf { it.isNotEmpty() } }
    ?: emptyList()

internal fun isWindMeta(meta: ForecastParameterMeta): Boolean =
    isForecastWindCategory(meta.category) || isForecastWindParameterId(meta.id)

internal fun roundDownToHour(wallUtcMs: Long): Long = (wallUtcMs / HOUR_MS) * HOUR_MS

internal data class ResolvedSelection(
    val primaryParameters: List<ForecastParameterMeta>,
    val selectedPrimaryParameterId: ForecastParameterId,
    val windParameters: List<ForecastParameterMeta>,
    val selectedWindParameterId: ForecastParameterId,
    val timeSlots: List<ForecastTimeSlot>,
    val selectedTimeSlot: ForecastTimeSlot
)

internal data class SelectionInput(
    val preferences: ForecastPreferences,
    val nowUtcMs: Long
)

internal data class OverlayTileKey(
    val regionCode: String,
    val parameterId: ForecastParameterId,
    val timeUtcMs: Long
)

internal data class OverlayLegendKey(
    val regionCode: String,
    val parameterId: ForecastParameterId,
    val dayBucket: Long
)

private const val HOUR_MS = 3_600_000L
private const val RETRY_AFTER_FAILURE_MS = 30_000L
private const val AUTO_TIME_TICK_MS = 60_000L
