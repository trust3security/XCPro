# OGN Features Plan: Render OGN Targets With `ic_adsb_glider.png`

## 0) Metadata

- Title: OGN Glider Icon Rendering Plan
- Owner: XCPro Team
- Date: 2026-02-11
- Status: Proposed for release hardening
- Scope type: UI/runtime overlay feature

---

## 0A) Implementation Status (2026-02-11)

| Phase | Status | Notes / Evidence |
|---|---|---|
| Phase 1: Runtime Overlay Icon Migration | DONE | `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt` now uses SymbolLayer + `R.drawable.ic_adsb_glider` |
| Phase 2: Runtime Wiring and Reapply Paths | DONE | OGN icon-size + targets are reapplied via `MapOverlayManager`, `MapScreenRoot`, `MapScreenScaffoldInputs`, `MapInitializer` |
| Phase 3: Settings + Contracts | DONE | OGN icon-size settings path is implemented end-to-end (repo/use-case/viewmodel/screen/nav) |
| Phase 4: Documentation Sync | DONE | OGN icon-size path is documented in `docs/ARCHITECTURE/PIPELINE.md` |
| Release Hardening Manual QA | IN PROGRESS | Manual map checks (visual behavior at min/max, style-switch UX) remain |

---

## 0B) Policy Update (2026-02-22)

- OGN and ADS-b icon-size policy is now aligned.
- Effective range/default for both:
  - min: `124 px`
  - default: `124 px`
  - max: `248 px`

---

## 1) Objective

Render OGN glider traffic targets on the map using `ic_adsb_glider.png` (instead of dot/circle markers), while preserving current OGN behavior:

- same target stream and filtering
- same stale/live visual semantics
- same viewport culling and target cap
- same style-change and lifecycle resilience
- no architecture boundary regressions (MVVM + UDF + SSOT)

---

## 2) Product Outcome

When OGN traffic is enabled:

1. OGN targets appear as glider icons from `R.drawable.ic_adsb_glider`.
2. Icons rotate by track when available.
3. Label behavior remains stable and readable.
4. Icon size settings continue to apply live and after style reloads.

---

## 3) Current End-to-End OGN Display Path (Baseline)

1. UI toggle:
   - `MapActionButtons` toggles OGN overlay state.
2. ViewModel:
   - `MapScreenTrafficCoordinator` gates streaming with `allowSensorStart && mapVisible && ognOverlayEnabled`.
   - ownship GPS (`mapLocation`) drives subscription center updates.
3. Domain/use-case:
   - `OgnTrafficUseCase` exposes targets/snapshot and preference flows.
4. Data/repository:
   - `OgnTrafficRepositoryImpl` parses APRS lines, enriches labels via DDB, and publishes sorted targets.
5. Runtime map:
   - `MapScreenRoot` + `MapScreenScaffoldInputs` push targets and icon-size state to `MapOverlayManager`.
   - `MapOverlayManager` delegates rendering to `OgnTrafficOverlay`.
6. Overlay:
   - `OgnTrafficOverlay` owns MapLibre source/layers/images and renders OGN features.

This plan changes only runtime visual representation for OGN targets and keeps pipeline ownership intact.

---

## 4) Non-Goals

- No APRS protocol changes.
- No OGN backend/network behavior changes.
- No new marker detail/tap flow for OGN.
- No ADS-B behavior changes.

---

## 5) Architecture Guardrails

- Preserve `UI -> ViewModel -> UseCase -> Repository`.
- No repository access from composables.
- Keep map runtime objects in UI runtime layer only (`MapOverlayManager`, `OgnTrafficOverlay`).
- Keep replay determinism unaffected (OGN remains overlay-only).
- Do not introduce global mutable state.

---

## 6) Technical Design

### 6.1 Rendering Strategy

- Use MapLibre `SymbolLayer` for OGN icons.
- Register style image id: `ogn_icon_glider`.
- Back icon bitmap with `R.drawable.ic_adsb_glider`.
- Rotate with `track_deg` property when present, fallback to `0`.
- Preserve label layer above icon layer.

### 6.2 Layer/Source Ownership

- Source: `ogn-traffic-source`
- Icon layer: `ogn-traffic-icon-layer`
- Label layer: `ogn-traffic-label-layer`
- OGN overlay cleanup must remove only OGN-owned style image id (`ogn_icon_glider`), not ADS-B image ids.

### 6.3 Visual Semantics to Preserve

- Live alpha: `0.90`
- Stale alpha: `0.45`
- Stale visual threshold: `60_000 ms`
- Render cap: `500` targets
- Viewport culling: keep `OgnSubscriptionPolicy.isInViewport(...)`

### 6.4 Icon Sizing

- Keep OGN icon sizing contract:
  - min: `124 px`
  - default: `124 px`
  - max: `248 px`
- Convert px value to MapLibre scale:
  - `iconScale = configuredPx / baseBitmapPx`
- Scale label Y-offset with icon scale to keep text spacing stable.

---

## 7) File-Level Implementation Plan

## Phase 1: Runtime Overlay Icon Migration

- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
  - migrate from circle marker rendering to icon-based `SymbolLayer`
  - add style image registration for `ic_adsb_glider`
  - keep stale alpha, viewport culling, and target cap logic
  - keep cleanup idempotent and overlay-scoped

Exit criteria:
- OGN markers visually render as glider icons, not circles.

## Phase 2: Runtime Wiring and Reapply Paths

- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - ensure OGN overlay creation path uses icon-capable overlay
  - preserve and reapply latest OGN targets across style changes
  - preserve and reapply OGN icon size on map-ready and style recreation
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - push OGN targets and icon-size settings into overlay manager
- `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
  - initialize OGN overlay with correct runtime map/context on first style load

Exit criteria:
- style switch and map recreation keep OGN glider icons and configured size.

## Phase 3: Settings + Contracts (if not already present)

- `feature/map/src/main/java/com/example/xcpro/ogn/OgnIconSizing.kt`
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsScreen.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt`

Exit criteria:
- OGN icon size is user-configurable and persisted.

## Phase 4: Documentation Sync

- `docs/ARCHITECTURE/PIPELINE.md`
  - include OGN icon-size and runtime overlay wiring path

Exit criteria:
- pipeline docs match production wiring.

---

## 8) Verification Plan

## Automated

Run:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Targeted tests:

- `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
  - default, clamp, persist for OGN icon size
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
  - OGN icon-size flow exposure and persisted value on startup

## Manual

1. Enable OGN and verify icons are glider image, not circles.
2. Verify icon rotation follows track where present.
3. Move OGN slider to 124 and 248, verify live resizing.
4. Switch map style and verify icon + size persist.
5. Pan map and confirm viewport-culling behavior remains sane.
6. Confirm stale targets fade and disappear on expected timings.

---

## 9) Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| OGN icon image id collides with ADS-B assets | High | Use OGN-specific image id (`ogn_icon_glider`) and overlay-owned cleanup only |
| Style reload resets icon sizing | High | Cache size in `MapOverlayManager` and reapply in map-ready + style-change paths |
| Label overlap at max size | Medium | Scale label offset with icon size and verify min/max manually |
| Runtime jank from frequent slider changes | Medium | Clamp and no-op when unchanged; apply only on value changes |
| Boundary regression | High | Keep strict MVVM/UDF path; no direct repository calls from UI |

---

## 10) Acceptance Criteria

1. OGN targets render with `ic_adsb_glider.png`.
2. OGN overlay behavior (culling, stale alpha, target cap) remains unchanged functionally.
3. OGN icon size preference remains persisted and applied live.
4. Style changes and map re-inits keep correct icon and sizing.
5. Required build/test gates pass.

---

## 11) Rollback Plan

If regression appears in production:

1. Revert `OgnTrafficOverlay` to previous marker rendering mode.
2. Keep persisted OGN size preference key as inert if needed.
3. Ship hotfix preserving OGN stream stability before reattempting icon migration.

---

## 12) Release Readiness Checklist

- [ ] Code merged with architecture review
- [ ] Required Gradle checks green
- [ ] Manual QA pass for icon visuals and style reload
- [ ] Pipeline doc updated
- [ ] No new deviation entry required in `KNOWN_DEVIATIONS.md`

---

## 13) Future OGN Changes and Bug-Fix Playbook

Use this section as the default execution contract for future OGN display work.

### 13.1 Scope Guardrails

- Keep OGN repository/network parsing independent from map rendering changes.
- Keep OGN map rendering isolated to `OgnTrafficOverlay` and `MapOverlayManager`.
- Treat map style reload behavior as a first-class requirement, not a polish item.

### 13.2 Common Failure Modes

1. Label visible but icon missing:
   - Check layer ordering around `BlueLocationOverlay.LAYER_ID`.
   - Re-validate icon style-image registration (`ogn_icon_glider`).
2. Icon size slider appears ignored:
   - Confirm `OgnTrafficPreferencesRepository` clamp range and persisted value.
   - Confirm `MapScreenRoot`/`MapScreenScaffoldInputs` push size updates to `MapOverlayManager`.
3. Style switch resets icon size or icon image:
   - Confirm style-change path recreates OGN overlay and reapplies cached `ognIconSizePx`.
4. Dense traffic frame drops:
   - Validate target cap remains `500`.
   - Profile render cadence and avoid unnecessary re-inits per frame.

### 13.3 Debug Checklist (Fast Path)

1. Capture OGN debug panel state (`Targets`, `connection`, `center`, `radius`).
2. Confirm one known target renders icon + label at current zoom.
3. Move icon slider to min and max; verify immediate visual change.
4. Switch map style; verify icon persists with same size.
5. Repeat with target near ownship to catch layer-order regressions.

### 13.4 Recommended Regression Tests

- Unit:
  - OGN preference clamp/read/write (`OgnTrafficPreferencesRepositoryTest`).
  - ViewModel exposure of persisted OGN size (`MapScreenViewModelTest`).
- Manual:
  - Screenshot evidence at `124 px`, `186 px`, and `248 px`.
  - One style-switch clip proving reapply behavior.

### 13.5 Safe Extension Strategy

If adding future OGN features (tap details, trail hints, class-based icons):

1. Add feature behind a dedicated runtime flag.
2. Keep parsing/repository contracts unchanged unless explicitly scoped.
3. Preserve existing stale alpha and viewport-culling semantics by default.
4. Provide a one-commit rollback path for rendering-only changes.
