# Flight Data Cards: Architecture, Persistence, and Debug Notes

This document summarizes how flight data cards are configured, persisted, and rendered, plus the
recent fixes and debugging steps used on the Levo branch. It is intended for future Codex agents.

## High-level flow
- **FlightDataViewModel** (dfcards-library) is the SSOT for card selection, templates, and live data
  mapping. It persists profile/template selections via **CardPreferences**.
- **FlightCardsUseCase** wraps **CardStateRepository** and derivations. It owns the card state map
  and live update logic.
- **CardStateRepository** owns card state flows, layout, persistence, and live updates.
- **CardContainer** (dfcards-library) renders cards from the view model's state flows.
- **MapScreenRoot / MapComposeEffects** (feature/map) wires the map screen, profile, mode, and
  card view model to card rendering and live data updates.
- **FlightDataMgmt / FlightDataScreensTab** (feature/map) is the Flight Data UI where users select
  templates and cards.
- **FlightDataManager** (feature/map) is a UI-facing bridge for live data samples, mode selection,
  and card data flow (used by map and Flight Data UI).

## Key responsibilities
### FlightDataViewModel (dfcards-library)
- Holds:
  - `profileModeCards`: profile -> mode -> card IDs
  - `profileModeTemplates`: profile -> mode -> template ID
  - `selectedCardIds` / `activeCardIds` / `activeCards`
- Persists:
  - `saveProfileTemplateCards(profileId, templateId, cardIds)`
  - `saveProfileFlightModeTemplate(profileId, mode, templateId)`
- Provides:
  - `prepareCardsForProfile(profileId, flightMode, containerSize, density)`
  - `selectProfileTemplate(profileId, flightMode, template)`
  - `setProfileCards(profileId, flightMode, cardIds)`
  - `setProfileTemplate(profileId, flightMode, templateId)`
  - `ensureCardsExist(cardIds)`

### CardStateRepository (dfcards-library)
- Owns `cardStateFlowsMap` and exposes it via `cardStateFlows`.
- Maintains layout based on:
  - `cardsAcrossPortrait` (default from CardPreferences)
  - `cardsAnchorPortrait`
  - last known container size and density
- Supports:
  - `initializeCards(containerSize, density)`
  - `applyTemplate(...)`
  - `ensureCardsExist(cardIds)` (creates missing states using last layout)
  - `updateCardsWithLiveData(liveData)`

### CardContainer (dfcards-library)
- Renders cards from `viewModel.cardStateFlows` and `selectedCardIds`.
- Uses `onSizeChanged/onGloballyPositioned` to report safe container size.
- Ensures layout + missing card state creation once it has a size.

### MapScreenRoot / MapComposeEffects (feature/map)
- `FlightDataViewModel` must be scoped to the **map backstack entry** to share state across screens:
  - `val mapEntry = navController.getBackStackEntry("map")`
  - `val flightViewModel: FlightDataViewModel = viewModel(mapEntry)`
- Map effects call:
  - `prepareCardsForProfile(...)` on mode/profile changes or container size updates.
  - `updateCardsWithLiveData(...)` on `cardFlightDataFlow` updates (gated by `cardHydrationReady`).

### FlightDataScreensTab (feature/map)
- Template selection:
  - Do nothing if the user re-selects the same template.
  - If new template selected, call `selectProfileTemplate(...)` so the profile-specific card list
    is restored (if saved).
- Card selection:
  - Always persist card IDs via `setProfileCards(...)`.
  - Persist template id before cards so the card list is stored under the correct template key.

## Persistence (CardPreferences)
- Profile-specific template cards:
  - Key: `profile_{profileId}_template_{templateId}_cards`
  - Stored as comma-separated card IDs.
- Profile mode template mapping:
  - Stored by `saveProfileFlightModeTemplate(profileId, mode, templateId)`.

## Known fixes (Levo)
1) **Cards not appearing on map** even though selections persisted:
   - Root cause: `FlightCardsUseCase.cardStateFlows` captured the map once at init time, so it never
     reflected newly created card states. Result: `selectedCardIds` present, `cardStateFlows` empty.
   - Fix: change `cardStateFlows` to a **getter** that reads from repository each time.

2) **Re-tapping template clears selections**:
   - Fix: in `FlightDataScreensTab`, no-op if the selected template is tapped again. For new template,
     call `selectProfileTemplate(...)` which uses stored profile template cards when available.

3) **Cards selected in Flight Data but not created for map**:
   - Added `ensureCardsExist(cardIds)` in `CardStateRepository` and routed it from `FlightDataViewModel`.
   - `CardContainer` calls `ensureCardsExist(selectedCardIds)` once it has size.

4) **VM scoping mismatch** (cards updated in Flight Data but map uses a different VM):
   - `FlightDataViewModel` is scoped to the map backstack entry in **both** MapScreenRoot and FlightDataMgmt.

## Debugging signals (log tags)
Useful log tags when cards fail to render:
- `FlightDataViewModel`
- `CardContainer`
- `MapComposeEffects`
- `MapScreenRoot`
- `FlightDataScreensTab`

Expected pattern when things work:
- `prepareCardsForProfile(...)` called with non-zero container size.
- `ensureCardsExist: ids=N` when cards are selected.
- `CardContainer: size=... selected=N active=N flows=N` where `flows` should be **non-zero**.

If `flows=0` but `selected>0`:
- Check `FlightCardsUseCase.cardStateFlows` is a getter (not a cached map).

## Quick validation checklist
1) Open app -> Flight Data -> select template + cards.
2) Return to map -> cards should render immediately.
3) Confirm template re-tap does not clear selected cards.
4) Confirm selection persists after switching modes.

## Notes
- App uninstall wipes DataStore; profiles and card selections will reset unless you implement
  explicit export/import.
- `cardHydrationReady` gates live card updates; cards should still render (with placeholder values)
  as long as state flows exist.


