# MapScreenViewModel Line-Budget Refactor Plan (2026-03-08)

## 0) Metadata

- Title: `MapScreenViewModel.kt` line-budget compliance refactor
- Owner: XCPro Team
- Date: 2026-03-08
- Issue/PR: local refactor pass
- Status: In progress

## 1) Scope

- Problem statement:
  - `enforceRules` currently fails because `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` is 356 lines (hotspot max 350).
- Why now:
  - Rule gate blocks architecture verification and release-quality gates.
- In scope:
  - No-behavior-change refactor in `MapScreenViewModel` and closely related support files.
  - Keep MVVM/UDF flow intact while reducing file length.
- Out of scope:
  - Functional feature changes.
  - Traffic/task business-policy changes.
- User-visible impact:
  - None expected.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Map UI state | `MapScreenViewModel` | `StateFlow<MapUiState>` | UI-local mutable mirrors |
| ADS-B/OGN traffic state | Traffic repositories/use-cases | `StateFlow` from use-cases | ViewModel-owned authoritative copies |

### 2.2 Dependency Direction

`UI -> domain -> data` remains unchanged.

- Modules/files touched:
  - `feature/map/.../MapScreenViewModel.kt`
  - optional `feature/map/.../MapScreenViewModel*.kt` helper files
- Boundary risk:
  - Low; refactor is shape-only and keeps use-case dependencies unchanged.

### 2.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| ViewModel local wiring helpers | `MapScreenViewModel.kt` | `MapScreenViewModel*` helper file(s) | reduce hotspot size | compile + unit tests |

### 2.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A | N/A |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| N/A (no new time logic) | unchanged | refactor only |

### 2.4 Threading and Cadence

- Dispatcher ownership: unchanged.
- Primary cadence/gating sensor: unchanged.
- Hot-path latency budget: unchanged.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (unchanged).
- Randomness used: No.
- Replay/live divergence rules: unchanged.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Hotspot file size regression | CODING_RULES `File size budget` | `enforceRules` | `MapScreenViewModel.kt` |
| Wiring regression | MVVM/UDF rules | unit tests | `:feature:map:testDebugUnitTest` |

### 2.7 Visual UX SLO Contract

Not applicable for behavior-neutral refactor (no overlay/runtime behavior changes intended).

## 3) Data Flow (Before -> After)

Unchanged:

`Source -> Repository (SSOT) -> UseCase -> MapScreenViewModel -> UI`

## 4) Implementation Phases

### Phase 0 - Baseline
- Goal: confirm failing gate and target file.
- Files: none.
- Exit criteria: baseline failure captured (`356 > 350`).

### Phase 1 - Pure Refactor
- Goal: reduce `MapScreenViewModel.kt` to <=350 lines with no behavior change.
- Files: ViewModel and helper wiring files only.
- Tests: existing tests only.
- Exit criteria: code compiles; no new logic paths.

### Phase 2 - Hardening and Verification
- Goal: verify rule gate and assemble gate.
- Commands:
  - `./gradlew enforceRules`
  - `./gradlew assembleDebug`
- Exit criteria: both pass.

## 5) Test Plan

- Unit tests: existing map/viewmodel tests (no new logic expected).
- Replay/regression tests: unchanged.
- Required checks for this pass:
  - `./gradlew enforceRules`
  - `./gradlew assembleDebug`

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Accidental behavior drift in initialization wiring | medium | refactor only structure; keep call graph identical | XCPro Team |
| Hidden compile issue from helper extraction | low | run assemble after edits | XCPro Team |

## 7) Acceptance Gates

- `MapScreenViewModel.kt` <= 350 lines.
- No architecture rule violations introduced.
- `enforceRules` passes.
- `assembleDebug` passes.

## 8) Rollback Plan

- Revert only `MapScreenViewModel` and helper-file diffs from this plan.
- Re-run `enforceRules` and `assembleDebug` to confirm recovery.
