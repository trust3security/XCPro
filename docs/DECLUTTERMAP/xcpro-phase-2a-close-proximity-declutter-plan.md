# XCPro Phase 2A Close-Proximity Declutter Change Plan

## 0) Metadata

- Title: Phase 2A packed-group label control
- Owner: Codex
- Date: 2026-03-31
- Issue/PR: local branch `feat/traffic-declutter-phase2a`
- Status: Verified locally

## 1) Scope

- Problem statement:
  - Close-proximity traffic groups still become unreadable because multiple aircraft labels render at once in nearly the same screen-space area.
- Why now:
  - [xcpro-close-proximity-declutter-brief.md](./xcpro-close-proximity-declutter-brief.md) is now the canonical product direction and requires label control before any icon fan-out work.
- In scope:
  - Detect packed groups in screen space in the existing traffic overlay/runtime path.
  - Keep icons at true coordinates.
  - Admit a full label only for one primary aircraft per packed group.
  - Hide labels for non-primary aircraft in packed groups.
  - Apply the rule to both OGN and ADS-B traffic overlays.
  - Wire existing selected-aircraft state into the overlay runtime so selected aircraft win within packed groups.
- Out of scope:
  - Icon fan-out or spiderfy.
  - Leader lines.
  - Selection-driven expansion beyond current selection state.
  - Cross-overlay OGN/ADS-B joint collision resolution.
  - Repository, ViewModel, or Compose state ownership changes.
  - Real-coordinate mutation.
- User-visible impact:
  - Close packed groups keep one readable full label instead of rendering unreadable full-label stacks.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN traffic state | `OgnTrafficRepository` | existing `StateFlow<List<OgnTrafficTarget>>` | no packed-group state in repositories/ViewModels |
| ADS-B traffic state | `AdsbTrafficRepository` | existing `StateFlow<List<AdsbTrafficUiModel>>` | no packed-group state in repositories/ViewModels |
| Selected OGN aircraft id | `MapTrafficSelectionState` | existing `selectedOgnTarget` / `selectedOgnId` | no overlay-owned selection authority |
| Selected ADS-B aircraft id | `MapTrafficSelectionState` | existing `selectedAdsbTarget` / `selectedAdsbId` | no overlay-owned selection authority |
| Packed-group label admission | traffic overlay runtime | render-local derived values only | no persisted or cross-layer packed-group cache |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| packed-group full-label admission | OGN/ADS-B overlay runtime render pass | overlay render methods only | feature builders during GeoJSON emission | visible targets + selected id + current map projection + icon footprint policy | none | recomputed each render; clears when overlays clear/cleanup | none | grouping, primary election, selected promotion, non-packed regression |

### 2.2 Dependency Direction

Confirmed dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:map`
  - `feature:map-runtime`
  - `feature:traffic`
- Boundary risk:
  - selection ids must flow through existing runtime ports only; no repository or ViewModel bypass.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/example/xcpro/map/TrafficPackedGroupLabelControl.kt` | shared Phase 2A packed-group runtime owner | deterministic screen-space grouping with stable keys and no randomness | label admission only; no icon displacement |
| `feature/traffic/src/test/java/com/example/xcpro/map/OgnTrafficOverlayFeatureLabelsTest.kt` | feature-property label behavior test | assert feature label properties directly | add packed-group admission assertions |
| `feature/traffic/src/main/java/com/example/xcpro/map/ui/MapTrafficOverlayEffects.kt` | map shell to runtime overlay boundary | forward runtime-only inputs through existing render port | extend the existing port with selected ids only |

### 2.2B Boundary Moves

None. Ownership stays in the existing traffic runtime overlay path.

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `AdsbTrafficOverlay.renderFrame(...)` | display-only icon displacement via screen declutter coordinates | packed-group label admission only with true coordinates | Phase 2A |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/DECLUTTERMAP/xcpro-phase-2a-close-proximity-declutter-plan.md` | New | change-plan contract | required for non-trivial work | not runtime code | no |
| `feature/traffic/src/main/java/com/example/xcpro/map/TrafficPackedGroupLabelControl.kt` | New | canonical packed-group detection + primary-label admission | shared runtime display policy for both overlays | not repository/VM/domain because it is screen-space display policy | no |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt` | Existing | OGN runtime render owner | already owns OGN map render path | not repository/VM | no |
| `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt` | Existing | ADS-B runtime render owner | already owns ADS-B map render path | not repository/VM | no |
| `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlayFeatureSupport.kt` | Existing | OGN feature emission | owns feature label properties | not style/VM | no |
| `feature/traffic/src/main/java/com/example/xcpro/map/AdsbGeoJsonMapper.kt` | Existing | ADS-B feature emission | owns feature label properties | not style/VM | no |
| `feature/traffic/src/main/java/com/example/xcpro/map/ui/MapTrafficOverlayEffects.kt` | Existing | render-port contract | existing UI -> runtime seam | not ViewModel direct call | no |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficOverlayUiAdapters.kt` | Existing | map-shell adapter | existing shell wiring owner | not overlay runtime | no |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` | Existing | runtime-shell forwarder | central runtime shell seam | not UI or repository | no |
| `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | Existing | OGN runtime cache/render orchestration | existing OGN runtime owner | not UI or repository | no |
| `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt` | Existing | ADS-B runtime cache/render orchestration | existing ADS-B runtime owner | not UI or repository | no |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| packed-group label admission | none | derived from current projected screen positions only |
| existing render throttles | Monotonic | unchanged runtime scheduling |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged; runs inside existing map runtime render path
- Primary cadence/gating sensor:
  - existing target updates and viewport invalidation path
- Hot-path latency budget:
  - must stay within current traffic overlay runtime expectations; no extra background collectors or new long-lived scope owners

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none introduced; this is runtime display policy for traffic overlays only

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| packed-group state drifts into ViewModel or repository | `ARCHITECTURE.md` SSOT / responsibility ownership | review + compile | runtime overlay files only |
| non-deterministic primary election | `CODEBASE_CONTEXT_AND_INTENT.md` deterministic behavior | unit test | new packed-group label-control tests |
| selected aircraft does not win | product brief + state contract | unit test | new packed-group label-control tests |
| non-packed aircraft lose labels unexpectedly | regression safety | unit test | feature label tests |

## 3) Data Flow (Before -> After)

Before:

`Traffic repositories -> MapScreenViewModel -> MapTrafficOverlayEffects -> overlay runtime -> GeoJSON features with full labels for all visible aircraft`

After:

`Traffic repositories -> MapScreenViewModel -> MapTrafficOverlayEffects (existing selection ids forwarded) -> overlay runtime packed-group label admission -> GeoJSON features with full labels only for packed-group primaries`

## 4) Implementation Phases

### Phase 0

- Goal:
  - add plan + canonical runtime owner for packed-group label admission
- Files to change:
  - change plan doc
- Exit criteria:
  - implementation contract is explicit

### Phase 1

- Goal:
  - implement shared packed-group label-control owner and wire selection ids into runtime overlays
- Files to change:
  - shared runtime label-control owner
  - map shell/runtime port wiring
  - OGN and ADS-B overlay render paths
- Exit criteria:
  - labels suppress correctly in packed groups and icons remain at true coordinates

### Phase 2

- Goal:
  - add regression tests and sync declutter docs
- Files to change:
  - traffic tests
  - declutter docs
- Exit criteria:
  - tests lock behavior and misleading older docs are marked superseded or archived

## 5) Test Plan

- Unit tests:
  - packed-group grouping and primary election
  - selected-target promotion
  - non-packed labels stay unchanged
  - feature builders blank labels only when suppression is intended
- Replay/regression tests:
  - not required beyond determinism/unit behavior; no replay-path logic change
- UI/instrumentation tests:
  - not required for this slice unless later runtime sequencing proves ambiguous
- Degraded/failure-mode tests:
  - empty targets / invalid projection inputs return stable no-crash results
- Boundary tests for removed bypasses:
  - ADS-B render path keeps true coordinates after fan-out removal

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| packed-group footprint too small or too large | under- or over-suppression | keep one shared constant and lock behavior with tests; adjust only in follow-up if device QA requires it | runtime overlay owner |
| selection ids not forwarded to runtime | selected aircraft does not win | extend existing render port and test selection-driven admission | runtime shell owner |
| stale fan-out docs confuse review | architecture drift | mark older declutter docs superseded or archive them in this change | docs owner |

## 6A) ADR / Durable Decision Record

- ADR required: No
- Decision summary:
  - no new durable module or ownership boundary; this is a bounded runtime-policy change inside the existing traffic overlay owner

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Icons remain at true coordinates
- Packed groups show one full label only
- Selected aircraft wins within packed groups
- `KNOWN_DEVIATIONS.md` unchanged unless a new approved exception becomes necessary

## 8) Rollback Plan

- What can be reverted independently:
  - packed-group label-control runtime owner
  - selection-id runtime port wiring
  - doc supersession notes
- Recovery steps if regression is detected:
  - revert Phase 2A branch or restore full-label render path while keeping selection/runtime owners unchanged
