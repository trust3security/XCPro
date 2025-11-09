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
private const val DEFAULT_PROFILE_ID = "__default_profile__"

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
        get() = repository.cardStateFlows

    @Deprecated(
        message = "Use cardStateFlows instead for better performance",
        replaceWith = ReplaceWith("cardStateFlows")
    )
    val cardStates: StateFlow<List<CardState>> = repository.legacyCardStates

    val selectedCardIds: StateFlow<Set<String>> = repository.selectedCardIds

    val activeCardIds: StateFlow<List<String>> =
        combine(_profileModeCards, _activeProfileId, _currentFlightMode) { cardsMap, profileId, mode ->
            cardsMap[normalizeProfileId(profileId)]?.get(mode).orEmpty()
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
            templateMap[normalizeProfileId(profileId)]?.get(mode)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private var templatesJob: Job? = null

    fun initializeCardPreferences(preferences: CardPreferences) {
        if (_cardPreferences.value === preferences) return
        _cardPreferences.value = preferences
        repository.setCardPreferences(preferences)
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
        ensureVisibilityEntry(normalizeProfileId(profileId))
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
        val targetProfileId = normalizeProfileId(profileId)
        val sanitized = cardIds.distinct()
        _profileModeCards.value = _profileModeCards.value.toMutableMap().apply {
            val existing = this[targetProfileId]?.toMutableMap() ?: mutableMapOf()
            existing[flightMode] = sanitized
            this[targetProfileId] = existing
        }
        if (normalizeProfileId(_activeProfileId.value) == targetProfileId && _currentFlightMode.value == flightMode) {
            repository.setSelectedCardIds(sanitized.toSet())
        }
        ensureVisibilityEntry(targetProfileId)
        persistProfileCards(targetProfileId, flightMode, sanitized)
    }

    fun setProfileTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        templateId: String
    ) {
        val targetProfileId = normalizeProfileId(profileId)
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
        val targetProfileId = normalizeProfileId(profileId)
        val updated = _profileModeVisibilities.value.toMutableMap()
        val profileEntries = (updated[targetProfileId]?.toMutableMap() ?: defaultVisibilityMap()).apply {
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
        val targetProfileId = normalizeProfileId(profileId)
        val current = _profileModeVisibilities.value[targetProfileId]?.get(flightMode) ?: true
        setProfileFlightModeVisibility(profileId, flightMode, !current)
    }

    fun flightModeVisibilitiesFor(profileId: ProfileId?): Map<FlightModeSelection, Boolean> {
        val defaults = defaultVisibilityMap()
        _profileModeVisibilities.value[normalizeProfileId(profileId)]?.forEach { (mode, visible) ->
            if (mode != FlightModeSelection.CRUISE) {
                defaults[mode] = visible
            }
        }
        return defaults.toMap()
    }

    fun getProfileCards(profileId: ProfileId?, flightMode: FlightModeSelection): List<String> =
        _profileModeCards.value[normalizeProfileId(profileId)]?.get(flightMode).orEmpty()

    fun getProfileTemplateId(profileId: ProfileId?, flightMode: FlightModeSelection): String? =
        _profileModeTemplates.value[normalizeProfileId(profileId)]?.get(flightMode)

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
        val normalized = normalizeProfileId(profileId)
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
        val normalized = normalizeProfileId(profileId)
        val templateId = _profileModeTemplates.value[normalized]?.get(flightMode)
        val allCounts = allTemplateCardCounts(profileId)
        return if (templateId != null && allCounts.containsKey(templateId)) {
            mapOf(templateId to allCounts.getValue(templateId))
        } else {
            emptyMap()
        }
    }

    private fun syncSelectedIdsWithRepository() {
        val profileId = normalizeProfileId(_activeProfileId.value)
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
        val profileId = normalizeProfileId(_activeProfileId.value)
        val mode = _currentFlightMode.value
        val ids = repository.selectedCardIds.value.toList()
        setProfileCards(profileId, mode, ids)
    }

    private fun persistTemplateSelection(templateId: String) {
        val profileId = normalizeProfileId(_activeProfileId.value)
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
                visibilityMappings[profileId] = buildVisibilityMap(raw)
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
            this[profileId] = defaultVisibilityMap().toMap()
        }
    }

    private fun defaultVisibilityMap(): MutableMap<FlightModeSelection, Boolean> =
        FlightModeSelection.values().associateWith { true }.toMutableMap().apply {
            this[FlightModeSelection.CRUISE] = true
        }

    private fun buildVisibilityMap(raw: Map<String, Boolean>?): Map<FlightModeSelection, Boolean> {
        val defaults = defaultVisibilityMap()
        raw?.forEach { (modeName, visible) ->
            val mode = modeName.toFlightModeOrNull() ?: return@forEach
            if (mode != FlightModeSelection.CRUISE) {
                defaults[mode] = visible
            }
        }
        return defaults.toMap()
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

    private fun buildActiveTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection
    ): FlightTemplate? {
        val templates = _availableTemplates.value
        val profileKey = normalizeProfileId(profileId)
        val profileTemplateId = _profileModeTemplates.value[profileKey]?.get(flightMode)
        val profileCards = _profileModeCards.value[profileKey]?.get(flightMode)

        val baseTemplate = when {
            profileTemplateId != null ->
                templates.firstOrNull { it.id == profileTemplateId }
            else -> null
        } ?: fallbackTemplateForMode(flightMode, templates)

        return when {
            profileCards != null -> {
                val source = baseTemplate
                FlightTemplate(
                    id = source?.id ?: profileTemplateId ?: fallbackTemplateIdFor(flightMode),
                    name = source?.name ?: "${flightMode.displayName} Custom",
                    description = source?.description ?: "Profile specific layout",
                    cardIds = profileCards,
                    icon = source?.icon ?: Icons.Default.Star,
                    isPreset = source?.isPreset ?: false
                )
            }
            else -> baseTemplate
        }
    }

    private fun fallbackTemplateForMode(
        flightMode: FlightModeSelection,
        templates: List<FlightTemplate>
    ): FlightTemplate? {
        val fallbackId = fallbackTemplateIdFor(flightMode)
        return templates.firstOrNull { it.id == fallbackId }
            ?: FlightTemplates.getDefaultTemplates().firstOrNull { it.id == fallbackId }
    }

    private fun fallbackTemplateIdFor(flightMode: FlightModeSelection): String =
        DEFAULT_TEMPLATE_IDS[flightMode] ?: DEFAULT_TEMPLATE_IDS[FlightModeSelection.CRUISE]!!

    private fun normalizeProfileId(profileId: ProfileId?): ProfileId =
        profileId ?: DEFAULT_PROFILE_ID

    companion object {
        private val DEFAULT_TEMPLATE_IDS = mapOf(
            FlightModeSelection.CRUISE to "id01",
            FlightModeSelection.THERMAL to "id02",
            FlightModeSelection.FINAL_GLIDE to "id03",
            FlightModeSelection.HAWK to "id04"
        )
    }
}
