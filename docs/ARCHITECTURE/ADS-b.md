# ADS-b and OGN Icon Size Parity + Startup Apply Fix Plan

## 0) Metadata

- Title: Align ADS-b icon size policy with OGN and fix startup icon-size apply regression
- Owner: XCPro Team
- Date: 2026-02-22
- Issue/PR: TBD
- Status: Implemented (code + tests + pipeline docs updated on 2026-02-22)

## 1) Problem and Scope

- Problem statement:
  - Before this change, ADS-b icon size policy was `50..124` with default `56`.
  - Before this change, OGN icon size policy was `124..512` with default `124`.
  - Requested product policy is parity at `124..248` for both OGN and ADS-b in `General` settings.
  - On cold start, persisted ADS-b (and likely OGN) icon size could be skipped; runtime could fall back to default in some startup orderings.
- In scope:
  - Normalize icon-size policy for ADS-b to match OGN target policy.
  - Normalize OGN max from `512` to `248` for parity.
  - Fix startup sequencing so persisted icon size is applied deterministically on first render.
  - Add regression coverage and update architecture docs.
- Out of scope:
  - ADS-b or OGN network/protocol logic.
  - Traffic filtering policy.
  - Marker visual design beyond size-policy and startup application correctness.

## 2) Re-pass Findings (What Was Missed)

1. OGN range mismatch versus requested policy:
   - `feature/map/src/main/java/com/example/xcpro/ogn/OgnIconSizing.kt` was `124..512`, not `124..248`.

2. ADS-b policy is still legacy:
   - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbIconSizing.kt` was `50..124` default `56`.

3. Startup callback uses stale binding values:
   - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt` registers `getMapAsync` inside `AndroidView(factory=...)`.
   - `onMapReady(map)` is invoked twice (`before` and `after` `initializeMap`), but callback captures factory-time composition values.

4. Overlay creation ownership is split and bypasses cached size:
   - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` directly creates `OgnTrafficOverlay(context, map)` and `AdsbTrafficOverlay(context, map)` using default constructors.
   - This bypasses `MapOverlayManager` cached size path (`createOgnTrafficOverlay`, `createAdsbTrafficOverlay`).

5. Dead-path indication:
   - `MapOverlayManager.initializeOverlays(map)` exists but is not called from runtime wiring.

6. Resulting symptom:
   - Cold start can show default icon size despite persisted value, until the user changes slider again.
   - Same structural risk applies to OGN and ADS-b.

## 3) Fixed Compliance Matrix (Locked Before Coding)

| ID | Requirement | Evidence Target | Verification |
|---|---|---|---|
| CM-01 | ADS-b icon range/default matches policy (`124..248`, default `124`) | `AdsbIconSizing.kt`, `AdsbSettingsScreen.kt`, `AdsbTrafficPreferencesRepository.kt` | Unit tests + manual settings screen |
| CM-02 | OGN icon range/default matches policy (`124..248`, default `124`) | `OgnIconSizing.kt`, `OgnSettingsScreen.kt`, `OgnTrafficPreferencesRepository.kt` | Unit tests + manual settings screen |
| CM-03 | Startup applies persisted icon size on first map render (no fallback to default) | `MapScreenSections.kt`, `MapInitializer.kt`, `MapOverlayManager.kt`, `MapScreenScaffoldInputs.kt` | Regression test + manual cold start |
| CM-04 | Overlay creation for OGN/ADS-b has single runtime owner | `MapInitializer.kt`, `MapOverlayManager.kt`, `MapScreenManagers.kt` | Code audit |
| CM-05 | Layering remains `UI -> ViewModel -> UseCase -> Repository` | settings + use-case + repository files | Code audit |
| CM-06 | Style change and map recreation preserve configured icon size | `MapOverlayManager.kt`, `MapRuntimeController.kt`, `MapScreenScaffoldInputs.kt` | Manual style-switch check |
| CM-07 | Existing traffic tap-selection behavior remains intact after size changes | `AdsbTrafficOverlay.kt`, `OgnTrafficOverlay.kt` | Manual tap checks |
| CM-08 | Required checks pass | Gradle tasks | `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` |
| CM-09 | Pipeline docs reflect corrected startup ownership path | `docs/ARCHITECTURE/PIPELINE.md` | Doc review |

## 4) Architecture Contract

### 4.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicate |
|---|---|---|---|
| ADS-b icon size preference | `AdsbTrafficPreferencesRepository` | `iconSizePxFlow` | UI/runtime authoritative mirrors |
| OGN icon size preference | `OgnTrafficPreferencesRepository` | `iconSizePxFlow` | UI/runtime authoritative mirrors |
| Runtime applied icon sizes | `MapOverlayManager` cache (non-SSOT) | imperative overlay apply | treating runtime cache as persisted source |

### 4.2 Dependency Direction

- Preserved flow: `UI -> ViewModel -> UseCase -> Repository`.
- `MapInitializer` must not become a second owner of OGN/ADS-b overlay sizing policy.

### 4.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Icon size settings | N/A | config value, not time-derived |
| Startup sequencing | lifecycle/event order | not numerical time-based business logic |

### 4.4 Replay Determinism

- Replay data outputs are unaffected.
- Fix is in map runtime overlay initialization/order only.

## 5) Data Flow (Before -> After)

Before (problematic startup):

`Settings SSOT -> ViewModel icon size -> onMapReady(stale capture) + MapInitializer direct default overlay creation -> default icon rendered`

After (target):

`Settings SSOT -> ViewModel icon size -> latest onMapReady callback -> single-owner MapOverlayManager overlay creation (with cached size) -> correct icon size on first render`

## 6) Implementation Phases

### Phase 0: Confirm Product Policy

- Goal:
  - Lock whether parity target is `124..248` (this plan assumes yes).
- Files:
  - This plan doc.
- Exit criteria:
  - CM-01 and CM-02 bounds are final and approved.

### Phase 1: Size Policy Alignment

- Goal:
  - Align constants, clamping, and settings slider ranges for ADS-b and OGN.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbIconSizing.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnIconSizing.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsScreen.kt`
  - repository clamp consumers already use these constants.
- Exit criteria:
  - Both settings screens and repositories clamp to the same policy range.

### Phase 2: Startup Ownership and Callback Freshness Fix

- Goal:
  - Remove stale callback/default overlay overwrite path.
- Planned changes:
  - Ensure map-ready callback always reads latest bindings in `MapViewHost`.
  - Remove duplicate/stale-sensitive `onMapReady` invocation pattern (or ensure both invocations are value-fresh and non-overwriting).
  - Stop direct OGN/ADS-b overlay construction in `MapInitializer.setupOverlays`; route through `MapOverlayManager` as single owner.
  - Keep blue-location/snail-trail/scale-bar setup responsibilities clear and unchanged unless required.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenManagers.kt` (if wiring ownership changes)
- Exit criteria:
  - Cold start applies persisted OGN/ADS-b icon sizes without user interaction.

### Phase 3: Regression Tests and Docs

- Goal:
  - Lock behavior and prevent reintroduction.
- Tests to add/update:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt` (constants/default exposure impacts)
  - Add map-runtime regression test(s) for startup apply ordering (new test file in `feature/map/src/test/java/com/example/xcpro/map/`).
- Docs:
  - `docs/ARCHITECTURE/PIPELINE.md`
- Exit criteria:
  - CM matrix green; required checks pass.

## 7) Test Plan

- Unit tests:
  - Clamp/default/persist for ADS-b and OGN ranges.
  - Startup-order regression for icon-size apply.
- Manual tests:
  - Set ADS-b icon size to non-default, force-stop app, relaunch, verify first map render uses persisted size.
  - Repeat for OGN.
  - Switch map style and verify sizes remain.
  - Tap overlapping markers at min and max sizes.
- Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 8) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Changing OGN max from `512` to `248` may break user expectation | Medium | Explicit release note and migration decision in product scope | XCPro Team |
| Startup sequencing fix can regress map-ready timing | High | Add targeted startup regression tests and manual cold-start validation | XCPro Team |
| Overlay ownership refactor may affect style-change flows | High | Keep `MapOverlayManager` as single owner and verify style reload path | XCPro Team |
| Runtime update order still race-prone | Medium | Remove duplicated callbacks or enforce latest-callback capture and deterministic apply order | XCPro Team |

## 9) Acceptance Gates

- CM-01..CM-09 all pass.
- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No new `KNOWN_DEVIATIONS.md` entry unless explicitly approved.

## 10) Rollback Plan

- Revert independently:
  - size-constant/range changes,
  - startup callback ownership changes.
- Recovery fallback:
  - keep persistence keys intact,
  - temporarily freeze runtime at default `124` if startup regression appears in hotfix window.
