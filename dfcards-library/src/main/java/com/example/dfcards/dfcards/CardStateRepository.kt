package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences
import com.example.dfcards.RealTimeFlightData
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.CardState
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal val FAST_UPDATE_CARD_IDS = setOf(
    "vario",
    "vario_optimized",
    "vario_legacy",
    "vario_raw",
    "vario_gps",
    "vario_complementary",
    "ground_speed",
    "ias"
)

private const val FAST_UPDATE_INTERVAL_MS = 80L
private const val PRIMARY_UPDATE_INTERVAL_MS = 250L
private const val BACKGROUND_UPDATE_INTERVAL_MS = 1_000L

internal class CardStateRepository(
    internal val scope: CoroutineScope
) {

    internal val cardStateFlowsMap = mutableMapOf<String, MutableStateFlow<CardState>>()
    private val _legacyCardStates = MutableStateFlow<List<CardState>>(emptyList())
    private val _selectedCardIds = MutableStateFlow<Set<String>>(emptySet())

    val cardStateFlows: Map<String, StateFlow<CardState>>
        get() = cardStateFlowsMap.mapValues { it.value.asStateFlow() }

    val selectedCardIds: StateFlow<Set<String>> = _selectedCardIds.asStateFlow()
    val legacyCardStates: StateFlow<List<CardState>> = _legacyCardStates.asStateFlow()

    internal var cardPreferences: CardPreferences? = null
    internal var currentFlightMode: FlightModeSelection? = null
    internal var hasInitialized = false
    internal var isInitializing = false
    internal var isManuallyPositioning = false
    internal var manualPositioningTimeout: Job? = null
    internal val manuallyPositionedCards = mutableSetOf<String>()
    internal var unitsPreferences: UnitsPreferences = UnitsPreferences()
    internal var lastRealTimeData: RealTimeFlightData? = null
    internal var lastFastUpdateTime = 0L
    internal var lastPrimaryUpdateTime = 0L
    internal var lastBackgroundUpdateTime = 0L
    internal val fastUpdateIntervalMs = FAST_UPDATE_INTERVAL_MS
    internal val primaryUpdateIntervalMs = PRIMARY_UPDATE_INTERVAL_MS
    internal val backgroundUpdateIntervalMs = BACKGROUND_UPDATE_INTERVAL_MS
    internal var clockTimerJob: Job? = null

    internal val essentialCardIds = listOf("gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed")

    fun setCardPreferences(preferences: CardPreferences) {
        cardPreferences = preferences
    }

    fun updateFlightMode(mode: FlightModeSelection) {
        currentFlightMode = mode
    }

    fun clearAllCards() {
        cardStateFlowsMap.clear()
        _selectedCardIds.value = emptySet()
        manuallyPositionedCards.clear()
        hasInitialized = false
        isManuallyPositioning = false
        manualPositioningTimeout?.cancel()
    }

    fun markLegacyStateDirty() {
        _legacyCardStates.value = cardStateFlowsMap.values.map { it.value }
    }

    fun setSelectedCardIds(ids: Set<String>) {
        val changed = _selectedCardIds.value != ids
        _selectedCardIds.value = ids
        if (changed) {
            refreshVisibleCards()
        }
    }

    fun resumeLiveUpdates() {
        isManuallyPositioning = false
        manualPositioningTimeout?.cancel()
    }

    fun refreshVisibleCards() {
        val liveData = lastRealTimeData ?: return
        updateCardsWithLiveData(liveData, forceVisible = true)
    }
}
