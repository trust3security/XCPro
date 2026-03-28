# CHANGE_PLAN_OGN_SCREEN_DISTANCE_ICON_DECLUTTER_2026-03-28.md

## Purpose

Reduce OGN icon clutter when nearby gliders collapse into the same screen area
by adding render-only screen-distance declutter for OGN traffic.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN screen-distance icon declutter
- Owner: XCPro Team
- Date: 2026-03-28
- Issue/PR: TBD
- Status: Phase 2 implemented; Phase 3 validation pending

## 1) Scope

- Problem statement:
  OGN zoom-adaptive icon sizing and label declutter reduce clutter, but when
  gliders are geographically close they still collapse into the same few screen
  pixels and become visually indistinguishable.
- Why now:
  This is the next real clutter lever after icon-size and label declutter, and
  it addresses the remaining user-visible failure mode in dense OGN gaggles.
- In scope:
  - add render-only OGN icon declutter based on current screen distance between
    projected targets
  - replace stacked nearby icons with a generic micro-cluster marker plus count
  - add deterministic tap behavior for grouped markers (`zoom to expand`, not
    wrong-aircraft selection)
  - keep raw OGN targets unchanged as the upstream SSOT
  - add focused policy, feature-build, and tap-routing tests
- Out of scope:
  - OGN backend, receive radius, sorting, or subscription changes
  - persisting any new screen-distance declutter setting in this change
  - random radial jitter that misrepresents aircraft position
  - a full spiderfy/fan-out UI in the first pass
  - ADS-B declutter changes
- User-visible impact:
  - at close zoom, nearby aircraft continue to render individually
  - at medium/wide zoom, very close aircraft render as a grouped OGN marker with
    a count instead of unreadable overlap
  - tapping a grouped marker zooms toward separation instead of opening the wrong
    aircraft details
- Rule class touched: Default

## 1A) Narrow Seam Findings

1. The current OGN icon layer explicitly permits overlap:
   - `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlaySupport.kt`
     sets `iconAllowOverlap(true)` and `iconIgnorePlacement(true)`.
   - The current issue is therefore consistent with the implementation, not a
     hidden renderer bug.

2. Current OGN feature build is one-target -> one-feature:
   - `buildOgnTrafficOverlayFeatures(...)` creates one point feature per target
     with no screen-space grouping stage.
   - Existing label declutter does not affect icon collision.

3. The narrowest safe screen-distance seam already exists:
   - `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
     owns both `MapLibreMap` projection access and the resolved rendered OGN icon
     size.
   - That makes `OgnTrafficOverlay` the correct runtime owner for projecting
     targets to screen points before calling a pure grouping helper.

4. Tap semantics are the real boundary constraint:
   - current `findTargetAt(tap): String?` returns the first rendered OGN feature
     key found at a screen point.
   - once grouped markers exist, returning a single aircraft key on tap would be
     wrong.
   - a typed OGN hit result is therefore required for a correct grouped-marker UX.

5. MapLibre has two relevant built-in primitives, but neither is the right first
   owner for this slice:
   - symbol collision can hide overlapping icons, but it only chooses winners and
     does not preserve density information
   - GeoJSON source clustering exists, but cluster behavior is re-evaluated at
     integer zoom levels and would push grouped-marker semantics into the source
     path instead of the existing render-only runtime seam

6. The best UX improvement is a generic micro-cluster marker, not a fake
   representative aircraft icon:
   - a generic grouped marker with count communicates "multiple aircraft here"
     honestly
   - raw jitter/offset would make aircraft look physically separated when they
     are not

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Raw OGN traffic target list | OGN repository / use-case owners | existing OGN target flows | overlay-owned authoritative filtered target state |
| Resolved OGN rendered icon size | `MapOverlayManagerRuntimeOgnDelegate` -> `OgnTrafficOverlay` | imperative runtime overlay size apply | a second zoom or settings owner for screen declutter |
| Derived OGN screen-distance grouping result | new pure helper in `feature:traffic` map slice | render-only helper output | repository, ViewModel, or persisted grouping state |
| OGN grouped-marker hit result | new typed map-overlay contract in `feature:traffic` | runtime tap hit result | string sentinels pretending a cluster is one target |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Projected OGN screen points for current frame | `OgnTrafficOverlay` | current render pass only | internal overlay render path | raw targets + map projection + rendered icon size | none | recomputed every render | N/A | render/policy tests |
| Effective OGN screen-distance declutter plan (`single` / `cluster`) | new pure screen-declutter helper | helper input only | `OgnTrafficOverlay` -> feature build | projected points + rendered icon size | none | recomputed every render | N/A | pure helper tests |
| OGN grouped-marker hit result (`target` / `cluster`) | new typed overlay hit-result contract | overlay query only | OGN runtime delegate -> map tap routing | rendered features + grouped-marker properties | none | per tap only | N/A | tap-routing tests |
| Deterministic grouped-marker key | pure screen-declutter helper | helper creation only | cluster feature props / tap result | sorted member canonical keys | none | per render only | N/A | deterministic identity tests |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:traffic`
  - `feature:map-runtime`
  - `feature:map`
- Any boundary risk:
  - leaking MapLibre projection math into repository/ViewModel code
  - representing a cluster as if it were a single aircraft in tap selection
  - using random jitter to make icons look separated

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficViewportSizing.kt` | render-only OGN declutter helper already exists | keep screen-distance grouping as a pure helper next to existing render policy helpers | output render grouping instead of icon size |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficLabelDeclutterPolicy.kt` | recent OGN declutter work already isolates pure render logic | keep feature-build gating in helper + overlay support | this change adds grouped markers, not only label stripping |
| `feature/traffic/src/main/java/com/example/xcpro/map/TrafficOverlayRuntimeState.kt` | existing feature:traffic -> feature:map-runtime overlay handle seam | extend the same seam for grouped hit results if needed | additive typed hit result instead of raw `String?` |
| `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt` | viewport-derived declutter policy already exists in traffic overlay slice | keep camera/viewport-derived rendering runtime-owned | OGN uses exact screen-distance grouping, not ADS-B label gating |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN close-target icon grouping | no explicit owner; every target always renders as its own icon | new pure OGN screen-distance declutter helper in `feature:traffic` | grouping is render-only policy, not repository truth | pure helper tests + feature-build tests |
| OGN grouped-marker hit semantics | raw `String?` target key path | new typed OGN hit-result contract in traffic overlay runtime seam | grouped markers are not a single target | tap-routing tests |
| Cluster expansion action | none | OGN runtime delegate / map tap routing | zoom-to-expand is runtime camera behavior, not overlay truth | runtime tap tests + manual validation |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `OgnTrafficOverlay.findTargetAt(tap)` returns first rendered feature key | stacked icons can resolve to the wrong aircraft | typed `OgnTrafficHitResult` with `Target` vs `Cluster` | Phase 2 |
| `buildOgnTrafficOverlayFeatures(...)` writes one icon feature per target | no screen-distance grouping stage | pass render items (`single` / `cluster`) into feature building | Phase 2 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/ARCHITECTURE/CHANGE_PLAN_OGN_SCREEN_DISTANCE_ICON_DECLUTTER_2026-03-28.md` | New | phased execution contract | required for non-trivial overlay/runtime change | not production code | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficScreenDeclutterPolicy.kt` | New | pure screen-distance grouping rules and deterministic group identity | render-only policy owner belongs with OGN map helpers | not repository or ViewModel state | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficScreenDeclutterModels.kt` | New | small render-item / hit-result models if file split keeps policy file focused | keeps models out of mixed-purpose files | avoids expanding `TrafficOverlayRuntimeState.kt` with helper internals | Maybe |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt` | Existing | project targets to screen space, call helper, render grouped result, hit-test grouped markers | already owns map projection and rendered icon size | do not move MapLibre projection into pure helper or delegate | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlaySupport.kt` | Existing | author features/layers for single-target and grouped-marker render items | current OGN feature authoring owner | do not build GeoJSON in delegate or UI | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/TrafficOverlayRuntimeState.kt` | Existing | additive OGN hit-result overlay contract if grouped taps need typed output | current cross-module overlay handle seam | do not encode clusters as fake target IDs | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | Existing | runtime handling of grouped taps (`zoom to expand`) and existing target selection path | current OGN runtime owner | do not let UI decide cluster semantics directly | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` | Existing | pass additive grouped-hit result through the runtime shell | current runtime bridge owner | do not bypass runtime shell from UI | No |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt` | Existing | map tap routing for grouped OGN markers | current map tap routing owner | do not hide grouped behavior inside composables elsewhere | No |
| `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficScreenDeclutterPolicyTest.kt` | New | pure grouping math coverage | deterministic grouping needs isolated tests | not only integration tests; policy is pure | No |
| `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficOverlayFeatureScreenDeclutterTest.kt` | New | grouped-marker feature-build coverage | verifies render items map to correct feature properties | not UI-shell tests | No |
| `feature/traffic/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegateClusterTapTest.kt` | New | grouped tap action coverage | cluster tap must not select wrong aircraft | not only manual validation | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `OgnTrafficHitResult` (`Target` / `Cluster`) | `feature:traffic` overlay runtime seam | `MapOverlayManagerRuntimeOgnDelegate`, `MapOverlayManagerRuntime`, `MapOverlayStack` | public cross-module | grouped taps cannot be represented safely as `String?` | additive replacement for raw OGN target-key hit path |
| grouped-marker feature properties (`cluster`, `cluster_id`, `point_count`, or app-owned equivalents) | `feature:traffic` OGN overlay support | OGN overlay render + hit test only | internal | feature authoring needs explicit grouped-marker semantics | no long-lived compatibility shim planned |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| OGN screen-distance grouping radius from rendered icon size | `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficScreenDeclutterPolicy.kt` | OGN overlay render path and tests | exact screen declutter is a render-only policy | No |
| Deterministic grouped-marker key creation | `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficScreenDeclutterPolicy.kt` | grouped features + tap routing tests | identity belongs with grouping owner | No |
| Cluster tap action policy (`zoom to expand`, not wrong-aircraft selection) | `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | OGN runtime delegate + map tap routing | camera action is runtime behavior, not pure policy | No |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN screen-distance grouping result | N/A | pure render policy from projected screen points |
| grouped-marker key | N/A | deterministic identity from member keys only |

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - `Main` only; grouping is part of current map overlay render path
- Primary cadence/gating sensor:
  - existing OGN traffic render cadence and camera-driven icon-size updates
- Hot-path latency budget:
  - keep additional screen-distance grouping bounded so OGN render remains within
    existing overlay apply expectations (`MS-ENG-01`)

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - for the same target list and the same camera state, grouped markers must be
    identical across runs
  - random jitter/slotting is explicitly forbidden in the first pass

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| map projection unavailable during OGN render | Degraded | `OgnTrafficOverlay` | fall back to current one-target-per-icon render | render singles for that frame; do not hide traffic | fallback render test |
| grouping helper fails unexpectedly | Degraded | `OgnTrafficOverlay` | fall back to current OGN icon render for that frame | catch and render singles | degraded-path test |
| grouped marker tapped | User Action | OGN runtime delegate / map tap routing | zoom toward expansion; do not open wrong-aircraft sheet | repeat tap after zoom if still grouped | runtime tap test |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| grouped-marker key | pure screen-declutter helper | sorted member keys only | Yes | group identity is part of render grouping, not repository truth |
| grouped-marker feature props | OGN overlay support | render-item mapping only | Yes | feature authoring belongs with GeoJSON builder |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| screen-space grouping leaks into repository/ViewModel | SSOT / ownership | review + file ownership plan | change plan + code review |
| grouped marker masquerades as a single target | honest outputs / boundary correctness | tap-routing tests + review | new hit-result tests |
| random jitter breaks replay determinism | replay determinism | pure helper tests + review | grouping tests |
| declutter hides nearby density with no affordance | UX / honest outputs | feature-build tests + manual validation | grouped-marker render tests |
| hot-path grouping regresses map overlay performance | Map visual SLO | targeted perf/manual validation + PR-ready gates | Phase 3 validation |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Nearby OGN gliders remain distinguishable instead of collapsing into one blob | `MS-UX-03` | stacked icons overlap and become indistinguishable | grouped marker with count replaces unreadable overlap | helper tests + feature-build tests + manual dense-scene validation | Phase 2 |
| Pan/zoom at medium-wide ranges remains readable in dense OGN scenes | `MS-UX-01` | label declutter helps but icon stacks still merge | no unreadable icon blobs in close gaggles at medium/wide zoom | manual validation + evidence if implemented | Phase 3 |
| OGN tap does not open the wrong aircraft from a grouped marker | `MS-UX-03` | stacked taps can resolve to the first rendered feature | grouped tap zooms to expansion or otherwise preserves correct intent | runtime tap tests + manual validation | Phase 2 |

## 3) Data Flow (Before -> After)

Before:

`OGN targets -> OgnTrafficOverlay -> one point feature per target -> overlapping icons render at same screen location -> tap returns first rendered target key`

After:

`OGN targets -> OgnTrafficOverlay projects targets to screen points -> OgnTrafficScreenDeclutterPolicy groups targets by screen distance -> OgnTrafficOverlaySupport builds single-target or grouped-marker features -> OGN icon layers render distinct singles or count markers -> tap returns Target or Cluster hit result -> runtime owner selects target or zooms to expand cluster`

## 4) Implementation Phases

### Phase 0 - Contract lock

- Goal:
  lock the screen-distance micro-cluster approach and reject raw jitter or
  hide-only collision as the first implementation.
- Files to change:
  - this plan
- Exit criteria:
  - grouped-marker semantics and typed hit-result need are explicit
- Status:
  - Implemented

### Phase 1 - Pure screen-distance grouping policy

- Goal:
  add a pure helper that groups projected OGN targets by screen distance and
  returns deterministic render items.
- Files to change:
  - new `OgnTrafficScreenDeclutterPolicy.kt`
  - optional new `OgnTrafficScreenDeclutterModels.kt`
  - new `OgnTrafficScreenDeclutterPolicyTest.kt`
- Ownership/file split changes:
  - helper owns grouping radius policy tied to rendered icon size
  - helper owns deterministic grouped-marker identity and member ordering
  - helper does not own map projection or camera actions
- Tests to add/update:
  - singleton vs grouped threshold cases
  - stable group identity for same input
  - no randomness / deterministic regrouping
  - fallback-safe behavior for invalid screen points
- Exit criteria:
  - pure grouping output is deterministic and test-locked
- Status:
  - Implemented in `OgnTrafficScreenDeclutterPolicy.kt` and
    `OgnTrafficScreenDeclutterPolicyTest.kt`

### Phase 2 - Overlay render and hit wiring

- Goal:
  render grouped markers instead of overlapping icons and make grouped taps safe.
- Files to change:
  - `OgnTrafficOverlay.kt`
  - `OgnTrafficOverlaySupport.kt`
  - `TrafficOverlayRuntimeState.kt`
  - `MapOverlayManagerRuntimeOgnDelegate.kt`
  - `MapOverlayManagerRuntime.kt`
  - `MapOverlayStack.kt`
  - new `OgnTrafficOverlayFeatureScreenDeclutterTest.kt`
  - new `MapOverlayManagerRuntimeOgnDelegateClusterTapTest.kt`
- Ownership/file split changes:
  - `OgnTrafficOverlay` projects current targets to screen points and invokes the
    pure grouping helper
  - `OgnTrafficOverlaySupport` authors grouped-marker features and generic cluster
    icon/count properties
  - OGN tap routing widens from raw `String?` to typed hit results so cluster taps
    do not select a wrong aircraft
  - runtime delegate owns `zoom to expand` action for grouped taps
- Tests to add/update:
  - grouped markers render when targets fall within the configured screen radius
  - grouped marker count is correct
  - grouped marker tap does not return a fake target key
  - single target tap behavior remains unchanged
- Exit criteria:
  - overlapping close targets render as grouped markers
  - grouped taps no longer open the wrong aircraft
- Status:
  - Implemented in `OgnTrafficOverlay.kt`,
    `OgnTrafficOverlaySupport.kt`,
    `TrafficOverlayRuntimeState.kt`,
    `MapOverlayManagerRuntimeOgnDelegate.kt`,
    `MapOverlayManagerRuntime.kt`, and
    `MapOverlayStack.kt`

### Phase 3 - Hardening and validation

- Goal:
  verify grouped-marker behavior is visually helpful and performant enough.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` if runtime wiring changes materially
  - validation docs/evidence only as needed
- Tests to add/update:
  - extend runtime tests if selection or camera behavior needs more coverage
  - collect manual dense-scene validation evidence
- Exit criteria:
  - required checks pass
  - manual validation confirms:
    - nearby OGN blobs become grouped markers
    - grouped taps zoom toward separation
    - close zoom still restores individual aircraft

### Phase 4 - Optional UX refinement

- Goal:
  decide whether grouped markers need a richer follow-up such as list preview or
  fan-out after the base cluster path lands.
- Files to change:
  - TBD only if Phase 3 proves zoom-to-expand alone is insufficient
- Exit criteria:
  - explicit decision recorded to keep or extend grouped-marker UX

## 5) Test Plan

- Unit tests:
  - `OgnTrafficScreenDeclutterPolicyTest.kt`
  - `OgnTrafficOverlayFeatureScreenDeclutterTest.kt`
- Replay/regression tests:
  - deterministic repeat-run behavior for same targets and camera state
- UI/instrumentation tests:
  - not required initially unless grouped tap behavior proves hard to cover at
    runtime/unit level
- Degraded/failure-mode tests:
  - projection unavailable -> safe single-target fallback
  - grouped tap never opens the wrong aircraft
- Boundary tests for removed bypasses:
  - raw first-feature OGN tap path replaced by typed hit result for grouped markers
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | `OgnTrafficScreenDeclutterPolicyTest.kt` |
| Ownership move / bypass removal / API boundary | Boundary lock tests | grouped hit-result runtime tests |
| UI interaction / lifecycle | UI or instrumentation coverage | manual tap validation; runtime tests first |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | Phase 3 manual/SLO validation if render cost changes materially |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| grouped marker hides too much context | pilots lose detail at medium zoom | use count marker, not silent icon hiding | XCPro Team |
| grouped tap still opens wrong aircraft | trust/selection regression | typed hit result + zoom-to-expand semantics | XCPro Team |
| grouping feels jumpy during zoom | poor UX | tie radius to rendered icon size and keep grouping deterministic; avoid integer-zoom-only source clustering in first pass | XCPro Team |
| screen-distance grouping adds too much hot-path cost | map performance regression | keep helper bounded to existing `maxTargets`; validate in Phase 3 | XCPro Team |
| grouped marker lies about actual aircraft position | misleading aviation UX | use generic grouped marker, not fake radial jitter | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: N/A
- Decision summary:
  - keep this as a localized runtime/render-slice evolution first
- Why this belongs in an ADR instead of plan notes:
  - it does not change global architecture invariants; it extends an existing
    traffic-overlay runtime seam

## 7) Acceptance Gates

- No architecture rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- Raw OGN target list remains authoritative and unchanged upstream
- Screen-distance grouping remains runtime-only and non-persisted
- Grouped markers never resolve to the wrong aircraft on tap
- Replay behavior remains deterministic for the same data and camera state

## 8) Rollback Plan

If grouped markers regress readability or tap behavior:

1. remove grouped-marker feature authoring and restore one-target-per-icon render
2. keep the already-landed icon-size and label declutter work intact
3. remove the typed cluster hit path and restore raw target-key taps
4. update `PIPELINE.md` and this plan to reflect rollback

