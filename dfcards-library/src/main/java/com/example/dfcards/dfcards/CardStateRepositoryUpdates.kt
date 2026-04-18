package com.example.dfcards.dfcards

import com.example.dfcards.CardLibrary
import com.example.dfcards.CardStrings
import com.example.dfcards.CardTimeFormatter
import com.example.dfcards.dfcards.CardState
import com.example.dfcards.dfcards.FlightData
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.core.flight.RealTimeFlightData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class UpdateTier {
    FAST,
    PRIMARY,
    BACKGROUND
}

// Match infobox palette: bright red/green for sink/lift.
private const val NEGATIVE_VARIO_COLOR = 0xFFFF0000L
private const val POSITIVE_VARIO_COLOR = 0xFF0D8A16L // darker green for readability
internal fun CardStateRepository.updateCardsWithLiveData(
    liveData: RealTimeFlightData,
    forceVisible: Boolean = false
) {
    lastRealTimeData = liveData

    if (cardStateFlowsMap.isEmpty()) {
    }

    if (isManuallyPositioning && !forceVisible) {
        return
    }

    val currentTime = clock.nowMonoMs()
    val fastDue = currentTime - lastFastUpdateTime >= fastUpdateIntervalMs
    val primaryDue = currentTime - lastPrimaryUpdateTime >= primaryUpdateIntervalMs
    val backgroundDue = currentTime - lastBackgroundUpdateTime >= backgroundUpdateIntervalMs

    val visibleIds = selectedCardIds.value
    val effectivePrimaryDue = primaryDue || forceVisible
    val effectiveFastDue = fastDue || forceVisible

    if (!effectiveFastDue && !effectivePrimaryDue && !backgroundDue) {
        return
    }

    if (fastDue || forceVisible) lastFastUpdateTime = currentTime
    if (primaryDue || forceVisible) lastPrimaryUpdateTime = currentTime
    if (backgroundDue) lastBackgroundUpdateTime = currentTime

    cardStateFlowsMap.forEach { (cardId, stateFlow) ->
        if (cardId == "local_time") return@forEach

        val tier = when {
            cardId in FAST_UPDATE_CARD_IDS -> UpdateTier.FAST
            cardId in visibleIds -> UpdateTier.PRIMARY
            else -> UpdateTier.BACKGROUND
        }

        val shouldUpdate = when (tier) {
            UpdateTier.FAST -> effectiveFastDue
            UpdateTier.PRIMARY -> effectivePrimaryDue && cardId in visibleIds
            UpdateTier.BACKGROUND -> backgroundDue
        }

        if (!shouldUpdate) return@forEach

        val currentState = stateFlow.value
        val updatedFlightData = mapRealDataToCard(currentState.flightData, liveData)

        if (updatedFlightData != currentState.flightData) {
            stateFlow.value = currentState.copy(flightData = updatedFlightData)
        }
    }
}

internal fun CardStateRepository.startIndependentClockTimer() {
    clockTimerJob?.cancel()

    clockTimerJob = scope.launch {
        while (true) {
            cardStateFlowsMap["local_time"]?.let { timeCardFlow ->
                val currentState = timeCardFlow.value
                val currentTime = clock.nowWallMs()
                val (time, seconds) = cardTimeFormatter.formatLocalTime(currentTime)

                val updatedFlightData = currentState.flightData.copy(
                    primaryValue = time,
                    secondaryValue = seconds
                )

                if (updatedFlightData != currentState.flightData) {
                    timeCardFlow.value = currentState.copy(flightData = updatedFlightData)
                }
            }

            delay(1000L)
        }
    }
}

internal fun CardStateRepository.stopIndependentClockTimer() {
    clockTimerJob?.cancel()
    clockTimerJob = null
}

private fun CardStateRepository.mapRealDataToCard(
    currentFlightData: FlightData,
    realData: RealTimeFlightData
): FlightData {
    val (primaryValue, secondaryValue) = CardLibrary.mapLiveDataToCard(
        cardId = currentFlightData.id,
        liveData = realData,
        units = unitsPreferences,
        strings = cardStrings,
        timeFormatter = cardTimeFormatter
    )
    val (primaryNumber, primaryUnit) = splitPrimaryValue(primaryValue)
    val primaryColor = highlightColorFor(currentFlightData.id, realData)

    return currentFlightData.copy(
        primaryValue = primaryValue,
        secondaryValue = secondaryValue,
        primaryValueNumber = primaryNumber,
        primaryValueUnit = primaryUnit,
        primaryColorOverride = primaryColor
    )
}

internal fun CardStateRepository.updateUnitsPreferences(preferences: UnitsPreferences) {
    if (unitsPreferences == preferences) {
        return
    }
    unitsPreferences = preferences
    val liveData = lastRealTimeData ?: return

    cardStateFlowsMap.forEach { (cardId, stateFlow) ->
        if (cardId == "local_time") return@forEach
        val currentState = stateFlow.value
        val remappedFlightData = mapRealDataToCard(currentState.flightData, liveData)
        if (remappedFlightData != currentState.flightData) {
            stateFlow.value = currentState.copy(flightData = remappedFlightData)
        }
    }
}

internal fun CardStateRepository.updateCardStrings(strings: CardStrings) {
    if (cardStrings === strings) {
        return
    }
    cardStrings = strings
    val liveData = lastRealTimeData ?: return

    cardStateFlowsMap.forEach { (cardId, stateFlow) ->
        if (cardId == "local_time") return@forEach
        val currentState = stateFlow.value
        val remappedFlightData = mapRealDataToCard(currentState.flightData, liveData)
        if (remappedFlightData != currentState.flightData) {
            stateFlow.value = currentState.copy(flightData = remappedFlightData)
        }
    }
}

internal fun CardStateRepository.updateCardTimeFormatter(formatter: CardTimeFormatter) {
    if (cardTimeFormatter === formatter) {
        return
    }
    cardTimeFormatter = formatter
}

internal fun CardStateRepository.onCleared() {
    manualPositioningTimeout?.cancel()
    stopIndependentClockTimer()
}

private fun splitPrimaryValue(primaryValue: String): Pair<String?, String?> {
    val trimmed = primaryValue.trim()
    if (trimmed.isEmpty()) {
        return null to null
    }

    val firstSpace = trimmed.indexOf(' ')
    return if (firstSpace > 0) {
        val numberPart = trimmed.substring(0, firstSpace)
        val unitPart = trimmed.substring(firstSpace + 1).trim().takeIf { it.isNotEmpty() }
        numberPart to unitPart
    } else {
        trimmed to null
    }
}

private fun highlightColorFor(cardId: String, realData: RealTimeFlightData): Long? {
    val risk = realData.macCreadyRisk
    // Primary colouring for vario cards: sign-based, fall back to risk highlighting.
    return when (cardId) {
        "thermal_avg" -> {
            // Colour must match the primary value (TC 30s), not alternate vario sources.
            val avg = realData.thermalAverage.toDouble()
            if (!avg.isFinite()) return null
            when {
                risk > 0.0 && 2 * avg < risk -> NEGATIVE_VARIO_COLOR
                avg > 0.0 -> POSITIVE_VARIO_COLOR
                avg < 0.0 -> NEGATIVE_VARIO_COLOR
                else -> null
            }
        }
        else -> {
            if (risk <= 0.0) return null
            val (value, factor) = when (cardId) {
                "thermal_tc_avg" -> realData.thermalAverageCircle.toDouble() to 1.5
                else -> return null
            }
            if (factor * value < risk) NEGATIVE_VARIO_COLOR else null
        }
    }
}

