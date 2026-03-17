# CHANGE PLAN - ADS-B Category + Metadata Icon Refinement (2026-03-16)

## 0) Metadata

- Title: ADS-B category plus metadata/typecode icon refinement
- Owner: XCPro Team
- Date: 2026-03-16
- Issue/PR: TBD
- Status: Draft

## 0A) Seam-Pass Decisions (2026-03-16)

Locked decisions from the pre-implementation seam pass:

1. Do not add `PlaneJetMedium` in slice 1.
   - Current candidate `ic_adsb_jet_medium.png` is byte-identical to
     `feature:traffic` `ic_adsb_jet_twin.png`, so a new enum value would add
     semantic churn without a visible gain.
   - `L2J` remains mapped to `PlaneTwinJet` in slice 1.
2. Do not add a dedicated high-performance/fighter class in slice 1.
   - Category `7` should stop falling back to `PlaneLarge`.
   - In the first release-safe slice, category `7` should fall back to
     `PlaneTwinJet` unless stronger metadata resolves to another existing class.
3. Do not wire the candidate `feature:map` `ic_adsb_plane_light.png` file in
   slice 1.
   - It is byte-identical to `feature:traffic` `ic_adsb_plane_medium.png`.
   - `feature:traffic` is the owning module for `AdsbAircraftIcon`; `feature:map`
     resources must not become the source of truth for the traffic icon enum.
4. Keep `PlaneLight` as a semantic class and stable style id, but keep its
   current drawable binding until genuinely distinct light-aircraft art exists.
5. Categories `15..20` are label-only in slice 1.
   - Add explicit labels.
   - Keep icon fallback on the existing unknown/default-medium path.
6. Categories `6`, `8`, `9`, `10`, `11`, `12`, and `14` remain authoritative
   category outcomes and are not overridden by fixed-wing metadata heuristics.

## 1) Scope

- Problem statement:
  - Current ADS-B icon mapping is correct for the clearly non-fixed-wing categories, but fixed-wing buckets are still too coarse for release-grade aircraft recognition.
  - Category-only mapping currently collapses materially different aircraft into the same silhouette.
  - The clearest mismatch is category `4`, which is labeled "Large" but is currently rendered with the medium fixed-wing icon.
  - Current labels also collapse category `13` and categories `15..20` into generic `Unknown`.
- Why now:
  - The current runtime already has metadata and typecode inputs.
  - The existing resolver already supports `PlaneTwinJet` and `PlaneTwinProp`, so the highest-value uplift is refinement, not rewrite.
  - The seam pass confirmed the highest-value code changes are category fallback correction and label completion, not new icon classes or asset moves.
- In scope:
  - Refine fixed-wing icon selection using category plus metadata/typecode.
  - Correct category-to-icon mismatches where the current mapping is semantically weak.
  - Add explicit human-readable labels for currently collapsed categories.
  - Keep replay determinism and existing overlay flow unchanged.
- Out of scope:
  - Rewriting ADS-B repository/store/runtime architecture.
  - Changing the existing default-medium unknown visual rollout policy.
  - New dedicated surface-vehicle or obstacle art in the first release-safe slice.
  - Changing the ADS-B airborne-only display gate unless explicitly requested in a later plan.
- User-visible impact:
  - More recognizable fixed-wing aircraft silhouettes on the map.
  - Better emitter-category wording in details.
  - Fewer cases where distinct aircraft classes share the same icon.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Raw OpenSky emitter category | ADS-B repository/store output | `AdsbTrafficUiModel.category` | UI-owned category mirrors |
| Aircraft metadata typecode | aircraft metadata repository + enrichment path | `AdsbTrafficUiModel.metadataTypecode` | overlay-local metadata caches |
| Aircraft ICAO class metadata | aircraft metadata repository + enrichment path | `AdsbTrafficUiModel.metadataIcaoAircraftType` | UI-owned classification state |
| Derived aircraft class | domain resolver | `AdsbAircraftClass` at read time | persisted or repository-owned icon class copies |
| Derived icon projection | UI mapper / overlay projection | `AdsbAircraftIcon` and style image id | repository-owned drawable/resource ids |

Notes:
- This plan introduces no new authoritative state owner.
- Classification remains derived from existing SSOT inputs.

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `category` | ADS-B repository/store | OpenSky mapper + repository update path | `AdsbTrafficUiModel` -> resolver | OpenSky state vector index `17` | none | replaced on target update / target removal | none | parser + mapper regression tests |
| `metadataTypecode` | metadata repository/enrichment | metadata import and on-demand enrichment | `AdsbTrafficUiModel` -> resolver | metadata repository row | metadata repository | replaced on metadata refresh / metadata miss | none | enrichment + resolver tests |
| `metadataIcaoAircraftType` | metadata repository/enrichment | metadata import and on-demand enrichment | `AdsbTrafficUiModel` -> resolver | metadata repository row | metadata repository | replaced on metadata refresh / metadata miss | none | enrichment + resolver tests |
| `AdsbAircraftClass` | resolver (derived only) | pure resolver logic | icon mapper, labels, sticky projection | category + metadata fields + optional ICAO24 override | none | recomputed every read | none | table-driven resolver tests |
| `AdsbAircraftIcon` | icon mapper (derived only) | pure icon mapping | overlay feature projection and style image registration | `AdsbAircraftClass` | none | recomputed every read | none | icon enum + mapper tests |

### 2.2 Dependency Direction

Dependency direction remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:traffic` domain + UI icon mapping files
  - `docs/ADS-b`
- Any boundary risk:
  - Icon drawables must be owned by the same module as `AdsbAircraftIcon` or a lower dependency owner.
  - `feature:traffic` must not depend upward on `feature:map` resources for ADS-B icon model compilation.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/example/xcpro/adsb/domain/AdsbAircraftClassResolver.kt` | current ADS-B class resolution owner | keep pure domain classification with no Android imports | add new fixed-wing refinement classes/branches only if needed |
| `feature/traffic/src/main/java/com/example/xcpro/adsb/ui/AdsbAircraftIcon.kt` | current icon projection owner | keep drawable/style mapping in one narrow UI enum | may add `PlaneJetMedium` and rebind `PlaneLight` asset |
| `docs/ADS-b/CHANGE_PLAN_ADSB_ICON_CORRECTNESS_RELEASE_GRADE_2026-03-09.md` | existing ADS-B icon workstream plan | reuse phased execution, focused scope, and release-gate style | this plan adds change-plan template sections and explicit state ownership tables |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Fixed-wing shape refinement policy | mixed category fallback + limited metadata heuristics in resolver | same resolver owner, expanded policy contract | keep one canonical classification owner | resolver unit tests |
| Category label completeness for `13` and `15..20` | generic `Unknown` fallback | same label mapper owner with explicit text | details should tell the truth even when icon remains generic | label tests |
| Candidate ADS-B light/jet-medium resource ownership | duplicate worktree art outside the owning module | keep `feature:traffic` resource ownership unchanged in slice 1 | avoid no-op resource churn and upward resource coupling | compile + icon enum tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| none required | current flow already resolves through resolver -> mapper -> overlay projection | keep current path; do not introduce overlay-local classification logic | n/a |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/traffic/src/main/java/com/example/xcpro/adsb/domain/AdsbAircraftClassResolver.kt` | Existing | canonical ADS-B classification policy | owns pure classification today | classification must not move into UI/overlay code | No |
| `feature/traffic/src/main/java/com/example/xcpro/adsb/ui/AdsbAircraftIcon.kt` | Existing | drawable/style mapping for ADS-B icons | current UI icon owner | drawables/style ids do not belong in domain resolver | No |
| `feature/traffic/src/main/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapper.kt` | Existing | category labels + icon projection helpers | current label/projection seam | labels should stay out of repository/domain logic | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/AdsbStickyIconProjectionCache.kt` | Existing | sticky style id reuse for strong fixed-wing classes | projection cache already owns sticky rules | sticky visual behavior must not move into resolver | No |
| `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlayStyleImages.kt` | Existing | runtime style image registration | already registers all ADS-B icons | registration should remain below icon enum, not in docs/tests | No |
| `feature/traffic/src/test/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapperTest.kt` | Existing | mapping/label regression tests | current high-signal test owner | category/icon truth should be locked near mapper | No |
| `feature/traffic/src/test/java/com/example/xcpro/adsb/ui/AdsbAircraftIconTest.kt` | Existing | drawable/style id enum regression tests | current icon enum test owner | resource bindings should be locked near enum | No |
| `docs/ADS-b/ADSB_CategoryIconMapping.md` | Existing | runtime mapping contract documentation | current icon mapping contract owner | `ADSB.md` is broader runtime contract | No |
| `docs/ADS-b/ADSB.md` | Existing | user-visible ADS-B runtime contract wording | current ADS-B runtime contract | detailed category mapping table belongs in the specialized doc | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| none in slice 1 | n/a | n/a | n/a | existing ADS-B classification/icon contracts are sufficient for the first refinement slice | n/a |

ADR requirement:
- No ADR expected if implementation remains within the existing ADS-B resolver/mapper/overlay path and does not change module boundaries.

### 2.2F Scope Ownership and Lifetime

No new long-lived scope is expected in this IP.

### 2.2G Compatibility Shim Inventory

No compatibility shim is planned.

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| ADS-B category + metadata/typecode -> aircraft class policy | `feature/traffic/src/main/java/com/example/xcpro/adsb/domain/AdsbAircraftClassResolver.kt` | icon mapper, labels, sticky projection, tests, docs | one pure classification owner avoids icon drift across UI/runtime paths | No |

### 2.2I Stateless Object / Singleton Boundary

No new `object` or singleton-like holder is planned.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| category/metadata/typecode classification | none | pure mapping, not time-based |
| sticky icon TTL | Monotonic | existing visual hold behavior remains monotonic-safe |
| replay output ordering | Replay inputs + existing monotonic visual cache timing | existing replay determinism contract remains unchanged |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged; pure classification remains on existing call path
- Primary cadence/gating sensor:
  - unchanged; ADS-B target update cadence remains repository-driven
- Hot-path latency budget:
  - icon/class refinement must not cause `MS-ENG-03` regression in ADS-B per-frame feature-build cost

### 2.4A Logging and Observability Contract

No new logging is planned.

If temporary icon-debug logs are added during implementation:
- use `AppLogger` or debug-only gated logs,
- remove them before merge or track explicitly.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none planned
  - icon classification must remain a pure function of category + metadata/typecode + existing ICAO24 override policy

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| category missing / ambiguous (`0`, `1`, `13`) | Unavailable | resolver + mapper | keep unknown semantics and current unknown visual rollout | existing unknown fallback path | mapper regression tests |
| metadata unavailable for fixed-wing refinement | Degraded | metadata enrichment + resolver | use category fallback icon and explicit label | existing metadata-enrichment retry path; no UI-side fallback invention | resolver + enrichment tests |
| unsupported categories `15..20` in first slice | Degraded | label mapper + icon mapper | explicit label, unknown/default-medium visual fallback | new dedicated art deferred unless airborne gate changes | label tests |

### 2.5B Identity and Model Creation Strategy

No ID/timestamp creation changes are planned.

### 2.5C No-Op / Test Wiring Contract

No `NoOp` or convenience-constructor path is planned.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| category `4` remains medium after plan | ADS-B mapping contract + maintainability rules | unit test + review | `AdsbAircraftIconMapperTest` |
| fixed-wing refinement drifts into overlay/UI logic | dependency direction + domain ownership rules | review + unit test ownership | resolver file + test placement |
| new icon assets live in wrong module owner | module boundary rules | compile + review | `AdsbAircraftIcon.kt` + resource owner |
| new class added but sticky cache does not treat it as strong fixed-wing | visual consistency contract | unit test | `AdsbStickyIconProjectionCacheTest` |
| labels for `13` and `15..20` stay generic | explicit state/error labeling rules | unit tests | `AdsbAircraftIconMapperTest` |
| replay output becomes non-deterministic | replay determinism rules | regression/unit review | existing ADS-B/replay tests plus resolver purity review |

### 2.7 Visual UX SLO Contract

Impacted map/overlay SLOs for this IP:

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| fixed-wing icon changes do not increase ADS-B marker jump/flicker during metadata upgrades | `MS-UX-03` | current ADS-B icon churn baseline | no regression vs baseline | targeted ADS-B projection/sticky-cache tests plus evidence run if runtime path changes materially | Phase 3 |
| icon-class refinement does not cause redundant icon flips when state is unchanged | `MS-UX-04` | current steady-state icon override behavior | redundant icon flips with unchanged inputs = 0 | mapper + sticky-cache tests | Phase 2 |
| ADS-B frame build cost remains within dense-scene threshold | `MS-ENG-03` | existing ADS-B overlay baseline | no regression vs threshold or baseline | targeted ADS-B perf evidence if hot-path logic grows | Phase 4 |

## 3) Data Flow (Before -> After)

Before:

`OpenSky category + metadata fields -> AdsbAircraftClassResolver -> AdsbAircraftIconMapper -> AdsbGeoJsonMapper -> sticky projection cache -> overlay style image registration`

Current issues:

1. category `4` is resolved to `PlaneMedium`.
2. `PlaneLight` and `PlaneMedium` currently share the same visual asset.
3. fixed-wing transport jets and light piston aircraft can still collapse into similar visuals.
4. labels for `13` and `15..20` are not explicit.

After:

`OpenSky category + metadata fields -> refined fixed-wing resolver policy -> icon mapper/labels -> unchanged GeoJson/sticky/overlay registration path`

Target changes:

1. Keep the existing path and owners.
2. Refine only the classification policy and icon enum/resource bindings.
3. Preserve the current unknown fallback rollout unless explicitly changed in another plan.

## 4) Implementation Phases

### Phase 0 - Contract Lock and Baseline

- Goal:
  - lock the exact mapping contract before code changes
  - confirm asset/module ownership
  - cut low-value scope from the first release-safe slice
- Files to change:
  - change plan doc only
  - during implementation: tests first in `AdsbAircraftIconMapperTest.kt`
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - add a table-driven category truth table for `0..20`
  - add explicit assertions for category `4`
  - add explicit assertions for labels `13`, `15..20`
- Exit criteria:
  - mapping contract for category + metadata refinement is written and reviewed
  - decision recorded:
    - `6`, `8`, `9`, `10`, `11`, `12`, `14` remain authoritative
    - unknown fallback visual policy remains unchanged
    - first release-safe slice does not require new dedicated `15..20` art

### Phase 1 - Fixed-Wing Asset and Class Normalization

- Goal:
  - give fixed-wing refinement a stable icon vocabulary owned by the correct module
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/ui/AdsbAircraftIcon.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/ui/AdsbAircraftIconTest.kt`
  - none required by default in slice 1
- Ownership/file split changes in this phase:
  - no owner moves beyond correct resource placement
- Tests to add/update:
  - keep existing icon enum tests green
  - add a regression assertion documenting that `PlaneLight` remains a semantic
    distinction even while sharing the current medium-plane drawable
- Exit criteria:
  - no upward module/resource dependency is introduced
  - no new ADS-B icon enum value is added without distinct art

### Phase 2 - Category and Metadata/Typecode Refinement

- Goal:
  - improve fixed-wing classification without touching runtime wiring
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/domain/AdsbAircraftClassResolver.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapper.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapperTest.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/map/AdsbStickyIconProjectionCacheTest.kt`
- Ownership/file split changes in this phase:
  - none; resolver remains the one classification owner
- Tests to add/update:
  - category `4 -> PlaneLarge`
  - `L1P/L1T -> PlaneLight`
  - `L2P/L2T -> PlaneTwinProp`
  - `L2J -> PlaneTwinJet`
  - `L3J/L3P/L3T -> PlaneLarge`
  - `L4J -> PlaneHeavy`
  - typecode fallbacks for `C172`, `BE58`, `AT76`, `B738`, `A388`, `B757`
- Exit criteria:
  - category `4` no longer resolves to medium
  - category `7` no longer resolves to large
  - fixed-wing refinement is metadata-first, category-fallback, and pure
  - existing sticky projection remains correct without adding a new strong fixed-wing class

### Phase 3 - Label Truth and Overlay Contract Closure

- Goal:
  - make details truth explicit without widening the first release slice unnecessarily
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapper.kt`
  - `docs/ADS-b/ADSB_CategoryIconMapping.md`
  - `docs/ADS-b/ADSB.md`
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - labels for `13`, `15`, `16`, `17`, `18`, `19`, `20`
  - unknown visual fallback remains unchanged for unsupported art buckets in this slice
- Exit criteria:
  - details sheet wording is explicit for all categories `0..20`
  - runtime contract docs match code
  - no dedicated surface/obstacle art is required for slice completion

### Phase 4 - Release Gate and Evidence Closure

- Goal:
  - prove the refinement is release-safe
- Files to change:
  - docs only if residual-risk notes are needed
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - run targeted ADS-B suites
  - run required repo gates
  - run map evidence only if overlay runtime behavior changed enough to affect SLOs materially
- Exit criteria:
  - `./gradlew enforceRules` passes
  - `./gradlew testDebugUnitTest` passes
  - `./gradlew assembleDebug` passes
  - impacted SLO evidence is attached or explicitly judged unchanged with rationale

## 5) Test Plan

- Unit tests:
  - `AdsbAircraftIconMapperTest`
  - `AdsbAircraftIconTest`
  - `AdsbStickyIconProjectionCacheTest`
- Replay/regression tests:
  - existing deterministic ADS-B/replay suites remain green
- UI/instrumentation tests (if needed):
  - not required by default unless runtime overlay behavior beyond pure mapping changes
- Degraded/failure-mode tests:
  - metadata missing -> category fallback
  - unsupported categories -> explicit label + unknown/default-medium visual fallback
- Boundary tests for removed bypasses:
  - none expected; no bypass removal in scope
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | resolver and mapper table-driven tests |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | sticky projection tests only if strong fixed-wing classes change visual hold behavior |
| Persistence / settings / restore | Round-trip / restore / migration tests | none expected |
| Ownership move / bypass removal / API boundary | Boundary lock tests | resource-owner/module compile safety + enum tests |
| UI interaction / lifecycle | UI or instrumentation coverage | only if overlay runtime path changes materially |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | ADS-B overlay evidence if `MS-ENG-03` risk increases |

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

Targeted test commands:

```bash
./gradlew :feature:traffic:testDebugUnitTest --tests com.example.xcpro.adsb.ui.AdsbAircraftIconMapperTest
./gradlew :feature:traffic:testDebugUnitTest --tests com.example.xcpro.adsb.ui.AdsbAircraftIconTest
./gradlew :feature:traffic:testDebugUnitTest --tests com.example.xcpro.map.AdsbStickyIconProjectionCacheTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| adding new jet/light art without distinct visuals | semantic churn with no user-visible gain | do not add new enum values or asset moves until art is genuinely different | XCPro Team |
| assets live in the wrong module owner | compile/boundary drift | keep icon resources in `feature:traffic` or a lower shared owner | XCPro Team |
| metadata heuristics override authoritative category incorrectly | wrong icon truth for helicopters/gliders/heavy | preserve authoritative category gate for `6`, `8`, `9`, `10`, `11`, `12`, `14` | XCPro Team |
| dedicated `15..20` art expands scope with little runtime value | late release churn | keep first slice label-only for those categories | XCPro Team |
| fixed-wing refinement adds hot-path overhead | ADS-B overlay cost regression | keep resolver pure and small; validate `MS-ENG-03` if needed | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: n/a
- Decision summary:
  - this plan assumes no new module, public API, or long-lived runtime ownership change
- Why this belongs in an ADR instead of plan notes:
  - not applicable unless implementation later changes module/resource ownership in a durable way

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Existing unknown visual fallback contract remains unchanged unless explicitly re-planned
- Fixed-wing refinement is owned only by the resolver/icon mapper path
- Time base handling remains explicit where sticky visual timing is involved
- Replay behavior remains deterministic
- Error/degraded-state behavior is explicit for missing metadata and unsupported categories
- Ownership/boundary/public API decisions stay inside existing ADS-B module boundaries
- For map/overlay behavior: impacted SLOs are named and evidence is attached when required
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

## 8) Rollback Plan

- What can be reverted independently:
  - category mapping changes
  - metadata/typecode refinement branches
  - label changes
  - resource rebinding for `PlaneLight` / `PlaneJetMedium`
- Recovery steps if regression is detected:
  - revert to existing category fallback mapping
  - keep explicit label updates if they are low-risk and correct
  - re-run targeted ADS-B tests and required repo gates

## 9) Readiness Verdict

`Ready`

Implementation can start with the seam decisions above locked:

1. keep the current unknown rollout,
2. refine fixed-wing only,
3. remap category `4` to `PlaneLarge`,
4. remap category `7` fallback to `PlaneTwinJet`,
5. add explicit labels for `13` and `15..20`,
6. defer dedicated surface/obstacle art until the airborne gate or product scope changes.
