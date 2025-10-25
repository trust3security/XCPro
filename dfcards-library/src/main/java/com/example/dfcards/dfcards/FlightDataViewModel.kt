package com.example.dfcards.dfcards

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardDefinition
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private typealias ProfileId = String

/**
 * Single-source-of-truth owner for flight data card configuration. Persisted values are read/written
 * via [CardPreferences], and the UI observes a consistent state regardless of which screen modifies it.
 */
class FlightDataViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val repository = CardStateRepository(viewModelScope)

    private val _cardPreferences = MutableStateFlow<CardPreferences?>(null)

    private val _profileModeCards =
        MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>>(emptyMap())
    val profileModeCards: StateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>> =
        _profileModeCards.asStateFlow()

    private val _profileModeTemplates =
        MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, String>>>(emptyMap())
    val profileModeTemplates: StateFlow<Map<ProfileId, Map<FlightModeSelection, String>>> =
        _profileModeTemplates.asStateFlow()

    private val _activeProfileId = MutableStateFlow<ProfileId?>(null)
    val activeProfileId: StateFlow<ProfileId?> = _activeProfileId.asStateFlow()

    private val _currentFlightMode = MutableStateFlow(FlightModeSelection.CRUISE)
    val currentFlightMode: StateFlow<FlightModeSelection> = _currentFlightMode.asStateFlow()

    val cardStateFlows: Map<String, StateFlow<CardState>>
        get() = repository.cardStateFlows

    @Deprecated(
        message = "Use cardStateFlows instead for better performance",
        replaceWith = ReplaceWith("cardStateFlows")
    )
    val cardStates: StateFlow<List<CardState>> = repository.legacyCardStates

    val selectedCardIds: StateFlow<Set<String>> = repository.selectedCardIds

    val activeCardIds: StateFlow<List<String>> =
        combine(_profileModeCards, _activeProfileId, _currentFlightMode) { cardsMap, profileId, mode ->
            if (profileId == null) {
                emptyList()
            } else {
                cardsMap[profileId]?.get(mode).orEmpty()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val activeCards: StateFlow<List<CardState>> =
        combine(activeCardIds, repository.legacyCardStates) { desiredIds, allStates ->
            if (desiredIds.isEmpty()) {
                emptyList()
            } else {
                val stateMap = allStates.associateBy { it.id }
                desiredIds.mapNotNull { stateMap[it] }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val activeTemplateId: StateFlow<String?> =
        combine(_profileModeTemplates, _activeProfileId, _currentFlightMode) { templateMap, profileId, mode ->
            profileId?.let { templateMap[it]?.get(mode) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun initializeCardPreferences(preferences: CardPreferences) {
        if (_cardPreferences.value === preferences) return
        _cardPreferences.value = preferences
        viewModelScope.launch(ioDispatcher) {
            hydrateFromPreferences(preferences)
        }
    }

    fun updateFlightMode(flightMode: FlightModeSelection) {
        setFlightMode(flightMode)
    }

    fun initializeCards(containerSize: IntSize, density: Density) {
        repository.initializeCards(containerSize, density)
    }

    suspend fun loadEssentialCardsOnStartup(
        containerSize: IntSize,
        density: Density,
        flightMode: FlightModeSelection? = null
    ) {
        repository.loadEssentialCardsOnStartup(containerSize, density, flightMode)
    }

    fun updateCardState(cardState: CardState) {
        repository.updateCardState(cardState)
        syncSelectedIdsWithRepository()
    }

    fun toggleCardFromLibrary(
        cardDefinition: CardDefinition,
        containerSize: IntSize,
        density: Density
    ) {
        repository.toggleCardFromLibrary(cardDefinition, containerSize, density)
        syncSelectedIdsWithRepository()
        persistActiveCards()
    }

    fun applyTemplate(
        template: FlightTemplate,
        containerSize: IntSize,
        density: Density
    ) {
        repository.applyTemplate(template, containerSize, density)
        syncSelectedIdsWithRepository()
        persistTemplateSelection(template.id)
    }

    fun updateCardsWithLiveData(liveData: RealTimeFlightData) {
        repository.updateCardsWithLiveData(liveData)
    }

    fun startIndependentClockTimer() {
        repository.startIndependentClockTimer()
    }

    fun stopIndependentClockTimer() {
        repository.stopIndependentClockTimer()
    }

    fun saveCurrentLayoutToTemplate() {
        repository.saveCurrentLayoutToTemplate()
        persistActiveCards()
    }

    fun clearAllCards() {
        repository.clearAllCards()
        syncSelectedIdsWithRepository()
        persistActiveCards()
    }

    fun resumeLiveDataUpdates() {
        repository.resumeLiveUpdates()
    }

    fun getAllCardStates(): List<CardState> = repository.getAllCardStates()

    fun getCardState(cardId: String): CardState? = repository.getCardState(cardId)

    fun hasCard(cardId: String): Boolean = repository.hasCard(cardId)

    fun getCardCount(): Int = repository.getCardCount()

    fun updateUnitsPreferences(preferences: UnitsPreferences) {
        repository.updateUnitsPreferences(preferences)
    }

    fun setActiveProfile(profileId: ProfileId?) {
        if (_activeProfileId.value == profileId) return
        _activeProfileId.value = profileId
        syncSelectedIdsWithRepository()
    }

    fun setFlightMode(mode: FlightModeSelection) {
        if (_currentFlightMode.value == mode) return
        _currentFlightMode.value = mode
        repository.updateFlightMode(mode)
        syncSelectedIdsWithRepository()
    }

    fun setProfileCards(
        profileId: ProfileId,
        flightMode: FlightModeSelection,
        cardIds: List<String>
    ) {
        val sanitized = cardIds.distinct()
        _profileModeCards.value = _profileModeCards.value.toMutableMap().apply {
            val existing = this[profileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = sanitized
            this[profileId] = existing
        }
        if (_activeProfileId.value == profileId && _currentFlightMode.value == flightMode) {
            repository.setSelectedCardIds(sanitized.toSet())
        }
        persistProfileCards(profileId, flightMode, sanitized)
    }

    fun setProfileTemplate(
        profileId: ProfileId,
        flightMode: FlightModeSelection,
        templateId: String
    ) {
        _profileModeTemplates.value = _profileModeTemplates.value.toMutableMap().apply {
            val existing = this[profileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = templateId
            this[profileId] = existing
        }
        persistProfileTemplate(profileId, flightMode, templateId)
    }

    fun getProfileCards(profileId: ProfileId, flightMode: FlightModeSelection): List<String> =
        _profileModeCards.value[profileId]?.get(flightMode).orEmpty()

    fun getProfileTemplateId(profileId: ProfileId, flightMode: FlightModeSelection): String? =
        _profileModeTemplates.value[profileId]?.get(flightMode)

    fun clearProfile(profileId: ProfileId) {
        _profileModeCards.value = _profileModeCards.value.toMutableMap().apply {
            remove(profileId)
        }
        _profileModeTemplates.value = _profileModeTemplates.value.toMutableMap().apply {
            remove(profileId)
        }
        if (_activeProfileId.value == profileId) {
            _activeProfileId.value = null
            repository.setSelectedCardIds(emptySet())
        }
        viewModelScope.launch(ioDispatcher) {
            _cardPreferences.value?.clearProfile(profileId)
        }
    }

    override fun onCleared() {
        repository.onCleared()
        super.onCleared()
    }

    private fun syncSelectedIdsWithRepository() {
        val profileId = _activeProfileId.value ?: return
        val mode = _currentFlightMode.value
        val desiredIds = _profileModeCards.value[profileId]?.get(mode)
        if (desiredIds != null) {
            repository.setSelectedCardIds(desiredIds.toSet())
        } else {
            val states = repository.getAllCardStates()
            val ids = states.map { it.id }
            if (ids.isNotEmpty()) {
                setProfileCards(profileId, mode, ids)
            }
        }
    }

    private fun persistActiveCards() {
        val profileId = _activeProfileId.value ?: return
        val mode = _currentFlightMode.value
        val ids = repository.selectedCardIds.value.toList()
        setProfileCards(profileId, mode, ids)
    }

    private fun persistTemplateSelection(templateId: String) {
        val profileId = _activeProfileId.value ?: return
        val mode = _currentFlightMode.value
        setProfileTemplate(profileId, mode, templateId)
    }

    private fun persistProfileCards(
        profileId: ProfileId,
        flightMode: FlightModeSelection,
        cardIds: List<String>
    ) {
        val preferences = _cardPreferences.value ?: return
        val templateId = getTemplateIdForPersistence(profileId, flightMode)
        viewModelScope.launch(ioDispatcher) {
            preferences.saveProfileTemplateCards(profileId, templateId, cardIds)
        }
    }

    private fun persistProfileTemplate(
        profileId: ProfileId,
        flightMode: FlightModeSelection,
        templateId: String
    ) {
        val preferences = _cardPreferences.value ?: return
        viewModelScope.launch(ioDispatcher) {
            preferences.saveProfileFlightModeTemplate(profileId, flightMode.name, templateId)
        }
    }

    private suspend fun hydrateFromPreferences(preferences: CardPreferences) {
        val templateMappingsRaw = preferences.getAllProfileFlightModeTemplates().first()
        val templateCardsRaw = preferences.getAllProfileTemplateCards().first()

        val templateMappings = mutableMapOf<ProfileId, MutableMap<FlightModeSelection, String>>()
        val cardsByMode = mutableMapOf<ProfileId, MutableMap<FlightModeSelection, List<String>>>()

        templateMappingsRaw.forEach { (profileId, modeMap) ->
            val templateDest = templateMappings.getOrPut(profileId) { mutableMapOf() }
            val cardDest = cardsByMode.getOrPut(profileId) { mutableMapOf() }
            modeMap.forEach { (modeName, templateId) ->
                val mode = modeName.toFlightModeOrNull() ?: return@forEach
                templateDest[mode] = templateId
                val cards = templateCardsRaw[profileId]?.get(templateId).orEmpty()
                if (cards.isNotEmpty()) {
                    cardDest[mode] = cards
                }
            }
        }

        _profileModeTemplates.value = templateMappings.mapValues { it.value.toMap() }
        _profileModeCards.value = cardsByMode.mapValues { it.value.toMap() }
        syncSelectedIdsWithRepository()
    }

    private fun getTemplateIdForPersistence(
        profileId: ProfileId,
        flightMode: FlightModeSelection
    ): String {
        val existing = getProfileTemplateId(profileId, flightMode)
        if (existing != null) return existing
        val fallback = DEFAULT_TEMPLATE_IDS[flightMode]
            ?: DEFAULT_TEMPLATE_IDS[FlightModeSelection.CRUISE]!!
        setProfileTemplate(profileId, flightMode, fallback)
        return fallback
    }

    private fun String.toFlightModeOrNull(): FlightModeSelection? =
        runCatching { FlightModeSelection.valueOf(this) }.getOrNull()

    companion object {
        private val DEFAULT_TEMPLATE_IDS = mapOf(
            FlightModeSelection.CRUISE to "id01",
            FlightModeSelection.THERMAL to "id02",
            FlightModeSelection.FINAL_GLIDE to "id03",
            FlightModeSelection.HAWK to "hawk"
        )
    }
}
