# CHANGE_PLAN_OGN_OWNSHIP_FLARM_ICAO_SELF_FILTER_2026-02-23.md

## Purpose

Prevent ownship from appearing as an OGN traffic target (marker/trail/thermal source)
when the pilot already has blue ownship overlay on map. The plan adds explicit ownship
identity settings and typed matching by FLARM and ICAO identifiers.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN ownship self-filter using FLARM + ICAO typed IDs
- Owner: XCPro Team
- Date: 2026-02-23
- Issue/PR: TBD
- Status: Implemented (2026-02-23)

## 1) Scope

- Problem statement:
  OGN labels often show competition number or registration, but these are not stable
  transport identifiers for self-filtering. Today XCPro can still render ownship OGN
  target on top of the blue ownship triangle.
- Why now:
  Users need deterministic ownship suppression in General OGN mode to avoid duplicate
  ownship symbols and false trail/thermal artifacts from their own aircraft.
- In scope:
  - Add two OGN settings inputs:
    - own FLARM ID (6 hex)
    - own ICAO24 ID (6 hex)
  - Parse and preserve OGN `id` type information required to distinguish FLARM vs ICAO.
  - Add deterministic type inference fallback from callsign prefix when APRS `id` token
    does not carry an explicit type byte (for example `ICAxxxxxx`, `FLRxxxxxx`).
  - Tighten parser token acceptance for `id` payload:
    - accept exactly 6 or 8 hex chars
    - reject malformed 7-hex variants
  - Normalize APRS source callsigns for inference/fallback by stripping SSID suffix
    before prefix and hex extraction (`CALL-1` -> `CALL`).
  - Align DDB identity lookup with typed addressing by consuming DDB `device_type`
    where available (avoid cross-type identity/tracked collisions on same 6-hex).
  - Define one explicit address-type mapping matrix across:
    - APRS typed `id` token
    - source callsign prefixes
    - DDB `device_type`
    with unknown-safe fallback when mapping is not recognized.
  - Keep `OgnTrafficTarget.id` wire shape stable in this slice; add typed transport
    identity as separate fields to avoid key churn regressions.
  - Introduce a canonical typed transport key (internal) for repository/domain state,
    while keeping `target.id` for UI compatibility display/tap contracts.
  - Route map marker tap/selection internals through canonical typed keys to avoid
    ambiguous detail selection when different address types share the same legacy `id`.
  - Apply ownship filtering in OGN repository before publishing target list.
  - Extend OGN repository contract to expose suppression state for downstream consumers:
    `suppressedTargetIds: StateFlow<Set<String>>`.
  - Ensure filtering affects all downstream OGN consumers (icons, trails, thermal derivation,
    details selection) by keeping filter at repository SSOT boundary.
  - Purge already-derived ownship OGN trail/thermal artifacts in-session when self-filter
    settings change from unset to set.
  - Reconcile OGN trail-selection persisted keys by removing suppressed ownship ids to avoid
    stale hidden selections that re-activate if suppression is later cleared.
  - Add backward-compatible migration/alias handling for pre-existing persisted trail
    keys (legacy untyped values) when canonical typed keys are adopted.
  - Persist ownship text settings with null/remove-key semantics (not empty-string sentinel).
  - Apply text-field edits on explicit commit action (save/IME) rather than per-keystroke writes.
  - Keep new ownship text-field drafts and validation errors in ViewModel state
    (survive recomposition/config changes; repository remains authoritative only on commit).
  - Surface suppression diagnostics (count and/or reason) for debug visibility to verify
    live ownship suppression behavior during rollout.
  - Add parser/preferences/repository tests for matching and non-matching cases.
  - Update OGN protocol docs and pipeline docs.
- Out of scope:
  - Callsign/CN/registration-based self-filter logic.
  - ADS-B ownship filtering changes.
  - Device auto-discovery of own FLARM/ICAO from hardware integrations.
  - UI redesign of OGN settings page beyond required new fields.
  - Full external/public API rename of legacy `OgnTrafficTarget.id`.
- User-visible impact:
  - New OGN settings fields in General -> OGN.
  - Own aircraft no longer shown in OGN traffic when configured identifiers match.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN own FLARM hex | `OgnTrafficPreferencesRepository` | `Flow<String?>` (normalized uppercase 6-hex or null) | UI-local shadow state as authoritative |
| OGN own ICAO hex | `OgnTrafficPreferencesRepository` | `Flow<String?>` (normalized uppercase 6-hex or null) | UI-local shadow state as authoritative |
| Parsed OGN transport address type + address hex | `OgnAprsLineParser` output model (`OgnTrafficTarget`) | immutable target fields | Re-parsing from `rawLine` in UI/domain |
| Canonical transport key (`type + hex`) | `OgnTrafficTarget` internal identity field | immutable string (for non-UI keying) | Using legacy `target.id` for repository/trail/thermal keying |
| Typed DDB identity index | `OgnDdbRepository` | typed lookup API (`type + hex`) | Flat hex-only index as authoritative key for policy |
| Filtered OGN targets (ownship removed) | `OgnTrafficRepositoryImpl` | `StateFlow<List<OgnTrafficTarget>>` | Secondary filtered stores in ViewModel/UI |
| Suppressed target ids (self-filter match result) | `OgnTrafficRepositoryImpl` | `StateFlow<Set<String>>` | Re-deriving suppression ad hoc in trail/thermal repos |
| Selected OGN marker key | `MapScreenViewModel` (`selectedOgnId`) | `StateFlow<String?>` using canonical typed key | Binding marker/details selection to ambiguous legacy `target.id` |
| OGN trail selected aircraft keys | `OgnTrailSelectionPreferencesRepository` | `Flow<Set<String>>` | local composable-selected key authority |
| Legacy trail-key compatibility/migration | `OgnTrailSelectionUseCase` + repository | canonical key flow with legacy-alias resolution | UI-only ad hoc legacy-key expansion logic |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - Data: `feature/map/src/main/java/com/trust3/xcpro/ogn/*`
  - Domain/use-case: `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsUseCase.kt`
  - ViewModel/UI: `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsViewModel.kt`, `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsScreen.kt`
- Any boundary risk:
  - UI doing ad-hoc validation/matching policy.
  - Filtering in UI instead of repository SSOT.
  - Regressing parser compatibility for frames with 6-hex id token variants.
  - Derived OGN repositories retaining ownship history after filter settings change.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN ownship identifiers persistence | None | `OgnTrafficPreferencesRepository` | SSOT persistence with DataStore | Repository tests |
| Ownship filtering policy | Not implemented | `OgnTrafficRepositoryImpl` | Single policy point for all downstream OGN consumers | Repository policy tests |
| OGN id-type decoding | Partial parser behavior (drops type byte) | `OgnAprsLineParser` explicit typed decode + callsign-prefix fallback | Avoid FLARM/ICAO mismatch when id token is partial | Parser tests |
| DDB identity lookup keying | Hex-only `device_id` map | typed keying (`device_type + device_id`) with controlled fallback | Prevent wrong identity/tracked policy across types | DDB parser/repository tests |
| Address-type taxonomy mapping | Implicit/scattered inference rules | shared mapping helper used by parser + DDB | Prevent drift between parser and DDB type semantics | parser + DDB mapping tests |
| Non-UI OGN keying | Legacy `target.id` across repos | canonical typed transport key for repository/trail/thermal/selection internals | Prevent cross-type state aliasing | key-collision tests |
| OGN tap/details selection keying | Legacy `target.id` from map feature -> ViewModel selection | canonical typed key from overlay feature property to selection state | Prevent selecting wrong aircraft when legacy ids collide | map selection collision tests |
| Ownship artifact purge in derived repos | Not implemented | `OgnGliderTrailRepositoryImpl` and `OgnThermalRepositoryImpl` consume suppression state | Remove stale ownship trail/thermal visuals after settings change | Trail/thermal tests |
| Ownship trail-selection key hygiene | Not implemented | `OgnTrailSelectionUseCase` prunes suppressed ids via repository API | Prevent hidden stale selection reactivation | Trail-selection use-case tests |
| Legacy trail-selection compatibility | Existing persisted untyped keys | one-time migration + alias resolution in selection use-case | Avoid losing pilot-selected trails after key format upgrade | selection migration tests |
| OGN settings draft/validation ownership | Composable-local transient state | `OgnSettingsViewModel` draft state + commit intents | Keep MVVM/UDF boundaries and survive recreation | ViewModel/UI tests |
| OGN ingest/settings concurrency safety | Implicit best effort | `OgnTrafficRepositoryImpl` shared mutation guard for targets + suppression state | Deterministic state under concurrent updates | repository concurrency tests |
| Suppression diagnostics visibility | No explicit suppression counters/reasons | snapshot/debug-panel suppression diagnostics | Speed verification and regression triage | snapshot/debug tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Self-filter by display label/CN (potential workaround) | Non-authoritative label matching outside repository | Typed ID matching in `OgnTrafficRepositoryImpl` | Phase 3 |
| Parser implicit `takeLast(6)` behavior | Type information discarded | Preserve type + hex in parsed model | Phase 1 |
| DDB lookup by hex only | Type collisions can override identities/policy | Typed lookup API and index | Phase 1B |
| Parser/DDB type mapping split across codepaths | Drift-prone implicit mapping | Centralized mapping matrix helper | Phase 1B |
| Derived repos keyed by `target.id` | Cross-type aliasing for trails/thermals/selection | migrate non-UI keying to canonical typed key | Phase 1C |
| OGN overlay feature stores legacy `target.id` only | Tap returns ambiguous id when collisions exist | Store canonical selection key in overlay feature payload | Phase 1C |
| ViewModel selection state keyed by legacy id | `selectedOgnId` can resolve to wrong target | canonical key selection path in coordinator/state builder | Phase 1C |
| Derived repos infer suppression from target disappearance only | Trails/hotspots can persist for session windows | consume explicit `suppressedTargetIds` flow and purge matching artifacts | Phase 3 |
| Trail selection keeps suppressed ownship keys | Hidden stale selection can reappear unexpectedly | prune suppressed ids from selection preferences | Phase 3 |
| Persisted trail keys are legacy/untyped | Existing user selections can be lost after canonical key adoption | migration/alias strategy in trail selection use-case | Phase 3 |
| Repository does not expose suppression state | Downstream repos cannot purge deterministically | add `suppressedTargetIds` to `OgnTrafficRepository` contract | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN target timestamps / staleness | Monotonic | Existing OGN stream freshness/stale policy |
| OGN preferences persistence timing | Wall | DataStore persistence lifecycle only |
| Self-filter matching | N/A (non-temporal) | Pure value comparison (type + hex) |
| Suppression-driven cleanup scheduling | Monotonic | Keep deterministic aging/cleanup in derived repos |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - DataStore reads/writes and OGN socket loops: `IO`
  - Map/UI rendering: `Main`
  - No heavy work added to `Main`
- Primary cadence/gating sensor:
  - Existing OGN center updates remain GPS-driven via `mapLocation`.
- Hot-path latency budget:
  - Per-frame ownship check must be O(1) lookup/match and not affect stream cadence.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - OGN remains a live traffic side-channel; replay/fusion outputs remain unaffected.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| UI starts applying filtering policy | MVVM/UDF + SSOT | Review + tests | `OgnSettingsScreen.kt`, `OgnSettingsViewModel.kt` |
| Parser drops id type and causes wrong matches | Deterministic correctness | Unit test | `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnAprsLineParserTest.kt` |
| Callsign-prefix inference introduces false positives | Deterministic correctness | Unit tests with strict prefix allowlist (`ICA`, `FLR`) | `OgnAprsLineParserTest.kt` |
| Malformed `id` tokens accepted (`7 hex`) | Deterministic correctness | Parser edge-case unit tests | `OgnAprsLineParserTest.kt` |
| Callsign suffix (`-SSID`) breaks inference/fallback | Deterministic correctness | Parser unit tests with `CALL-1` forms | `OgnAprsLineParserTest.kt` |
| DDB identity from wrong type influences tracked/label policy | Deterministic correctness + privacy contract | Unit tests with duplicate hex across types | `OgnDdbJsonParserTest.kt`, new DDB repository lookup tests |
| Parser/DDB type mapping drift (unknown type handling mismatch) | Deterministic correctness | Unit tests for unsupported/unknown type tokens and `device_type` values | parser + DDB mapping tests |
| Same 6-hex in different types aliases thermal/trail state | Deterministic correctness | collision tests for canonical typed key propagation | new OGN collision tests |
| Same legacy id across types yields wrong marker/details selection | Deterministic correctness + UX correctness | map overlay + ViewModel selection key collision tests | new map OGN selection tests |
| Preferences accept invalid hex values | SSOT contract clarity | Unit test | `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt` |
| OGN settings draft state lost on recreation | MVVM/UDF + UX consistency | ViewModel/UI tests with recreation and deferred commit | new `OgnSettingsViewModelTest.kt` + UI tests |
| Ownship filtering not applied to trails/thermals | SSOT ownership | Integration/unit test | `OgnTrafficRepository` + existing thermal/trail tests |
| Trail-selection state not cleaned for suppressed ownship ids | UI/SSOT consistency | Unit test | new `OgnTrailSelectionPreferencesRepositoryTest.kt` |
| Legacy trail selection keys break after canonical key adoption | Backward compatibility | migration/alias tests | new `OgnTrailSelectionKeyMigrationTest.kt` |
| Socket ingest races with settings updates | Deterministic correctness | Repository concurrency tests + shared mutex guarding state | new `OgnTrafficRepositorySettingsRaceTest.kt` |
| Suppression diagnostics diverge from filtered outputs | Debug correctness | snapshot/debug-panel consistency tests | new suppression diagnostics tests |
| OGN repository interface expansion breaks test doubles | Build stability | Update all `FakeOgnTrafficRepository` test doubles in map/ogn tests | existing map/ogn unit tests |
| Pipeline drift undocumented | Documentation sync rule | Review gate | `docs/ARCHITECTURE/PIPELINE.md`, `docs/OGN/OGN_PROTOCOL_NOTES.md` |

## 3) Data Flow (Before -> After)

Before:

`APRS line -> OgnAprsLineParser (id type dropped) -> OgnTrafficRepository.targets -> Map OGN overlay + OgnThermalRepository + OgnGliderTrailRepository`

After:

`APRS line -> OgnAprsLineParser (typed id retained) -> OgnTrafficRepository ownship matcher (FLARM/ICAO from preferences) -> filtered targets + suppressedTargetIds -> Map OGN overlay + OgnThermalRepository + OgnGliderTrailRepository`

Settings flow:

`General -> OGN screen -> OgnSettingsViewModel -> OgnSettingsUseCase -> OgnTrafficPreferencesRepository -> OgnTrafficRepository matcher state`

## 4) Implementation Phases

### Phase 0 - Contract Lock + Test Vectors

- Goal:
  Lock protocol and matching contract before behavior change.
- Files to change:
  - `docs/OGN/OGN_PROTOCOL_NOTES.md`
  - `docs/OGN/OGN_APRS_TEST_VECTORS.md`
  - this plan file status/checklist updates
- Tests to add/update:
  - Define expected decode vectors for:
    - `idXXYYYYYY` (typed)
    - `idYYYYYY` (untyped)
    - malformed `id` (length 7) rejected
    - callsign-prefix fallback (`ICA`, `FLR`)
    - callsign with SSID suffix (`ICA484D20-1`, `FLRABC123-2`)
  - Define explicit precedence:
    - `id` token type (if present) > callsign prefix > unknown.
- Exit criteria:
  - Team-approved decode/match rules documented with no ambiguity.

### Phase 1 - Parser and Model Upgrade

- Goal:
  Preserve OGN address-type information in parsed target model.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnAprsLineParser.kt`
  - Optional new helper: `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnAddressIdentity.kt`
- Tests to add/update:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnAprsLineParserTest.kt`
  - Optional new parser-specific decode test file.
- Exit criteria:
  - Parsed target contains normalized 6-hex address plus typed source when available.
  - Parser rejects malformed `id` token lengths (including 7-hex).
  - Callsign fallback/inference works with and without APRS SSID suffix.
  - `OgnTrafficTarget.id` remains backward-compatible for this slice (no target-key migration).
  - Existing APRS vectors still pass.

### Phase 1B - Typed DDB Identity Alignment

- Goal:
  Keep identity enrichment and tracked-policy decisions consistent with typed addresses.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnDdbJsonParser.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnDdbRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
- Tests to add/update:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnDdbJsonParserTest.kt`
  - New: `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnDdbRepositoryLookupTest.kt`
- Exit criteria:
  - DDB lookups use type+hex when type is known.
  - Parser/DDB type mapping matrix is centralized and documented (including unknown handling).
  - Ambiguous hex-only collisions do not silently apply wrong identity/tracked policy.

### Phase 1C - Canonical Typed Key Adoption (Repository + Selection)

- Goal:
  Eliminate cross-type aliasing for repository state and marker selection while keeping
  UI-visible `target.id` display semantics stable.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelection*`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindings.kt` (selection filter key path)
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt` (trail row key path)
- Tests to add/update:
  - New: `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTypedKeyCollisionPolicyTest.kt`
  - New: `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenOgnSelectionKeyPolicyTest.kt`
  - New: `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrailSelectionKeyMigrationTest.kt`
- Exit criteria:
  - Two simultaneous targets with same legacy `id` but different address types remain independent in:
    - traffic cache
    - thermal trackers/hotspots
    - trail segments
    - trail selection keys
  - Marker tap/details selection uses canonical key internally and stays deterministic under collision.
  - UI-visible `target.id` text/details remain unchanged.
  - Existing persisted trail selection keys remain effective via migration/alias rules.

### Phase 2 - Preferences SSOT + OGN Settings UI

- Goal:
  Add persistent own FLARM/ICAO inputs with strict validation.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsUseCase.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsViewModel.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsScreen.kt`
  - Optional new UI state model: `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsUiState.kt`
- Tests to add/update:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
  - New: `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsViewModelTest.kt`
    (commit/save semantics, normalization path, recreation-safe draft state).
- Exit criteria:
  - Both fields accept only uppercase 6-hex (stored null if blank).
  - Input surface enforces hex-only entry path (UI + repository clamp/normalize).
  - Blank/invalid commit removes key or keeps previous valid value per agreed policy;
    no empty-string sentinel in SSOT.
  - ViewModel owns draft text + validation/error state; Composable remains render/intent only.
  - UI avoids DataStore write-on-every-keystroke churn.
  - Settings UI includes concise guidance text for FLARM/ICAO hex format and where values come from.
  - Existing icon-size settings behavior remains intact.

### Phase 3 - Repository-Level Ownship Filtering

- Goal:
  Filter ownship targets once in repository before publish.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelectionUseCase.kt` (if repository API expands)
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt` (if suppression flow surfaced)
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanels.kt` (if suppression diagnostics surfaced)
  - (if needed) `feature/map/src/main/java/com/trust3/xcpro/di/MapBindingsModule.kt` wiring remains valid
- Tests to add/update:
  - New: `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficOwnshipFilterPolicyTest.kt`
  - New: `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositorySettingsRaceTest.kt`
  - New: suppression diagnostics consistency tests (snapshot/target list/suppression set).
  - Update OGN repository policy tests if helper methods are exposed internally.
  - Update all `FakeOgnTrafficRepository` implementations under:
    - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`
    - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnGliderTrailRepositoryTest.kt`
    - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt`
  - Verify existing `OgnThermalRepositoryTest` and `OgnGliderTrailRepositoryTest` remain green.
- Exit criteria:
  - Incoming ownship targets are excluded when typed id matches configured ownship IDs.
  - Unknown/missing type never causes false-positive cross-type filtering.
  - Changing settings at runtime updates filtering behavior without app restart.
  - In-session ownship trail/thermal artifacts are purged promptly after suppression becomes active.
  - Suppressed ownship ids are pruned from persisted trail-selection keys.
  - Legacy persisted trail-selection keys are migrated/aliased to canonical typed keys without user data loss.
  - `suppressedTargetIds` flow is exposed and consumed where required.
  - Marker/details selection is cleared or re-bound deterministically when suppression removes a selected target.
  - Ownship match policy is unit-testable via extracted pure helper (no socket/network dependency).
  - Shared-state mutation remains deterministic under concurrent ingest/settings updates.

### Phase 4 - Hardening + Docs + Verification

- Goal:
  Ensure no architectural drift and publish final contract docs.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/OGN/OGN_PROTOCOL_NOTES.md`
  - `docs/OGN/OGN.md` (if user-facing settings contract section needs update)
- Tests to add/update:
  - Any missing regression test found during verification.
- Exit criteria:
  - Required checks pass and docs reflect implemented flow.

## 5) Test Plan

- Unit tests:
  - Parser decodes typed id and preserves legacy behavior for existing vectors.
  - Parser infers type from callsign prefixes only for approved prefixes.
  - Parser leaves unknown type as unknown for non-approved prefixes.
  - Parser rejects invalid `id` lengths and preserves compatibility for valid 6/8 forms.
  - Callsign parsing strips SSID before fallback/inference checks.
  - DDB typed parse and lookup:
    - duplicate `device_id` across different `device_type` values handled deterministically
    - unknown type does not override known typed entry
    - unsupported `device_type` maps to explicit unknown type (no cross-type coercion)
  - Canonical typed key behavior:
    - same legacy id + different type yields distinct internal keys
    - suppression, trail, thermal, and selection operate on typed keys
    - marker tap/details selection remains deterministic when legacy ids collide
    - UI label/details still display existing `target.id` contract
  - Trail key migration/compatibility:
    - legacy stored trail key resolves to canonical typed keys via migration/alias rules
    - ambiguous legacy key fan-out behavior is deterministic and documented
    - canonical keys are persisted on next mutation to converge storage format
  - Preferences normalization:
    - accepts `abcdef` -> stores `ABCDEF`
    - accepts lowercase/whitespace and normalizes
    - rejects `0x` prefix and non-hex content
    - rejects/normalizes invalid input
    - blank -> null
    - updates occur on commit action; typing alone does not write to DataStore
    - draft values and validation messages survive recreation until commit/cancel
  - Ownship matcher policy:
    - FLARM match filters only FLARM targets with same hex
    - ICAO match filters only ICAO targets with same hex
    - same 6-hex different type does not filter
    - unknown type does not filter unless explicit policy says otherwise
  - Runtime update policy:
    - enabling ownship settings prunes already-cached matching targets
    - suppression set updates are emitted deterministically
  - Suppression diagnostics:
    - snapshot/debug suppression counters stay consistent with filtered target output
    - diagnostics remain deterministic under ingest/settings races
- Unit/integration tests for derived artifact purge:
  - `OgnGliderTrailRepositoryTest`: matching `sourceTargetId` segments removed on suppression.
  - `OgnThermalRepositoryTest`: matching hotspots/trackers removed on suppression.
  - `OgnTrailSelectionPreferencesRepositoryTest`: suppressed ids removed from persisted selected keys.
- Concurrency and interface-compat tests:
  - Repository race tests for ingest + settings update interleaving.
  - Compile/behavior checks for all updated fake `OgnTrafficRepository` test doubles.
- Replay/regression tests:
  - Existing replay and map unit suites remain unchanged/green.
- UI/instrumentation tests (if needed):
  - OGN settings screen persists both fields across recreation.
  - OGN settings screen shows validation/help text and commit-only writes.
- Degraded/failure-mode tests:
  - Parser receives frame without `id` token (no crash, no false filter).
  - Preferences unavailable/corrupt fallback uses null (no filtering).
  - Unsupported DDB `device_type` / unknown typed token does not trigger wrong suppression.
- Boundary tests for removed bypasses:
  - No label/CN-based self-filter.
  - Filtering remains in repository path, not composable/viewmodel.
  - Derived repositories do not duplicate hex/type matching logic.
  - Repository ownship match logic tested through pure helper, not only socket-path integration.
  - Marker selection/tap path does not depend on ambiguous legacy `target.id`.

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
| Wrong type decode from OGN `id` prefix | High (false hide/show) | Lock decode vectors first; keep unknown fallback; avoid heuristic cross-type matching | XCPro Team |
| Filtering only in UI path | High (trails/thermals still include ownship) | Enforce repository-level filtering before `targets` publish | XCPro Team |
| Invalid user input causes over-filtering | Medium | strict 6-hex normalization + null on invalid/blank | XCPro Team |
| Runtime setting updates do not re-filter existing cache | Medium | re-evaluate/prune current `targetsByKey` on preference changes | XCPro Team |
| Ownship trail/thermal artifacts remain after enabling self-filter | High | consume `suppressedTargetIds` and purge matching derived state | XCPro Team |
| DDB duplicate hex across types applies wrong identity/tracked policy | High | typed DDB key index and ambiguity-safe fallback | XCPro Team |
| Same legacy id across types aliases non-UI state | High | canonical typed key for all non-UI repository/domain keying | XCPro Team |
| Parser accepts malformed `id` token lengths | Medium | enforce strict 6/8 token validation with regression tests | XCPro Team |
| APRS SSID suffix breaks source-based inference | Medium | normalize source callsign (`substringBefore('-')`) before prefix/hex logic | XCPro Team |
| Trail selection retains suppressed ownship keys | Low/Medium | prune suppressed ids in selection repository and test persistence behavior | XCPro Team |
| Per-keystroke settings writes cause DataStore churn and transient invalid SSOT | Medium | commit-on-save semantics with normalization at commit boundary | XCPro Team |
| Concurrent ingest/settings mutations produce non-deterministic target set | High | shared mutex/atomic section for target map + suppression state updates | XCPro Team |
| Repository interface expansion causes widespread test failures | Medium | explicit fake-repo migration checklist in Phase 3 | XCPro Team |
| Marker/details selection ambiguity under legacy id collisions | High | canonical typed selection key path in overlay/coordinator/viewmodel | XCPro Team |
| Legacy persisted trail-selection keys become ineffective after key upgrade | Medium | migration/alias compatibility + convergence rewrite | XCPro Team |
| Parser and DDB type mapping drift over time | Medium | single mapping matrix helper + docs + tests | XCPro Team |
| Draft ownship settings lost across recreation before commit | Medium | ViewModel-owned draft state with explicit commit/cancel actions | XCPro Team |
| Suppression diagnostics disagree with filtered output | Low/Medium | snapshot/debug consistency tests and single-writer update path | XCPro Team |
| Contract drift with docs | Low | update pipeline + OGN protocol notes in same PR | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Filtering policy is type-aware (`FLARM`, `ICAO`) and deterministic.
- Competition-number/callsign labels are not used for ownship suppression.
- Existing ownship marker/trail/thermal artifacts are removed in-session once suppression is configured.
- `OgnTrafficTarget.id` behavior remains stable in this change (unless explicit migration is approved).
- Marker tap/details selection remains deterministic even if two targets share legacy `id`.
- Existing user trail selections survive canonical-key migration/alias handling.
- Replay behavior remains deterministic and unaffected.
- `KNOWN_DEVIATIONS.md` unchanged unless explicit approved exception is required.

## 8) Rollback Plan

- What can be reverted independently:
  - UI fields and preference keys (keep keys inert if rollback needed).
  - Parser typed-id decode while preserving legacy 6-hex fallback.
  - Repository filtering block (temporary return to no self-filter behavior).
- Recovery steps if regression is detected:
  1. Disable repository ownship filter path behind temporary guard.
  2. Keep parser/model additions (non-breaking) to avoid repeated migrations.
  3. Ship hotfix and continue with corrected decode vectors.

## 9) Recommended Product Decision (Advisory)

Use both fields and typed matching:

1. Keep two optional inputs: `Own FLARM ID` and `Own ICAO24`.
2. Match only against same address type + same 6-hex value.
3. Do not match using competition ID, registration, or callsign.
4. Infer type from callsign prefix only on strict allowlist when `id` token type is missing.
5. Align DDB identity/tracked lookup with address type where available.
6. Normalize callsign for inference by dropping APRS SSID suffix.
7. Use commit-on-save semantics for ownship ID inputs; persist only normalized committed values.
8. If no own IDs configured, behavior remains unchanged.
9. Keep marker/details selection keyed by canonical typed key internally; keep `target.id` visible for continuity.
10. Preserve legacy trail-selection keys via migration/alias compatibility to avoid user-facing selection loss.
11. Keep unknown type non-matching by default; prefer false-negative (visible ownship) over false-positive suppression.

This is the lowest-risk approach to avoid hiding another aircraft that shares a
6-hex value under a different address type family.

## 10) Pass-2 Gap Closure (What Was Missed In First Draft)

1. Added explicit callsign-prefix fallback and precedence rules for type inference.
2. Added explicit `OgnTrafficTarget.id` stability decision to avoid accidental key churn.
3. Added suppression propagation and purge requirements for OGN trail/thermal repositories.
4. Added tests for runtime settings-change behavior and derived-artifact cleanup.
5. Added risk entry for legacy typed-key collision debt and follow-up requirement.

## 11) Pass-3 Gap Closure (What Was Missed In Second Draft)

1. Added typed DDB alignment requirement (`device_type + device_id`) to avoid cross-type identity collisions.
2. Added dedicated Phase 1B for DDB parser/repository updates and lookup safety tests.
3. Added requirement to extract ownship match helper so policy is testable without socket-path integration.
4. Tightened input-normalization test cases (`0x` reject, whitespace/lowercase normalization).

## 12) Pass-4 Gap Closure (What Was Missed In Third Draft)

1. Added strict parser token-length validation (accept 6/8 hex only, reject 7-hex malformed ids).
2. Added APRS callsign SSID normalization requirement before prefix/hex inference.
3. Added trail-selection key reconciliation so suppressed ownship ids do not linger in persisted selection state.
4. Added parser and trail-selection regression tests for these cases.

## 13) Pass-5 Gap Closure (What Was Missed In Fourth Draft)

1. Added repository contract expansion requirement (`suppressedTargetIds`) and downstream consumption plan.
2. Added explicit concurrency hardening requirement for ingest/settings interleaving.
3. Added commit-on-save persistence semantics for OGN ownship text fields to avoid DataStore churn.
4. Added explicit fake-repository migration checklist for test suites impacted by interface expansion.

## 14) Pass-6 Gap Closure (What Was Missed In Fifth Draft)

1. Added canonical typed transport-key requirement for non-UI keying paths.
2. Added dedicated Phase 1C to migrate repository/trail/thermal/selection internals off legacy `target.id`.
3. Added collision-policy tests for same legacy id across different address types.
4. Kept UI-facing `target.id` compatibility explicit while hardening internal identity correctness.

## 15) Pass-7 Gap Closure (What Was Missed In Sixth Draft)

1. Added explicit marker tap/details selection collision handling: map overlay + ViewModel selection now require canonical typed keys internally.
2. Added Phase 1C file/test coverage for `MapOverlayManager`, `OgnTrafficOverlay`, coordinator/state builders, and map selection policy tests.
3. Added acceptance/risk coverage for deterministic selection when multiple targets share legacy `id`.

## 16) Pass-8 Gap Closure (What Was Missed In Seventh Draft)

1. Added backward-compatible migration/alias plan for legacy persisted trail-selection keys when canonical typed keys are introduced.
2. Added deterministic ambiguity handling requirements for legacy keys and convergence rewrite to canonical keys.
3. Added dedicated migration test coverage in Phase 1C/Test Plan and updated risk/acceptance gates.

## 17) Pass-9 Gap Closure (What Was Missed In Eighth Draft)

1. Added ViewModel-owned draft/validation state contract for new FLARM/ICAO inputs to preserve MVVM/UDF boundaries.
2. Added recreation-safe behavior requirements and tests for deferred commit flows.
3. Added settings guidance text requirement to reduce user entry errors without expanding scope to full UI redesign.

## 18) Pass-10 Gap Closure (What Was Missed In Ninth Draft)

1. Added centralized address-type mapping matrix requirement across parser typed-id decode, callsign fallback, and DDB `device_type`.
2. Added unknown/unsupported mapping guardrails so unknown types remain unknown (no silent coercion).
3. Added parser + DDB mapping test expectations and protocol-doc update requirements.

## 19) Pass-11 Gap Closure (What Was Missed In Tenth Draft)

1. Added suppression diagnostics visibility requirement (snapshot/debug counters/reasons) to verify self-filter behavior during rollout.
2. Added consistency-test requirements to keep filtered targets, suppression set, and diagnostics aligned under race conditions.
3. Added risk and Phase 3 file/test updates to keep suppression observability deterministic and reviewable.

## 20) Implementation Update (2026-02-23)

Implemented in code:
1. Parser and addressing:
   - strict OGN `id` acceptance (6/8 hex, reject malformed 7-hex),
   - typed address derivation and canonical key model (`OgnAddressing.kt`).
2. DDB alignment:
   - parser emits typed DDB entries,
   - repository lookup is typed-first with ambiguity-safe unknown fallback.
3. Settings and preferences:
   - own FLARM/ICAO preference flows and setters,
   - OGN settings ViewModel/state and UI commit flow with validation.
4. Repository filtering:
   - ownship suppression in `OgnTrafficRepositoryImpl`,
   - suppression diagnostics in snapshot (`suppressedTargetIds`).
5. Canonical key path:
   - canonical-key traffic cache identity,
   - canonical-key map marker tap selection,
   - canonical-key trail/thermal tracking.
6. Derived artifact purge:
   - trail and thermal repositories consume suppression keys and purge ownship artifacts.
7. Trail selection compatibility:
   - alias-aware key matching for legacy/canonical keys,
   - suppressed-key pruning from trail-selection preferences.

Verification run locally:
1. `./gradlew --no-configuration-cache enforceRules` (pass)
2. `./gradlew --no-configuration-cache testDebugUnitTest` (pass)
3. `./gradlew --no-configuration-cache assembleDebug` (pass)
