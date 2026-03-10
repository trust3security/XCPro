# ADS-B Position Freshness and Rewind Fix - Production-Grade Phased IP

## 0) Metadata

- Title: Eliminate stale-position rewind jumps in ADS-B and harden position freshness semantics
- Owner: XCPro Team
- Date: 2026-03-10
- Issue/PR: TBD
- Status: Draft
- Baseline score: `94/100`
- Target score after plan hardening and full implementation: `97/100`

### 0.1 Score-Lift Rationale

- `+1`: explicit authoritative sample-ordering contract (no ambiguity on older/newer/null timestamp cases)
- `+1`: compatibility bridge for `ageSec`/`isStale` plus GeoJSON/export surfaces
- `+1`: broader test blast-radius coverage, fixture migration, and visual-churn guardrails

## 1) Scope

- Problem statement:
  - ADS-B aircraft can visibly jump backward because the runtime treats packet receipt/contact freshness as position freshness, blindly overwrites by ICAO, and animates the corrective move.
- Why now:
  - A user-visible reverse-jump bug is present in live ADS-B traffic.
  - The current timing contract is inconsistent across parser, repository, store, proximity, details UI, and map smoothing.
  - Existing docs/tests currently lock the wrong freshness semantics, so delaying the fix increases regression risk.
- In scope:
  - Provider position-time authority (`timePositionSec`) and provider response-time normalization (`response.timeSec`).
  - Latest-position-wins repository/store policy for ADS-B targets.
  - Position-age vs contact-age separation through store/UI details.
  - Proximity/emergency/ordering/expiry migration to geometry freshness.
  - ADS-B map smoother hardening against regressive or large corrective updates.
  - Transitional compatibility for `ageSec` / `isStale` and existing GeoJSON/style consumers.
  - Low-cost observability for timing fallbacks, stale-geometry drops, and visual snap events.
  - Test-support fixture migration so future coverage does not silently keep receipt-time authority.
  - Regression, integration, and perf evidence for the affected ADS-B path.
- Out of scope:
  - ADS-B transport/auth retry policy.
  - OGN logic.
  - Aircraft metadata import correctness beyond fields already consumed by ADS-B runtime.
  - New user settings or rollout flags unless required by measured risk.
- User-visible impact:
  - ADS-B aircraft should no longer animate backward due to older provider positions.
  - Details/debug surfaces should distinguish stale geometry from fresh packet/contact state.
  - Emergency/proximity output should be more conservative when position geometry is stale.

## 1A) Deep-Pass Findings (What Was Missed)

1. Provider position time is parsed but dropped before SSOT.
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/OpenSkyStateVectorMapper.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/OpenSkyModels.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficModels.kt`

2. Provider response time is parsed but unused; ADS-B freshness still depends on device wall time.
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/OpenSkyStateVectorMapper.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimePolling.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficThreatPolicies.kt`

3. All rows in a poll are stamped with the same local `receivedMonoMs`, so mixed fresh/stale rows become equally fresh at ingest.
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimePolling.kt`

4. Store writes are blind by ICAO; older geometry can overwrite newer geometry.
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`

5. Staleness, expiry, emergency age gating, display ordering, and emergency-candidate selection all consume the wrong age semantics.
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficSelectionOrdering.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbCollisionRiskEvaluator.kt`

6. Target-motion/trend timing currently advances from local receipt time, so repeated stale coordinates can still look like fresh target updates.
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficThreatPolicies.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTargetTrackEstimator.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbProximityTrendEvaluator.kt`

7. Details UI exposes one ambiguous `Age` field, so operators cannot distinguish stale position from fresh contact.
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbSelectedTargetDetails.kt`
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt`

8. The ADS-B display smoother retargets any coordinate delta with no explicit regression/teleport guard, so upstream timing mistakes become a visible backward animation.
   - `feature/map/src/main/java/com/example/xcpro/map/AdsbDisplayMotionSmoother.kt`

9. Current docs/tests codify the wrong contract.
   - `docs/ADS-b/ADSB.md`
   - `docs/PROXIMITY/README.md`
   - `docs/PROXIMITY/CHANGE_PLAN_ADSB_OGN_PROXIMITY_ALERTS_2026-02-22.md`
   - existing ADS-B repository/store/detail tests currently do not vary provider position time meaningfully.

10. GeoJSON/export and map animation-state helpers still assume one ambiguous freshness field.
   - `feature/map/src/main/java/com/example/xcpro/map/AdsbGeoJsonMapper.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlaySupport.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/AdsbEmergencyFlashPolicy.kt`

11. The smoother currently compares whole `AdsbTrafficUiModel` objects, so adding explicit position/contact age fields would create render churn unless visual equality is narrowed to geometry/render-relevant fields.
   - `feature/map/src/main/java/com/example/xcpro/map/AdsbDisplayMotionSmoother.kt`

12. Store expiry is still keyed to local receive time only, so the implementation plan must explicitly decide whether contact-only refreshes are allowed to extend target lifetime. Current production behavior effectively says yes; the fix should say no for geometry authority.
   - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`

13. Test fixtures and helper builders across ADS-B and map modules encode `receivedMonoMs` as freshness authority, which can mask regressions unless Phase 0 migrates the helpers first.
   - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTestSupport.kt`
   - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTestRuntime.kt`
   - `feature/traffic/src/test/java/com/example/xcpro/adsb/metadata/AdsbMetadataEnrichmentUseCaseTestSupport.kt`
   - `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTestSupport.kt`

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OpenSky response time (`response.timeSec`) | ADS-B repository ingest path | internal normalization input | recomputing provider response time from device wall clock in store/UI |
| ADS-B provider position timestamp (`timePositionSec`) | `AdsbTarget` / ADS-B repository-store SSOT | target timing field | inferring position freshness from `lastContactSec` or `receivedMonoMs` alone |
| ADS-B provider last-contact timestamp (`lastContactSec`) | `AdsbTarget` / ADS-B repository-store SSOT | target timing field | treating contact freshness as geometry freshness |
| Local ingest monotonic time (`receivedMonoMs`) | ADS-B repository-store SSOT | housekeeping/input timing | using it as authoritative provider position time |
| Effective geometry-timing authority (`position time` / `response fallback` / `receipt fallback`) | ADS-B repository-store SSOT | internal target timing enum/field | scattered null-handling or implicit fallback in store/UI |
| Derived position age | `AdsbTrafficStore` selection output | `AdsbTrafficUiModel` and details state | UI recomputing position age independently |
| Derived contact age | `AdsbTrafficStore` selection output | `AdsbTrafficUiModel` and details state | UI recomputing contact age independently |
| Visual export compatibility fields (`age_s`, stale alpha flags) | map overlay mapping layer | GeoJSON properties derived from authoritative position freshness | style/export layers inventing their own age semantics |
| Visual rewind/teleport handling | ADS-B map overlay smoother | visual-only render policy | repository/store mutating SSOT for visual easing |

### 2.1A Transitional Compatibility Contract

- `AdsbTrafficUiModel.ageSec` remains a temporary alias for authoritative `positionAgeSec` during migration so existing tests and map bindings do not all break in one phase.
- `AdsbTrafficUiModel.isStale` remains a temporary alias for authoritative `isPositionStale` until Phase 5 doc/test cleanup is complete.
- New explicit fields should be added alongside legacy aliases before any alias removal:
  - `positionAgeSec`
  - `contactAgeSec`
  - `isPositionStale`
  - `positionTimestampEpochSec` (or equivalent authoritative geometry timestamp for diagnostics/smoother policy)
- GeoJSON keeps legacy `age_s` mapped to position age for one compatibility cycle while adding explicit position/contact freshness properties.

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-case facade -> repository/data`

- Modules/files touched:
  - `feature/traffic`
  - `feature/map`
  - `docs/ADS-b`
  - `docs/PROXIMITY`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Boundary risk:
  - Low if freshness normalization remains in repository/store and visual snap policy remains in map overlay runtime only.
- Boundary adapter check:
  - No new external I/O boundary is required.
  - Existing network boundary remains `AdsbProviderClient`.
  - The fix stays inside the current data/repository layer and map UI consumer layer.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Geometry freshness normalization | `AdsbTrafficStore.select(...)` mixed receipt/contact logic | repository ingest plus store selection contract | provider position freshness must be normalized before store policy consumes it | repository/store unit tests |
| Latest sample authority | `AdsbTrafficStore.upsertAll(...)` blind overwrite | store/repository latest-position policy | older geometry must not overwrite newer geometry | store ordering tests |
| Details age semantics | ambiguous `ageSec` in details UI | explicit position-age/contact-age model from store | debug UI must reflect true geometry freshness | details UI tests |
| GeoJSON/export freshness semantics | single `age_s` property and stale flag consumers | explicit position-age/contact-age export contract with temporary alias | overlay/render code must not keep receipt-time semantics | GeoJSON mapper tests |
| Visual correction policy | unconditional smoother retarget | map smoother regression/teleport guard | a corrected/stale sample should not become a long backward animation | smoother tests + SLO evidence |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `AdsbTrafficRepositoryRuntimePolling.handleSuccess(...)` -> `state.toTarget(nowMonoMs)` | drops provider timing and stamps all rows with one local receive time | carry provider response/position/contact timing into target model | Phase 1 |
| `AdsbTrafficStore.select(... nowWallEpochSec = clock.nowWallMs() / 1000L ...)` | device wall clock drives provider-age semantics | normalize age-at-receipt from provider times and advance with monotonic elapsed only | Phase 2 |
| `AdsbTrafficStore.upsertAll(...)` | blind ICAO overwrite | latest-position-wins upsert with explicit tie-break contract | Phase 2 |
| `AdsbGeoJsonMapper.toFeature(...)` + stale/emergency overlay helpers | one ambiguous age property/flag drives render semantics | map render/export consumes explicit position freshness with compatibility alias only | Phase 4 |
| `AdsbDisplayMotionSmoother.onTargets(...)` | retarget any delta | snap/hold policy for regressive or large corrective position changes | Phase 4 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| `receivedMonoMs` | Monotonic | local elapsed-since-ingest, expiry housekeeping, visual cadence |
| `response.timeSec` | Wall (provider UTC epoch) | authoritative provider reference time for per-row age-at-receipt normalization |
| `timePositionSec` | Wall (provider UTC epoch) | authoritative geometry time from provider |
| `lastContactSec` | Wall (provider UTC epoch) | message/contact freshness only, not geometry freshness |
| `effectivePositionEpochSec` | Wall (provider UTC epoch) | authoritative timestamp used for geometry ordering/tie-breaks |
| `positionFreshnessSource` | Derived classification | makes fallback path explicit and testable |
| `positionAgeAtReceiptSec` | Derived from provider wall times | removes device-clock dependence from geometry freshness |
| `contactAgeAtReceiptSec` | Derived from provider wall times | preserves contact diagnostics without redefining geometry freshness |
| `positionAgeSec` / `contactAgeSec` after ingest | Monotonic-derived from age-at-receipt | safe runtime freshness progression without wall-clock drift |
| ADS-B smoother frame time | Monotonic | visual-only render interpolation |

Explicitly forbidden comparisons:

- Monotonic vs device wall time for ADS-B geometry freshness.
- `lastContactSec` as a substitute for position freshness when `timePositionSec` is older.
- UI-side recomputation of target freshness from raw timestamps.

### 2.3A Authoritative Sample Ordering Contract

| Incoming vs Existing | Planned Store Action | Why | Test Requirement |
|---|---|---|---|
| incoming `effectivePositionEpochSec` newer | replace geometry and motion fields | later provider geometry is authoritative | newer-position-wins test |
| incoming `effectivePositionEpochSec` older | keep existing geometry; allow contact/metadata refresh only | fresh packet must not rewind geometry | stale-geometry-fresh-contact test |
| equal position time, same geometry | merge non-geometry metadata/contact fields; no visual animation | idempotent duplicate update | duplicate-update test |
| equal position time, different geometry | accept as correction, but mark for snap/no interpolation in smoother | provider may revise same-timestamp geometry | same-timestamp-correction test |
| incoming position time null, existing position time present | keep existing geometry; optionally refresh contact/metadata only | null timestamp must not erase authoritative geometry | null-position-time guard test |
| both position times null | bounded fallback to latest receive time with explicit degraded authority marker | preserves visibility for degraded feeds while keeping behavior explicit | degraded-authority fallback test |
| contact newer but position not newer | contact age may refresh, geometry age/lifetime may not | splits packet freshness from geometry freshness | expiry/contact-only test |

### 2.3B Expiry Contract

- Target lifetime is governed by geometry freshness, not packet contact freshness.
- Contact-only refreshes must not extend geometry expiry windows.
- If geometry expires but contact is still fresh, the target drops from display/store rather than advertising a stale coordinate indefinitely.
- Any future requirement to show "contact only, no geometry" must be modeled as a separate state, not by keeping expired coordinates alive.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - repository/store normalization and selection: existing ADS-B single-writer IO lane
  - map overlay render/smoother: existing UI runtime/choreographer path
- Primary cadence/gating source:
  - repository/store updates remain poll-driven plus ownship-driven reselection
  - smoother remains frame-driven, but only for visual-only interpolation
- Hot-path latency budget:
  - preserve `MS-ENG-03` (`ADS-B per-frame feature build p95 <= 8 ms`)
  - preserve `MS-ENG-01` (`overlay update apply duration p95 <= 30 ms`)

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - ADS-B remains a live-path feature only.
  - Replay mode behavior should not change except for shared map overlay runtime stability if the overlay is visible.
  - For identical ADS-B input stream plus injected clock progression, repository/store/smoother outputs must be deterministic.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Provider position time dropped before SSOT | SSOT + explicit time base | unit/integration tests | new ADS-B repository timing tests |
| Device wall clock drives ADS-B freshness | injected timebase only, no hidden wall dependency | unit tests + review | new repository/store freshness tests |
| Older geometry overwrites newer geometry | SSOT ownership and deterministic policy | unit tests | new store latest-position-wins tests |
| Stale geometry still qualifies for ordering/emergency | domain policy explicitness | unit tests | store/emergency/selection tests |
| Repeated stale coordinates advance target-motion freshness | explicit state-machine timing contract | unit tests | trend/track estimator tests |
| UI cannot distinguish position vs contact age | honest outputs over fabricated precision | UI/unit tests | details formatter/sheet tests |
| GeoJSON/export keeps ambiguous age semantics | SSOT and honest-output rules | unit tests | `AdsbGeoJsonMapperTest`, overlay helper tests |
| Smoother churns on freshness-only field changes | UI-only smoothing must not redefine truth or waste frame budget | unit/perf tests | `AdsbDisplayMotionSmootherTest`, `MapOverlayManagerRuntimeTrafficDelegateTest` |
| Reverse animation remains visible | UI-only smoothing must not redefine truth | unit/perf/SLO tests | `AdsbDisplayMotionSmootherTest`, MapScreen evidence |

### 2.7 Visual UX SLO Contract (Mandatory for map/overlay/replay interaction changes)

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Fewer ADS-B marker backward snaps under stale/corrective updates | `MS-UX-03` | capture in Phase 0 | <= 1 snap event per target per 5 min stress window | dense traffic playback/stress scenario + ADS-B rewind regression harness | Phase 4 |
| ADS-B overlay apply cost remains bounded after timing model changes | `MS-ENG-01` | capture in Phase 0 | p95 <= 30 ms | overlay apply micro-perf | Phase 4 |
| ADS-B per-frame feature build cost remains bounded | `MS-ENG-03` | capture in Phase 0 | p95 <= 8 ms | ADS-B frame micro-perf | Phase 4 |
| ADS-B smoothing retarget windows remain stable with no zero-seeded/regressive windows | `MS-ENG-07` | capture in Phase 0 | p95 <= 2000 ms and zero-seeded window count = 0 | smoother transition harness + unit tests | Phase 4 |
| Freshness-only updates do not create unnecessary animation/frame-loop churn | `MS-ENG-03` / `MS-ENG-07` | capture in Phase 0 | no sustained frame-loop activity when only age/contact fields change | smoother + overlay-support tests | Phase 4 |

## 3) Data Flow (Before -> After)

Before:

`OpenSky response -> parse timePositionSec/lastContactSec -> drop timePositionSec -> map each row with receivedMonoMs = now -> blind ICAO overwrite -> ageSec from received/contact freshness -> map smoother retargets any delta`

After:

`OpenSky response -> parse response.timeSec + timePositionSec + lastContactSec -> normalize position/contact age at receipt + authority source -> batch dedupe by ICAO with explicit ordering rules -> map target timing into SSOT -> latest-position-wins store policy -> position-age/contact-age derived with monotonic elapsed -> proximity/emergency/order/expiry consume position freshness -> details UI and GeoJSON export render separate age semantics with temporary compatibility aliases -> map smoother uses geometry-only diff and snaps/holds on regressive or corrective updates`

## 4) Implementation Phases

### Phase 0 - Baseline Lock and Evidence

- Goal:
  - Reproduce the reverse-jump bug and lock current failure modes before changing timing semantics.
- Files to change:
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTestRuntime.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTestSupport.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/metadata/AdsbMetadataEnrichmentUseCaseTestSupport.kt`
  - new ADS-B repository/store timing tests under `feature/traffic/src/test/java/com/example/xcpro/adsb/`
  - `feature/map/src/test/java/com/example/xcpro/map/AdsbDisplayMotionSmootherTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/AdsbGeoJsonMapperTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/AdsbEmergencyFlashPolicyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegateTest.kt`
  - Mapscreen evidence artifacts under `artifacts/mapscreen/...` when executed
- Tests to add/update:
  - older position after newer position for same ICAO
  - stale geometry with fresh contact
  - device clock skew vs provider time
  - same-timestamp correction update for the same ICAO
  - contact-only refresh does not extend geometry lifetime
  - GeoJSON/export keeps compatibility alias while adding explicit fields
  - smoother reverse-animation reproduction
- Exit criteria:
  - Failing paths are reproducible in tests/evidence.
  - Test helper/builders can express provider position time, response time, contact time, and degraded-authority cases explicitly.
  - Impacted SLO baselines are identified (`MS-UX-03`, `MS-ENG-01`, `MS-ENG-03`, `MS-ENG-07`).
- Target phase score: `95/100`

### Phase 1 - Introduce Authoritative ADS-B Timing Model

- Goal:
  - Carry authoritative provider timing through ADS-B SSOT without flipping all downstream policy in one step.
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/OpenSkyModels.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficModels.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimePolling.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbSelectedTargetDetails.kt`
- Tests to add/update:
  - repository mapping tests for response/position/contact time normalization
  - null/invalid provider timestamp fallback tests
  - explicit authority-source classification tests (`position time`, `response fallback`, `receipt fallback`)
- Exit criteria:
  - `AdsbTarget` carries enough timing data to distinguish position freshness, contact freshness, and local ingest time.
  - Batch ingest can order/dedupe multiple rows for one ICAO deterministically before store write.
  - No device-wall dependency remains necessary for provider-age-at-receipt normalization.
- Target phase score: `96/100`

### Phase 2 - Repository/Store Freshness Authority Migration

- Goal:
  - Make store authority latest-position-wins and migrate stale/expiry/order/emergency gating to geometry freshness.
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficThreatPolicies.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficSelectionOrdering.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbCollisionRiskEvaluator.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbEmergencyRiskStabilizer.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
- Tests to add/update:
  - latest-position-wins upsert tests
  - stale geometry does not remain fresh because contact is fresh
  - expiry is based on geometry freshness contract, not repeated packet receipt alone
  - emergency candidate/order selection uses position age correctly
  - contact-only refresh updates diagnostics but not geometry lifetime/order/emergency gates
- Exit criteria:
  - Older geometry cannot overwrite newer geometry for the same ICAO.
  - Device wall time is removed from ADS-B store freshness policy.
  - Display/emergency ordering reflects true position freshness.
  - Legacy `ageSec` / `isStale` aliases now map only to position freshness semantics.
- Target phase score: `97/100`

### Phase 3 - Motion and Proximity Timing Hardening

- Goal:
  - Prevent repeated stale coordinates from counting as fresh target-motion samples while preserving ownship-relative reselection behavior.
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbProximityTrendEvaluator.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTargetTrackEstimator.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTrendTransitionsTest.kt`
- Tests to add/update:
  - repeated unchanged/old `timePositionSec` does not create fresh target-motion progress
  - ownship-only motion still republishes distance/bearing but does not upgrade stale target geometry to fresh target motion
  - track derivation uses actual target position progression only
  - equal-position-time corrections do not fabricate closing-rate progress
- Exit criteria:
  - Target-motion freshness is no longer conflated with packet receipt freshness.
  - Proximity/emergency transitions remain deterministic under stale target geometry plus moving ownship.
- Target phase score: `97/100`

### Phase 4 - UI and Map Runtime Hardening

- Goal:
  - Make stale-geometry state explicit to operators and prevent visible backward-animation regressions.
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbSelectedTargetDetails.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbDisplayMotionSmoother.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbGeoJsonMapper.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlaySupport.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbEmergencyFlashPolicy.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/AdsbDisplayMotionSmootherTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/AdsbGeoJsonMapperTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/AdsbEmergencyFlashPolicyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegateTest.kt`
- Tests to add/update:
  - details show separate position age/contact age semantics
  - smoother snaps or holds on regressive/teleport corrections
  - smoother does not animate or keep frame loop active for freshness-only updates
  - GeoJSON exposes explicit position/contact freshness properties while retaining temporary `age_s` compatibility
  - no zero-seeded or stale-window regressions
- Exit criteria:
  - UI clearly distinguishes stale geometry from fresh contact.
  - Reverse-jump animation path is eliminated or bounded by explicit visual policy.
  - Overlay/export consumers use position freshness explicitly; emergency flash and active-animation gating no longer rely on ambiguous stale semantics.
  - Impacted SLOs pass locally or are ready for measured evidence capture.
- Target phase score: `98/100`

### Phase 5 - Docs Sync, Performance Evidence, and Release Gate

- Goal:
  - Close contract drift and collect release-grade evidence.
- Files to change:
  - `docs/ADS-b/ADSB.md`
  - `docs/PROXIMITY/README.md`
  - `docs/PROXIMITY/CHANGE_PLAN_ADSB_OGN_PROXIMITY_ALERTS_2026-02-22.md`
  - `docs/PROXIMITY/CHANGE_PLAN_ADSB_CIRCLING_1KM_RED_EMERGENCY_AUDIO_2026-03-04.md`
  - `docs/PROXIMITY/CHANGE_PLAN_ADSB_SMART_PROXIMITY_TREND_2026-03-01.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ADS-b/README.md`
- Tests to add/update:
  - docs sync only; final verification reruns targeted and full suites
- Exit criteria:
  - Runtime docs describe position freshness correctly.
  - Impacted SLO evidence is attached.
  - Remaining legacy alias/deprecated-field intent is documented clearly for the next cleanup pass.
  - Required build/test gates pass.
- Target phase score: `98/100`

## 5) Test Plan

- Unit tests:
  - `timePositionSec` authority and fallback rules
  - `response.timeSec` normalization vs device-wall skew
  - latest-position-wins tie-break policy
  - same-position-time correction policy
  - null-position-time with existing authoritative geometry
  - both-position-times-null degraded fallback policy
  - stale geometry/fresh contact split
  - expiry/order/emergency based on position age
  - target-motion freshness under repeated stale coordinates
  - smoother regression/teleport handling
  - smoother ignores freshness-only field changes for animation decisions
  - GeoJSON compatibility alias plus explicit freshness-property coverage
  - details formatting for position age/contact age
- Replay/regression tests:
  - deterministic ADS-B repository/store output for identical input stream and fake clocks
  - no replay-path regressions in shared map runtime tests
- UI/instrumentation tests (if needed):
  - details sheet semantics
  - dense traffic marker stability evidence for `MS-UX-03`
- Degraded/failure-mode tests:
  - missing `timePositionSec`
  - future/skewed provider timestamps
  - repeated polls with unchanged old position but fresh contact
  - mixed response rows with different position ages in one poll
  - contact newer while geometry older for same ICAO
  - equal geometry time but corrected coordinates
- Boundary tests for removed bypasses:
  - `handleSuccess(...)` no longer depends on `clock.nowWallMs()` for provider freshness
  - `store.select(...)` no longer requires device-wall provider age input
  - overlay/export helpers no longer treat contact freshness as geometry freshness

Required checks:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Null/missing provider timestamps are more common than expected | could suppress too many targets or degrade UI trust | define explicit fallback policy in Phase 1 and test null-path behavior | XCPro Team |
| Geometry-freshness migration changes emergency/proximity behavior | user-visible alert severity shifts | lock baseline tests first and stage policy migration separately from model introduction | XCPro Team |
| Smoother snap guard makes motion feel too abrupt | visual regression under normal updates | gate with `MS-UX-03` and `MS-ENG-07`; snap only on explicit regression/teleport conditions | XCPro Team |
| Adding explicit age fields causes render churn or perf regressions | wasted frame-loop work and noisy map updates | use geometry/render-only equality in smoother and add overlay helper tests | XCPro Team |
| Compatibility alias survives too long and reintroduces ambiguity | future contributors keep using legacy names | Phase 5 docs must mark aliases temporary and map them only to position freshness | XCPro Team |
| Added timing fields increase code complexity | maintainability drift | keep timing contract centralized in one model/policy area and update docs in same change set | XCPro Team |
| Existing docs/tests fight the new semantics | future regressions reintroduce receipt-time freshness | Phase 5 doc sync and new targeted tests become mandatory closure criteria | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- ADS-B geometry freshness no longer depends on device wall time
- Older provider geometry cannot overwrite newer geometry
- Contact-only refreshes do not extend geometry expiry or emergency freshness
- Details/debug semantics explicitly separate position freshness from contact freshness
- GeoJSON/export and overlay helpers consume explicit position freshness semantics
- Freshness-only updates do not create sustained ADS-B animation churn
- Replay behavior remains deterministic
- Impacted visual SLOs (`MS-UX-03`, `MS-ENG-01`, `MS-ENG-03`, `MS-ENG-07`) pass or approved deviation is recorded
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry)

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 1 model-introduction changes
  - Phase 2 repository/store freshness migration
  - Phase 3 trend/track timing hardening
  - Phase 4 smoother/details UI hardening
  - Phase 5 docs/evidence updates
- Recovery steps if regression is detected:
  1. Revert the current phase only.
  2. Re-run required ADS-B targeted tests plus repo-wide required checks.
  3. Preserve failing evidence under `artifacts/mapscreen/rollback/<issue-id>/`.
  4. Adjust fallback policy or tie-break semantics before retrying the phase.
