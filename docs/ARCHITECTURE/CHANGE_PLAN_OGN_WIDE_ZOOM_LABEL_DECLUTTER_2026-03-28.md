# CHANGE_PLAN_OGN_WIDE_ZOOM_LABEL_DECLUTTER_2026-03-28.md

## Purpose

Reduce OGN text clutter on MapScreen at wider zoom levels by progressively
decluttering OGN labels while keeping the current icon-size work unchanged.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN wide-zoom label declutter
- Owner: XCPro Team
- Date: 2026-03-28
- Issue/PR: TBD
- Status: In progress (`Phase 0` complete, `Phase 1` implemented, `Phase 2` implemented)

## 1) Scope

- Problem statement:
  OGN icon declutter helped, but dense OGN scenes are still visually crowded
  because both OGN text labels continue to render at wider zoom levels.
- Why now:
  This is the next smallest lever after icon declutter and directly targets the
  user-visible clutter that remains in dense OGN traffic scenes.
- In scope:
  - Add a render-only OGN label declutter policy for wider zoom levels.
  - Progressively reduce label density for unselected OGN traffic as zoom widens.
  - Reuse the existing zoom-adaptive OGN rendered icon size as the narrow input
    seam for label declutter.
  - Add focused helper and overlay-feature tests.
- Out of scope:
  - Any new OGN icon-size increase or rebalance.
  - Changing the persisted OGN icon-size preference or slider semantics.
  - Changing OGN receive radius, target caps, sorting, or backend behavior.
  - Adding a new user-facing OGN label preference in this change.
- User-visible impact:
  - At close zoom, OGN labels remain as they are today.
  - At medium-wide zoom, label density is reduced.
  - At wide zoom, labels are suppressed so the map stays readable.
- Rule class touched: Default

## 1A) Narrow Seam Findings

1. The label authoring seam is already local to the OGN overlay feature build:
   - `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlaySupport.kt`
     builds per-target feature properties and writes `PROP_TOP_LABEL` /
     `PROP_BOTTOM_LABEL`.
   - This is narrower than introducing new ViewModel, repository, or settings state.

2. Semantic label composition already has a dedicated owner:
   - `feature/traffic/src/main/java/com/example/xcpro/map/OgnRelativeAltitudeFeatureMapper.kt`
     owns the semantic mapping between delta text, secondary label text, and
     top/bottom layout.
   - That owner should stay responsible for semantic label content, while a new
     declutter helper decides how much of that authored text is rendered.
   - The one-label state must therefore retain `secondaryLabelText`
     (`identifier/distance`), not positional `top` or `bottom`, because
     top/bottom swaps by altitude band.

3. The existing rendered icon size is the narrowest safe policy input:
   - `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
     already holds `currentIconSizePx`, which is now the runtime-resolved,
     zoom-adaptive rendered size.
   - Using rendered icon size avoids adding another zoom or settings state seam
     just for label declutter.
   - This intentionally means label declutter varies with both zoom and the
     pilot's chosen base icon size. Larger configured icons will keep labels
     longer before decluttering.

4. No new startup/style-reload zoom wiring is required for this follow-up:
   - label declutter can piggyback on the already-correct rendered icon-size
     path rather than touching `MapInitializer` or runtime zoom bridges again.

5. Selected-target inspection does not require label exemptions in Phase 1:
   - OGN selection already has the target ring and details sheet path.
   - The smallest first pass can declutter traffic labels without adding
     selected-target feature exemptions or new selection-threading seams.

6. Phase 2 must preserve semantic label position rather than forcing a
   positional keep-top/keep-bottom rule:
   - `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
     already owns the resolved rendered icon size as `currentIconSizePx`.
   - `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlaySupport.kt`
     still writes `mapping.topLabel` and `mapping.bottomLabel` directly.
   - The narrowest safe Phase 2 seam is therefore:
     `currentIconSizePx -> resolve declutter policy -> apply policy to semantic
     mapper output -> write final top/bottom feature properties`.
   - In the one-label state, keep `secondaryLabelText` in whichever slot the
     mapper already assigned it to, instead of always moving the retained label
     to top or bottom.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Base OGN label semantics (`deltaText`, secondary label text, top/bottom semantic mapping) | `feature/traffic/src/main/java/com/example/xcpro/map/OgnRelativeAltitudeFeatureMapper.kt` | feature mapping output | UI, ViewModel, or settings owning alternate label semantics |
| Effective OGN rendered icon size | `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` -> `OgnTrafficOverlay` | imperative runtime overlay size apply | a second zoom owner for label policy |
| Effective OGN label declutter policy | new pure helper in `feature:traffic` map overlay slice | feature-build apply only | persisted state, ViewModel state, or Compose-owned authoritative state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Semantic OGN label content | `OgnRelativeAltitudeFeatureMapper` | mapper input only | feature build | target identity + relative altitude inputs | none | per-frame feature rebuild only | N/A | existing mapper tests + targeted follow-up tests |
| Effective label declutter mode (`show both`, `show identifier/distance only`, `show none`) | new pure declutter helper | helper input only | `OgnTrafficOverlay` / feature builder | rendered icon size | none | recomputed on icon-size update / render | N/A | new helper tests |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:traffic` map overlay helpers/tests only
- Any boundary risk:
  - putting declutter policy into settings/UI just because it is user-visible
  - duplicating label semantics outside the mapper

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficViewportSizing.kt` | pure render-only policy helper already exists for OGN icon declutter | keep label declutter as a pure helper in the same runtime slice | label policy returns text-visibility mode rather than pixel sizing |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnRelativeAltitudeFeatureMapper.kt` | already owns semantic label content and placement | keep semantic mapping separate from declutter gating | apply declutter after semantic mapping instead of changing mapper ownership |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlaySupport.kt` | current owner of OGN feature property creation | keep per-feature property mutation local to overlay support | add label apply step without widening runtime wiring |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Wide-zoom label visibility policy | no explicit owner; labels always render | new pure label declutter helper in OGN map slice | render-only policy belongs with overlay/runtime helpers | helper tests + feature-build tests |
| One-label retention choice (`identifier/distance`, not relative altitude) | implicit positional behavior | new pure label declutter helper in OGN map slice | positional top/bottom is not stable because mapper swaps by altitude band | helper tests + feature-build tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `buildOgnTrafficOverlayFeatures(...)` writes both labels unconditionally | no render-policy gate between semantic label mapping and feature output | route mapped labels through a pure declutter helper before writing `PROP_TOP_LABEL` / `PROP_BOTTOM_LABEL` | Phase 2 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/ARCHITECTURE/CHANGE_PLAN_OGN_WIDE_ZOOM_LABEL_DECLUTTER_2026-03-28.md` | New | phased execution contract | required for non-trivial overlay policy follow-up | not production code | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficLabelDeclutterPolicy.kt` | New | pure render-only label visibility policy | keeps declutter thresholds/testability isolated | not settings or ViewModel; this is not persisted state | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlaySupport.kt` | Existing | apply declutter policy when authoring feature label properties | current feature property owner | do not move feature-writing into runtime/controller code | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt` | Existing | pass current rendered icon size into the feature-build path | already owns current rendered icon size | do not add another zoom state owner | No |
| `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficLabelDeclutterPolicyTest.kt` | New | pure policy coverage | lock thresholds/states without Android/runtime dependencies | not only integration tests; policy is pure | No |
| `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficOverlayFeatureLabelDeclutterTest.kt` | New | feature-build wiring coverage | verifies label properties written to features match declutter mode | not UI-shell tests; feature authoring lives here | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `OgnTrafficLabelDeclutterPolicy` helper | `feature:traffic` map overlay slice | `OgnTrafficOverlaySupport` and tests | `internal` | canonical owner for wide-zoom label visibility policy | no compatibility shim planned |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| OGN wide-zoom label declutter thresholds and modes | `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficLabelDeclutterPolicy.kt` | OGN feature-builder path and tests | this is a pure render-only policy, parallel to icon sizing | No |
| OGN one-label retention rule (`identifier/distance` retained, relative-altitude hidden first) | `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficLabelDeclutterPolicy.kt` | OGN feature-builder path and tests | semantic retention belongs with the declutter policy, not the mapper's positional output | No |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN label declutter mode | N/A | pure render policy from rendered icon size |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - for the same replay data and the same map zoom actions, OGN label visibility
    must remain identical across runs

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| label policy leaks into settings/ViewModel | SSOT / ownership | review + file ownership plan | change plan + code review |
| semantic label content is duplicated outside mapper | canonical policy ownership | helper/feature tests + review | mapper tests + feature label declutter tests |
| wide zoom still renders both labels | runtime correctness | helper + feature-build tests | new declutter policy tests |
| label declutter accidentally hides all text at close zoom | visual regression | helper + feature-build tests | new declutter policy tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Dense OGN scenes are less text-cluttered at wider zoom | `MS-UX-03` | both labels render for all visible OGN targets | progressive label declutter at medium/wide zoom | helper tests + manual dense-scene validation | Phase 2 |
| Pan/zoom with OGN enabled stays readable | `MS-UX-01` | icon declutter landed but labels remain dense | no unreadable text crowding at wide zoom | manual validation + evidence lane if implemented | hardening |

## 3) Data Flow (Before -> After)

Before:

`OgnRelativeAltitudeFeatureMapper -> buildOgnTrafficOverlayFeatures -> always write top/bottom labels -> OGN text layers render both labels`

After:

`OgnRelativeAltitudeFeatureMapper -> OgnTrafficLabelDeclutterPolicy(resolve from rendered icon size) -> buildOgnTrafficOverlayFeatures writes both labels / identifier-distance only / none -> OGN text layers render decluttered labels`

Phase 2 seam note:

`OgnTrafficOverlay.currentIconSizePx -> resolveOgnTrafficLabelDeclutterPolicy(...) -> apply declutter to OgnRelativeAltitudeFeatureMapping while preserving the mapper-selected slot for secondaryLabelText -> write final PROP_TOP_LABEL / PROP_BOTTOM_LABEL`

## 4) Implementation Phases

### Phase 0 - Contract lock

- Goal:
  lock the ownership seam for label declutter and explicitly reject any icon-size
  rebalance in this follow-up.
- Files to change:
  - this plan
- Exit criteria:
  - label declutter owner and narrow input seam are explicit
- Status update:
  - Completed on 2026-03-28 by locking semantic one-label retention to
    `identifier/distance` and documenting rendered-size input tradeoffs

### Phase 1 - Pure label declutter policy

- Goal:
  add a pure helper that maps rendered icon size to a declutter mode.
- Files to change:
  - new `OgnTrafficLabelDeclutterPolicy.kt`
  - new `OgnTrafficLabelDeclutterPolicyTest.kt`
- Ownership/file split changes:
  - helper owns thresholds such as:
    - close: show both labels
    - medium-wide: show `identifier/distance` only
    - wide: show no labels
  - exact thresholds should be tuned against the current rendered icon sizes
    produced by the OGN icon declutter policy
  - using rendered icon size as the policy input intentionally preserves the
    pilot's larger-icon preference as a factor in when labels declutter
- Tests to add/update:
  - close / medium / wide policy transitions
  - min/max clamp behavior
  - identifier-distance retention is explicit and semantic, not positional
- Exit criteria:
  - policy is deterministic and test-locked
- Status update:
  - Implemented on 2026-03-28 in `OgnTrafficLabelDeclutterPolicy.kt` with
    focused unit coverage

### Phase 2 - Feature-build wiring

- Goal:
  apply the declutter policy when writing OGN feature label properties.
- Files to change:
  - `OgnTrafficLabelDeclutterPolicy.kt`
  - `OgnTrafficOverlay.kt`
  - `OgnTrafficOverlaySupport.kt`
  - optional narrow mapper test updates if semantic label access needs exposure
  - new `OgnTrafficOverlayFeatureLabelDeclutterTest.kt`
- Ownership/file split changes:
  - `OgnTrafficLabelDeclutterPolicy.kt` adds the pure semantic apply step that turns
    `OgnRelativeAltitudeFeatureMapping + OgnTrafficLabelDeclutterPolicy` into final
    rendered top/bottom label strings
  - `OgnTrafficOverlay` resolves the declutter policy from `currentIconSizePx`
    and passes it into the feature-build path
  - `OgnTrafficOverlaySupport` applies declutter after semantic mapping and before
    writing `PROP_TOP_LABEL` / `PROP_BOTTOM_LABEL`
  - one-label declutter preserves the mapper-selected slot for `secondaryLabelText`
    instead of converting the semantic rule into a positional one
- Tests to add/update:
  - both labels still render at close zoom
  - only `identifier/distance` renders at medium-wide zoom
  - both labels are empty at wide zoom
  - no change to `PROP_TARGET_KEY`, `PROP_TARGET_ID`, or icon identity properties
- Exit criteria:
  - wide-zoom OGN feature labels are decluttered without new runtime wiring
  - feature-build tests prove label declutter changes only rendered text properties,
    not selection/identity properties
- Status update:
  - Implemented on 2026-03-28 in `OgnTrafficLabelDeclutterPolicy.kt`,
    `OgnTrafficOverlay.kt`, and `OgnTrafficOverlaySupport.kt` with focused
    feature-build coverage in `OgnTrafficOverlayFeatureLabelDeclutterTest.kt`

### Phase 3 - Hardening and validation

- Goal:
  confirm the text declutter is strong enough without hiding needed information too early.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` only if implementation changes documented overlay flow materially
- Tests to add/update:
  - extend affected runtime/feature tests if needed
- Exit criteria:
  - required checks pass
  - manual dense-scene validation covers:
    - close zoom: both labels present
    - medium-wide zoom: one label retained
    - wide zoom: labels suppressed, icons still visible

## 5) Test Plan

- Unit tests:
  - `OgnTrafficLabelDeclutterPolicyTest.kt`
  - `OgnTrafficOverlayFeatureLabelDeclutterTest.kt`
- Replay/regression tests:
  - deterministic repeat-run behavior only; replay data path is unchanged
- UI/instrumentation tests:
  - not required unless feature-build tests prove insufficient
- Degraded/failure-mode tests:
  - invalid or extremely small rendered icon sizes still resolve to a safe declutter mode
  - one-label declutter keeps `secondaryLabelText` in the mapper-chosen slot and
    does not rewrite it to a fixed top/bottom position
- Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| declutter hides too much information too early | usability regression | use phased `both -> one -> none` policy instead of an immediate full hide | XCPro Team |
| wrong label is retained in the one-label state | operational confusion | explicitly retain semantic `identifier/distance`, not positional top/bottom, and lock that in tests | XCPro Team |
| label declutter differs by base icon-size preference | pilots with larger chosen icons retain labels longer | treat this as intentional because rendered icon size is the narrow runtime input seam; tune thresholds against default/min/max sizes | XCPro Team |
| follow-up expands into another zoom wiring refactor | unnecessary runtime churn | use rendered icon size as the only policy input seam | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No new OGN settings or persisted state introduced
- Semantic label composition remains owned by `OgnRelativeAltitudeFeatureMapper`
- Effective label declutter remains runtime-only and non-persisted
- Replay behavior remains deterministic for the same data and zoom actions

## 8) Rollback Plan

- What can be reverted independently:
  - new label declutter helper
  - OGN feature-label apply step
- Recovery steps if regression is detected:
  - revert wide-zoom label declutter
  - keep the current icon declutter path unchanged
