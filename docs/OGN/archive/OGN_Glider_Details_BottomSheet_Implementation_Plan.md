# OGN Glider Details Bottom Sheet Implementation Plan (Template-Compliant)

## 0) Metadata

- Title: OGN map-marker tap details sheet (ADS-B style)
- Owner: XCPro Team
- Date: 2026-02-17
- Issue/PR: TBD
- Status: Draft

## 1) Scope

- Problem statement:
  OGN glider icons are rendered on map, but tapping an OGN glider does not open a details surface. Users cannot inspect key OGN values (altitude, speed, climb, identity fields) from map interaction.
- Why now:
  ADS-B already provides this interaction pattern, and OGN users need the same in-map inspection workflow.
- In scope:
  - Tap detection for OGN glider markers.
  - Selection state for one OGN target.
  - Half-open bottom sheet using the same Material style/pattern as ADS-B details.
  - Display all currently available OGN target fields (including identity enrichment fields when present).
  - Clear selection when selected target disappears or OGN overlay is turned off.
  - Clear OGN selection when streaming/map visibility state removes OGN targets.
  - Enforce single active traffic-details sheet at a time (OGN or ADS-B, never both).
  - Preserve existing ADS-B empty-map-tap behavior (no forced dismiss on empty tap).
  - Documentation updates for new behavior.
- Out of scope:
  - OGN network protocol changes.
  - OGN parser schema expansion beyond already-parsed fields.
  - OGN uplink/transmit behavior.
  - New distance/bearing geospatial calculations outside existing OGN data flow.
  - Collision-avoidance logic.
- User-visible impact:
  - Tapping an OGN glider icon opens a bottom sheet showing live OGN glider details (height/speed/climb and related fields).

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN traffic targets | `OgnTrafficRepository` | `StateFlow<List<OgnTrafficTarget>>` | UI-owned target mirrors |
| OGN selected target id | `MapScreenViewModel` | `StateFlow<String?>` | composable-local selected-id authority |
| OGN selected target details | ViewModel-derived state from selected id + OGN targets | `StateFlow<OgnSelectedTargetDetails?>` | recomputing business mapping in composables |
| Active traffic details surface (OGN vs ADS-B) | `MapScreenViewModel` | intent handlers clear opposite selection | parallel traffic-sheet visibility states |
| OGN overlay hit-test runtime state | `OgnTrafficOverlay` / `MapOverlayManager` runtime only | imperative `findOgnTargetAt` | persisting runtime hit-test cache as SSOT |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - OGN data model/ui files.
  - Map runtime overlay tap-routing files.
  - Map ViewModel + scaffold bindings.
- Boundary risks:
  - Putting OGN field mapping/formatting logic directly in composables.
  - Adding direct repository calls from UI.
  - Introducing new geospatial math in ViewModel/UI paths.
  - Changing existing tap behavior in edit modes (AAT/UI edit mode).

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN tap hit-test result propagation | Not implemented | `MapOverlayStack` via `MapOverlayManager.findOgnTargetAt` callback -> ViewModel | Introduce marker-selection intent flow | ViewModel tests + manual tap QA |
| OGN selected-target details mapping | Not implemented | ViewModel/use-case derived state | Keep UI render-only and stable | Unit tests for selection mapping |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| OGN marker taps | No path (tap ignored) | `MapOverlayStack` intent callback to `MapScreenViewModel.onOgnTargetSelected` | Phase 2-3 |
| OGN details rendering | N/A | `selectedOgnTarget` state consumed by `MapScreenContent` | Phase 3 |
| Dual traffic-sheet visibility | OGN and ADS-B could both remain selected | Selection handlers enforce mutual exclusion | Phase 1 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN target last-seen/timestamps from repository | Monotonic (existing OGN repository behavior) | Existing target freshness semantics |
| Bottom-sheet display formatting | UI-only rendering (no new timebase policy change) | Feature is read-only presentation |
| Replay behavior | Unchanged | OGN overlay remains non-authoritative for replay/fusion |

Explicitly forbidden:
- Monotonic vs wall arithmetic in UI/business logic.
- New direct `System.currentTimeMillis` in domain/fusion paths.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Main: map tap handling and bottom-sheet UI.
  - Existing repository threads unchanged.
- Primary cadence/gating sensor:
  - OGN updates continue GPS-centered via existing traffic coordinator.
- Hot-path latency budget:
  - Marker tap -> details sheet visible target <= 100 ms typical.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (no replay pipeline changes).
- Randomness used: No.
- Replay/live divergence rules:
  - OGN details sheet is UI-only and must not alter flight/task/replay state.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| UI starts doing OGN business mapping | CODING_RULES UI/VM boundaries | Review + unit tests | `MapScreenViewModel*`, `OgnMarkerDetailsSheet.kt` |
| Selected id not cleared when target removed | SSOT correctness | Unit test | `MapScreenViewModelTest.kt` |
| OGN hit-test returns wrong target | Runtime correctness | Manual QA + focused helper test | `OgnTrafficOverlay.kt`, `MapOverlayManager.kt` |
| Stale OGN hit after overlay disable | UX/state correctness | Review + manual QA | `MapOverlayStack.kt`, `MapScreenContent.kt` |
| Tap priority conflict with ADS-B/wind | UX correctness | Review + manual QA | `MapOverlayStack.kt` |
| OGN/ADS-B overlap arbitration regression | UX correctness | Layer-order aware arbitration + manual QA | `MapOverlayStack.kt`, `MapOverlayManager.kt` |
| Edit-mode tap conflict (AAT/UI edit) | UX correctness | Manual QA + branch guards | `MapOverlayStack.kt` |
| OGN + ADS-B details sheets show simultaneously | UX/state correctness | Unit test + review | `MapScreenViewModelTest.kt`, `MapScreenContent.kt` |
| Line-budget regression in guarded map files | enforceRules maintainability guard | Review + preflight checks | `MapScreenViewModel.kt`, `MapScreenScaffoldInputs.kt` |
| Raw payload text harms UI stability/readability | UI robustness | Sanitization + max-length cap + helper tests | `OgnMarkerDetailsSheet.kt`, formatter helper tests |
| Traffic tap behavior breaks after map style reload | Runtime regression | Manual QA after style change | `MapOverlayManager.kt`, `OgnTrafficOverlay.kt` |
| Unexpected sheet dismissal behavior drift | UX parity regression | Keep empty-tap behavior aligned with ADS-B | `MapOverlayStack.kt`, `MapScreenContent.kt` |
| Docs drift | Documentation sync | Review gate | `docs/OGN/OGN.md`, `docs/ARCHITECTURE/PIPELINE.md` |

### 2.7 UX Contract (Deep-Pass Additions)

- Tap resolution must be deterministic and layer-order aware:
  - When both OGN and ADS-B hits exist at same tap point, selected target follows topmost rendered layer.
  - Forecast wind-arrow callout remains fallback when no traffic marker was hit.
- Traffic details sheets are mutually exclusive:
  - selecting OGN clears ADS-B selection.
  - selecting ADS-B clears OGN selection.
- OGN sheet must preserve ADS-B interaction style:
  - `ModalBottomSheet` with partial expansion enabled (`skipPartiallyExpanded = false`).
  - On first show, prefer partial state; if partial is unavailable on device/window size, allow expanded fallback.
  - details content scrolls when long, instead of forcing unreadable clipped rows.
  - long raw payload fields use bounded display (truncate/ellipsis or dedicated wrapped section).
- Empty map tap parity:
  - Empty map taps should not forcibly dismiss traffic details sheet unless this is already ADS-B behavior.

### 2.8 Maintainability Constraints (Deep-Pass Additions)

- Keep `MapScreenViewModel.kt` and `MapScreenScaffoldInputs.kt` under current enforceRules line budgets.
- Prefer helper extraction over inline growth:
  - selection mapping helpers in `MapScreenViewModelStateBuilders.kt` / `MapScreenViewModelMappers.kt`
  - sheet-host composable wiring in `MapScreenContentOverlays.kt`
  - OGN details text sanitization/formatting in dedicated helper (`OgnDetailsFormatter`-style utility)
- Keep tap-routing changes minimal in `MapOverlayStack.kt` (single deterministic branch extension).

## 3) OGN Field Inventory For Bottom Sheet

Display target uses all currently available OGN fields:

- From `OgnTrafficTarget`:
  - `id`
  - `displayLabel`
  - `callsign`
  - `destination`
  - `deviceIdHex`
  - `latitude`, `longitude`
  - `altitudeMeters`
  - `groundSpeedMps`
  - `verticalSpeedMps`
  - `trackDegrees`
  - `signalDb`
  - `rawComment`
  - `rawLine` (raw payload section)
  - `timestampMillis`, `lastSeenMillis` (if surfaced)
- From `OgnTrafficIdentity` (DDB enrichment):
  - `registration`
  - `competitionNumber`
  - `aircraftModel`
  - `aircraftTypeCode`
  - `tracked`
  - `identified`

Formatting rules:
- Reuse `UnitsFormatter` for altitude and speed.
- Reuse vertical-rate formatting semantics used by ADS-B details.
- Show `--` for missing fields.
- Preserve existing warning copy: informational only, not for collision avoidance.

## 4) Data Flow (Before -> After)

Before:

`Map tap -> ADS-B hit test or wind-arrow hit test -> no OGN selection path`

After:

`Map tap -> OGN hit test -> onOgnTargetSelected(id) -> ViewModel selectedOgnTarget state -> OgnMarkerDetailsSheet`

Secondary unchanged flow:

`OGN stream -> OgnTrafficRepository (SSOT) -> OgnTrafficUseCase -> MapScreenViewModel.ognTargets -> map overlay render`

## 5) Implementation Phases

### Phase 0: Baseline Lock

- Goal:
  Confirm current ADS-B sheet behavior and OGN tap non-behavior baseline.
- Files:
  - no production changes.
- Tests:
  - none.
- Exit criteria:
  - baseline interaction documented in PR notes.

### Phase 1: OGN Selection Model + UI Contract

- Goal:
  Add selected OGN target state path in ViewModel/scaffold contracts.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelMappers.kt` (if mapping helpers added)
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindings.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentOverlays.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnSelectedTargetDetails.kt` (new, if separate model used)
- Tests:
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`
    - select OGN target by id
    - clear when target disappears
    - clear when OGN overlay toggles off
    - clear when OGN stream target list becomes empty due map visibility/streaming gate
    - selecting OGN clears ADS-B selection
    - selecting ADS-B clears OGN selection
    - dismiss callback clears selected OGN target id/details state
  - helper tests for OGN details text sanitization/truncation (new test file near OGN UI package)
- Exit criteria:
  - selection state is lifecycle-safe and sheet wiring compiles end-to-end.
  - `MapScreenViewModel.kt` remains within enforced line budget (`<= 350`).
  - `MapScreenScaffoldInputs.kt` remains within enforced line budget (`<= 320`).

### Phase 2: OGN Overlay Hit-Test Support

- Goal:
  Detect tapped OGN glider marker and return selected target id.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
- Implementation notes:
  - Add stable target-id property into OGN GeoJSON features.
  - Add `findTargetAt(tap: LatLng): String?` to OGN overlay.
  - Add `findOgnTargetAt` in overlay manager.
  - In tap routing, resolve OGN/ADS-B overlap by rendered layer order rather than fixed type precedence.
  - Query both OGN icon and label layers for tap hit-test parity with ADS-B behavior.
  - Guard OGN hit-test by overlay-enabled state at tap callsite to avoid stale selection during disable transitions.
  - Preserve existing edit-mode semantics: traffic selection must not hijack tap flows intended for AAT/UI edit interactions.
  - Re-validate hit-test behavior after map style changes recreate overlay layers/sources.
- Tests:
  - helper-level unit tests where feasible.
  - manual map tap validation in dense traffic.
- Exit criteria:
  - tapping visible OGN marker triggers OGN selection callback reliably.

### Phase 3: OGN Bottom Sheet UI

- Goal:
  Add ADS-B-style OGN details sheet with half-open modal behavior.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnMarkerDetailsSheet.kt` (new)
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentOverlays.kt` (preferred host extraction)
- Implementation notes:
  - Use `ModalBottomSheet` with the same styling pattern as `AdsbMarkerDetailsSheet`.
  - Keep typography/row spacing/section structure visually aligned with ADS-B sheet.
  - Show OGN flight state first (height, speed, climb, track), then identity, then raw payload section.
  - Make long raw fields safe for layout (bounded text/scrollable section).
  - Sanitize control characters and cap raw text length before rendering in sheet sections.
  - Do not add new distance/bearing geospatial math in UI/ViewModel for this change scope.
- Tests:
  - Compose UI smoke test for visible rows and dismiss action (recommended if test harness already available).
- Exit criteria:
  - sheet opens from OGN tap, dismisses cleanly, and renders all mapped fields.
  - sheet initially presents partially expanded when supported.

### Phase 4: Docs + Verification

- Goal:
  Sync documentation and run required checks.
- Files to change:
  - `docs/OGN/OGN.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/OGN/OGN_PROTOCOL_NOTES.md` (if field mapping notes are added)
- Required checks:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Optional when relevant:
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
- Exit criteria:
  - docs reflect implemented behavior and verification passes.

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| OGN and ADS-B markers overlap; wrong sheet opens | Medium | prioritize tap-hit resolution by visual layer order, manual overlap QA | XCPro Team |
| OGN and ADS-B sheets both visible | Medium | enforce mutual selection clearing in ViewModel/coordinator | XCPro Team |
| Large raw payload text hurts readability | Low | keep raw payload in dedicated section and allow truncation/scroll | XCPro Team |
| Control characters in raw payload reduce legibility | Low | sanitize non-printable characters before render | XCPro Team |
| Selection state lingers after target eviction | Medium | clear selected id when target list no longer contains selected item | XCPro Team |
| Disable transition allows stale OGN tap selection | Medium | gate hit-test by overlay-enabled state and clear selection on disable | XCPro Team |
| AAT/UI edit tap flow regression | Medium | preserve edit-mode routing precedence and verify manually | XCPro Team |
| ViewModel size/regression risk | Medium | keep mapping helpers in state-builder/helper files, not inline in composables | XCPro Team |
| `MapScreenScaffoldInputs` exceeds enforceRules line budget | Medium | keep traffic-sheet wiring minimal; extract helper if needed | XCPro Team |
| Style reload breaks OGN hit-test | Medium | validate post-style-reload hit testing in QA checklist | XCPro Team |
| Empty-map tap behavior drift from ADS-B | Low | keep dismissal behavior parity with ADS-B interactions | XCPro Team |
| False expectation that OGN data is collision-grade | High | explicit warning text retained in sheet | XCPro Team |

## 7) Acceptance Gates

- OGN glider tap opens bottom sheet in ADS-B style.
- Sheet contains altitude, speed, climb rate, and other available OGN identity/radio/raw fields.
- At most one traffic details sheet is visible at any time.
- Selection clears correctly when target disappears or OGN overlay is disabled.
- OGN tap selection is ignored when OGN overlay is disabled.
- ADS-B tap + ADS-B details behavior remains unchanged.
- AAT edit mode and UI edit mode tap behavior remain unchanged.
- Empty-map tap dismissal behavior remains aligned with ADS-B behavior.
- OGN tap selection still works after map style reload.
- Bottom sheet opens partially expanded when supported by device/window constraints.
- No architecture rule violations (`UI -> VM -> use-case -> repository` maintained).
- Replay and sensor pipelines remain behaviorally unchanged.
- Required verification commands pass.

## 8) Manual QA Matrix (Deep-Pass Additions)

1. Enable OGN, tap a glider icon:
   - OGN details sheet appears in partial state (or expanded fallback if partial unsupported).
2. With OGN sheet open, tap ADS-B target:
   - OGN sheet closes, ADS-B sheet opens.
3. With ADS-B sheet open, tap OGN target:
   - ADS-B sheet closes, OGN sheet opens.
4. Disable OGN overlay while OGN sheet is open:
   - OGN sheet dismisses and cannot be re-opened by stale taps.
5. Move until selected OGN target ages out/disappears:
   - OGN sheet dismisses automatically.
6. Long raw payload line:
   - UI remains readable (no overflow/crash; text is bounded/scrollable).
7. OGN and ADS-B markers overlapping same tap point:
   - selected traffic type follows topmost rendered layer.
8. AAT edit mode and UI edit mode:
   - traffic detail sheets do not interfere with edit gestures/taps.
9. Map style change while OGN overlay enabled:
   - OGN marker tap-to-sheet still works after style reload.
10. Empty map tap with OGN sheet open:
   - dismissal behavior matches ADS-B sheet behavior.

## 9) Rollback Plan

- Revert independently:
  - OGN hit-test additions in overlay runtime.
  - OGN selection state and callbacks.
  - OGN details sheet composable.
- Recovery steps:
  1. Disable OGN tap selection callback in `MapOverlayStack`.
  2. Keep OGN overlay rendering unchanged.
  3. Re-run required checks and ship as non-interactive OGN overlay if needed.
