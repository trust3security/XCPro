# OGN ownship target badge always-on selected-target readability pass

## 0) Metadata

- Title: OGN ownship target badge always-on selected-target readability pass
- Owner: Codex
- Date: 2026-03-30
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  - The blue ownship triangle currently shows the target badge only when the
    selected OGN target is off-screen.
  - The user wants the badge to stay visible whenever a target is selected,
    even while the target remains on-screen.
  - The badge currently shows distance and relative altitude only; the user also
    wants target speed shown there.
  - The current typography is small/light for in-flight readability.
- Why now:
  - The badge is already the correct glanceable target-reference surface beside
    ownship, but the current off-screen-only rule hides the information during
    active target tracking.
- In scope:
  - change the OGN ownship target badge visibility rule from off-screen-only to
    selected-target-always
  - add target speed to the badge content
  - make the badge text more readable using badge-local typography/contrast
    changes only
  - add focused regression coverage for badge visibility/content
- Out of scope:
  - changing bottom-sheet target actions
  - changing target selection ownership or persistence
  - changing OGN target ring or target line behavior
  - changing ADS-B badge/marker behavior
  - introducing new repository/runtime state for badge visibility
- User-visible impact:
  - when a target is selected, the ownship-adjacent badge stays visible and
    shows distance, relative height difference, and target speed in a more
    readable style
- Rule class touched: Default + Guideline

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| selected OGN target | traffic selection runtime -> `MapOverlayManagerRuntimeOgnDelegate` | existing `updateTargetVisuals(...)` path | separate badge-only selected-target state |
| badge visibility policy | `OgnOwnshipTargetBadgeRenderModelBuilder` | `build(request)` returning render model or `null` | UI-layer visibility checks |
| badge text content | `OgnOwnshipTargetBadgeRenderModelBuilder` | `labelText` | duplicate formatting in overlay/delegate/UI |
| badge style properties | `OgnOwnshipTargetBadgeOverlay` | MapLibre `SymbolLayer` properties | typography rules in delegate/UI |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| selected target visual payload | `MapOverlayManagerRuntimeOgnDelegate` | existing `updateTargetVisuals(...)` | badge/ring/line render calls | selected target + ownship location + units | none | target cleared, overlays recreated, map detached | n/a | delegate target-visual tests |
| badge render model | `OgnOwnshipTargetBadgeRenderModelBuilder` | pure `build(...)` function only | `OgnOwnshipTargetBadgeOverlay.render(...)` | enabled flag, selected target, ownship altitude, units | none | recomputed every render; `null` clears badge | n/a | badge render-model tests |
| badge text style | `OgnOwnshipTargetBadgeOverlay` | overlay constants only | MapLibre `SymbolLayer` config | local overlay policy | none | overlay recreate/cleanup | n/a | focused overlay/render-model coverage |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:traffic` map overlay/runtime code and tests
  - `docs/refactor` for the phased IP only in this step
- Any boundary risk:
  - do not move badge formatting or visibility into Composables, ViewModels, or
    repository/runtime owners

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeOverlay.kt` | current owner of badge layer style and placement | keep overlay as the only MapLibre style owner | larger/more legible text constants |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeRenderModel.kt` | current owner of badge visibility/content | keep pure render-model builder as the only content/visibility owner | remove `targetOnScreen` suppression from the decision |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnMarkerDetailsSheet.kt` | already formats OGN speed from `groundSpeedMps` | reuse `UnitsFormatter.speed(...)` for badge speed text | badge stays compact; not full detail-sheet layout |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| badge visibility semantics | `OgnOwnshipTargetBadgeRenderModelBuilder` (off-screen-only) | `OgnOwnshipTargetBadgeRenderModelBuilder` (selected-target-always) | same owner should stay canonical; only the visibility rule changes | render-model tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| none | existing delegate already renders the badge from authoritative target visuals | retain existing path; do not add UI-side badge logic | n/a |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeRenderModel.kt` | existing | badge visibility rule and content formatting | canonical content/policy owner | overlay should not decide business/display content | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeOverlay.kt` | existing | badge text style, halo, offset, and render application | canonical MapLibre layer owner | delegate should not own font/contrast constants | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeRenderModelTest.kt` | existing | focused coverage for visibility, text composition, and color semantics | existing test owner for badge content | no need for a new test file unless scope expands | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt` or a focused OGN delegate test | existing | lock that selected-target updates still drive the badge through the same delegate path | existing runtime boundary owner | avoid introducing a new runtime owner just for badge policy | maybe |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `OgnOwnshipTargetBadgeRenderRequest` content inputs | `OgnOwnshipTargetBadgeRenderModel.kt` | badge builder tests and overlay | internal | already sufficient to support visibility/content change | no new cross-module API needed |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| badge visibility/content policy | `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeRenderModel.kt` | badge overlay + tests | it already decides whether the badge exists and what text it shows | no |
| badge typography/contrast constants | `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeOverlay.kt` | overlay layer configuration | it already owns the MapLibre symbol layer | no |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| badge content and visibility | n/a | pure render-time formatting from current selected target snapshot |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged existing map runtime overlay path
- Primary cadence/gating sensor:
  - existing `updateTargetVisuals(...)` render cadence
- Hot-path latency budget:
  - badge formatting must remain a small pure formatting step with no new I/O or
    repository calls

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none added; badge content is a pure function of the selected target snapshot
    and current units

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| no selected target | User action / Unavailable | delegate + badge builder | badge hidden | existing clear behavior retained | existing delegate tests |
| unknown distance, altitude, or speed | Recoverable | badge builder | field shows `--` for missing value | no retry; format best available info | badge render-model tests |
| target below ownship | Recoverable | badge builder | badge keeps below-ownship color semantic | existing color policy retained | badge render-model tests |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| visibility logic leaks into UI/runtime layers | SSOT / ownership clarity | review + focused unit test | `OgnOwnshipTargetBadgeRenderModelTest.kt` |
| speed formatting duplicates details-sheet logic incorrectly | canonical formatting reuse | unit test | `OgnOwnshipTargetBadgeRenderModelTest.kt` |
| readability tuning accidentally changes selection runtime behavior | scope containment | delegate boundary test | existing OGN target-visual tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| selected target badge remains visible beside ownship | OGN-TARGET-BADGE-01 | badge disappears whenever target is on-screen | badge is visible for every selected target, on-screen or off-screen | render-model tests + manual map check | Phase 2 |
| badge content supports in-flight glanceability | OGN-TARGET-BADGE-02 | only distance and height diff are shown | distance, height diff, and speed are visible in a compact layout | render-model tests + manual map check | Phase 2 |
| badge text is easier to read | OGN-TARGET-BADGE-03 | text is small/light for cockpit use | larger text and stronger halo/contrast improve legibility without obscuring ownship | manual map check + focused overlay constants review | Phase 2 |

## 3) Data Flow (Before -> After)

Before:

`selectedOgnTarget -> MapOverlayManagerRuntimeOgnDelegate -> OgnOwnshipTargetBadgeRenderModelBuilder suppresses on-screen target -> OgnOwnshipTargetBadgeOverlay shows distance + delta only when target is off-screen`

After:

`selectedOgnTarget -> MapOverlayManagerRuntimeOgnDelegate -> OgnOwnshipTargetBadgeRenderModelBuilder returns badge for any selected target -> OgnOwnshipTargetBadgeOverlay shows distance + delta + speed with more legible text beside ownship`

## 4) Implementation Phases

### Phase 0: Scope lock

- Goal:
  - confirm the change is badge-only and does not alter target selection,
    repository state, or bottom-sheet actions
- Files to change:
  - phased IP only
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - none
- Exit criteria:
  - plan approved with badge content/visibility still owned by
    `OgnOwnshipTargetBadgeRenderModelBuilder`

### Phase 1: Always-show selected-target badge

- Goal:
  - remove off-screen-only suppression so the ownship badge appears whenever a
    selected target exists
- Files to change:
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeRenderModel.kt`
- Ownership/file split changes in this phase:
  - none; same builder remains canonical
- Tests to add/update:
  - update `OgnOwnshipTargetBadgeRenderModelTest.kt` to cover on-screen selected
    target visibility
- Exit criteria:
  - on-screen selected targets still produce a render model
  - no new state or delegate flags are introduced

Phase 1 implementation note:

- 2026-03-30 narrow seam/code pass removed the off-screen suppression from
  `OgnOwnshipTargetBadgeRenderModelBuilder`, dropped the now-unneeded
  `targetOnScreen` request field from the badge path, and updated the focused
  render-model regression to prove the badge still renders for selected targets
  that would previously have been suppressed.

### Phase 2: Add speed and readability tuning

- Goal:
  - include target speed in the badge and improve cockpit readability
- Files to change:
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeRenderModel.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnOwnshipTargetBadgeOverlay.kt`
- Ownership/file split changes in this phase:
  - none; builder owns text, overlay owns typography
- Tests to add/update:
  - extend `OgnOwnshipTargetBadgeRenderModelTest.kt` for speed formatting and
    missing-value fallback
  - add or update one focused overlay/delegate test if needed to lock the render
    path remains unchanged
- Exit criteria:
  - badge text includes distance, relative altitude, and speed
  - text size/halo/contrast are increased without changing badge placement

Phase 2 implementation note:

- 2026-03-30 narrow seam/code pass kept content formatting in
  `OgnOwnshipTargetBadgeRenderModel.kt`, added target speed from
  `groundSpeedMps`, preserved `--` fallback behavior for unknown values, and
  tuned the badge overlay typography in `OgnOwnshipTargetBadgeOverlay.kt` with
  larger text, a stronger halo, and a tighter offset that stays beside ownship.

### Phase 3: Verification and manual map QA

- Goal:
  - prove the narrow badge slice is stable and readable
- Files to change:
  - tests only if verification exposes a true gap
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - focused `feature:traffic` badge/render-model tests
- Exit criteria:
  - targeted tests pass
  - manual map check confirms the badge remains visible on-screen and is readable

## 5) Test Plan

- Unit tests:
  - `OgnOwnshipTargetBadgeRenderModelTest`
- Replay/regression tests:
  - n/a
- UI/instrumentation tests (if needed):
  - not required for the first narrow pass
- Degraded/failure-mode tests:
  - missing distance/altitude/speed -> `--`
- Boundary tests for removed bypasses:
  - existing OGN delegate target-visual tests
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / display policy | Unit tests + regression cases | `OgnOwnshipTargetBadgeRenderModelTest` |
| Ownership move / bypass removal / API boundary | Boundary lock tests | existing OGN target-visual delegate tests |
| UI interaction / lifecycle | UI or instrumentation coverage | manual map check after focused unit pass |

Required checks for merge-ready completion:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Planned narrow proof for the first implementation pass:

```bash
./gradlew :feature:traffic:testDebugUnitTest --tests "com.trust3.xcpro.map.OgnOwnshipTargetBadgeRenderModelTest"
./gradlew enforceRules
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| always-visible badge becomes visually busy when target is close to ownship | medium | keep layout compact; tune readability without increasing footprint unnecessarily | Codex |
| extra speed line makes the badge too tall | medium | use concise formatting and verify against ownship placement manually | Codex |
| readability tuning changes color semantics for above/below target | low | retain current text color rule and only tune size/halo/contrast | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: n/a
- Decision summary:
  - ownership does not change; this is a narrow badge visibility/content/style
    adjustment inside existing owners
- Why this belongs in plan notes instead of an ADR:
  - no module boundary, SSOT owner, or long-lived architecture decision changes

## Verdict

- Ready for a narrow seam/code pass.
- Recommended first implementation order:
  1. `OgnOwnshipTargetBadgeRenderModel.kt`
  2. `OgnOwnshipTargetBadgeRenderModelTest.kt`
  3. `OgnOwnshipTargetBadgeOverlay.kt`
  4. focused verification
