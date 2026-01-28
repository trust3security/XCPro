package com.example.dfcards.dfcards

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dfcards.CardDefinition
import com.example.dfcards.CardPreferences
import com.example.dfcards.FlightModeSelection
import com.example.dfcards.FlightTemplate
import com.example.dfcards.FlightTemplates
import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.IntSizePx
import com.example.xcpro.core.time.Clock
import com.example.xcpro.core.time.DefaultClockProvider
import kotlinx.coroutines.withContext
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

private const val TAG = "FlightDataViewModel"

/**
 * Single-source-of-truth owner for flight data card configuration. Persisted values are read/written
 * via [CardPreferences], and the UI observes a consistent state regardless of which screen modifies it.
 */
class FlightDataViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = DefaultClockProvider(),
    cardsUseCaseOverride: FlightCardsUseCase? = null
) : ViewModel() {

    private val cardsUseCase = cardsUseCaseOverride ?: FlightCardsUseCase(viewModelScope, clock)
    private var ingest: FlightDataIngest? = null

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

    private val profileStore = FlightProfileStore(
        profileModeTemplates = _profileModeTemplates,
        profileModeCards = _profileModeCards,
        profileModeVisibilities = _profileModeVisibilities,
        activeProfileId = _activeProfileId,
        availableTemplates = _availableTemplates,
        ingestProvider = { ingest }
    )

    val cardStateFlows: Map<String, StateFlow<CardState>>
        get() = cardsUseCase.cardStateFlows

    @Deprecated(
        message = "Use cardStateFlows instead for better performance",
        replaceWith = ReplaceWith("cardStateFlows")
    )
    val cardStates: StateFlow<List<CardState>> = cardsUseCase.legacyCardStates

    val selectedCardIds: StateFlow<Set<String>> = cardsUseCase.selectedCardIds

    val activeCardIds: StateFlow<List<String>> =
        combine(_profileModeCards, _activeProfileId, _currentFlightMode) { cardsMap, profileId, mode ->
            cardsMap[FlightVisibility.normalizeProfileId(profileId)]?.get(mode).orEmpty()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val activeCards: StateFlow<List<CardState>> =
        combine(activeCardIds, cardsUseCase.legacyCardStates) { desiredIds, allStates ->
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
        Log.d(TAG, "initializeCardPreferences: ${preferences.hashCode()}")
        _cardPreferences.value = preferences
        ingest = FlightDataIngest(preferences, ioDispatcher)
        cardsUseCase.setPreferences(preferences)
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
            profileStore.hydrateFromPreferences(preferences)
            syncSelectedIdsWithRepository()
        }
    }

    fun updateFlightMode(flightMode: FlightModeSelection) {
        setFlightMode(flightMode)
    }

    fun initializeCards(containerSize: IntSizePx, density: DensityScale) {
        cardsUseCase.initializeCards(containerSize, density)
    }

    suspend fun loadEssentialCardsOnStartup(
        containerSize: IntSizePx,
        density: DensityScale,
        flightMode: FlightModeSelection? = null
    ) {
        cardsUseCase.loadEssentialCardsOnStartup(containerSize, density, flightMode)
    }

    fun updateCardState(cardState: CardState) {
        cardsUseCase.updateCardState(cardState)
        syncSelectedIdsWithRepository()
    }

    fun toggleCardFromLibrary(
        cardDefinition: CardDefinition,
        containerSize: IntSizePx,
        density: DensityScale
    ) {
        cardsUseCase.toggleCardFromLibrary(cardDefinition, containerSize, density)
        syncSelectedIdsWithRepository()
        persistActiveCards()
    }

    suspend fun applyTemplate(
        template: FlightTemplate,
        containerSize: IntSizePx,
        density: DensityScale
    ) {
        cardsUseCase.applyTemplate(template, containerSize, density)
        syncSelectedIdsWithRepository()
        persistTemplateSelection(template.id)
    }

    fun updateCardsWithLiveData(liveData: RealTimeFlightData) {
        cardsUseCase.updateCardsWithLiveData(liveData)
    }

    fun startIndependentClockTimer() {
        cardsUseCase.startIndependentClockTimer()
    }

    fun stopIndependentClockTimer() {
        cardsUseCase.stopIndependentClockTimer()
    }

    fun saveCurrentLayoutToTemplate() {
        cardsUseCase.saveCurrentLayoutToTemplate()
        persistActiveCards()
    }

    fun clearAllCards() {
        cardsUseCase.clearAllCards()
        syncSelectedIdsWithRepository()
        persistActiveCards()
    }

    fun resumeLiveDataUpdates() {
        cardsUseCase.resumeLiveUpdates()
    }

    fun getAllCardStates(): List<CardState> = cardsUseCase.getAllCardStates()

    fun getCardState(cardId: String): CardState? = cardsUseCase.getCardState(cardId)

    fun hasCard(cardId: String): Boolean = cardsUseCase.hasCard(cardId)

    fun getCardCount(): Int = cardsUseCase.getCardCount()

    fun updateUnitsPreferences(preferences: UnitsPreferences) {
        cardsUseCase.updateUnitsPreferences(preferences)
    }

    fun ensureCardsExist(cardIds: Set<String>) {
        Log.d(TAG, "ensureCardsExist: ids=${cardIds.size}")
        cardsUseCase.ensureCardsExist(cardIds)
    }

    fun setActiveProfile(profileId: ProfileId?) {
        if (_activeProfileId.value == profileId) return
        Log.d(TAG, "setActiveProfile: $profileId")
        _activeProfileId.value = profileId
        profileStore.ensureVisibilityEntry(FlightVisibility.normalizeProfileId(profileId))
        syncSelectedIdsWithRepository()
    }

    fun setFlightMode(mode: FlightModeSelection) {
        if (_currentFlightMode.value == mode) return
        _currentFlightMode.value = mode
        cardsUseCase.updateFlightMode(mode)
        syncSelectedIdsWithRepository()
    }

    fun setProfileCards(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        cardIds: List<String>
    ) {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        val sanitized = cardIds.distinct()
        Log.d(TAG, "setProfileCards: profile=$targetProfileId mode=$flightMode cards=${sanitized.size}")
        _profileModeCards.value = _profileModeCards.value.toMutableMap().apply {
            val existing = this[targetProfileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = sanitized
            this[targetProfileId] = existing
        }
        if (FlightVisibility.normalizeProfileId(_activeProfileId.value) == targetProfileId && _currentFlightMode.value == flightMode) {
            cardsUseCase.setSelected(sanitized.toSet())
            cardsUseCase.ensureCardsExist(sanitized.toSet())
        }
        profileStore.ensureVisibilityEntry(targetProfileId)
        persistProfileCards(targetProfileId, flightMode, sanitized)
    }

    fun setProfileTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        templateId: String
    ) {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        Log.d(TAG, "setProfileTemplate: profile=$targetProfileId mode=$flightMode template=$templateId")
        _profileModeTemplates.value = _profileModeTemplates.value.toMutableMap().apply {
            val existing = this[targetProfileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = templateId
            this[targetProfileId] = existing
        }
        profileStore.ensureVisibilityEntry(targetProfileId)
        persistProfileTemplate(targetProfileId, flightMode, templateId)
    }

    suspend fun selectProfileTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        template: FlightTemplate
    ) {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        Log.d(TAG, "selectProfileTemplate: profile=$targetProfileId mode=$flightMode template=${template.id}")
        val preferences = _cardPreferences.value
        val storedCards = if (preferences == null) {
            null
        } else {
            withContext(ioDispatcher) {
                preferences.getProfileTemplateCards(targetProfileId, template.id).first()
            }
        }
        val cards = storedCards ?: template.cardIds
        setProfileTemplate(profileId, flightMode, template.id)
        setProfileCards(profileId, flightMode, cards)
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
            cardsUseCase.setSelected(emptySet())
        }
        viewModelScope.launch(ioDispatcher) {
            _cardPreferences.value?.clearProfile(profileId)
        }
    }

    override fun onCleared() {
        cardsUseCase.onCleared()
        templatesJob?.cancel()
        super.onCleared()
    }

    suspend fun prepareCardsForProfile(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        containerSize: IntSizePx,
        density: DensityScale
    ) {
        Log.d(TAG, "prepareCardsForProfile(profile=$profileId, mode=$flightMode, size=${containerSize.width}x${containerSize.height})")
        setFlightMode(flightMode)
        setActiveProfile(profileId)
        initializeCards(containerSize, density)

        val template = profileStore.buildActiveTemplate(profileId, flightMode)
        if (template != null) {
            applyTemplate(template, containerSize, density)
            setProfileCards(profileId, flightMode, template.cardIds)
        } else {
            cardsUseCase.loadEssentialCardsOnStartup(containerSize, density, flightMode)
        }
    }

    suspend fun createTemplate(name: String, cardIds: List<String>) {
        val preferences = _cardPreferences.value ?: return
        val newTemplate = FlightTemplate(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "Custom template",
            cardIds = cardIds,
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
        profileStore.buildActiveTemplate(profileId, flightMode)

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
            cardsUseCase.setSelected(desiredIds.toSet())
        } else {
            val states = cardsUseCase.getAllStates()
            val ids = states.map { it.id }
            if (ids.isNotEmpty()) {
                setProfileCards(profileId, mode, ids)
            }
        }
    }

    private fun persistActiveCards() {
        val profileId = FlightVisibility.normalizeProfileId(_activeProfileId.value)
        val mode = _currentFlightMode.value
        val ids = cardsUseCase.selectedCardIds.value.toList()
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
}
