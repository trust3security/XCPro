# MapOrientationManager Refactor Plan

Date: 2026-01-27
Owner: app/map team
Stakeholders: map UI, sensors, replay

## Goals
- Make orientation logic deterministic and unit-testable (pure core, no Android calls).
- Make SSOT ownership explicit (settings, sensor input, derived output).
- Preserve existing behavior (modes, staleness, override, logging).
- Align with ARCHITECTURE.md and CODING_RULES.md (UDF, SSOT, time base rules).

## Non-Goals
- Change user-facing behavior or defaults.
- Change map camera or display smoothing behavior.
- Redesign sensor fusion (OrientationDataSource stays the source of sensor inputs).

## Current Issues (Why)
- MapOrientationManager mixes orchestration, logic, and time source calls.
- Preferences are read directly in multiple places and from UI.
- Time base usage is implicit and hard to test.
- Behavior is not protected by unit tests.

## Target State (What)
### SSOT Ownership
- Settings SSOT: MapOrientationSettingsRepository (wraps MapOrientationPreferences).
- Sensor SSOT: OrientationDataSource (already exists).
- Derived SSOT: OrientationEngine (pure domain logic).

### Flow Diagram (UDF)
Sensors -> OrientationDataSource -> OrientationEngine -> MapOrientationManager.orientationFlow -> UI
Settings -> MapOrientationSettingsRepository -> OrientationEngine
User events -> MapOrientationManager -> OrientationEngine

### Time Base
- Monotonic time for staleness and override timers.
- Wall time only for OrientationData.timestamp.
- Replay uses replay timestamps only (no wall time mixing).

### State Machine (Explicit)
- UserOverrideState: Inactive -> Active on user interaction; Active -> Inactive after timeout.
- ProfileState: Cruise <-> Circling on flight mode change.
- BearingValidity: Valid, HeadingStale, TrackStale, LastKnown.

## Phases
### Phase 0: Baseline + Docs
Owner: app/map team
Scope:
- Add this plan doc to docs/refactor.
- Capture current invariants and thresholds.
- Define behavior parity checklist.
Deliverables:
- docs/refactor/map_orientation_manager_plan.md (this file).
- Behavior parity checklist section below.

### Phase 1: Extract OrientationEngine (Pure)
Owner: app/map team
Scope:
- Create OrientationEngine and OrientationState (pure Kotlin, no Android).
- Move logic from MapOrientationManager:
  - calculateBearing
  - staleness / validity decisions
  - normalizeBearing / shortestDelta
  - user override timing
- Add Clock interface for nowMonoMs and nowWallMs.
Deliverables:
- New domain classes in feature/map or core/domain (choose existing domain location).
- MapOrientationManager calls OrientationEngine with explicit time inputs.
Exit Criteria:
- OrientationEngine has unit tests covering all modes and staleness behavior.

### Phase 2: Settings Repository + UI Cleanup
Owner: app/map team
Scope:
- Add MapOrientationSettings data class.
- Add MapOrientationSettingsRepository that wraps MapOrientationPreferences and exposes StateFlow.
- Update OrientationSettingsScreen to use a ViewModel or controller, not direct preferences.
- Remove MapOrientationManager.reloadFromPreferences and direct preference reads.
Deliverables:
- Repository class + tests.
- UI uses state holder instead of preferences.
Exit Criteria:
- Settings are observable and testable.
- No UI writes directly to preferences.

### Phase 3: Manager as Orchestrator
Owner: app/map team
Scope:
- MapOrientationManager becomes a thin adapter:
  - Collect OrientationDataSource orientationFlow.
  - Collect settings flow.
  - Collect user events (SharedFlow).
  - Call OrientationEngine.reduce(...) and emit OrientationData.
- Inject dispatcher and scope explicitly.
- Ensure cancellation and lifecycle correctness.
Exit Criteria:
- MapOrientationManager has no business logic.
- OrientationFlow is derived from engine only.

### Phase 4: Debugging and Cleanup
Owner: app/map team
Scope:
- Move jitter logging into a separate debug-only helper.
- Remove dead fields in manager and data source.
- Update docs if flow changes are significant.
Exit Criteria:
- No unused state or ad-hoc logging in core logic.

## Behavior Parity Checklist
- North Up always returns 0.0 bearing, valid.
- Track Up uses GPS track only when GPS valid and speed >= threshold.
- Heading Up uses headingSolution; staleness causes fallback or zero.
- User override freezes orientation for timeout duration.
- lastValidBearing used when bearing is invalid but not stale.
- Jitter logging behavior unchanged (debug only).

## Tests
### Unit Tests (Required)
- OrientationEngine:
  - North/Track/Heading paths.
  - Track stale after TRACK_STALE_TIMEOUT_MS.
  - Heading stale after HEADING_STALE_TIMEOUT_MS when invalid.
  - lastValidBearing fallback.
  - user override timeout.
  - normalizeBearing and shortestDelta.
- Settings Repository:
  - read/write cruise + circling modes.
  - min speed threshold persists and migrates.
- Manager Orchestration:
  - combines sensor + settings + user events with fake clock.

### Integration / Replay Tests (Optional but Preferred)
- Replay session uses replay timestamps without wall time mixing.
- Orientation state updates during replay match expected sequence.

## Risks and Mitigations
- Behavior drift: lock behavior with unit tests before moving logic.
- Time base bugs: use explicit Clock and tests.
- UI regression: keep OrientationController API stable.

## Open Questions
- Location for OrientationEngine (core/domain vs feature/map/domain).
- Whether to keep MapOrientationManager in feature/map or move to core/map.
