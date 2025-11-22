package com.example.dfcards.dfcards

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardDefinition
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.FlightTemplates
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.units.UnitsPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.util.UUID

private typealias ProfileId = String

/**
 * Single-source-of-truth owner for flight data card configuration. Persisted values are read/written
 * via [CardPreferences], and the UI observes a consistent state regardless of which screen modifies it.
 */
class FlightDataViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val repository = CardStateRepository(viewModelScope)
    private var ingest: FlightDataIngest? = null
    private val derivations = FlightDataDerivations(repository)

    private val _cardPreferences = MutableStateFlow<CardPreferences?>(null)

    private val _profileModeCards =
        MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>>(emptyMap())
    val profileModeCards: StateFlow<Map<ProfileId, Map<FlightModeSelection, List<String>>>> =
        _profileModeCards.asStateFlow()

    private val _profileModeTemplates =
        MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, String>>>(emptyMap())
    val profileModeTemplates: StateFlow<Map<ProfileId, Map<FlightModeSelection, String>>> =
        _profileModeTemplates.asStateFlow()

    private val _profileModeVisibilities =
        MutableStateFlow<Map<ProfileId, Map<FlightModeSelection, Boolean>>>(emptyMap())
    val profileModeVisibilities: StateFlow<Map<ProfileId, Map<FlightModeSelection, Boolean>>> =
        _profileModeVisibilities.asStateFlow()

    private val _activeProfileId = MutableStateFlow<ProfileId?>(null)
    val activeProfileId: StateFlow<ProfileId?> = _activeProfileId.asStateFlow()

    private val _currentFlightMode = MutableStateFlow(FlightModeSelection.CRUISE)
    val currentFlightMode: StateFlow<FlightModeSelection> = _currentFlightMode.asStateFlow()

    private val _availableTemplates =
        MutableStateFlow<List<FlightTemplate>>(FlightTemplates.getDefaultTemplates())
    val availableTemplates: StateFlow<List<FlightTemplate>> = _availableTemplates.asStateFlow()

    val cardStateFlows: Map<String, StateFlow<CardState>>
        get() = derivations.cardStateFlows

    @Deprecated(
        message = "Use cardStateFlows instead for better performance",
        replaceWith = ReplaceWith("cardStateFlows")
    )
    val cardStates: StateFlow<List<CardState>> = derivations.legacyCardStates

    val selectedCardIds: StateFlow<Set<String>> = derivations.selectedCardIds

    val activeCardIds: StateFlow<List<String>> =
        combine(_profileModeCards, _activeProfileId, _currentFlightMode) { cardsMap, profileId, mode ->
            cardsMap[FlightVisibility.normalizeProfileId(profileId)]?.get(mode).orEmpty()
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
            templateMap[FlightVisibility.normalizeProfileId(profileId)]?.get(mode)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private var templatesJob: Job? = null

    fun initializeCardPreferences(preferences: CardPreferences) {
        if (_cardPreferences.value === preferences) return
        _cardPreferences.value = preferences
        ingest = FlightDataIngest(preferences, ioDispatcher)
        derivations.setPreferences(preferences)
        templatesJob?.cancel()
        templatesJob = viewModelScope.launch(ioDispatcher) {
            preferences.getAllTemplates().collect { templates ->
                _availableTemplates.value = if (templates.isEmpty()) {
                    FlightTemplates.getDefaultTemplates()
                } else {
                    templates
                }
            }
        }
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
        ensureVisibilityEntry(FlightVisibility.normalizeProfileId(profileId))
        syncSelectedIdsWithRepository()
    }

    fun setFlightMode(mode: FlightModeSelection) {
        if (_currentFlightMode.value == mode) return
        _currentFlightMode.value = mode
        repository.updateFlightMode(mode)
        syncSelectedIdsWithRepository()
    }

    fun setProfileCards(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        cardIds: List<String>
    ) {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        val sanitized = cardIds.distinct()
        _profileModeCards.value = _profileModeCards.value.toMutableMap().apply {
            val existing = this[targetProfileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = sanitized
            this[targetProfileId] = existing
        }
        if (FlightVisibility.normalizeProfileId(_activeProfileId.value) == targetProfileId && _currentFlightMode.value == flightMode) {
            derivations.setSelected(sanitized.toSet())
        }
        ensureVisibilityEntry(targetProfileId)
        persistProfileCards(targetProfileId, flightMode, sanitized)
    }

    fun setProfileTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        templateId: String
    ) {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        _profileModeTemplates.value = _profileModeTemplates.value.toMutableMap().apply {
            val existing = this[targetProfileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = templateId
            this[targetProfileId] = existing
        }
        ensureVisibilityEntry(targetProfileId)
        persistProfileTemplate(targetProfileId, flightMode, templateId)
    }

    fun setProfileFlightModeVisibility(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        isVisible: Boolean
    ) {
        if (flightMode == FlightModeSelection.CRUISE) return
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        val updated = _profileModeVisibilities.value.toMutableMap()
        val profileEntries = (updated[targetProfileId]?.toMutableMap() ?: FlightVisibility.defaultVisibilityMap()).apply {
            this[flightMode] = isVisible
            this[FlightModeSelection.CRUISE] = true
        }
        updated[targetProfileId] = profileEntries.toMap()
        _profileModeVisibilities.value = updated.toMap()
        val preferences = _cardPreferences.value ?: return
        viewModelScope.launch(ioDispatcher) {
            preferences.saveProfileFlightModeVisibility(targetProfileId, flightMode.name, isVisible)
        }
    }

    fun toggleProfileFlightModeVisibility(profileId: ProfileId?, flightMode: FlightModeSelection) {
        if (flightMode == FlightModeSelection.CRUISE) return
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        val current = _profileModeVisibilities.value[targetProfileId]?.get(flightMode) ?: true
        setProfileFlightModeVisibility(profileId, flightMode, !current)
    }

    fun flightModeVisibilitiesFor(profileId: ProfileId?): Map<FlightModeSelection, Boolean> {
        val defaults = FlightVisibility.defaultVisibilityMap()
        _profileModeVisibilities.value[FlightVisibility.normalizeProfileId(profileId)]?.forEach { (mode, visible) ->
            if (mode != FlightModeSelection.CRUISE) {
                defaults[mode] = visible
            }
        }
        return defaults.toMap()
    }

    fun getProfileCards(profileId: ProfileId?, flightMode: FlightModeSelection): List<String> =
        _profileModeCards.value[FlightVisibility.normalizeProfileId(profileId)]?.get(flightMode).orEmpty()

    fun getProfileTemplateId(profileId: ProfileId?, flightMode: FlightModeSelection): String? =
        _profileModeTemplates.value[FlightVisibility.normalizeProfileId(profileId)]?.get(flightMode)

    fun clearProfile(profileId: ProfileId) {
        _profileModeCards.value = _profileModeCards.value.toMutableMap().apply {
            remove(profileId)
        }
        _profileModeTemplates.value = _profileModeTemplates.value.toMutableMap().apply {
            remove(profileId)
        }
        if (_activeProfileId.value == profileId) {
            _activeProfileId.value = null
            derivations.setSelected(emptySet())
        }
        viewModelScope.launch(ioDispatcher) {
            _cardPreferences.value?.clearProfile(profileId)
        }
    }

    override fun onCleared() {
        repository.onCleared()
        templatesJob?.cancel()
        super.onCleared()
    }

    suspend fun prepareCardsForProfile(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        containerSize: IntSize,
        density: Density
    ) {
        Log.d("CARD_VM", "prepareCardsForProfile(profile=$profileId, mode=$flightMode, size=${containerSize.width}x${containerSize.height})")
        setFlightMode(flightMode)
        setActiveProfile(profileId)
        initializeCards(containerSize, density)

        val template = buildActiveTemplate(profileId, flightMode)
        if (template != null) {
            applyTemplate(template, containerSize, density)
            setProfileCards(profileId, flightMode, template.cardIds)
        } else {
            repository.loadEssentialCardsOnStartup(containerSize, density, flightMode)
        }
    }

    suspend fun createTemplate(name: String, cardIds: List<String>) {
        val preferences = _cardPreferences.value ?: return
        val newTemplate = FlightTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "Custom template",
            cardIds = cardIds,
            icon = Icons.Default.Star,
            isPreset = false
        )
        val updated = _availableTemplates.value + newTemplate
        preferences.saveAllTemplates(updated)
    }

    suspend fun updateTemplate(templateId: String, name: String, cardIds: List<String>) {
        val preferences = _cardPreferences.value ?: return
        val updated = _availableTemplates.value.map { template ->
            if (template.id == templateId) {
                template.copy(name = name, cardIds = cardIds, isPreset = false)
            } else template
        }
        preferences.saveAllTemplates(updated)
    }

    suspend fun deleteTemplate(templateId: String) {
        val preferences = _cardPreferences.value ?: return
        val updated = _availableTemplates.value.filterNot { it.id == templateId }
        preferences.saveAllTemplates(updated)
    }

    fun currentTemplateFor(profileId: ProfileId?, flightMode: FlightModeSelection): FlightTemplate? =
        buildActiveTemplate(profileId, flightMode)

    fun allTemplateCardCounts(profileId: ProfileId?): Map<String, Int> {
        val normalized = FlightVisibility.normalizeProfileId(profileId)
        val cardsByMode = _profileModeCards.value[normalized].orEmpty()
        val templatesByMode = _profileModeTemplates.value[normalized].orEmpty()
        if (cardsByMode.isEmpty() || templatesByMode.isEmpty()) return emptyMap()

        val counts = mutableMapOf<String, Int>()
        cardsByMode.forEach { (mode, cards) ->
            val templateId = templatesByMode[mode] ?: return@forEach
            counts[templateId] = cards.size
        }
        return counts
    }

    fun templateCardCounts(profileId: ProfileId?, flightMode: FlightModeSelection): Map<String, Int> {
        val normalized = FlightVisibility.normalizeProfileId(profileId)
        val templateId = _profileModeTemplates.value[normalized]?.get(flightMode)
        val allCounts = allTemplateCardCounts(profileId)
        return if (templateId != null && allCounts.containsKey(templateId)) {
            mapOf(templateId to allCounts.getValue(templateId))
        } else {
            emptyMap()
        }
    }

    private fun syncSelectedIdsWithRepository() {
        val profileId = FlightVisibility.normalizeProfileId(_activeProfileId.value)
        val mode = _currentFlightMode.value
        val desiredIds = _profileModeCards.value[profileId]?.get(mode)
        if (desiredIds != null) {
            derivations.setSelected(desiredIds.toSet())
        } else {
            val states = derivations.getAllStates()
            val ids = states.map { it.id }
            if (ids.isNotEmpty()) {
                setProfileCards(profileId, mode, ids)
            }
        }
    }

    private fun persistActiveCards() {
        val profileId = FlightVisibility.normalizeProfileId(_activeProfileId.value)
        val mode = _currentFlightMode.value
        val ids = derivations.selectedCardIds.value.toList()
        setProfileCards(profileId, mode, ids)
    }

    private fun persistTemplateSelection(templateId: String) {
        val profileId = FlightVisibility.normalizeProfileId(_activeProfileId.value)
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
        val ingestHelper = ingest ?: return
        val templateMappingsRaw = ingestHelper.loadProfileTemplates()
        val templateCardsRaw = ingestHelper.loadProfileTemplateCards()

        val templateMappings = mutableMapOf<ProfileId, MutableMap<FlightModeSelection, String>>()
        val cardsByMode = mutableMapOf<ProfileId, MutableMap<FlightModeSelection, List<String>>>()

        templateMappingsRaw.forEach { (profileId, modeMap) ->
            val templateDest = templateMappings.getOrPut(profileId) { mutableMapOf() }
            val cardDest = cardsByMode.getOrPut(profileId) { mutableMapOf() }
            modeMap.forEach { (modeName, templateId) ->
                val mode = modeName.toFlightModeOrNull() ?: return@forEach
                templateDest[mode] = templateId
                val cards = templateCardsRaw[profileId]?.get(templateId)
                if (cards != null) {
                    cardDest[mode] = cards
                }
            }
        }

        _profileModeTemplates.value = templateMappings.mapValues { it.value.toMap() }
        _profileModeCards.value = cardsByMode.mapValues { it.value.toMap() }

        val profileIds = (templateMappings.keys + cardsByMode.keys).toMutableSet()
        _activeProfileId.value?.let { profileIds.add(it) }
        if (profileIds.isNotEmpty()) {
            val visibilityMappings = mutableMapOf<ProfileId, Map<FlightModeSelection, Boolean>>()
            profileIds.forEach { profileId ->
                val raw = preferences.getProfileAllFlightModeVisibilities(profileId).first()
                visibilityMappings[profileId] = FlightVisibility.buildVisibilityMap(raw)
            }
            _profileModeVisibilities.value = visibilityMappings.toMap()
        } else {
            _profileModeVisibilities.value = emptyMap()
        }

        syncSelectedIdsWithRepository()
    }

    private fun ensureVisibilityEntry(profileId: ProfileId) {
        if (_profileModeVisibilities.value.containsKey(profileId)) return
        _profileModeVisibilities.value = _profileModeVisibilities.value.toMutableMap().apply {
            this[profileId] = FlightVisibility.defaultVisibilityMap().toMap()
        }
    }

    private fun getTemplateIdForPersistence(
        profileId: ProfileId,
        flightMode: FlightModeSelection
    ): String {
        val existing = getProfileTemplateId(profileId, flightMode)
        if (existing != null) return existing
        val fallback = FlightCardStateMapper.fallbackTemplateIdFor(flightMode)
        setProfileTemplate(profileId, flightMode, fallback)
        return fallback
    }

    private fun String.toFlightModeOrNull(): FlightModeSelection? =
        runCatching { FlightModeSelection.valueOf(this) }.getOrNull()

    private fun buildActiveTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection
    ): FlightTemplate? {
        val profileKey = FlightVisibility.normalizeProfileId(profileId)
        val profileTemplateId = _profileModeTemplates.value[profileKey]?.get(flightMode)
        val profileCards = _profileModeCards.value[profileKey]?.get(flightMode)

        return FlightCardStateMapper.buildActiveTemplate(
            profileId = profileId,
            flightMode = flightMode,
            availableTemplates = _availableTemplates.value,
            profileTemplates = _profileModeTemplates.value,
            profileCards = _profileModeCards.value
        )
    }
}
