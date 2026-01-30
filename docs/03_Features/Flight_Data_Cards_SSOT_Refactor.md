# Flight Data Cards - SSOT Refactoring Plan

**Created**: 2025-10-10
**Status**: Superseded - see `docs/2025-11-01-flight-data-cards-ssot-remediation-plan.md`
**Priority**: HIGH - Critical for long-term stability
**Estimated Time**: 2-3 hours

---

## z OBJECTIVE

Refactor the Flight Data Cards system to use **Single Source of Truth (SSOT)** architecture, eliminating all manual synchronization bugs and simplifying the codebase by ~350 lines.

---

##  PROBLEM STATEMENT

### Current Architecture Issues

**We fixed 5 synchronization bugs in one session:**
1. oe Templates blocking forever (`.collect()` issue)
2. oe Race condition in card application (async timing)
3. oe Redundant fallback in FlightDataManager
4. oe Unwanted fallback in FlightDataViewModel
5. oe UI not observing state changes

**Root Cause:** Multiple sources of truth with manual synchronization:
- `CardPreferences` (DataStore) - Persistent storage
- `FlightDataManager.allTemplates` - In-memory cache
- `FlightDataViewModel._cardStateFlows` - Display state
- `TemplateChangeNotifier` - Manual sync mechanism
- `templateVersion` - Manual change tracking

### SSOT Principle Violations

From CLAUDE.md:
> **EVERY piece of data MUST have exactly ONE authoritative source.**

**Current violations:**
1. oe **Dual Data Stores**: CardPreferences + FlightDataManager
2. oe **Manual Synchronization**: TemplateChangeNotifier + templateVersion
3. oe **Non-Reactive Updates**: LaunchedEffect triggers reload cycle
4. oe **Complex Flow**: 10+ steps from user action to display update

---

## ... SSOT DESIGN

### Core Principle

**FlightDataViewModel becomes the SINGLE source of truth:**
- ... Stores profile + mode -> cards mapping in memory
- ... All UI reads from ViewModel only
- ... All updates go through ViewModel only
- ... DataStore used ONLY for persistence (load once, save async)
- ... Zero manual synchronization

### Architecture Diagram

```
"oe""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
"                   FlightDataViewModel                        "
"                  (SINGLE SOURCE OF TRUTH)                    "
"                                                              "
"  _profileModeCards: Map<ProfileId, Map<Mode, List<CardId>>> "
"  _activeProfileId: StateFlow<String?>                       "
"  _currentFlightMode: StateFlow<FlightModeSelection>         "
"                                                              "
"  activeCards: StateFlow<List<CardState>> * DERIVED          "
"  (Automatically recomputes via combine())                    "
""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
                       ->                    *"
                       "                    "
            "oe"""""""""""'"""""""""  "oe""""""""'"""""""""""
            "                   "  "                   "
     "oe""""""-1/4"""""""    "oe""""""-1/4"""""""    "oe""""""-1/4"""""""
     " MapScreen   "    " FlightData  "    " CardPrefs   "
     " (Observer)  "    " Screen      "    " (Persistence"
     "             "    " (Observer + "    "  only)      "
     " Displays    "    "  Modifier)  "    "             "
     " cards       "    "             "    " Load once   "
     "             "    " Toggle      "    " Save async  "
     """"""""""""""""    " cards       "    """"""""""""""""
                        """"""""""""""""
```

### Key Design Elements

1. **Shared ViewModel Instance**
   - Activity-scoped ViewModel shared between MapScreen and FlightDataScreen
   - Both screens observe same StateFlow
   - No manual synchronization needed

2. **Reactive State Flow**
   ```kotlin
   val activeCards: StateFlow<List<CardState>> = combine(
       _profileModeCards,
       _activeProfileId,
       _currentFlightMode,
       _containerSize
   ) { configs, profileId, mode, size ->
       val cardIds = profileId?.let { configs[it]?.get(mode) } ?: emptyList()
       cardIds.map { cardId -> createCardState(cardId, size) }
   }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
   ```

3. **Single Update Path**
   ```kotlin
   fun updateCards(profileId: String, mode: FlightModeSelection, cardIds: List<String>) {
       // 1. Update SSOT immediately
       _profileModeCards.value = ...

       // 2. activeCards recomputes automatically

       // 3. UI updates automatically

       // 4. Save to DataStore (background, fire-and-forget)
       viewModelScope.launch {
           cardPreferences?.saveProfileTemplateCards(profileId, mode.name, cardIds)
       }
   }
   ```

---

## "< IMPLEMENTATION STEPS

### Phase 1: ViewModel Refactoring (Core SSOT)

**File:** `FlightDataViewModel.kt`

#### Step 1.1: Add SSOT State Properties

```kotlin
class FlightDataViewModel : ViewModel() {

    // ... SSOT: Profile + Mode -> Card IDs configuration
    private val _profileModeCards = MutableStateFlow<Map<String, Map<FlightModeSelection, List<String>>>>(emptyMap())

    // ... Current context
    private val _activeProfileId = MutableStateFlow<String?>(null)
    private val _currentFlightMode = MutableStateFlow(FlightModeSelection.CRUISE)
    private val _containerSize = MutableStateFlow(IntSize.Zero)

    // ... DERIVED STATE: Automatically computed active cards
    val activeCards: StateFlow<List<CardState>> = combine(
        _profileModeCards,
        _activeProfileId,
        _currentFlightMode,
        _containerSize
    ) { configs, profileId, mode, containerSize ->
        if (profileId == null || containerSize == IntSize.Zero) {
            emptyList()
        } else {
            val cardIds = configs[profileId]?.get(mode) ?: emptyList()
            cardIds.mapIndexedNotNull { index, cardId ->
                createCardStateFromId(cardId, index, containerSize)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Keep existing _cardStateFlows for backward compatibility during transition
    // Will be deleted after migration complete
}
```

#### Step 1.2: Add SSOT Update Methods

```kotlin
/**
 * ... SSOT: Single update path for card configuration
 * Updates in-memory SSOT immediately, saves to DataStore async
 */
fun updateCards(profileId: String, mode: FlightModeSelection, cardIds: List<String>) {
    Log.d(TAG, """ updateCards: Profile '$profileId', Mode '${mode.name}', Cards: ${cardIds.joinToString(",")}")

    // 1. Update SSOT immediately
    _profileModeCards.value = _profileModeCards.value.toMutableMap().apply {
        val modeMap = this[profileId]?.toMutableMap() ?: mutableMapOf()
        modeMap[mode] = cardIds
        this[profileId] = modeMap
    }

    // 2. activeCards StateFlow recomputes automatically via combine()

    // 3. UI observing activeCards recomposes automatically

    // 4. Save to DataStore (fire-and-forget, for persistence only)
    viewModelScope.launch {
        cardPreferences?.saveProfileTemplateCards(profileId, mode.name, cardIds)
        Log.d(TAG, "'3/4 Saved to DataStore: Profile '$profileId', Mode '${mode.name}', Cards: ${cardIds.joinToString(",")}")
    }
}

/**
 * ... SSOT: Switch active profile
 */
fun setActiveProfile(profileId: String?) {
    Log.d(TAG, "'$ setActiveProfile: $profileId")
    _activeProfileId.value = profileId
    // activeCards recomputes automatically
}

/**
 * ... SSOT: Switch flight mode
 */
fun setFlightMode(mode: FlightModeSelection) {
    Log.d(TAG, "^ setFlightMode: ${mode.name}")
    _currentFlightMode.value = mode
    // activeCards recomputes automatically
}

/**
 * ... SSOT: Update container size
 */
fun setContainerSize(size: IntSize) {
    if (_containerSize.value != size) {
        Log.d(TAG, "" setContainerSize: $size")
        _containerSize.value = size
        // activeCards recomputes automatically with new positions
    }
}
```

#### Step 1.3: Add Initialization Method

```kotlin
/**
 * ... SSOT: Load all profile configurations from DataStore on init
 * Called once when ViewModel is created
 */
suspend fun initializeFromDataStore(profileIds: List<String>) {
    Log.d(TAG, " initializeFromDataStore: Loading ${profileIds.size} profiles")

    val allConfigs = mutableMapOf<String, Map<FlightModeSelection, List<String>>>()

    profileIds.forEach { profileId ->
        val modeConfigs = mutableMapOf<FlightModeSelection, List<String>>()

        FlightModeSelection.values().forEach { mode ->
            val cardIds = cardPreferences?.getProfileTemplateCards(profileId, mode.name)?.first()
            if (cardIds != null) {
                modeConfigs[mode] = cardIds
                Log.d(TAG, "  "< Profile '$profileId', Mode '${mode.name}': ${cardIds.size} cards")
            }
        }

        allConfigs[profileId] = modeConfigs
    }

    _profileModeCards.value = allConfigs
    Log.d(TAG, "... Initialized with ${allConfigs.size} profile configurations")
}
```

#### Step 1.4: Helper Method for Card Creation

```kotlin
/**
 * Create CardState from card ID for current configuration
 */
private fun createCardStateFromId(cardId: String, index: Int, containerSize: IntSize): CardState? {
    val cardDefinition = CardLibrary.allCards.find { it.id == cardId } ?: return null

    // Use grid layout for positioning
    val cardWidth = 120.dp.toPx()
    val cardHeight = 80.dp.toPx()
    val spacing = 16.dp.toPx()
    val padding = 16.dp.toPx()

    val cols = ((containerSize.width - padding * 2 + spacing) / (cardWidth + spacing)).toInt().coerceAtLeast(1)
    val row = index / cols
    val col = index % cols
    val x = padding + col * (cardWidth + spacing)
    val y = padding + row * (cardHeight + spacing)

    return CardState(
        id = cardDefinition.id,
        x = x,
        y = y,
        width = cardWidth,
        height = cardHeight,
        flightData = FlightData(
            id = cardDefinition.id,
            label = cardDefinition.title,
            primaryValue = "--",
            secondaryValue = "INIT",
            labelFontSize = cardDefinition.labelFontSize,
            primaryFontSize = cardDefinition.primaryFontSize,
            secondaryFontSize = cardDefinition.secondaryFontSize
        )
    )
}
```

---

### Phase 2: FlightDataScreensTab Integration

**File:** `FlightDataScreensTab.kt`

#### Step 2.1: Update Card Toggle Handler

**BEFORE (lines 278-336):**
```kotlin
onCardToggle = { cardId, isSelected ->
    // Complex logic: update template, save to CardPreferences,
    // notify TemplateChangeNotifier, etc.
}
```

**AFTER:**
```kotlin
onCardToggle = { cardId, isSelected ->
    Log.d(TAG, "f Card toggle: $cardId, selected: $isSelected")

    activeProfile?.let { profile ->
        // Get current cards for this profile + mode
        val currentCards = flightViewModel.getCardsForProfileMode(profile.id, selectedFlightMode)

        // Update card list
        val updatedCards = if (isSelected) {
            currentCards + cardId
        } else {
            currentCards - cardId
        }

        // ... SSOT: Single update call
        flightViewModel.updateCards(profile.id, selectedFlightMode, updatedCards)

        Log.d(TAG, "... Updated cards: ${updatedCards.size} cards")
    }
}
```

#### Step 2.2: Remove Template Sync Logic

**DELETE these sections:**
- oe `cardPreferences.saveProfileTemplateCards()` call (line 307-312)
- oe `cardPreferences.saveProfileFlightModeTemplate()` call (line 315-321)
- oe `TemplateChangeNotifier.notifyTemplateChanged()` call (line 324)

**Result:** ~30 lines deleted, replaced with 1 line `flightViewModel.updateCards()`

---

### Phase 3: MapScreen Integration

**File:** `MapScreen.kt`

#### Step 3.1: Make ViewModel Activity-Scoped

**BEFORE:**
```kotlin
@Composable
fun MapScreen(...) {
    val flightViewModel: FlightDataViewModel = viewModel()
    // ...
}
```

**AFTER:**
```kotlin
@Composable
fun MapScreen(
    flightViewModel: FlightDataViewModel,  // ... Injected from parent
    // ... other params
) {
    // ... Same instance shared with FlightDataScreen
}
```

#### Step 3.2: Update CardContainer Call

**BEFORE:**
```kotlin
CardContainer(
    viewModel = flightViewModel,
    // ...
)
```

**AFTER:**
```kotlin
// ... Observe active cards from SSOT
val activeCards by flightViewModel.activeCards.collectAsState()

CardContainer(
    cards = activeCards,  // ... Pass cards directly, not ViewModel
    onCardUpdated = { updatedCard ->
        flightViewModel.updateCardPosition(updatedCard)
    },
    // ...
)
```

#### Step 3.3: Set Profile and Mode

```kotlin
// Update profile when it changes
LaunchedEffect(uiState.activeProfile?.id) {
    flightViewModel.setActiveProfile(uiState.activeProfile?.id)
}

// Update flight mode when it changes
LaunchedEffect(mapState.currentMode) {
    val mode = when (mapState.currentMode) {
        FlightMode.CRUISE -> FlightModeSelection.CRUISE
        FlightMode.THERMAL -> FlightModeSelection.THERMAL
        FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
    }
    flightViewModel.setFlightMode(mode)
}

// Update container size
LaunchedEffect(safeContainerSize) {
    flightViewModel.setContainerSize(safeContainerSize)
}
```

---

### Phase 4: MainActivity Integration

**File:** `MainActivity.kt`

#### Step 4.1: Create Activity-Scoped ViewModel

```kotlin
@Composable
fun MainNavigation() {
    // ... Create activity-scoped ViewModel
    val flightDataViewModel: FlightDataViewModel = viewModel()

    // Initialize from DataStore once
    val profileViewModel: ProfileViewModel = viewModel()
    val profiles by profileViewModel.profiles.collectAsState()

    LaunchedEffect(profiles) {
        val profileIds = profiles.map { it.id }
        flightDataViewModel.initializeFromDataStore(profileIds)
    }

    NavHost(...) {
        composable("map") {
            MapScreen(
                flightViewModel = flightDataViewModel,  // ... Pass shared instance
                // ...
            )
        }
        composable("flightdata") {
            FlightDataMgmt(
                flightViewModel = flightDataViewModel,  // ... Pass shared instance
                // ...
            )
        }
    }
}
```

---

### Phase 5: Cleanup - Delete Obsolete Code

#### Step 5.1: Delete Files

**Delete these entire files:**
- oe `app/src/main/java/com/example/xcpro/map/TemplateChangeNotifier.kt`

#### Step 5.2: Delete from FlightDataManager.kt

**Delete these sections:**
- oe `templateVersion` property (line 52-53)
- oe `incrementTemplateVersion()` method (lines 302-308)
- oe `loadTemplateForProfile()` method (lines 162-237)
- oe `applyTemplateToProfile()` method (lines 242-290)
- oe `loadAllTemplates()` method (lines 154-157)

**Result:** FlightDataManager shrinks from 391 lines -> ~150 lines

#### Step 5.3: Simplify MapComposeEffects.kt

**DELETE entire ProfileAndConfigurationEffects function** (lines 59-122)

**Replace with:**
```kotlin
/**
 * Profile and flight mode effects - SSOT version
 */
@Composable
fun ProfileAndModeEffects(
    uiState: ProfileUiState,
    flightDataManager: FlightDataManager,
    mapState: MapScreenState,
    flightViewModel: FlightDataViewModel
) {
    // Update ViewModel when profile changes
    LaunchedEffect(uiState.activeProfile?.id) {
        flightViewModel.setActiveProfile(uiState.activeProfile?.id)
    }

    // Update ViewModel when flight mode changes
    LaunchedEffect(mapState.currentMode) {
        val mode = when (mapState.currentMode) {
            FlightMode.CRUISE -> FlightModeSelection.CRUISE
            FlightMode.THERMAL -> FlightModeSelection.THERMAL
            FlightMode.FINAL_GLIDE -> FlightModeSelection.FINAL_GLIDE
        }
        flightViewModel.setFlightMode(mode)
    }
}
```

**Result:** MapComposeEffects shrinks from 303 lines -> ~200 lines

#### Step 5.4: Simplify CardContainer.kt

**BEFORE (lines 42-48):**
```kotlin
// Complex observation with remember()
val selectedCardIds by viewModel.selectedCardIds.collectAsState()
val cardStateFlows = remember(selectedCardIds) { viewModel.cardStateFlows }
```

**AFTER:**
```kotlin
@Composable
fun CardContainer(
    cards: List<CardState>,  // ... Direct cards list from SSOT
    onCardUpdated: (CardState) -> Unit,
    // ... other params
) {
    // ... Simple iteration - no complex observation needed
    cards.forEach { card ->
        EnhancedGestureCard(
            cardState = card,
            onCardUpdated = onCardUpdated,
            // ...
        )
    }
}
```

---

## "s IMPACT ANALYSIS

### Code Reduction

| File | Before | After | Reduction |
|------|--------|-------|-----------|
| FlightDataViewModel.kt | 548 lines | ~400 lines | -148 lines |
| FlightDataManager.kt | 391 lines | ~150 lines | -241 lines |
| MapComposeEffects.kt | 303 lines | ~200 lines | -103 lines |
| FlightDataScreensTab.kt | 367 lines | ~340 lines | -27 lines |
| TemplateChangeNotifier.kt | 25 lines | **DELETED** | -25 lines |
| **TOTAL** | **1,634 lines** | **~1,090 lines** | **-544 lines (33% reduction)** |

### Complexity Reduction

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Data sources | 3 (ViewModel, Manager, DataStore) | 1 (ViewModel) | 66% reduction |
| Sync mechanisms | 2 (Notifier + version) | 0 | 100% reduction |
| Update steps | 10+ | 3 | 70% reduction |
| Manual sync points | 4 | 0 | 100% reduction |
| LaunchedEffects for sync | 3 | 0 | 100% reduction |

### Performance Improvement

| Operation | Before | After | Improvement |
|-----------|--------|-------|-------------|
| Card toggle -> display | ~500ms (reload cycle) | <16ms (recomposition) | 30x faster |
| Flight mode switch | DataStore read + reload | Instant recomputation | 100x faster |
| Profile switch | DataStore read + reload | Instant recomputation | 100x faster |

---

## ... PRESERVED FUNCTIONALITY

### 1. Profile Awareness ...
Each profile still has independent card configurations per flight mode.

**BEFORE:**
```
Profile "Competition" -> CRUISE -> ["track", "gps_alt"]
Profile "Casual" -> CRUISE -> ["track"]
```

**AFTER:** (Same functionality)
```kotlin
_profileModeCards.value = {
    "competition_profile": {
        FlightModeSelection.CRUISE: ["track", "gps_alt"]
    },
    "casual_profile": {
        FlightModeSelection.CRUISE: ["track"]
    }
}
```

### 2. Three Flight Modes ...
Each flight mode can have different cards.

**BEFORE:**
```
CRUISE -> 1 card
THERMAL -> 3 cards
FINAL_GLIDE -> 5 cards
```

**AFTER:** (Same functionality)
```kotlin
configs["profile1"] = {
    FlightModeSelection.CRUISE: ["track"],
    FlightModeSelection.THERMAL: ["vario", "thermal_avg", "track"],
    FlightModeSelection.FINAL_GLIDE: ["gps_alt", "final_gld", "ground_speed", "ld_curr", "wpt_dist"]
}
```

### 3. User Customization ...
Toggle cards on/off in Flight Data screen still works.

### 4. Flight Mode Visibility ...
Hide/show flight modes per profile still works (stored separately in CardPreferences).

### 5. Live Data Updates ...
GPS/vario/sensor data still updates cards in real-time.

### 6. Manual Card Positioning ...
Drag cards around map still works (saved to DataStore separately).

### 7. DataStore Persistence ...
Still saves to DataStore for app restart persistence.

---

## sectiona TESTING STRATEGY

### Phase 1: Unit Tests (ViewModel)

```kotlin
class FlightDataViewModelTest {

    @Test
    fun `updateCards updates SSOT immediately`() {
        val viewModel = FlightDataViewModel()

        viewModel.updateCards("profile1", FlightModeSelection.CRUISE, listOf("track", "gps_alt"))

        val cards = viewModel.activeCards.value
        assertEquals(2, cards.size)
        assertEquals("track", cards[0].id)
        assertEquals("gps_alt", cards[1].id)
    }

    @Test
    fun `switching flight mode updates active cards`() {
        val viewModel = FlightDataViewModel()

        // Setup: Profile has different cards per mode
        viewModel.updateCards("profile1", FlightModeSelection.CRUISE, listOf("track"))
        viewModel.updateCards("profile1", FlightModeSelection.THERMAL, listOf("vario", "thermal_avg"))
        viewModel.setActiveProfile("profile1")

        // Initially CRUISE
        viewModel.setFlightMode(FlightModeSelection.CRUISE)
        assertEquals(1, viewModel.activeCards.value.size)

        // Switch to THERMAL
        viewModel.setFlightMode(FlightModeSelection.THERMAL)
        assertEquals(2, viewModel.activeCards.value.size)
    }

    @Test
    fun `switching profile updates active cards`() {
        val viewModel = FlightDataViewModel()

        // Setup: Two profiles with different cards
        viewModel.updateCards("profile1", FlightModeSelection.CRUISE, listOf("track"))
        viewModel.updateCards("profile2", FlightModeSelection.CRUISE, listOf("gps_alt", "vario"))
        viewModel.setFlightMode(FlightModeSelection.CRUISE)

        // Profile 1
        viewModel.setActiveProfile("profile1")
        assertEquals(1, viewModel.activeCards.value.size)

        // Profile 2
        viewModel.setActiveProfile("profile2")
        assertEquals(2, viewModel.activeCards.value.size)
    }
}
```

### Phase 2: Integration Tests

**Test Scenarios:**
1. ... Toggle card in Flight Data screen -> Card appears on map instantly
2. ... Toggle card off -> Card disappears from map instantly
3. ... Switch flight mode -> Different cards displayed
4. ... Switch profile -> Different cards displayed
5. ... Close app, reopen -> Cards restored from DataStore
6. ... Live data updates -> Cards show real-time GPS/vario data

### Phase 3: Manual Testing Checklist

- [ ] Create new profile
- [ ] Toggle cards for CRUISE mode
- [ ] Switch to map -> Verify cards displayed
- [ ] Toggle cards for THERMAL mode
- [ ] Switch flight mode on map -> Verify different cards
- [ ] Create second profile
- [ ] Configure different cards
- [ ] Switch profiles -> Verify correct cards per profile
- [ ] Close app completely
- [ ] Reopen app -> Verify all cards restored
- [ ] Drag card on map -> Verify position saved
- [ ] Toggle card off -> Verify card removed immediately

---

##  ROLLBACK PLAN

If SSOT implementation causes issues:

### Quick Rollback
```bash
git checkout fix/flight-data-cards-empty-template
git branch -D feature/ssot-refactor
```

### Partial Rollback
Keep SSOT ViewModel but restore old MapComposeEffects:
```bash
git checkout fix/flight-data-cards-empty-template -- app/src/main/java/com/example/xcpro/map/MapComposeEffects.kt
```

---

## " REFERENCES

- **CLAUDE.md** - SSOT requirements (lines 22-130)
- **Flight_Data_Cards.md** - Current system documentation
- **Current Branch** - `fix/flight-data-cards-empty-template` (5 bug fixes)

---

## z SUCCESS CRITERIA

**SSOT implementation is successful when:**
1. ... Toggle card in Flight Data -> Appears on map in <50ms
2. ... Switch flight mode -> Cards update instantly (no reload)
3. ... Switch profile -> Cards update instantly (no reload)
4. ... Zero manual synchronization code
5. ... Zero LaunchedEffects for card loading
6. ... All 3 flight modes work independently per profile
7. ... DataStore persistence still works
8. ... Unit tests pass (95%+ coverage)
9. ... Manual testing checklist complete
10. ... Code reduced by 500+ lines

---

##  NEXT STEPS

**When ready to implement:**
1. Create new branch: `feature/ssot-refactor`
2. Follow implementation steps in order (Phase 1 -> Phase 5)
3. Test after each phase
4. Run full test suite before merging
5. Update Flight_Data_Cards.md documentation
6. Delete this planning document (mission accomplished)

---

*Generated by Claude Code - 2025-10-10*
*Reference this document when implementing SSOT refactoring*


