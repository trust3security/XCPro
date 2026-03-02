# Airspace Correctness Compliance Plan 2026-03-01

## 0) Metadata

- Title: Airspace Overlay Correctness + OpenAir Compatibility Hardening
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: AIRSPACE-20260301-01
- Status: Completed (2026-03-01)

## 1) Scope

- Problem statement:
  - Airspace layer is not reliably cleared when all airspace files are disabled.
  - "All classes OFF" user intent is not stable and can be auto-overridden by defaults.
  - Airspace refresh uses fire-and-forget coroutines and can apply stale overlays out of order under rapid toggles.
  - Import validation rejects valid OpenAir files that do not contain `DP` records.
  - OpenAir parsing is too strict for leading whitespace/format variations and can silently drop valid directives.
  - Coordinate parsing misses common OpenAir decimal-minute formats (`DDMM.MMM[N/S]`, `DDDMM.MMM[E/W]`).
  - Parser support is incomplete for common OpenAir directive patterns (notably `V`/`DB` flows).
  - Class selection state is wiped when all files are temporarily disabled, causing unexpected default re-enables later.
  - GeoJSON cache key uses class-set hash only, creating a theoretical wrong-cache-hit risk from hash collisions.
  - File import path has weak provider metadata guards (`DISPLAY_NAME` assumptions).
- Why now:
  - Current behavior risks incorrect map safety context (stale overlays or unexpected classes shown).
  - Defects were confirmed in current runtime paths and are user-visible.
- In scope:
  - Airspace apply/clear behavior.
  - Airspace apply request sequencing (latest-only, no stale render overwrite).
  - Airspace class selection semantics.
  - Class-state persistence semantics when enabled-files set is empty.
  - OpenAir validation/parsing compatibility improvements.
  - Cache-key correctness for class-filtered GeoJSON caching.
  - Import filename fallback robustness.
  - Regression tests for these behaviors.
- Out of scope:
  - New geofencing/alerting features.
  - Airspace styling redesign.
  - Task scoring or AAT policy changes.
- User-visible impact:
  - Disabling airspace fully removes overlay.
  - Disabling all classes remains respected.
  - More OpenAir files import/render correctly.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Selected airspace files + enabled states | `AirspaceRepository` | `AirspaceUseCase.loadAirspaceFiles()` -> `AirspaceViewModel.uiState.checkedStates` | UI-local file enable mirrors that outlive VM state |
| Selected class states | `AirspaceRepository` | `AirspaceUseCase.loadSelectedClasses()` -> `AirspaceViewModel.uiState.classStates` | Runtime map helper writing independent class state |
| Available classes from enabled files | `AirspaceRepository.parseClasses()` | `AirspaceViewModel.uiState.classItems` | Separate parser cache in UI/runtime |
| Renderable GeoJSON | `AirspaceRepository.buildGeoJson()` | consumed by map runtime apply path | Persisted GeoJSON copies in ViewModel/UI |
| Map airspace visual state | Map runtime/style owner | Map style source/layer (`airspace-source`, `airspace-layer`) | Parallel unmanaged airspace layers |

### 2.2 Dependency Direction

Dependency flow remains: `UI -> domain -> data`.

- Modules/files touched:
  - `feature/map/.../airspace/*`
  - `feature/map/.../utils/AirspaceApply.kt`
  - `feature/map/.../utils/AirspaceParser.kt`
  - `feature/map/.../utils/AirspaceIO.kt`
  - airspace-focused unit tests under `feature/map/src/test/...`
- Boundary risk:
  - `AirspaceApply.kt` currently performs persistence side effects from map runtime path. This bypass will be removed.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Default class seeding when class map is empty | `loadAndApplyAirspace` runtime helper | `AirspaceViewModel.reconcileClassStates` (single owner) | Remove UI/runtime persistence side effects and preserve SSOT intent | VM + apply tests |
| Clear overlay when data is disabled/empty | Implicit/no clear path | `loadAndApplyAirspace` explicit clear branch | Prevent stale map safety overlays | apply tests |
| Overlay refresh sequencing under rapid state changes | `MapOverlayManager.refreshAirspace` fire-and-forget jobs | `MapOverlayManager` single-flight latest-request apply policy | Prevent stale/out-of-order map state | overlay manager tests |
| OpenAir geometry validity detection | `validateOpenAirFile` with `DP` hard requirement | parser-level geometry token validation (`DP/DC/DA/DB` + center directives where needed) | Accept valid source files and reject only truly invalid geometry | parser tests |
| OpenAir token normalization (whitespace/case tolerance where safe) | strict raw-line checks in parser | parser pre-normalization and directive-aware parsing | Avoid silent geometry drops on valid files | parser fixture tests |
| Class-state retention across temporary zero-enabled-files state | `AirspaceViewModel.reconcileClassStates` returns and persists empty map | ViewModel reconciliation that keeps persisted class intent unless classes truly disappear from sources | Preserve user intent | VM tests |
| GeoJSON cache class-filter keying | `selectedClassesKey` 32-bit hash | canonical class-key string (or equivalent collision-safe key) | Remove wrong-cache-hit risk | repository cache-key tests |
| Import display-name fallback handling | weak guard in `AirspaceIO` | robust guarded fallback in `AirspaceIO` | Prevent provider-specific import failures | import tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `utils/AirspaceApply.kt` | runtime helper calls `saveSelectedClasses` | runtime helper becomes read-only consumer of SSOT; persistence only in VM/use-case flow | Phase 2 |
| `map/MapOverlayManager.kt` | launches non-cancelled concurrent apply jobs | maintain and cancel/ignore superseded jobs via request token/latest-only policy | Phase 2 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Airspace cache TTL timestamps | Wall | non-domain cache freshness bookkeeping only |
| Airspace overlay refresh triggers | N/A (event-driven) | keyed by state changes, not elapsed-time math |
| Replay determinism behavior | Replay clock unaffected | airspace path is static file rendering, not time-derived fusion |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - File IO + parsing: `Dispatchers.IO`
  - Map style mutation: `Dispatchers.Main.immediate`
- Primary cadence/gating sensor:
  - Event-driven by file/class state changes and style-change hooks.
- Hot-path latency budget:
  - Typical apply refresh under 250 ms for common file sizes on debug devices.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules:
  - No replay-specific branches added in airspace pipeline.
  - Airspace rendering follows the same persisted file/class SSOT in both modes.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Stale overlay after disabling files | SSOT/UI correctness, no silent stale state | Unit test | `AirspaceApplyTest` |
| "All classes off" overridden by defaults | SSOT ownership and intent stability | Unit test + review | `AirspaceApplyTest`, `AirspaceViewModelTest` |
| Class-state wipe when all files are disabled | SSOT ownership and user-intent stability | Unit test | `AirspaceViewModelTest` |
| Out-of-order overlay refresh under rapid toggles | Deterministic UI state and no stale side effects | Unit/integration test | `MapOverlayManagerAirspaceTest` |
| Import rejects valid non-`DP` OpenAir | Honest outputs, correctness over convenience | Unit test | `AirspaceParserTest` |
| Indented/variant OpenAir directive lines are ignored | Correctness + parser robustness | Unit test | `AirspaceParserTest` |
| Decimal-minute coordinates are parsed incorrectly | Correctness in geospatial ingest | Unit test | `AirspaceParserTest` |
| Missing directive parsing (`V`/`DB`) | Correctness + regression resistance | Unit test | `AirspaceParserTest` |
| Class-filter cache key collision risk | Correctness and cache determinism | Unit test | `AirspaceRepositoryTest` |
| Import crash from missing display name metadata | Error handling (no silent failures) | Robolectric/unit test | `AirspaceIoTest` |
| Runtime helper persisting domain state | Dependency direction and boundary rules | Review + test verification | `AirspaceApply.kt` tests verifying no save side effect |

## 3) Data Flow (Before -> After)

Before:

`AirspaceViewModel state -> MapScreen effect -> MapOverlayManager.refreshAirspace -> (fire-and-forget concurrent jobs) -> loadAndApplyAirspace -> (load files/classes + sometimes save classes) -> buildGeoJson -> style update (potential stale overwrite)`

After:

`AirspaceViewModel state (authoritative class reconciliation only) -> MapScreen effect -> MapOverlayManager.refreshAirspace (latest-only serialized apply) -> loadAndApplyAirspace (read-only) -> clear style when no enabled files or no selected classes -> buildGeoJson only for valid selected set -> style update`

Import/parse path after hardening:

`DocumentRef -> AirspaceRepository.importAirspaceFile -> validateOpenAirFile (geometry-token aware) -> parseOpenAirToGeoJson (expanded directive support) -> persisted file selection`

## 4) Implementation Phases

### Phase 0 - Baseline and Safety Net

- Goal:
  - Lock current expected behavior and reproduce defects with failing tests first.
- Files to change:
  - Add tests only.
- Tests to add/update:
  - `AirspaceApplyTest`: verifies overlay clear when no enabled files.
  - `AirspaceApplyTest`: verifies no auto-persist override for explicit all-false class map.
  - `AirspaceViewModelTest`: reproduces class-state wipe when all files are disabled.
  - `MapOverlayManagerAirspaceTest`: reproduces stale/out-of-order overlay apply under rapid refresh calls.
  - `AirspaceParserTest`: validates acceptance of non-`DP` valid geometry files.
  - `AirspaceParserTest`: reproduces failures for indented directives and decimal-minute coordinates.
  - `AirspaceRepositoryTest`: reproduces collision-risk behavior in class-filter cache keying.
- Exit criteria:
  - Defect-reproducing tests exist and fail for known issues.

### Phase 1 - Pure Logic Hardening (Parser/Validation)

- Goal:
  - Make parser/validation accept valid OpenAir geometry patterns and maintain deterministic output.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceParser.kt`
- Tests to add/update:
  - Expand `AirspaceParserTest` for directive normalization (trim/spacing/case-safe handling), `DP`, `DC`, `DA`, `DB`, and `V X=` center handling.
  - Add coordinate-format tests for decimal-minute tokens (`DDMM.MMM[N/S]`, `DDDMM.MMM[E/W]`).
  - Validation tests: do not require `DP` when other supported geometry is present.
  - Negative tests for malformed geometry.
- Exit criteria:
  - Parser tests pass.
  - No Android/UI imports added.

### Phase 2 - Repository/SSOT Wiring

- Goal:
  - Remove runtime write-backs, enforce explicit clear semantics, and make apply sequencing deterministic.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceApply.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/airspace/AirspaceViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt` (cache-key canonicalization)
- Tests to add/update:
  - `AirspaceApplyTest` for:
    - clear-on-empty-enabled-files,
    - clear-on-empty-selected-classes,
    - no call to class persistence from apply path.
  - `AirspaceViewModelTest` for:
    - explicit all-off persistence and reload behavior,
    - retention of class selections when files are temporarily disabled.
  - `MapOverlayManagerAirspaceTest` for latest-only refresh ordering.
  - `AirspaceRepositoryTest` verifying collision-safe class-key behavior.
- Exit criteria:
  - Single class-state owner remains VM/repository path.
  - Apply path is read-only against persisted selections.
  - Refresh path cannot apply superseded stale results.

### Phase 3 - ViewModel/UI Integration and UX Consistency

- Goal:
  - Preserve user intent in UI and ensure consistent map refresh behavior.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt` (only if refresh keying requires adjustment)
  - `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataAirspaceTab.kt` (optional messaging for "all classes hidden")
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/tasks/TaskFileSection.kt` (optional parity messaging)
- Tests to add/update:
  - ViewModel/UI state tests for "all classes hidden" vs "no classes detected".
  - Existing map screen tests updated if effect keys change.
- Exit criteria:
  - End-to-end manual flow works: import, toggle file, toggle classes, clear all.

### Phase 4 - Hardening, Verification, and Documentation Sync

- Goal:
  - Final robustness pass with full required checks and architecture self-audit.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/utils/AirspaceIO.kt` (display name guard)
  - `docs/ARCHITECTURE/PIPELINE.md` (only if flow ownership/wiring text changes)
  - this plan doc status + completion notes.
- Tests to add/update:
  - `AirspaceIoTest` (provider metadata fallback).
  - Regression suite run for all added tests.
- Exit criteria:
  - Required verification commands pass.
  - Architecture drift self-audit complete.
  - Quality rescore added with evidence.

## 5) Test Plan

- Unit tests:
  - `AirspaceParserTest`
  - `AirspaceApplyTest`
  - `AirspaceViewModelTest` (airspace selection semantics and persistence)
  - `MapOverlayManagerAirspaceTest` (refresh ordering/serialization)
  - `AirspaceRepositoryTest` (cache key correctness)
- Replay/regression tests:
  - Confirm no replay-path behavior changes from airspace updates.
- UI/instrumentation tests (if needed):
  - Optional compose/UI state test for "all classes hidden" rendering text.
- Degraded/failure-mode tests:
  - Malformed OpenAir geometry is rejected with clear error.
  - Indented/variant directive formatting still parses correctly where valid.
  - Missing provider display name still imports with fallback filename.
- Boundary tests for removed bypasses:
  - Ensure apply path does not mutate persisted class state.
  - Ensure older refresh jobs cannot overwrite newer airspace state.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Parser compatibility change alters legacy rendering | High | Add fixture-based before/after parser tests for known files + golden feature-count assertions | XCPro Team |
| Concurrent refresh race applies stale airspace | High | Latest-only serialized apply in overlay manager + ordering tests | XCPro Team |
| Layer clear timing races with style reload | Medium | Keep clear/apply on Main; add map-style-available guards in apply tests | XCPro Team |
| Class-state wipe when all files disabled surprises users | Medium | Preserve persisted class map across temporary file-disable state + VM tests | XCPro Team |
| Hash-collision cache key returns wrong class-filtered GeoJSON | Low | Replace hash-only key with canonical stable key + repository tests | XCPro Team |
| All-off semantics misunderstood as data loss | Low | Explicit UI text for hidden classes and state-preserving tests | XCPro Team |
| Import fallback naming collisions | Low | Preserve existing naming plus collision-safe suffix if needed | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling remains explicit and non-mixed.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` unchanged unless explicit approval is provided.
- Required verification commands pass.

## 8) Rollback Plan

- What can be reverted independently:
  - Parser/validation updates.
  - Overlay refresh sequencing updates.
  - Apply-path clear/selection logic.
  - Repository cache-key updates.
  - UI messaging changes.
  - Import fallback guard changes.
- Recovery steps if regression is detected:
  - Revert the failing phase commit only.
  - Re-run `enforceRules`, targeted airspace tests, then full required checks.
  - Keep baseline tests to prevent reintroducing the same defect.

## 9) Completion Evidence (2026-03-01)

Implemented scope:

- Apply path now clears map overlay on empty enabled-files and explicit all-classes-off.
- Runtime apply path is read-only (no class persistence side effects).
- Overlay refresh uses latest-only sequencing with cancellation of superseded jobs.
- Class-state reconciliation preserves user intent across temporary disable-all-files state.
- OpenAir parser/validation now accepts valid non-`DP` geometry and supports broader directive/coordinate variants (`V`/`DB`, decimal-minute formats, indented directives).
- GeoJSON class-filter cache key moved from hash-only to canonical stable string.
- Import file-name extraction now robustly falls back when provider `DISPLAY_NAME` metadata is missing/blank.

Added/updated test coverage:

- `AirspaceApplyTest`
- `AirspaceParserTest`
- `AirspaceViewModelTest`
- `MapOverlayManagerAirspaceTest`
- `AirspaceRepositoryTest`
- `AirspaceIoTest`

Verification run:

- `./gradlew.bat enforceRules` -> PASS
- `./gradlew.bat testDebugUnitTest` -> PASS
- `./gradlew.bat assembleDebug` -> PASS

Architecture drift self-audit:

- No new UI imports in domain logic.
- No new data-layer imports in UI logic.
- No new direct time calls in domain/fusion paths from this change set.
- No new global mutable singleton state introduced.
- No use-case boundary bypass added.

## 10) Quality Rescore (Post-Implementation)

- Architecture cleanliness: 4.7 / 5
  - Evidence: runtime persistence bypass removed from `AirspaceApply.kt`; ownership retained in VM/repository flow; latest-only refresh in `MapOverlayManager.kt`.
  - Remaining risk: map-style lifecycle races still depend on runtime style availability order and should continue to be regression-tested.
- Maintainability / change safety: 4.6 / 5
  - Evidence: parser logic centralized with explicit directive handling and dedicated tests; cache-key behavior now deterministic and inspectable.
  - Remaining risk: OpenAir format diversity remains broad; additional fixture corpus will further reduce parser regression risk.
- Test confidence on risky paths: 4.5 / 5
  - Evidence: targeted tests now cover clear semantics, parser compatibility, class-state retention, refresh ordering, cache-key collision class, and import metadata fallback.
  - Remaining risk: no full instrumentation assertions for map style render timing in this plan.
- Overall map/task slice quality: 4.5 / 5
  - Evidence: user-visible airspace correctness issues addressed, deterministic behavior improved under rapid toggles.
  - Remaining risk: this score is airspace-slice focused; broader map/task quality also depends on unrelated ongoing work.
- Release readiness (airspace slice): 4.6 / 5
  - Evidence: required verification commands passed and regression tests added for identified defects.
  - Remaining risk: optional on-device manual validation should still be run before release sign-off.
