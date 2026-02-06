# UI and Domain Boundary Compliance Plan

Date: 2026-02-06
Status: Draft (plan only, no implementation yet)

## Purpose
Bring the codebase into compliance with ARCHITECTURE.md and CODING_RULES.md by
removing UI/Android types from domain models, repositories, and use-cases.

This plan targets the current highest-impact violations:
- Profile domain models depend on Compose UI types.
- A repository uses Compose UI types.
- A use-case depends on Android Context.

## Scope
- Profiles: remove `ImageVector` from domain models and move icon mapping to UI.
- dfcards: remove Compose `Color` usage from repository update logic.
- Map waypoints: remove `Context` from `MapWaypointsUseCase`.

## Non-Goals
- No behavior changes beyond the refactor.
- No UI redesign or new features.
- No changes to wind/vario/netto pipelines.
- No changes to storage formats unless required for compatibility.

## Current Problems (Observed)
- `UserProfile` / `AircraftType` includes `ImageVector` (Compose UI type) in
  domain models, and the repository references Compose to exclude it during
  serialization.
- `CardStateRepositoryUpdates` imports `androidx.compose.ui.graphics.Color`,
  which violates repository purity.
- `MapWaypointsUseCase` depends on `android.content.Context`.

## Target Architecture
Allowed dependency direction: UI -> domain -> data.

Rules enforced by this plan:
- Domain models contain no Android/Compose types.
- Repositories never import UI types.
- Use-cases never import Android framework classes.

## Implementation Phases

### Phase 0 - Inventory and Safety Net
- Enumerate all call sites that use:
  - `AircraftType.icon` or `ImageVector` in profiles
  - `CardStateRepositoryUpdates` highlight colors
  - `MapWaypointsUseCase`
- Add tests that lock current behavior (no functional changes):
  - Profile serialization/deserialization round-trip.
  - Card highlight selection logic.
  - Waypoint load workflow using a fake repository.

### Phase 1 - Profile Domain Cleanup
- Remove `ImageVector` from `AircraftType` and `UserProfile` domain models.
- Introduce a UI-only mapping function or table:
  `AircraftType -> ImageVector`.
- Remove `ImageVector` exclusion logic from `ProfileRepository` JSON serialization.
- Keep stable, UI-neutral identifiers in domain (enum names or ids).

Gate:
- No Compose imports remain in profile domain or repository packages.
- Existing profile behavior remains intact.

### Phase 2 - Update Profile UI Wiring
- Update UI surfaces to use the new `AircraftType -> ImageVector` mapping.
- Ensure profile lists, selection, and edit screens render the same icons.

Gate:
- UI parity verified (manual check + targeted UI test if available).

### Phase 3 - dfcards Repository Color Decoupling
- Replace Compose `Color` in repository logic with UI-neutral tokens
  (e.g., `CardHighlight` enum or ARGB `Int`).
- Map tokens to Compose `Color` in UI layer.
- Keep highlight rules unchanged.

Gate:
- No Compose imports remain in `*Repository*.kt` for dfcards state logic.
- Visual output matches previous behavior.

### Phase 4 - MapWaypoints Use-Case Refactor
- Introduce a data-layer interface (e.g., `WaypointRepository`) that owns
  `Context` and `WaypointLoader`.
- Change `MapWaypointsUseCase` to depend on the repository interface only.
- Provide a fake repository for tests.

Gate:
- `MapWaypointsUseCase` has no Android imports.
- Waypoint load behavior remains unchanged.

### Phase 5 - Cleanup and Enforcement
- Remove unused imports and legacy helpers.
- Consider adding a CI check that flags Compose/Android imports in
  `**/*Repository*.kt` and `**/*UseCase*.kt` outside UI modules, once
  code is clean.

Gate:
- `scripts/ci/enforce_rules.ps1` passes.
- No new deviations added.

## Testing Strategy
- Unit tests for profile model serialization (no UI types required).
- Unit tests for card highlight token mapping.
- Use-case test for waypoint loading with a fake repository.
- UI smoke test or snapshot for profile icon rendering if available.

## Definition of Done
- No Compose/Android types in profile domain models or repository.
- No Compose `Color` usage in repository update logic.
- No Android imports in `MapWaypointsUseCase`.
- All tests and rule checks pass.
- `KNOWN_DEVIATIONS.md` remains empty.

## Files Likely to Change (Initial List)
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileModels.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ui/*`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepositoryUpdates.kt`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/*` (UI mapping)
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
- `feature/map/src/main/java/com/example/xcpro/flightdata/*` (new repository or adapter)
- `scripts/ci/enforce_rules.ps1` (optional follow-up)

## Open Questions / Missing Docs
- `docs/GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md` and
  `docs/CONFIDENCE_MODEL_SPEC.md` are referenced in README_FOR_CODEX.md but
  are not present in the repo. This plan does not touch wind/netto behavior,
  but these docs should be restored for full compliance.

## Pre-Implementation Rule
No code changes should begin until this plan is reviewed and approved.
