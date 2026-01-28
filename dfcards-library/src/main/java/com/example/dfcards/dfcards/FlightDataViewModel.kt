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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
        FlightDataFlowBuilder.activeCardIds(
            profileModeCards = _profileModeCards,
            activeProfileId = _activeProfileId,
            currentFlightMode = _currentFlightMode,
            scope = viewModelScope
        )

    val activeCards: StateFlow<List<CardState>> =
        FlightDataFlowBuilder.activeCards(
            activeCardIds = activeCardIds,
            allStates = cardsUseCase.legacyCardStates,
            scope = viewModelScope
        )

    val activeTemplateId: StateFlow<String?> =
        FlightDataFlowBuilder.activeTemplateId(
            profileModeTemplates = _profileModeTemplates,
            activeProfileId = _activeProfileId,
            currentFlightMode = _currentFlightMode,
            scope = viewModelScope
        )

    private var templatesJob: Job? = null

    fun initializeCardPreferences(preferences: CardPreferences) {
        if (_cardPreferences.value === preferences) return
        logDebug("initializeCardPreferences: ${preferences.hashCode()}")
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
        logDebug("ensureCardsExist: ids=${cardIds.size}")
        cardsUseCase.ensureCardsExist(cardIds)
    }

    fun setActiveProfile(profileId: ProfileId?) {
        if (_activeProfileId.value == profileId) return
        logDebug("setActiveProfile: $profileId")
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
        val update = FlightDataStateMapper.updateProfileCards(
            profileModeCards = _profileModeCards,
            profileId = profileId,
            flightMode = flightMode,
            cardIds = cardIds
        )
        logDebug("setProfileCards: profile=${update.profileId} mode=$flightMode cards=${update.cardIds.size}")
        if (FlightVisibility.normalizeProfileId(_activeProfileId.value) == update.profileId &&
            _currentFlightMode.value == flightMode
        ) {
            cardsUseCase.setSelected(update.cardIds.toSet())
            cardsUseCase.ensureCardsExist(update.cardIds.toSet())
        }
        profileStore.ensureVisibilityEntry(update.profileId)
        persistProfileCards(update.profileId, flightMode, update.cardIds)
    }

    fun setProfileTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        templateId: String
    ) {
        val update = FlightDataStateMapper.updateProfileTemplate(
            profileModeTemplates = _profileModeTemplates,
            profileId = profileId,
            flightMode = flightMode,
            templateId = templateId
        )
        logDebug("setProfileTemplate: profile=${update.profileId} mode=$flightMode template=$templateId")
        profileStore.ensureVisibilityEntry(update.profileId)
        persistProfileTemplate(update.profileId, flightMode, templateId)
    }

    suspend fun selectProfileTemplate(
        profileId: ProfileId?,
        flightMode: FlightModeSelection,
        template: FlightTemplate
    ) {
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        logDebug("selectProfileTemplate: profile=$targetProfileId mode=$flightMode template=${template.id}")
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
        val update = FlightDataStateMapper.updateProfileVisibility(
            profileModeVisibilities = _profileModeVisibilities,
            profileId = profileId,
            flightMode = flightMode,
            isVisible = isVisible
        )
        val preferences = _cardPreferences.value ?: return
        viewModelScope.launch(ioDispatcher) {
            preferences.saveProfileFlightModeVisibility(update.profileId, flightMode.name, isVisible)
        }
    }

    fun toggleProfileFlightModeVisibility(profileId: ProfileId?, flightMode: FlightModeSelection) {
        if (flightMode == FlightModeSelection.CRUISE) return
        val targetProfileId = FlightVisibility.normalizeProfileId(profileId)
        val current = _profileModeVisibilities.value[targetProfileId]?.get(flightMode) ?: true
        setProfileFlightModeVisibility(profileId, flightMode, !current)
    }

    fun flightModeVisibilitiesFor(profileId: ProfileId?): Map<FlightModeSelection, Boolean> {
        return FlightDataStateMapper.flightModeVisibilitiesFor(
            profileModeVisibilities = _profileModeVisibilities.value,
            profileId = profileId
        )
    }

    fun getProfileCards(profileId: ProfileId?, flightMode: FlightModeSelection): List<String> =
        FlightDataStateMapper.getProfileCards(
            profileModeCards = _profileModeCards.value,
            profileId = profileId,
            flightMode = flightMode
        )

    fun getProfileTemplateId(profileId: ProfileId?, flightMode: FlightModeSelection): String? =
        FlightDataStateMapper.getProfileTemplateId(
            profileModeTemplates = _profileModeTemplates.value,
            profileId = profileId,
            flightMode = flightMode
        )

    fun clearProfile(profileId: ProfileId) {
        FlightDataUiEventHandler.clearProfile(
            profileId = profileId,
            profileModeCards = _profileModeCards,
            profileModeTemplates = _profileModeTemplates,
            activeProfileId = _activeProfileId,
            cardsUseCase = cardsUseCase,
            cardPreferences = _cardPreferences,
            scope = viewModelScope,
            ioDispatcher = ioDispatcher
        )
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
        logDebug("prepareCardsForProfile(profile=$profileId, mode=$flightMode, size=${containerSize.width}x${containerSize.height})")
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
        return FlightDataStateMapper.allTemplateCardCounts(
            profileModeCards = _profileModeCards.value,
            profileModeTemplates = _profileModeTemplates.value,
            profileId = profileId
        )
    }

    fun templateCardCounts(profileId: ProfileId?, flightMode: FlightModeSelection): Map<String, Int> {
        return FlightDataStateMapper.templateCardCounts(
            profileModeCards = _profileModeCards.value,
            profileModeTemplates = _profileModeTemplates.value,
            profileId = profileId,
            flightMode = flightMode
        )
    }

    private fun syncSelectedIdsWithRepository() {
        FlightDataUiEventHandler.syncSelectedIdsWithRepository(
            profileModeCards = _profileModeCards,
            activeProfileId = _activeProfileId,
            currentFlightMode = _currentFlightMode,
            cardsUseCase = cardsUseCase,
            setProfileCards = { profileId, flightMode, cardIds ->
                setProfileCards(profileId, flightMode, cardIds)
            }
        )
    }

    private fun persistActiveCards() {
        FlightDataUiEventHandler.persistActiveCards(
            activeProfileId = _activeProfileId,
            currentFlightMode = _currentFlightMode,
            cardsUseCase = cardsUseCase,
            setProfileCards = { profileId, flightMode, cardIds ->
                setProfileCards(profileId, flightMode, cardIds)
            }
        )
    }

    private fun persistTemplateSelection(templateId: String) {
        FlightDataUiEventHandler.persistTemplateSelection(
            activeProfileId = _activeProfileId,
            currentFlightMode = _currentFlightMode,
            setProfileTemplate = { profileId, flightMode, template ->
                setProfileTemplate(profileId, flightMode, template)
            },
            templateId = templateId
        )
    }

    private fun persistProfileCards(
        profileId: ProfileId,
        flightMode: FlightModeSelection,
        cardIds: List<String>
    ) {
        val templateId = getTemplateIdForPersistence(profileId, flightMode)
        FlightDataUiEventHandler.persistProfileCards(
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            cardPreferences = _cardPreferences,
            profileId = profileId,
            templateId = templateId,
            cardIds = cardIds
        )
    }

    private fun persistProfileTemplate(
        profileId: ProfileId,
        flightMode: FlightModeSelection,
        templateId: String
    ) {
        FlightDataUiEventHandler.persistProfileTemplate(
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
            cardPreferences = _cardPreferences,
            profileId = profileId,
            flightMode = flightMode,
            templateId = templateId
        )
    }

    private fun getTemplateIdForPersistence(
        profileId: ProfileId,
        flightMode: FlightModeSelection
    ): String =
        FlightDataStateMapper.resolveTemplateIdForPersistence(
            profileModeTemplates = _profileModeTemplates.value,
            profileId = profileId,
            flightMode = flightMode,
            setProfileTemplate = { targetProfileId, mode, templateId ->
                setProfileTemplate(targetProfileId, mode, templateId)
            }
        )

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }
}
