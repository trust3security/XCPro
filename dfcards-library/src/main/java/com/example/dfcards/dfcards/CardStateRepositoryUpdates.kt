package com.example.dfcards.dfcards

import com.example.dfcards.CardLibrary
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.dfcards.CardState
import com.example.dfcards.dfcards.FlightData
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun CardStateRepository.updateCardsWithLiveData(liveData: RealTimeFlightData) {
    lastRealTimeData = liveData

    if (isManuallyPositioning) {
        return
    }

    val currentTime = System.currentTimeMillis()
    if (currentTime - lastUpdateTime < updateThrottleMs) {
        return
    }
    lastUpdateTime = currentTime

    cardStateFlowsMap.forEach { (cardId, stateFlow) ->
        if (cardId == "local_time") return@forEach

        val currentState = stateFlow.value
        val updatedFlightData = mapRealDataToCard(currentState.flightData, liveData)

        if (updatedFlightData != currentState.flightData) {
            val oldPosition = Pair(currentState.x, currentState.y)
            val newState = currentState.copy(flightData = updatedFlightData)

            if (newState.x != oldPosition.first || newState.y != oldPosition.second) {
                println("ERROR: Live data update changed position for card $cardId; skipping update")
                return@forEach
            }

            stateFlow.value = newState

            if (cardId in selectedCardIds.value) {
                println("DATA: Updated card $cardId value: ${updatedFlightData.primaryValue}")
            }
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

    println("DEBUG: Independent clock timer started (1Hz updates)")
}

internal fun CardStateRepository.stopIndependentClockTimer() {
    clockTimerJob?.cancel()
    clockTimerJob = null
    println("DEBUG: Independent clock timer stopped")
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

    return currentFlightData.copy(
        primaryValue = primaryValue,
        secondaryValue = secondaryValue
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
    println("DEBUG: CardStateRepository - Cleared")
}
