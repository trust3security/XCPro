# REFACTOR_CARDS_PIPELINE.md

Owner: DFA
Status: Complete
Last updated: 2026-02-02

## Purpose
Refactor the cards/UI data path to be deterministic, low-latency, and simple to maintain.
This plan focuses on correctness of card values, predictable update cadence, and a thinner
MapScreenViewModel.

## Goals
- Cards consume a single, authoritative RealTimeFlightData stream.
- Update cadence is explicit, measured, and owned by a single layer for cards.
- Reduce ViewModel wiring complexity by moving adapters/managers into DI or small coordinators.
- Add lightweight tests that prevent silent card regressions.

## Non-goals
- Change fusion math, filters, or vario algorithms.
- Redesign UI visuals or card layouts.
- Modify replay logic beyond the card data handoff.

## Current pipeline summary (as of 2026-02-02)
CompleteFlightData
  -> FlightDataUiAdapter (wraps MapScreenObservers)
  -> convertToRealTimeFlightData(...)
  -> FlightDataManager.cardFlightDataFlow
  -> CardIngestionCoordinator
  -> dfcards FlightDataViewModel.updateCardsWithLiveData(...)
  -> CardLibrary / EnhancedFlightDataCard

Additional consumers:
- MapComposeEffects uses FlightDataManager.liveFlightDataFlow for orientation updates and replay pose.
- MapComposeEffects calls prepareCardsForProfile(...) on profile/mode/size changes.
- CardIngestionCoordinator initializes card preferences, starts the independent clock timer (local_time),
  applies cardHydrationReady gating, consumes the buffered sample, and pushes units preferences.
- CardContainer initializes card layout (initializeCards/ensureCardsExist) and injects CardStrings/CardTimeFormatter.
- CardContainer safe size -> MapScreenViewModel.updateSafeContainerSize -> MapStateStore.safeContainerSize;
  FlightDataUiAdapter/MapScreenObservers sets containerReady.
- MapScreenRoot seeds a fallback safe size from screen metrics if CardContainer is delayed
  (ensureSafeContainerFallback).
- cardHydrationReady (containerReady + liveDataReady) gates card updates;
  FlightDataManager buffers one sample until cards are ready.
- FlightDataMgmt binds the coordinator and uses FlightDataManager.liveFlightDataFlow (read-only)
  for CardsGrid/TemplateEditor previews.

## Current update cadence (observed in code)
- FlightDataManager:
  - numeric throttle ~83ms (`UI_NUMERIC_FRAME_MS`) for overlays.
  - needle throttle ~33ms (`UI_NEEDLE_FRAME_MS`).
  - cardFlightDataFlow is bucketed and unthrottled (near pass-through).
- dfcards CardStateRepository:
  - FAST tier: 80ms
  - PRIMARY tier: 250ms
  - BACKGROUND tier: 1000ms

Effective cadence (code-defined):
- Fast cards: 80ms.
- Primary cards: 250ms.
- Background cards: 1000ms.

Cadence owner decision (2026-02-02):
- Owner = dfcards tiered updates (FAST/PRIMARY/BACKGROUND).
- cardFlightDataFlow remains unthrottled so only dfcards enforces cadence.

Net effect: single cadence gate for cards (dfcards tiers).

## Timebase notes (RealTimeFlightData)
- timestamp is sourced from CompleteFlightData.outputTimestampMillis
  (live: wall time, replay: IGC time).
- lastUpdateTime is set by MapScreenObservers using gps timestamp or output timestamp.
- CardFormatSpec uses lastUpdateTime/timestamp for LOCAL_TIME formatting in some contexts.

## Risks
- Accidentally slowing or over-throttling card updates.
- Breaking existing card mapping semantics (units, labels, valid/invalid flags).
- Introducing extra recompositions or jank by changing flow cadence.
- Breaking card hydration or preview behavior (cardHydrationReady + buffered sample).
- Confusing timebase semantics for LOCAL_TIME or other time-derived cards.
- Local_time freezing if the independent clock timer or CardTimeFormatter wiring is dropped.
- Duplicate updateCardsWithLiveData callers (MapComposeEffects + FlightDataMgmt) causing redundant updates.
- Live data ingestion tied to Compose lifecycle, making start/stop behavior harder to reason about.
- Undefined null-data behavior (replay stop or sensor loss) leaving stale card values.
- Coordinator binding only on Map screen could break FlightDataMgmt-first entry; bind must be
  idempotent and callable from both screens (or a shared entry point).
- Fallback safe size can mark containerReady before CardContainer is measured; ingestion may start
  early unless explicitly gated.
- Buffered card sample is raw (not display-bucketed), so the first card update can differ from
  steady-state cardFlow output.
- Coordinator must handle the case where cardHydrationReady is already true at bind time.

## Plan

### Phase 0: Baseline + measurement
Deliverables:
- Add small contract tests that map CompleteFlightData -> RealTimeFlightData.
- Add small tests that map RealTimeFlightData -> card strings for a few key cards.
- Record effective card update cadence (code-defined; validate in live mode if needed).
- Document the target cadence and owner in this plan and in PIPELINE.md.
- Confirm intended timebase for RealTimeFlightData.timestamp and lastUpdateTime
  (live vs replay) and encode it in tests or docs.
- Confirm local_time updates every second (independent clock timer still active).
- Document cardHydrationReady gate (safe size + first data) and all updateCardsWithLiveData call sites.
- Define expected behavior when live data becomes null (freeze vs clear) and add a test for it.
- Record where the coordinator is bound (MapScreenRoot + FlightDataMgmt) to avoid missing entry paths.
- Decide whether the buffered sample should be display-bucketed to match cardFlightDataFlow.
- Ensure prepareCardsForProfile re-runs after profile/template hydration (keep dependencies or add an explicit refresh).

Acceptance:
- Tests run locally and pass without Android framework.
- Measured cadence recorded (fast + primary tiers, code-defined).
- Target cadence documented in this plan and in PIPELINE.md.
- Timebase behavior documented and covered by at least one test.

### Phase 1: Cards correctness (single adapter)
Deliverables:
- Ensure convertToRealTimeFlightData(...) is the only adapter from CompleteFlightData.
- Ensure dfcards does not pull or re-compute sensor values directly.
- Confirm card string mapping uses CardLibrary only (no duplicate mapping paths).
- Introduce a single card ingestion owner (Coordinator or ViewModel) and move all ingestion into it.
- Remove UI-side collectors in MapComposeEffects and FlightDataMgmt.
- Keep FlightDataMgmt previews fed via liveFlightDataFlow (not a second ingestion path).
- Move initializeCardPreferences/startIndependentClockTimer out of Compose effects and into the owner.
- Make binding idempotent (guard against multiple collectors).
- Update docs/RULES/PIPELINE.md and PIPELINE.svg if the flow changes.
- Verify cardHydrationReady + buffered sample behavior is preserved.

Acceptance:
- Cards update from cardFlightDataFlow only.
- Card string mapping is single-sourced in CardLibrary.
- Tests from Phase 0 still pass.
- Only one ingestion path calls updateCardsWithLiveData (no duplicate collectors).
- Card previews in FlightDataMgmt still update correctly.
- Null-data behavior (replay stop, sensor loss) matches the documented expectation.

### Ingestion owner design sketch (draft)
Location/ownership:
- `feature/map/...` small `CardIngestionCoordinator` owned by `MapScreenViewModel` (viewModelScope).

Inputs:
- `FlightDataManager.cardFlightDataFlow`
- `MapScreenViewModel.cardHydrationReady`
- `FlightDataManager.consumeBufferedCardSample()`
- `CardPreferences`
- `UnitsPreferences` (from MapScreenViewModel flow)
- `FlightDataViewModel` (dfcards)

Behavior:
- Initialize card preferences once; start independent clock timer once.
- Observe units preferences and forward into dfcards.
- When `cardHydrationReady` becomes true (or is already true at bind time), push the buffered sample (if any).
- Collect `cardFlightDataFlow`; when ready, call `updateCardsWithLiveData`.
- Ignore null samples (or clear cards) per the documented null-data decision.
- Ensure the buffered sample uses the same bucketing logic as cardFlightDataFlow (if chosen).

Lifecycle:
- Created once per map backstack entry; cancels collectors on ViewModel clear.
- UI calls a single `bindCards(flightViewModel)` entry point; it is idempotent.

### Ordered implementation checklist (do in this order)
1) Decide and document the null-data policy (freeze vs clear) in this plan and in `docs/flightdata.md`.
2) Add a non-Compose units flow:
   - Expose `StateFlow<UnitsPreferences>` from MapScreenViewModel, or
   - Add `unitsPreferencesFlow` to FlightDataManager.
3) Add `CardIngestionCoordinator` (`feature/map/...`) with an idempotent
   `bindCards(flightViewModel: FlightDataViewModel)` entry point.
4) Bind the coordinator from both MapScreenRoot and FlightDataMgmt (or a shared entry point),
   ensuring it is idempotent.
5) Move initialization into the coordinator:
   - `initializeCardPreferences(...)`
   - `startIndependentClockTimer()`
   - units updates to dfcards
6) Move ingestion into the coordinator:
   - Collect `cardFlightDataFlow`
   - Apply cardHydrationReady gating + buffered sample
   - If ready at bind time, still consume the buffered sample
   - Call `updateCardsWithLiveData(...)` from one place only
7) Remove UI-side collectors:
   - MapComposeEffects (cardFlow, init, units)
   - FlightDataMgmt (cardFlow)
8) Keep `prepareCardsForProfile(...)` in MapComposeEffects (profile/mode/size/density).
9) Optional: tighten cardHydrationReady gating if fallback size causes early ingestion
   (e.g., add a cardGridReady signal from CardContainer).
10) Tests:
   - Update `app/src/androidTest/.../MapCardHydrationTest.kt` to use the coordinator path.
   - Add coroutine tests for idempotent bind, buffered sample, units propagation, null-data policy.
   - Add a test (or assertion) that the first buffered sample matches the same bucketing as cardFlow.
11) Docs + diagram:
   - Update `docs/flightdata.md`.
   - Update `docs/RULES/PIPELINE.md` and `docs/RULES/PIPELINE.svg`.
    - Update `TC30s.md` and `docs/Cards/Netto30s.md` if they still name MapComposeEffects ingestion.
12) Optional cleanup: confirm `FlightDataProvider` (dfcards) is unused; delete or document as legacy.

### Phase 2: Cadence ownership (no redundant gating)
Status: Implemented (2026-02-02)
Deliverables:
- Decide the single cadence owner for cards:
  - Option A: keep dfcards tiered updates; make FlightDataManager cardFlow near pass-through.
  - Option B: keep FlightDataManager cadence; relax dfcards update tiers for visible cards.
- Remove redundant gating from the non-owner layer.
- Re-measure fast + primary card update cadence and record results.

Acceptance:
- Card updates meet the target cadence (fast and primary tiers).
- Only one layer enforces card cadence.
- Orientation + map pose updates remain smooth (liveFlightDataFlow unaffected).

### Phase 3: Simplify architecture (wiring only)
Deliverables:
- Move FlightDataManager creation into DI or a small factory.
- Extract a small FlightDataUiAdapter (or similar) so MapScreenViewModel owns no conversion logic.
- Keep MapScreenViewModel focused on state wiring, not transformations.
- Ensure MapScreen and FlightDataMgmt share the same FlightDataViewModel scope (map backstack entry).

Acceptance:
- MapScreenViewModel no longer constructs FlightDataManager directly.
- Conversion logic lives in a single, testable class.

## Verification checklist
- Run unit tests for new adapters and card mappings.
- Smoke test live mode: cards update, vario needle moves, no UI lag.
- Smoke test replay mode: cards update and stop/reset properly on replay stop.
- Verify cardHydrationReady gating and local_time ticking in live mode.
- Verify null-data behavior (replay stop/sensor loss) matches the spec.

## Rollback strategy
- Revert the refactor commits and restore the previous mapping/throttle behavior.
- Keep PIPELINE.md updated to reflect the actual code state.
