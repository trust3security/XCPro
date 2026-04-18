# OGN target ring zoom-scaled tight wrap

## 0) Metadata

- Title: OGN target ring zoom-scaled tight wrap
- Owner: Codex
- Date: 2026-03-30
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  - When the user taps a glider, targets it from the OGN details sheet, and the
    yellow target ring appears, the ring still feels too large and too detached
    from the glider icon.
  - The requested UX is that the yellow target ring should visibly track zoom so
    it stays tight around the targeted glider instead of looking like a mostly
    fixed-size circle.
- Why now:
  - The current targeted-aircraft UX communicates the right state but the ring
    size undermines precision and makes the targeted glider feel less clearly
    anchored.
- In scope:
  - make OGN target-ring radius and stroke width derive from the same zoom-aware
    rendered icon size already used by OGN traffic overlays
  - tighten ring visual sizing so it wraps the glider icon more closely across
    wide/mid/close zoom bands
  - add focused regression coverage for ring sizing policy
  - preserve existing target action flow from bottom sheet -> selection runtime
    -> map runtime target visuals
- Out of scope:
  - changing `Show Scia for this aircraft` / `Target this aircraft` behavior
  - changing target selection ownership or persistence
  - changing target line / ownship badge behavior
  - adding a new continuous per-frame zoom animation system
  - changing ADS-B overlays or shared traffic policy
- User-visible impact:
  - targeted OGN gliders show a smaller, thinner yellow ring that scales with
    the same zoom-derived icon size as the glider render
- Rule class touched: Default + Guideline

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Selected OGN target | `TrafficSelectionRuntime` / map selection state | `selectedOgnTarget` into map runtime | UI-owned duplicate selection state |
| OGN target visuals enablement + target payload | `MapOverlayManagerRuntimeOgnDelegate` | `updateTargetVisuals(...)` render path | separate ring-only target state |
| Zoom-derived rendered OGN icon size | `MapOverlayManagerRuntimeOgnDelegate` | `renderedOgnIconSizePx` passed into live overlays | second zoom scaling owner inside UI |
| Yellow target-ring radius + stroke policy | `OgnTargetRingOverlay` | local layer properties only | duplicate ring-size math in ViewModel/UI |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `ognViewportZoom` | `MapOverlayManagerRuntimeOgnDelegate` | delegate `setViewportZoom()` | overlay runtime only | map camera zoom | none | map detach / overlay recreate | n/a | viewport zoom tests |
| `renderedOgnIconSizePx` | `MapOverlayManagerRuntimeOgnDelegate` | delegate icon-size + zoom path | passed to traffic + target-ring overlays | `resolveOgnTrafficViewportSizing(...)` | none | overlay recreate / map detach recompute | n/a | viewport zoom tests |
| target-ring `currentIconSizePx` | `OgnTargetRingOverlay` | overlay `setIconSizePx()` | layer property updates only | delegate-provided rendered icon size | none | overlay cleanup / recreate | n/a | target-ring sizing tests |
| target-ring radius/stroke values | `OgnTargetRingOverlay` | local sizing helpers only | `CircleLayer` properties | `currentIconSizePx` | none | recomputed on icon-size change | n/a | target-ring sizing tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:traffic` map runtime overlay code and map-runtime tests
- Any boundary risk:
  - do not move ring sizing policy into Composables, ViewModels, or selection
    state owners just to tune visuals

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt` | same overlay family, same rendered icon-size input | overlay-local style-property updates from runtime-owned icon size | target ring uses circle radius/stroke, not icon bitmaps |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt` | already locks zoom -> rendered icon size propagation to OGN overlays | keep delegate as the single zoom-size owner | add ring-specific policy tests instead of new delegate owner |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Target-ring visual tight-wrap policy | `OgnTargetRingOverlay` (implicit loose constants) | `OgnTargetRingOverlay` (explicit zoom-aware ring sizing policy) | same owner should stay canonical; only the policy becomes clearer and tighter | focused sizing tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| none | existing delegate already pushes rendered icon size into the target ring | retain current path; do not add a second zoom-size feed | n/a |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt` | existing | yellow target-ring layer creation and ring sizing math | canonical owner of the visual ring | ViewModel/UI should not own map layer geometry | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/OgnTargetRingOverlaySizingTest.kt` | new | focused regression tests for ring radius/stroke policy | smallest unit test owner for the new sizing contract | avoid proving sizing only indirectly through broad runtime tests | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt` | existing | locks zoom-derived icon-size propagation to target ring | existing boundary test owner | no need for a new delegate test file unless scope expands | no |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| target-ring sizing helpers (if kept file-local `internal`) | `OgnTargetRingOverlay.kt` | target-ring sizing tests only | internal | allows focused policy tests without exposing UI/runtime APIs | remain internal; no cross-module contract |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| yellow target-ring radius/stroke sizing | `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt` | target-ring style updates and sizing tests | it is the only layer that renders the ring | no |
| zoom-derived rendered OGN icon size | `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | traffic overlay + target ring overlay | delegate already owns OGN overlay zoom/icon application | no |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| target-ring size | n/a | pure visual size derived from zoom and icon size; no clock involvement |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged map runtime overlay path; no new scope or dispatcher
- Primary cadence/gating sensor:
  - existing OGN overlay zoom and target-visual updates
- Hot-path latency budget:
  - ring sizing must remain constant-time local math with no extra allocations on
    the hot render path beyond current overlay updates

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none added; ring size remains a pure function of zoom-derived rendered icon
    size

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| no selected target | User action / Unavailable | map selection runtime + delegate | no yellow target ring | existing clear behavior retained | existing target-visual tests |
| invalid target coordinate | Recoverable | `OgnTargetRingOverlay` | ring clears instead of rendering a bad marker | existing clear behavior retained | existing overlay behavior |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| second zoom-size owner gets introduced | SSOT / ownership clarity | review + boundary test | `MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt` |
| ring drifts back to a visually loose fixed-size policy | map visual policy ownership | unit test | `OgnTargetRingOverlaySizingTest.kt` |
| target tap/selection flow gets changed while tuning visuals | scope containment | unit test | `MapOverlayManagerRuntimeOgnDelegateTargetTapTest.kt` |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| targeted ring visually wraps the glider across zoom bands | OGN-TARGET-RING-01 | ring feels oversized relative to the icon | ring outer radius tracks the same rendered icon-size owner and remains visibly tighter than the current loose policy at wide/mid/close zoom | unit tests + manual map check | Phase 2 |
| targeted ring stroke remains visible but not heavy | OGN-TARGET-RING-02 | stroke feels visually thick when ring is large | stroke scales down with ring size and does not overpower the glider icon | unit tests + manual map check | Phase 2 |

## 3) Data Flow (Before -> After)

Before:

`Bottom sheet target action -> selectedOgnTarget -> MapOverlayManagerRuntimeOgnDelegate -> OgnTargetRingOverlay uses local loose radius/stroke constants -> map ring looks oversized`

After:

`Bottom sheet target action -> selectedOgnTarget -> MapOverlayManagerRuntimeOgnDelegate (canonical zoom-derived rendered icon size) -> OgnTargetRingOverlay derives tighter radius/stroke from that rendered icon size -> map ring scales with zoom and stays close to the glider`

## 4) Implementation Phases

### Phase 0: Lock scope and owner

- Goal:
  - confirm this is a target-ring visual policy change only
- Files to change:
  - change plan only
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - none
- Exit criteria:
  - plan approved with ring sizing owned by `OgnTargetRingOverlay` and zoom-size
    ownership retained in `MapOverlayManagerRuntimeOgnDelegate`

### Phase 1: Tight zoom-aware ring sizing

- Goal:
  - make ring radius and stroke derive from the delegate-provided rendered icon
    size so the ring stays tight around the glider across existing zoom bands
- Files to change:
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt`
- Ownership/file split changes in this phase:
  - no ownership move; make the ring sizing policy explicit inside the existing
    ring overlay owner
- Tests to add/update:
  - add `OgnTargetRingOverlaySizingTest.kt`
- Exit criteria:
  - ring radius/stroke are expressed as explicit helpers/constants driven by
    `currentIconSizePx`
  - no new delegate, ViewModel, or UI sizing owner is introduced

Phase 1 implementation note:

- 2026-03-30 narrow seam/code pass keeps zoom-size ownership in
  `MapOverlayManagerRuntimeOgnDelegate` and updates only
  `OgnTargetRingOverlay.kt` plus the focused ring-sizing regression test.

### Phase 2: Boundary lock and visual validation

- Goal:
  - prove zoom propagation and target interaction behavior remain intact
- Files to change:
  - `feature/traffic/src/test/java/com/trust3/xcpro/map/OgnTargetRingOverlaySizingTest.kt`
  - `feature/traffic/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt` if additional assertion is needed
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - ring sizing regression
  - existing viewport zoom delegate tests
  - existing target tap test
- Exit criteria:
  - targeted tests pass and manual check confirms the ring tracks zoom and wraps
    the glider more tightly

Phase 2 implementation note:

- 2026-03-30 narrow seam/code pass keeps the ring tied to the same
  delegate-provided rendered icon size as the glider icon so the ring padding
  stays consistent as icon size changes across zoom bands; targeted boundary
  tests were expanded accordingly.
- Manual map visual validation remains `NOT RUN`.

## 5) Test Plan

- Unit tests:
  - `OgnTargetRingOverlaySizingTest`
- Replay/regression tests:
  - n/a
- UI/instrumentation tests (if needed):
  - not required for the initial implementation slice
- Degraded/failure-mode tests:
  - existing clear/no-target behavior remains unchanged
- Boundary tests for removed bypasses:
  - existing `MapOverlayManagerRuntimeOgnDelegateViewportZoomTest`
  - existing `MapOverlayManagerRuntimeOgnDelegateTargetTapTest`
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | `OgnTargetRingOverlaySizingTest` |
| Ownership move / bypass removal / API boundary | Boundary lock tests | existing viewport zoom delegate tests |
| UI interaction / lifecycle | UI or instrumentation coverage | existing target tap test + manual map check |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Focused implementation loop:

```bash
./gradlew :feature:traffic:testDebugUnitTest --tests "com.trust3.xcpro.map.OgnTargetRingOverlaySizingTest" --tests "com.trust3.xcpro.map.MapOverlayManagerRuntimeOgnDelegateTargetTapTest" --tests "com.trust3.xcpro.map.MapOverlayManagerRuntimeOgnDelegateViewportZoomTest"
```

Manual validation:

1. Target a visible OGN glider from the details sheet.
2. Check close zoom: yellow ring sits just around the glider icon.
3. Zoom out to mid/wide bands and confirm the ring shrinks with the glider.
4. Confirm target tap and bottom-sheet behavior are unchanged.

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| ring becomes too small at wide zoom | target becomes harder to visually pick out | keep an explicit minimum radius/stroke floor and manual-check wide zoom | Codex |
| a second zoom policy gets introduced just for the ring | ownership drift and future mismatch with glider icon size | reuse delegate-owned `renderedOgnIconSizePx` only | Codex |
| visual tweak accidentally changes target-selection behavior | regression in tap/target flow | keep scope out of selection code and run target tap boundary test | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: n/a
- Decision summary:
  - this is a local visual-policy change inside the existing target-ring owner,
    not a durable architecture boundary move
- Why this belongs in an ADR instead of plan notes:
  - n/a

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate zoom-size or target-visual owner introduced
- Target ring scales from the same rendered icon-size owner as OGN traffic icons
- Target selection / target tap behavior remains unchanged
- `KNOWN_DEVIATIONS.md` remains unchanged

## 8) Rollback Plan

- What can be reverted independently:
  - target-ring radius/stroke sizing helpers and constants
  - ring sizing regression test
- Recovery steps if regression is detected:
  - revert the target-ring sizing change
  - rerun focused target-ring/target-tap tests
  - rerun `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, and `./gradlew assembleDebug`

## 9) Review Verdict

- Verdict:
  - Ready
- Critical gaps:
  - None, as long as implementation reuses the existing delegate-owned
    `renderedOgnIconSizePx` instead of inventing a second zoom-scaling path
- Recommended next step:
  - implement Phase 1 in `OgnTargetRingOverlay.kt`, add the focused sizing test,
    and keep `MapOverlayManagerRuntimeOgnDelegate` unchanged unless a boundary
    test proves the current zoom propagation path is insufficient
