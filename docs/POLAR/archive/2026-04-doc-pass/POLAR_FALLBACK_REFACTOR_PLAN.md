# POLAR_FALLBACK_REFACTOR_PLAN.md

## 0) Metadata

- Title: Default Club Polar Fallback + Polar Crash Hardening
- Owner: XCPro maintainers (agent-executed)
- Date: 2026-02-27
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  - Polar-dependent paths can fail when no glider is selected or when a selected model has invalid/empty polar point lists.
  - Highest-risk crash found: `PolarCalculator.sinkFromPoints()` uses `first()/last()` without guarding empty lists.
- Why now:
  - Crash audit identified high-confidence runtime risk in core sink-rate path.
  - Safety fallback improves reliability for first-run users and profile migration edge cases.
  - Deep repass (2026-02-27) found additional crash/stability gaps:
    - non-finite/degenerate 3-point input can propagate invalid sink values
    - preview UI directly called calculator without defensive wrapping
    - sink provider allowed non-finite outputs through hot-path callers
- In scope:
  - Introduce a built-in fallback "club" polar model for runtime use when selected model is missing/invalid.
  - Add strict defensive guards in polar computation to prevent empty-list crashes.
  - Add explicit UI state/warning when fallback polar is active.
  - Add unit coverage for null/invalid/empty-polar paths.
- Out of scope:
  - Rebuilding full glider model catalog.
  - Advanced performance tuning of sink algorithms.
  - Changing non-polar forecast/crash findings.
- User-visible impact:
  - App continues operating with a default polar instead of missing output/crash.
  - User sees clear indication that default/fallback polar is in use.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Selected glider model ID | `GliderRepository` persistence (`SharedPreferences`) | `StateFlow<GliderModel?>` + resolved effective model accessor | ViewModel/UI-local model mirrors |
| Effective active glider model (selected or fallback) | `GliderRepository` domain/data boundary | `StateFlow<GliderModel>` or resolver method | Recomputing fallback policy in UI/use cases |
| Glider config overrides (`threePointPolar`, bugs, ballast, IAS bounds) | `GliderRepository` | `StateFlow<GliderConfig>` | Per-screen copies of config state |
| Fallback model definition | `core/common` glider model source | constant/provider function | Ad-hoc fallback constants in feature modules |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `core/common/.../glider/*`
  - `feature/map/.../glider/*`
  - `feature/map/.../screens/navdrawer/*` (warning only)
- Any boundary risk:
  - Risk of UI computing fallback state independently. Prevent by exposing fallback-active state from repository/use case.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Resolve "active model" when none selected | Implicit/null behavior in callers | `GliderRepository` (single resolver) | Remove duplicated null-handling and guarantee safe runtime model | Repository unit tests |
| Validate polar point list shape before interpolation | `PolarCalculator` with partial assumptions | `PolarCalculator` with explicit guards | Prevent `NoSuchElementException` | Calculator unit tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `StillAirSinkProvider` direct `selectedModel ?: return null` | Caller handles null model path | Use repository-provided effective model / fallback policy | Phase 2 |
| UI fallback messaging inferred locally | UI guesses from null/empty model | Repository/use case exposes fallback-active flag | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Polar fallback selection state | N/A (state-based, not time-based) | Determined by selected-model validity |
| Polar sink calculation | N/A (pure function of speed+config+model) | No clock input |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - Repository load/save remains on existing persistence path.
  - Sink calculation remains lightweight and synchronous.
- Primary cadence/gating sensor:
  - Existing flight data cadence; no new cadence source introduced.
- Hot-path latency budget:
  - Keep sink lookup O(1)-ish/O(n small list), no allocations on hot path beyond current behavior.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - None introduced; fallback selection is deterministic from persisted model/config state.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Empty polar list crash | CODING_RULES: defensive domain logic | Unit test | `PolarCalculator` tests for empty `points`, `pointsLight`, `pointsHeavy` |
| Null selected model causes missing sink path | ARCHITECTURE SSOT + reliability | Unit test | `GliderRepository` effective-model fallback tests |
| UI and domain disagreement on fallback state | SSOT ownership | Unit + review | Use case/repository state test + code review checklist |

## 3) Data Flow (Before -> After)

Before:

`Source(prefs selectedModelId) -> GliderRepository(selectedModel nullable) -> StillAirSinkProvider(nullable sink) -> PolarCalculator(partial guards) -> Consumers`

After:

`Source(prefs selectedModelId) -> GliderRepository(resolve effective model with fallback + fallback flag) -> StillAirSinkProvider(non-null model path) -> PolarCalculator(strict guards) -> Consumers + UI warning`

## 4) Implementation Phases

### Phase 0 - Baseline and Safety Net

- Goal:
  - Lock current behavior with regression tests around model selection and polar interpolation edge cases.
- Files to change:
  - `feature/map/src/test/.../glider/*` (new or expanded tests).
- Tests to add/update:
  - `selectedModel == null` behavior baseline.
  - Empty point-list crash reproduction test (red test first).
- Exit criteria:
  - Tests compile and reproduce current risk behavior before fix.

### Phase 1 - Fallback Model Definition

- Goal:
  - Define a canonical fallback "club" polar model in `core/common`.
- Files to change:
  - `core/common/src/main/java/com/example/xcpro/common/glider/GliderModels.kt`.
- Tests to add/update:
  - Model validity test (non-empty usable polar definition).
- Exit criteria:
  - Fallback model exists and is valid for interpolation path.

### Phase 2 - Repository and Calculator Hardening

- Goal:
  - Resolve effective model in repository.
  - Make `PolarCalculator` safe for empty/degenerate point lists.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/glider/GliderRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/glider/StillAirSinkProvider.kt`
  - `feature/map/src/main/java/com/example/xcpro/glider/PolarCalculator.kt`
- Tests to add/update:
  - Repository fallback resolution tests:
    - no selected ID
    - unknown selected ID
    - selected model with unusable polar data
  - Calculator tests:
    - empty `points`
    - empty `pointsLight`/`pointsHeavy`
    - 1-point and degenerate interpolation handling
- Exit criteria:
  - No crash on invalid/empty lists.
  - Effective model is always resolvable.

### Phase 3 - UI Wiring and User Clarity

- Goal:
  - Surface "fallback polar active" warning in polar settings/preview.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/glider/GliderUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/glider/GliderViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/PolarPreviewCard.kt` (or equivalent screen component).
- Tests to add/update:
  - ViewModel/UI state tests for fallback flag.
- Exit criteria:
  - Warning appears only when fallback is active.

### Phase 4 - Hardening and Docs Sync

- Goal:
  - Final verification and architecture-drift check.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` only if flow ownership changes.
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` only if an approved temporary exception is needed.
- Tests to add/update:
  - Any missing degraded-mode tests identified during implementation.
- Exit criteria:
  - Required checks pass.
  - No unresolved architecture drift.

## 5) Test Plan

- Unit tests:
  - `PolarCalculator` empty/degenerate inputs do not throw.
  - `GliderRepository` resolves fallback deterministically.
  - `StillAirSinkProvider` returns value or explicit null only for truly unsupported paths.
- Replay/regression tests:
  - Confirm no change in deterministic output for valid selected models.
- UI/instrumentation tests (if needed):
  - Fallback warning shown/hidden correctly.
- Degraded/failure-mode tests:
  - Corrupt persisted model ID.
  - Persisted config with incomplete polar.
- Boundary tests for removed bypasses:
  - Verify consumers no longer need direct null selected-model handling.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Fallback masks user misconfiguration | Lower data accuracy | Explicit UI warning + quick link to glider selection | Feature owner |
| Changing selected-model semantics breaks existing callers | Behavioral regression | Add effective-model API without removing old API immediately | Agent + reviewer |
| Overly permissive calculator fallback hides data quality issues | Silent logic drift | Log/telemetry hook for invalid polar structures (debug builds) | Feature owner |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling remains explicit and unchanged.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry).
- Polar path cannot crash from empty lists or missing selected model.

## 8) Rollback Plan

- What can be reverted independently:
  - UI warning changes.
  - Repository fallback exposure API.
  - Calculator defensive path.
- Recovery steps if regression is detected:
  1. Disable fallback activation path via repository gate (temporary).
  2. Keep calculator guards in place (do not roll back crash prevention).
  3. Re-run `enforceRules`, unit tests, and targeted glider tests.

## 9) Open Decisions (To Be Filled)

- Should fallback be a hidden internal model or visible/selectable in UI?
- Should fallback use a fixed "club class" curve or `ThreePointPolar()` defaults?
- Should activating fallback emit analytics/debug event for support diagnostics?
- Should app prompt user once per profile to confirm/replace fallback?
