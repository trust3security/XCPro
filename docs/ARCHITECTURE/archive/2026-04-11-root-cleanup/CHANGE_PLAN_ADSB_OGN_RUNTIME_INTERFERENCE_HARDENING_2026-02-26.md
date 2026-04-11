# CHANGE_PLAN_ADSB_OGN_RUNTIME_INTERFERENCE_HARDENING_2026-02-26.md

## Purpose

Harden ADS-B and OGN runtime behavior to reduce app interference (CPU/render churn,
unnecessary recompute, and avoidable metadata/network side load) while preserving
architecture rules and current user-visible traffic semantics.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: ADS-B and OGN runtime interference hardening
- Owner: XCPro Team
- Date: 2026-02-26
- Issue/PR: TBD
- Status: Draft

## 1) Scope

- Problem statement:
  ADS-B and OGN integrations are functionally correct but still perform avoidable
  work that can interfere with map smoothness, battery, and thermal behavior.
- Why now:
  Current implementation already includes reliability hardening; next risk cluster
  is performance interference from redundant update fan-out.
- In scope:
  - Remove unnecessary ADS-B recompute when streaming is disabled.
  - Bound ADS-B ownship-driven reselection cadence while streaming is enabled.
  - Reduce OGN center-update fan-out that cascades into thermal/trail processing.
  - Decouple ADS-B metadata enrichment queries (list + selected target) from high-frequency ownship-relative target churn.
  - Reduce disabled-overlay render churn from Compose effects.
  - Add guardrails for ADS-B overlay interpolation cadence.
  - Reduce OGN downstream no-fresh hot-loop cost (thermal/trail).
  - Expand focused tests for these paths.
- Out of scope:
  - OGN protocol/provider migration.
  - ADS-B provider/API contract changes.
  - New traffic features (alerts, collision policy redesign).
  - Task/replay scoring/navigation behavior.
- User-visible impact:
  - Same traffic semantics.
  - Lower background churn when overlays are off or map is hidden.
  - Lower jank/thermal pressure in traffic-heavy sessions.

### Re-pass Findings (What Was Missed)

1. ADS-B recompute still runs while streaming is disabled:
   - `MapScreenTrafficCoordinator` forwards GPS/ownship/filter updates unconditionally.
   - `AdsbTrafficRepository` immediately calls `publishFromStore(...)` for those updates.
   - Evidence:
     - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt:85`
     - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt:121`
     - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt:210`
     - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt:240`

2. ADS-B ownship altitude path can drive high-frequency reselection while enabled:
   - altitude state is sourced from live flight data and forwarded every emission.
   - repository equality check is exact `Double` equality, then full reselection runs.
   - reselection cost includes multiple haversines + sorting in `AdsbTrafficStore.select(...)`.
   - Evidence:
     - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelStateBuilders.kt:117`
     - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt:121`
     - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt:242`
     - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt:41`

3. OGN center updates trigger distance-refresh publishes that are not ingest-driven:
   - `updateCenter(...)` refreshes per-target distance and may publish every GPS movement.
   - thermal/trail repos consume the same target stream and re-run processing loops.
   - Evidence:
     - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt:211`
     - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt:550`
     - `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt:96`
     - `feature/map/src/main/java/com/example/xcpro/ogn/OgnGliderTrailRepository.kt:66`

4. OGN trail processing has avoidable sort cost on each upstream emission:
   - `processTargets(...)` sorts all targets before freshness checks.
   - distance-only updates still pay `sortedBy { it.id }` cost.
   - Evidence:
     - `feature/map/src/main/java/com/example/xcpro/ogn/OgnGliderTrailRepository.kt:104`

5. OGN thermal processing still loops full target list on no-fresh emissions:
   - freshness gating exists per target, but loop and downstream housekeeping work still run for all entries.
   - Evidence:
     - `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt:123`
     - `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt:142`
     - `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt:161`

6. Disabled overlay still causes render-path work in Compose:
   - effect keys are coupled to raw target lists and re-trigger while disabled.
   - Evidence:
     - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt:84`
     - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt:102`

7. ADS-B metadata enrichment is over-triggered by raw target updates:
   - list metadata flow is keyed by full target stream (distance/age churn included).
   - selected-target details path also re-queries metadata DB on target stream churn.
   - Evidence:
     - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt:26`
     - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt:59`
     - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt:75`

8. ADS-B interpolation runs at vsync while active (no explicit fps cap):
   - Evidence:
     - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt:57`
     - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt:273`

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B raw/cache targets | `AdsbTrafficRepository` | `StateFlow<List<AdsbTrafficUiModel>>` | UI-owned authoritative traffic mirrors |
| OGN raw targets/suppression/snapshot | `OgnTrafficRepository` | `StateFlow` snapshot + targets | Overlay-owned source-of-truth |
| OGN thermal hotspots | `OgnThermalRepository` | `StateFlow<List<OgnThermalHotspot>>` | Runtime overlay-owned hotspot truth |
| OGN glider trail segments | `OgnGliderTrailRepository` | `StateFlow<List<OgnGliderTrailSegment>>` | Runtime overlay-owned trail truth |
| ADS-B metadata records + revision | `AircraftMetadataRepository` | metadata store + `metadataRevision` flow | ViewModel-local metadata authorities |
| Runtime interpolation/throttle state | `AdsbTrafficOverlay` / `MapOverlayManager` | internal runtime state only | repository-side animation state |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnGliderTrailRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - related tests under `feature/map/src/test/java/com/example/xcpro/{adsb,ogn,map}`
- Boundary risk:
  - Accidentally moving business policy into Compose/runtime map classes.
  - Introducing duplicate target ownership while adding cache/trigger guards.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| ADS-B recompute scheduling on non-streaming updates | implicit immediate reselection inside repository mutators | explicit repository-side reselection gate policy | avoid disabled-path churn | `AdsbTrafficRepositoryTest` |
| ADS-B ownship altitude reselection cadence | unbounded per-sample forwarding and exact-value trigger | explicit ownship reselection policy (quantization + minimum interval) | avoid high-frequency CPU churn while preserving vertical-filter semantics | `AdsbTrafficRepositoryTest` |
| OGN center-distance refresh cadence | immediate on every center write | explicit repository distance-refresh policy (movement + interval gate) | reduce non-ingest target churn | `OgnTrafficRepositoryPolicyTest` |
| OGN no-fresh downstream loops (thermal/trail) | full-list processing on every upstream emission | no-fresh fast-path and deterministic housekeeping-only reentry | reduce CPU churn without dropping real ingest events | `OgnThermalRepositoryTest`, `OgnGliderTrailRepositoryTest` |
| ADS-B metadata lookup trigger policy | `targetsWithMetadata` keyed to full target stream | metadata enrichment keyed to ICAO set + metadata revision | avoid DB lookup storms from distance-only updates | `AdsbMetadataEnrichmentUseCaseTest` |
| ADS-B selected-target metadata trigger policy | selected details path re-queries metadata on raw target churn | selected metadata lookup keyed to selected ICAO + metadata revision/sync-state events | avoid repeated single-row DB lookups from distance-only churn | `AdsbMetadataEnrichmentUseCaseTest` |
| Disabled-overlay map traffic effect triggering | Compose key coupled to raw target list | Compose key coupled to overlay-gated target list | stop no-op runtime updates while disabled | map UI effect tests/review |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapScreenRootEffects` ADS-B effect | raw target changes trigger effect even when overlay disabled | overlay-gated key/effect input | 4 |
| `AdsbMetadataEnrichmentUseCase.targetsWithMetadata` | metadata DB lookup on every raw target emission | ICAO-set/revision keyed lookup path | 2 |
| `AdsbMetadataEnrichmentUseCase.selectedTargetDetails` | selected metadata lookup on each selected-target stream emission | selected-ICAO/revision keyed lookup with sync-state-only availability mapping | 2 |
| `MapScreenTrafficCoordinator` ownship forwarding | unconditional update forwarding | enabled-path forwarding and seed-on-enable | 1 |
| `OgnGliderTrailRepository.processTargets` | unconditional full-list sort + iterate | freshness-aware iteration with no-fresh fast-path | 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| ADS-B reselection cooldown/guard intervals | Monotonic | stable runtime duration comparisons |
| ADS-B ownship altitude minimum-interval gate | Monotonic | deterministic cadence guard without wall-time drift |
| OGN center distance-refresh min interval | Monotonic | stable duration gating |
| OGN movement threshold checks | N/A (spatial) + monotonic cadence guard | movement gating + anti-churn cadence |
| Metadata revision trigger | N/A (state revision) | non-time event sequencing |
| Snapshot/user diagnostics wall timestamps | Wall | user-facing/debug semantics only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `IO`: socket/http/file calls.
  - `Default`: target reselection, per-list filtering/sorting, non-I/O metadata merge calculations.
  - `Main`: map runtime render and Compose effects.
- Primary cadence/gating sensor:
  - ADS-B poll cadence remains repository policy-driven.
  - OGN ingest cadence remains APRS stream-driven.
  - New work adds anti-churn gates for non-ingest recompute paths only.
  - ADS-B ownship altitude reselection target cadence: at most 1 Hz unless threshold-crossing applies.
- Hot-path latency budget:
  - streaming enable -> first traffic update unchanged from current behavior.
  - non-streaming ownship updates should avoid full target reselection.
  - enabled-path ownship updates should avoid reselection storms under noisy altitude samples.
  - ADS-B interpolation render cap target: <= 15 fps equivalent when active.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No new randomness.
- Replay/live divergence rules:
  - Traffic stack remains live-network behavior only.
  - Replay fusion/timing semantics remain unchanged.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Disabled ADS-B updates still reselection-loop | ARCHITECTURE threading + CODING_RULES flow/state | unit tests | `AdsbTrafficRepositoryTest` |
| ADS-B ownship altitude jitter causes reselection storm | ARCHITECTURE threading + CODING_RULES hot-path discipline | unit tests | `AdsbTrafficRepositoryTest` |
| OGN center churn triggers thermal/trail work | ARCHITECTURE SSOT and deterministic behavior | unit tests + review | `OgnTrafficRepositoryPolicyTest`, `OgnThermalRepositoryTest`, `OgnGliderTrailRepositoryTest` |
| OGN trail no-fresh emissions still pay sort/traversal tax | CODING_RULES performance safety | unit tests | `OgnGliderTrailRepositoryTest` |
| Metadata lookup storm from target churn | CODING_RULES maintainability/perf safety | unit tests | `AdsbMetadataEnrichmentUseCaseTest` |
| Selected-target metadata lookup storm | CODING_RULES maintainability/perf safety | unit tests | `AdsbMetadataEnrichmentUseCaseTest` |
| Disabled-overlay no-op render churn reintroduced | UI/runtime boundary rules | code review + tests | `MapScreenRootEffects` tests (new) |
| ADS-B frame loop over-budget | threading/cadence hardening | runtime unit tests + manual profiling | `AdsbTrafficOverlay` tests + overheating playbook run |

## 3) Data Flow (Before -> After)

Before:

`GPS/ownship updates (including high-frequency altitude samples) -> ADS-B repository reselection (enabled + disabled paths) -> raw targets emit -> metadata enrichment list + selected details lookup -> UI effect trigger -> overlay no-op clear/render`

`GPS center updates -> OGN distance refresh publish -> thermal/trail repos process full target list (trail sort included) even without fresh ingest`

After:

`GPS/ownship updates -> gated ADS-B reselection (streaming-enabled path only + ownship cadence gate) -> raw targets emit only when meaningful -> metadata enrichment keyed by ICAO set/revision and selected-ICAO/revision -> overlay updates only when overlay-gated targets change`

`GPS center updates -> OGN distance-refresh policy gate -> reduced non-ingest emissions -> thermal/trail process only on fresh/suppression-significant input with no-fresh fast-path`

Authoritative ownership remains:

`Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI`

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  capture current interference behavior and add failing/targeted tests for missed cases.
- Files to change:
  - tests only (no production behavior changes).
- Tests to add/update:
  - `AdsbTrafficRepositoryTest`:
    - disabled updates do not reselection-loop.
    - ownship altitude jitter does not trigger unbounded reselection frequency (baseline failing case).
    - resume path still applies latest center/origin/filters.
  - `AdsbMetadataEnrichmentUseCaseTest`:
    - distance/age-only raw target updates do not trigger repeated metadata lookup.
    - selected-target details path does not query metadata repeatedly on raw-target churn.
  - `OgnTrafficRepositoryPolicyTest`:
    - tiny center movement does not republish targets repeatedly.
  - `OgnGliderTrailRepositoryTest`:
    - distance-only target updates do not repeatedly trigger full sort/processing.
- Exit criteria:
  baseline pain points are reproducible by tests.

### Phase 1 - ADS-B disabled-path churn hardening

- Goal:
  remove avoidable ADS-B computation while streaming is off and bound ownship-driven churn while on.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - (if needed) `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
- Planned changes:
  - gate coordinator forwarding of ADS-B ownship/center updates by enabled path and seed-on-enable behavior.
  - in repository mutators, store inputs always but defer `publishFromStore(...)` when `_isEnabled` is false.
  - add deterministic ownship reselection policy for altitude jitter (movement threshold and minimum monotonic interval).
  - keep explicit `clearTargets()` behavior unchanged.
- Tests to add/update:
  - `AdsbTrafficRepositoryTest` new/updated scenarios for disabled-path updates.
  - `AdsbTrafficRepositoryTest` cadence scenarios for ownship altitude jitter.
- Exit criteria:
  no full reselection while disabled; enabled ownship path avoids reselection storms; fast resume remains intact.

### Phase 2 - Metadata trigger hardening

- Goal:
  avoid metadata DB lookup work tied to ownship-relative target update frequency.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
  - (if required) `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelStateBuilders.kt`
- Planned changes:
  - key target metadata lookup by ICAO set changes + metadata revision, not by every raw target emission.
  - key selected-target metadata lookup by selected ICAO + metadata revision/sync transitions, not by distance-only raw churn.
  - keep selected-target dynamic flight fields (distance/bearing/age) fresh from raw target stream.
- Tests to add/update:
  - `AdsbMetadataEnrichmentUseCaseTest`
- Exit criteria:
  lookup cadence follows metadata relevance (set/revision/selection), not GPS churn.

### Phase 3 - OGN non-ingest fan-out hardening

- Goal:
  reduce center-induced OGN target churn that cascades into thermal/trail loops.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnGliderTrailRepository.kt`
- Planned changes:
  - add center distance-refresh policy (movement threshold + min interval gate).
  - add no-fresh fast-path in thermal/trail repos when input carries no fresh target samples and no suppression/state change requiring work.
  - remove unconditional target-list sort cost from trail processing path for no-fresh emissions.
- Tests to add/update:
  - `OgnTrafficRepositoryPolicyTest`
  - `OgnThermalRepositoryTest`
  - `OgnGliderTrailRepositoryTest`
- Exit criteria:
  no repeated full downstream processing from distance-only churn.

### Phase 4 - UI/runtime render-path hardening

- Goal:
  eliminate disabled-overlay render churn and bound ADS-B interpolation render cost.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- Planned changes:
  - overlay-gated effect keys for ADS-B/OGN render updates.
  - no-op short-circuit in overlay manager when target list has not changed.
  - cap ADS-B interpolation render cadence while preserving smoothness.
- Tests to add/update:
  - new/updated map runtime tests for no-op and capped-frame behavior.
- Exit criteria:
  disabled overlays stay computationally quiet; active overlay interpolation remains visually acceptable.

### Phase 5 - Docs sync and final verification

- Goal:
  sync architecture docs and close with full verification.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ADS-b/ADSB.md`
  - `docs/OGN/OGN.md`
  - this plan status section
- Exit criteria:
  docs match implemented behavior; all required checks pass.

## 5) Test Plan

- Unit tests:
  - ADS-B repository disabled-path recompute gating.
  - ADS-B ownship altitude cadence/threshold gating under noisy samples.
  - OGN center-refresh gating and downstream no-fresh fast paths.
  - metadata enrichment trigger policy (list + selected-target path).
  - OGN trail no-fresh path avoids repeated sort-driven churn.
  - map runtime no-op render gating and ADS-B frame cap behavior.
- Replay/regression tests:
  - replay remains unchanged; rerun targeted replay smoke tests for no side effects.
- UI/instrumentation tests (if needed):
  - overlay toggling with map visible/invisible lifecycle transitions.
- Degraded/failure-mode tests:
  - rapid overlay on/off transitions.
  - no-center startup then center seed.
  - ownship altitude noise bursts (high-frequency small deltas).
  - network offline/online transitions with ADS-B disabled and re-enabled.
- Boundary tests for removed bypasses:
  - Compose effect not re-triggered by disabled-overlay upstream target churn.
  - selected-target metadata lookup not retriggered by distance-only raw target updates.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Additional for this slice:

```bash
./gradlew :feature:map:compileDebugKotlin
./gradlew :feature:map:testDebugUnitTest
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Over-gating causes stale distance/bearing updates | Medium | movement+time gates tuned with tests and manual map checks | XCPro Team |
| ADS-B altitude cadence gate hides legitimate vertical-filter boundary crossings | High | threshold + max-interval dual gate; targeted boundary tests around above/below filter limits | XCPro Team |
| Resume after disabled state misses latest context | High | explicit seed-on-enable and resume tests | XCPro Team |
| Metadata details regress under new trigger policy | Medium | selected-target details tests and metadata revision tests | XCPro Team |
| Visual smoothness drops after ADS-B frame cap | Medium | cap with conservative threshold + A/B profiling | XCPro Team |
| OGN thermal/trail fast-path drops legitimate events | High | fresh-sample detection tests + housekeeping timeout tests | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling remains explicit and correct.
- Replay behavior remains deterministic and unchanged.
- ADS-B disabled path does not execute avoidable reselection work.
- ADS-B ownship altitude jitter no longer causes unbounded reselection churn.
- OGN center update path no longer causes avoidable downstream full-loop churn.
- Metadata lookup cadence follows metadata-relevant triggers for both list and selected-target paths.
- `KNOWN_DEVIATIONS.md` remains unchanged unless explicitly approved.

## 8) Rollback Plan

- What can be reverted independently:
  1. ADS-B disabled-path reselection gating.
  2. ADS-B ownship altitude cadence gate.
  3. metadata enrichment trigger refactor.
  4. OGN center-distance refresh/no-fresh fast-path gating.
  5. UI effect/render-path no-op gates and ADS-B frame cap.
- Recovery steps if regression is detected:
  1. Revert only the failing phase.
  2. Re-run targeted module tests for that phase.
  3. Keep docs consistent with rolled-back behavior before merge.
