# CHANGE_PLAN_OGN_RELATIVE_ALTITUDE_COLOR_BANDS_2026-02-28

## Purpose

Define a phased implementation plan for OGN glider altitude-relative map rendering with dual labels:
- If target is above ownship altitude by more than `100 ft` (`delta > +100 ft`): dark green icon + height delta label above icon
- If target is below ownship altitude by more than `100 ft` (`delta < -100 ft`): dark blue icon + height delta label below icon
- If target is within `+-100 ft` (inclusive) or altitude is unavailable: black fallback icon/label behavior
- Competition ID is rendered in the opposite vertical position from the delta label
- Improve map label readability with a clearer font strategy and stronger contrast

This plan follows:
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN Relative Altitude Color + Dual Label Layout
- Owner: Map/OGN slice
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Draft (comprehensive code-pass update)

## 1) Scope

- Problem statement:
  - OGN traffic currently renders one label and static icon imagery, so pilot cannot immediately read both relative vertical position and competition identity.
- Why now:
  - Pilot requested immediate above/below awareness relative to ownship altitude with simultaneous competition ID visibility.
- In scope:
  - Relative altitude sign policy for OGN glider icons:
    - `delta > +100 ft` -> dark green icon
    - `delta < -100 ft` -> dark blue icon
    - `abs(delta) <= 100 ft` or unknown altitude -> black fallback
    - threshold boundary is inclusive black at `+100 ft` and `-100 ft`
    - internal comparison remains SI with one boundary conversion (`100 ft = 30.48 m`)
  - Dual-label layout:
    - Above-target case: delta above icon, competition ID below icon
    - Below-target case: delta below icon, competition ID above icon
    - Same-level/unknown case: deterministic fallback placement
  - Competition ID source contract:
    - primary: `identity.competitionNumber`
    - fallback: existing `displayLabel`
  - Height-difference label formatting contract (signed value, deterministic fallback string).
  - Delta label precision contract:
    - integer precision for in-flight readability and lower churn
    - explicit sign (`+` / `-`) for non-zero deltas
    - sign is derived from raw (pre-rounding) delta to preserve strict `+/-` semantics outside the black band
  - Height-difference unit contract:
    - use `UnitsPreferences.altitude` for label unit (`m` or `ft`)
    - re-render when altitude unit preference changes
  - Ownship-altitude render cadence contract:
    - avoid high-frequency map churn from raw ownship altitude stream updates
    - keep threshold-band behavior while coalescing redundant rerender work
  - Label readability upgrade (font stack, size, halo width/color, contrast).
  - Label scale/readability coupling:
    - define how label size scales with icon-size changes so larger icons remain readable
  - Runtime wiring so altitude-only changes trigger OGN overlay rerender.
  - Stale-fade parity:
    - delta and competition labels follow target stale alpha (same fade behavior as icon)
  - Tests for sign policy, placement policy, and rerender triggers.
  - Documentation sync in OGN and pipeline docs.
- Out of scope:
  - ADS-B proximity color policy.
  - OGN network/parsing/repository ingest behavior.
  - Collision-avoidance semantics.
  - Thermal/trail style policies.
- User-visible impact:
  - Pilot sees both competition ID and relative altitude delta directly on map with clearer label readability.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN target altitude (`altitudeMeters`) | `OgnTrafficRepository` | `StateFlow<List<OgnTrafficTarget>>` | UI-side mirrored target stores |
| Ownship altitude (`Double?`) | `FlightDataRepository` -> `MapScreenViewModel` derived state | `StateFlow<Double?>` for map rendering | Overlay-local independently computed ownship altitude |
| Competition label text | OGN identity/display label pipeline | target properties (`competitionNumber` / `displayLabel`) | Re-deriving identity from raw APRS in overlay |
| Altitude label units (`m`/`ft`) | Units preferences SSOT (`UnitsRepository` -> map UI state) | `UnitsPreferences.altitude` in map runtime wiring | Overlay-local independent unit selection |
| Relative color band + label placement | Map runtime display policy (UI-only) | transient feature properties | Persisting display policy in repository/domain |
| Satellite readability toggle | `MapOverlayManager` runtime state | overlay runtime setter | Independent shadow toggle in overlay/business layers |

### 2.2 Dependency Direction

`UI -> domain -> data` remains unchanged.

- Modules/files touched (planned):
  - `feature/map/.../map/MapScreenViewModel.kt`
  - `feature/map/.../map/ui/MapScreenBindings.kt`
  - `feature/map/.../map/ui/MapScreenRoot.kt`
  - `feature/map/.../map/ui/MapScreenRootEffects.kt`
  - `feature/map/.../map/MapScreenViewModelStateBuilders.kt` (if exposing ownship altitude to bindings requires helper adjustment)
  - `feature/map/.../map/MapOverlayManager.kt`
  - `feature/map/.../map/OgnTrafficOverlay.kt`
  - new map-level policy helper(s) and tests in `feature/map/.../map/`
- Boundary risk:
  - Keep all new behavior in map runtime/display policy; do not push into OGN repository/domain.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN glider icon color + dual-label placement | `OgnTrafficOverlay` static icon + one label | OGN map runtime policy helpers + overlay rendering | Show identity and relative altitude at a glance | Unit tests + runtime smoke |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A | N/A |

### 2.3 Time Base

No new elapsed-time logic is introduced.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN stale-alpha lifecycle | Monotonic (existing) | Preserve existing stale/fresh visual semantics |
| Relative altitude sign and delta label | None (instant arithmetic from two altitudes) | Display-only derivation |

Forbidden comparisons unchanged:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Existing map runtime/UI thread ownership remains unchanged.
- Primary cadence/gating:
  - Existing OGN display update mode throttle remains owner of render cadence.
- Hot-path latency budget:
  - O(1) per target policy derivation and property mapping.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules:
  - None added; sign/color/placement derive deterministically from ownship and target altitudes.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Display policy leaks into repository/domain | ARCHITECTURE MVVM/UDF/SSOT | review + unit tests | map policy tests |
| Threshold mapping regression (`> +100 ft` / `< -100 ft` / `within +-100 ft`) | CODING_RULES explicit behavior | unit test | `OgnRelativeAltitudeColorPolicyTest` |
| Opposite-position label contract regression | UI behavior contract | unit test | `OgnRelativeAltitudeLabelLayoutPolicyTest` |
| Ownship-altitude-only changes not rerendered | PIPELINE runtime wiring contract | unit/integration test | overlay manager/root-effects tests |
| Unit preference changes not reflected in delta label | SSOT + UI wiring contract | unit/integration test | bindings/root-effects + formatter tests |
| Raw ownship-altitude stream causes excessive OGN rerenders | runtime perf/cadence contract | unit test + manual profiling | overlay-effects/manager cadence tests |
| Sign derived from rounded delta text (instead of raw delta) | strict `+/-` contract (outside black band) | unit test | formatter/policy tests |
| Dual-label tap hit-test regression | runtime overlay behavior | unit/integration test + review | overlay query-layer tests |
| Satellite-mode conflict with color policy | runtime overlay behavior | unit test + manual QA | policy and overlay tests |
| Stale labels remain fully bright while icon fades | runtime visual consistency | unit/integration test | overlay feature/layer property tests |

## 3) Data Flow (Before -> After)

Before:
`OgnTrafficRepository targets -> OgnTrafficOverlay render(targets) -> static icon + single label`

After:
`OgnTrafficRepository targets + ownship altitude (FlightData SSOT) + altitude units (UnitsPreferences SSOT) -> MapScreen bindings -> MapOverlayManager -> OgnTrafficOverlay render(targets, ownshipAltitudeMeters, altitudeUnit) -> sign-based icon variant + delta/competition dual labels + readability styling`

SSOT ownership remains unchanged; all new logic is display-only.

## 4) Implementation Phases

### Phase 0 - Baseline Lock and Gap Tests

- Goal:
  - Lock baseline behavior and add tests for newly discovered risk points from code pass.
- Files to change:
  - New/updated tests under `feature/map/src/test/java/com/example/xcpro/map/`.
- Tests to add/update:
  - Threshold color policy cases (`> +100 ft`, `< -100 ft`, `within +-100 ft`, nulls).
  - Boundary tests at exactly `+100 ft` and `-100 ft` (black, inclusive).
  - Opposite-position dual-label layout policy cases.
  - Delta formatter unit cases (`m` / `ft`) and fallback strings.
  - Satellite-mode precedence policy cases.
- Exit criteria:
  - Policy tests define and validate target behavior before overlay wiring changes.

### Phase 1 - Pure Display Policy Helpers

- Goal:
  - Implement pure helpers for color, label placement, and delta text formatting.
- Files to change:
  - Add `OgnRelativeAltitudeColorPolicy.kt`
  - Add `OgnRelativeAltitudeLabelLayoutPolicy.kt`
  - Add `OgnRelativeAltitudeLabelFormatter.kt` (if needed)
  - Add `OgnRelativeAltitudeFeatureMapper.kt` (pure mapping from target + ownship + unit -> icon/labels/colors)
- Tests to add/update:
  - `OgnRelativeAltitudeColorPolicyTest.kt`
  - `OgnRelativeAltitudeLabelLayoutPolicyTest.kt`
  - formatter tests for signed output and fallback text
  - tests that sign uses raw delta before rounding (including boundary-near outcomes around `+-100 ft`)
  - feature-mapper tests that icon band and label placement always come from the same delta input
- Exit criteria:
  - Deterministic sign and placement tests pass.

### Phase 2 - SSOT-to-Overlay Wiring (Altitude Rerender Path)

- Goal:
  - Wire ownship altitude into OGN overlay runtime and ensure rerender occurs when altitude changes even if target list is unchanged.
- Files to change:
  - `MapScreenViewModel.kt` (expose UI-safe ownship altitude state for overlay wiring)
  - `MapScreenBindings.kt`
  - `MapScreenRoot.kt`
  - `MapScreenRootEffects.kt`
  - `MapOverlayManager.kt`
- Required wiring adjustments (identified gap):
  - `MapScreenOverlayEffects` LaunchedEffect key must include ownship altitude.
  - `MapScreenOverlayEffects` LaunchedEffect key must include altitude unit preference.
  - `MapScreenOverlayEffects` should key on altitude unit only (not full `UnitsPreferences`) to avoid unrelated unit-change rerenders.
  - `MapOverlayManager.updateOgnTrafficTargets(...)` signature/cache comparison must include ownship altitude to avoid early-return skipping rerenders.
  - `MapOverlayManager` should cache latest ownship altitude/unit even when rendered targets are empty (overlay disabled) so re-enable is immediately correct.
  - Add a cadence guard for ownship-altitude-driven rerenders (coalescing strategy) so `REAL_TIME` mode does not degenerate into excessive map updates.
  - `MapOverlayManager` initialization/reapply paths (`initializeTrafficOverlays`, style-change paths, satellite-toggle forced update) must pass latest ownship altitude and unit to render.
- Tests to add/update:
  - VM/binding/root-effects test proving altitude-only changes propagate to overlay update path.
  - test proving unit-only changes propagate to overlay update path.
  - cadence/coalescing tests proving repeated altitude updates do not schedule redundant renders beyond contract.
- Exit criteria:
  - Altitude changes update OGN rendering without waiting for new OGN packets.

### Phase 3 - OGN Overlay Runtime Implementation

- Goal:
  - Implement sign-colored glider icons + dual-label runtime rendering.
- Files to change:
  - `OgnTrafficOverlay.kt`
- Implementation notes (code-pass corrections):
  - Current OGN icons are bitmap style images; do not rely on `iconColor` tint expressions.
  - Create/register explicit glider icon style-image variants (e.g., dark green/dark blue/black) and reuse by property mapping.
  - Preserve existing non-glider icon mappings unchanged.
  - Add two independent text properties (`label_top`, `label_bottom`) and two label layers.
  - Add text-color properties if needed (`delta_color`, `comp_color`) so delta can be color-coded independently from competition ID readability color.
  - Do not reuse `formatBaroGpsDelta` directly for OGN delta labels; its sign behavior is tied to rounded output and can break strict `+/-` requirements.
  - Ensure both label layers are included in hit-test query layers.
  - Update layer ordering and cleanup to handle additional layers/images safely.
  - Update icon-size reapply path so both top/bottom label offsets scale with icon size.
  - Couple label text size/readability with icon-size changes (bounded scaling) so larger icons do not keep undersized text.
  - Bind `textOpacity` for both label layers to the same alpha property used by icon stale-fade.
  - Ensure style image lifecycle is complete:
    - register colored glider image variants in `ensureStyleImage`
    - remove all added variant IDs in `cleanup`
  - Font/readability:
    - add explicit clear font stack with safe fallbacks for MapLibre style glyph availability,
    - increase text size/weight/halo contrast relative to current settings.
- Satellite precedence decision:
  - Relative-altitude color policy has priority for glider icons.
  - Satellite readability mode remains for non-glider mappings and fallback behavior where applicable.
  - Vertical deadband is applied around ownship altitude: `+-100 ft` inclusive renders black.
- Tests to add/update:
  - Overlay render-policy tests for icon variant selection and top/bottom label property mapping.
- Exit criteria:
  - Pilot can simultaneously read delta + competition ID with correct above/below semantics.

### Phase 4 - Hardening, Docs, and QA Matrix

- Goal:
  - Finalize contracts, documentation, and verification.
- Files to change:
  - `docs/OGN/OGN.md`
  - `docs/ARCHITECTURE/PIPELINE.md` (if wiring signatures/flow change)
  - align `docs/OGN/OGN.md` satellite-icon refresh wording with runtime behavior (current code forces immediate OGN redraw on contrast toggle)
- Manual QA matrix (must-pass):
  - glider above ownship (`delta > +100 ft`) -> dark green
  - glider below ownship (`delta < -100 ft`) -> dark blue
  - glider within `+-100 ft` (including exactly `+100/-100`) -> black
  - unknown altitude fallback -> black
  - competition ID present/absent fallback
  - satellite overlay on/off
  - style reload + map reopen persistence
  - units switch (`m` <-> `ft`) updates delta labels without requiring new OGN packet
  - stale target label fade matches icon fade
- Exit criteria:
  - Required checks pass and docs reflect updated runtime semantics.

## 5) Test Plan

- Unit tests:
  - threshold policy (`> +100 ft`, `< -100 ft`, `within +-100 ft`, null)
  - boundary inclusivity (`+100 ft`, `-100 ft` -> black)
  - label placement policy (delta and competition opposite positions)
  - label formatter output and fallback (`m`/`ft` unit coverage)
  - icon variant resolution policy (glider vs non-glider; satellite precedence)
- Replay/regression tests:
  - deterministic replay snapshots produce deterministic color/label outputs
- UI/instrumentation tests (if needed):
  - visual readability verification on primary map styles
- Degraded/failure-mode tests:
  - missing ownship altitude -> black fallback
  - missing target altitude -> black fallback
- Wiring tests:
  - altitude-only change triggers overlay rerender path
  - unit-only change triggers overlay rerender path

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

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Altitude-only updates skipped due existing equality/LaunchedEffect keys | Stale wrong colors/labels | Include ownship altitude in effect keys and manager cache key | Map/OGN slice |
| Unit preference changes not reflected in labels | Incorrect delta values/labels | Include units in effect keys and manager cache key | Map/OGN slice |
| Bitmap icon tinting implemented with unsupported expression path | Colors fail silently | Use explicit pre-tinted style image variants | Map/OGN slice |
| Dual labels hurt tap selection | Marker details open reliability drops | Include both label layers in `queryRenderedFeatures` | Map/OGN slice |
| Font stack unavailable in some styles | Label readability inconsistent | Define robust fallback stack and manual QA across styles | Map/OGN slice |
| Satellite contrast mode conflicts with requested color rule | User expectation mismatch | Explicit precedence rule + tests + docs | Map/OGN slice |
| Extra style images/layers leak on style changes | Memory/style clutter | Explicit cleanup list and style-reload coverage | Map/OGN slice |
| Ownship altitude flow drives high-frequency rerenders (especially `REAL_TIME`) | UI jank / CPU/GPU overhead | Add ownship-driven render coalescing contract and verify with cadence tests + profiling | Map/OGN slice |
| Strict sign displayed from rounded value instead of raw delta | Wrong `+/-` text near band edges | Compute sign from raw delta; round magnitude only; add boundary-near tests | Map/OGN slice |
| Black/green/blue band flip near `+-100 ft` threshold under noisy altitude | Potential visual jitter around threshold boundary | Keep requested `+-100 ft` rule; document boundary behavior and verify readability in QA | Map/OGN slice |
| Unknown aircraft type can hide glider color semantics | Some gliders may not receive green/blue style if type unresolved | Keep glider-only policy for this phase; record false-negative risk and evaluate follow-up expansion if needed | Map/OGN slice |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Timebase handling remains explicit and unchanged.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry).

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 3 overlay dual-label + icon-variant rendering changes.
  - Phase 2 altitude-to-overlay wiring path.
  - Phase 1 policy helper additions.
- Recovery steps if regression is detected:
  1. Revert Phase 3 overlay runtime changes.
  2. Keep or revert Phase 2 wiring depending on blast radius.
  3. Restore previous static OGN single-label icon rendering.

## 9) Comprehensive Code Pass Audit (20x)

Iteration 1:
- Area: `OgnTrafficOverlay` icon rendering path.
- Finding: OGN uses bitmap style images; color expressions alone are insufficient.
- Plan action: use explicit pre-tinted glider style-image variants.

Iteration 2:
- Area: Compose overlay effects wiring.
- Finding: altitude-only changes are not currently part of OGN render-effect keys.
- Plan action: include ownship altitude in `MapScreenOverlayEffects` render trigger path.

Iteration 3:
- Area: manager-level render cache/gating.
- Finding: `MapOverlayManager.updateOgnTrafficTargets` early-return can suppress non-target-input rerenders.
- Plan action: include ownship altitude and altitude unit in manager render input signature.

Iteration 4:
- Area: units and formatting contract.
- Finding: plan previously lacked explicit unit source for delta labels.
- Plan action: wire `UnitsPreferences.altitude`; add unit-only rerender tests and integer signed formatting contract.

Iteration 5:
- Area: stale visual consistency.
- Finding: existing OGN label layer does not bind stale alpha opacity.
- Plan action: apply icon stale alpha to delta and competition label layers.

Iteration 6:
- Area: dual-label interaction behavior.
- Finding: marker tap query currently includes only one label layer.
- Plan action: include both new label layers in `queryRenderedFeatures`.

Iteration 7:
- Area: style lifecycle and cleanup.
- Finding: new icon variants/layers increase style-resource leak risk on style changes.
- Plan action: explicit `ensureStyleImage`/`cleanup` coverage for all added image IDs and label layers.

Iteration 8:
- Area: inter-overlay anchor dependencies.
- Finding: forecast/satellite/weather overlays anchor below `ogn-traffic-icon-layer`.
- Plan action: preserve `ogn-traffic-icon-layer` ID and verify new label layers remain above icon without breaking anchor assumptions.

Iteration 9:
- Area: identity and label semantics.
- Finding: competition ID may be absent; `displayLabel` already resolves comp/reg/fallback in repository.
- Plan action: use competition number primary and `displayLabel` fallback; add explicit empty/fallback rendering tests.

Iteration 10:
- Area: threshold-band behavior and type coverage.
- Finding: black/green/blue state can flicker near the `+-100 ft` boundary; unknown type targets may not be treated as gliders.
- Plan action: keep `+-100 ft` rule (per requirement), document boundary jitter risk, and keep glider-only scope with follow-up risk note for unknown-type false negatives.

Iteration 11:
- Area: ownship altitude source stream cadence (`MapScreenViewModelStateBuilders` -> overlay effects).
- Finding: wiring raw ownship altitude directly into OGN effect keys can trigger frequent rerender requests, especially in `REAL_TIME` mode.
- Plan action: add explicit ownship-driven render coalescing/cadence contract and tests so updates remain responsive without render storms.

Iteration 12:
- Area: signed delta text formatting.
- Finding: existing helper `formatBaroGpsDelta` derives sign from rounded value, which can violate strict `+/-` semantics near the `+-100 ft` boundary if reused.
- Plan action: define OGN formatter to derive sign from raw delta first, then round magnitude for display; add boundary-near sign tests.

Iteration 13:
- Area: unit-change rerender trigger scope.
- Finding: using full `UnitsPreferences` as a key would trigger OGN rerenders for unrelated unit changes (speed/distance/pressure/temperature).
- Plan action: key and cache only `UnitsPreferences.altitude` for OGN delta labels.

Iteration 14:
- Area: overlay-disabled state caching.
- Finding: when OGN overlay is disabled, updates pass `emptyList`; manager still needs latest altitude/unit cached for immediate correctness on re-enable.
- Plan action: include altitude/unit in manager render-input cache even when target list is empty.

Iteration 15:
- Area: text readability under icon-size scaling.
- Finding: current OGN label text size is fixed while icon size is user-configurable; larger icons can leave labels comparatively too small.
- Plan action: define bounded label-size scaling tied to icon size, plus QA at min/default/max icon sizes.

Iteration 16:
- Area: testability seam for overlay property mapping.
- Finding: OGN traffic overlay currently mixes MapLibre plumbing with feature-property derivation, limiting unit-test coverage.
- Plan action: add pure feature-mapper helper for icon/label/color/alpha properties and unit-test it independently.

Iteration 17:
- Area: icon/label consistency at threshold-edge deltas.
- Finding: if color-band policy and label formatter use different delta inputs/rounding, icon color and displayed sign can diverge.
- Plan action: enforce single-delta-source contract in feature mapper and add consistency tests.

Iteration 18:
- Area: style/image lifecycle completeness for new variants.
- Finding: adding dark green/dark blue/black glider variants increases risk of partial cleanup or duplicate registrations across style reloads.
- Plan action: enumerate all variant image IDs in `ensureStyleImage` and `cleanup`, then validate with style-change smoke tests.

Iteration 19:
- Area: documentation drift check.
- Finding: `docs/OGN/OGN.md` currently states satellite contrast icon updates are lazy, but runtime now forces immediate OGN refresh on contrast toggle.
- Plan action: explicitly sync OGN docs with current runtime semantics in Phase 4.

Iteration 20:
- Area: font stack portability across remote map styles.
- Finding: map styles are remote MapTiler styles; unavailable font names can degrade readability unexpectedly.
- Plan action: use a robust fallback font stack and include multi-style readability QA in acceptance matrix.
