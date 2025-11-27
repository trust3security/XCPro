package com.example.dfcards.dfcards

import androidx.compose.ui.graphics.Color
import com.example.dfcards.CardLibrary
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.CardState
import com.example.dfcards.dfcards.FlightData
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class UpdateTier {
    FAST,
    PRIMARY,
    BACKGROUND
}

// Match XCSoar infobox palette: bright red/green for sink/lift.
private val NEGATIVE_VARIO_COLOR = Color(0xFFFF0000)
private val POSITIVE_VARIO_COLOR = Color(0xFF00FF00)

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

    val currentTime = System.currentTimeMillis()
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
                val currentTime = System.currentTimeMillis()

                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(currentTime))
                val seconds = java.text.SimpleDateFormat("ss", java.util.Locale.getDefault())
                    .format(java.util.Date(currentTime))

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
        units = unitsPreferences
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

private fun highlightColorFor(cardId: String, realData: RealTimeFlightData): Color? {
    val risk = realData.macCreadyRisk
    // Primary colouring for vario cards: sign-based, fall back to risk highlighting.
    return when (cardId) {
        "thermal_avg" -> {
            val avg = when {
                realData.isCircling && realData.thermalAverageValid -> realData.thermalAverage.toDouble()
                realData.nettoAverage30s.isFinite() -> realData.nettoAverage30s
                else -> realData.verticalSpeed
            }
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
                "vario_avg30" -> realData.thermalAverage.toDouble() to 2.0
                "thermal_tc_avg" -> realData.thermalAverageCircle.toDouble() to 1.5
                else -> return null
            }
            if (factor * value < risk) NEGATIVE_VARIO_COLOR else null
        }
    }
}
