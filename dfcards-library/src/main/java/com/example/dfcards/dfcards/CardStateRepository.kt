package com.example.dfcards.dfcards

import com.example.dfcards.CardPreferences
import com.example.dfcards.CardPreferences.CardAnchor
import com.example.dfcards.CardStrings
import com.example.dfcards.DefaultCardStrings
import com.example.dfcards.CardTimeFormatter
import com.example.dfcards.SystemCardTimeFormatter
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.dfcards.CardState
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.IntSizePx
import com.example.xcpro.core.flight.RealTimeFlightData
import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.time.DefaultClockProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    internal val scope: CoroutineScope,
    internal val clock: Clock = DefaultClockProvider()
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
    internal var cardStrings: CardStrings = DefaultCardStrings()
    internal var cardTimeFormatter: CardTimeFormatter = SystemCardTimeFormatter()
    internal var lastRealTimeData: RealTimeFlightData? = null
    internal var lastFastUpdateTime = 0L
    internal var lastPrimaryUpdateTime = 0L
    internal var lastBackgroundUpdateTime = 0L
    internal val fastUpdateIntervalMs = FAST_UPDATE_INTERVAL_MS
    internal val primaryUpdateIntervalMs = PRIMARY_UPDATE_INTERVAL_MS
    internal val backgroundUpdateIntervalMs = BACKGROUND_UPDATE_INTERVAL_MS
    internal var clockTimerJob: Job? = null

    internal val essentialCardIds = listOf("gps_alt", "baro_alt", "agl", "vario", "ias", "ground_speed")
    internal var cardsAcrossPortrait: Int = CardPreferences.DEFAULT_CARDS_ACROSS_PORTRAIT
    internal var lastContainerSize: IntSizePx? = null
    internal var lastDensity: DensityScale? = null
    internal var activeProfileId: ProfileId = FlightVisibility.normalizeProfileId(null)
    private var cardsAcrossJob: Job? = null
    internal var cardsAnchorPortrait: CardAnchor = CardPreferences.DEFAULT_ANCHOR_PORTRAIT
    private var cardsAnchorJob: Job? = null

    fun setCardPreferences(preferences: CardPreferences) {
        cardPreferences = preferences
        restorePersistedPositions()

        cardsAcrossJob?.cancel()
        cardsAcrossJob = scope.launch {
            preferences.getCardsAcrossPortrait().collect { desired ->
                val clamped = desired.coerceIn(
                    CardPreferences.MIN_CARDS_ACROSS_PORTRAIT,
                    CardPreferences.MAX_CARDS_ACROSS_PORTRAIT
                )
                if (clamped == cardsAcrossPortrait) return@collect
                cardsAcrossPortrait = clamped
                maybeRelayoutExistingCards()
            }
        }

        cardsAnchorJob?.cancel()
        cardsAnchorJob = scope.launch {
            preferences.getCardsAnchorPortrait().collect { anchor ->
                if (anchor == cardsAnchorPortrait) return@collect
                cardsAnchorPortrait = anchor
                maybeRelayoutExistingCards()
            }
        }
    }

    fun updateFlightMode(mode: FlightModeSelection) {
        if (currentFlightMode != mode) {
            manuallyPositionedCards.clear()
            isManuallyPositioning = false
        }
        currentFlightMode = mode
    }

    fun updateActiveProfile(profileId: ProfileId?) {
        val normalized = FlightVisibility.normalizeProfileId(profileId)
        if (activeProfileId != normalized) {
            manuallyPositionedCards.clear()
            isManuallyPositioning = false
        }
        activeProfileId = normalized
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
