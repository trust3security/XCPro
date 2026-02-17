# ADS-B Distance + Vertical Filter Settings Change Plan

## 0) Metadata

- Title: ADS-B configurable display radius, vertical filters, and proximity coloring
- Owner: XCPro Team
- Date: 2026-02-17
- Issue/PR: TBD
- Status: Implemented (code + tests landed)

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 1) Scope

- Problem statement:
  - ADS-B display radius was hardcoded at 20 km before this change.
  - Users cannot filter ADS-B targets by vertical separation above/below ownship.
  - Target icon urgency/proximity is not represented as continuous distance color.
- Why now:
  - User-requested operational control for traffic relevance near ownship.
- In scope:
  - ADS-B settings sliders:
    - Horizontal max distance
    - Vertical above limit
    - Vertical below limit
  - Runtime filtering of displayed ADS-B targets using those settings.
  - Distance-based icon color policy for ADS-B overlay.
  - Unit-aligned slider labels/values for vertical limits using `General -> Units`.
- Out of scope:
  - OGN behavior.
  - ADS-B provider/network protocol changes.
  - Replay engine logic.

## 2) Required Behavior Contract

1. Horizontal max display distance slider:
   - Default: `10 km`
   - Range: `1..50 km`
2. Vertical filters:
   - Two independent sliders:
     - `Above ownship`
     - `Below ownship`
   - Values shown in user-selected altitude unit (`m` or `ft`).
   - Internally stored/processed in SI (meters).
3. Proximity color policy (horizontal distance to ownship):
   - Distance `> 5 km`: green
   - Distance `2..5 km`: linear interpolation green -> red
   - Distance `<= 2 km`: red
4. Emergency-collision visualization remains higher priority than proximity color.
5. If ownship altitude is unavailable:
   - Vertical filter does not remove targets (fail-open).
6. If ownship position is unavailable:
   - Proximity color falls back to neutral/default style.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B overlay enabled | `AdsbTrafficPreferencesRepository` | `enabledFlow` | UI-owned persistent state |
| ADS-B icon size | `AdsbTrafficPreferencesRepository` | `iconSizePxFlow` | Runtime cache as authority |
| ADS-B max distance km (new) | `AdsbTrafficPreferencesRepository` | `Flow<Int>` | UI-owned persistent state |
| ADS-B vertical above meters (new) | `AdsbTrafficPreferencesRepository` | `Flow<Double>` | UI-owned persistent state |
| ADS-B vertical below meters (new) | `AdsbTrafficPreferencesRepository` | `Flow<Double>` | UI-owned persistent state |
| ADS-B target filtering state | `AdsbTrafficRepository` + `AdsbTrafficStore` | `targets/snapshot StateFlow` | UI business filtering |

### 3.2 Dependency Direction

Preserve:

`UI -> ViewModel -> UseCase -> Repository`

Rules:

- No business filtering in Composables/ViewModels.
- No direct preferences access in UI.
- No map rendering policy stored as authoritative preferences in runtime overlay classes.

### 3.3 Time Base

- No new wall-time or cross-timebase logic is introduced.
- Existing ADS-B monotonic freshness model remains authoritative.

## 4) Data Flow (Before -> After)

Before:

`General ADS-b Screen -> icon size only -> AdsbTrafficPreferencesRepository -> map overlay size`

After:

`General ADS-b Screen -> ViewModel -> AdsbSettingsUseCase -> AdsbTrafficPreferencesRepository (range + vertical + icon size)`
`-> MapScreenViewModel / MapScreenTrafficCoordinator -> AdsbTrafficUseCase -> AdsbTrafficRepository -> AdsbTrafficStore selection`
`-> AdsbGeoJsonMapper properties -> AdsbTrafficOverlay style expression (proximity color)`

## 4A) Implementation outcome (2026-02-17)

Implemented in runtime:
- New persistent settings in `AdsbTrafficPreferencesRepository`:
  - max distance km
  - vertical above meters
  - vertical below meters
- Settings UI in `AdsbSettingsScreen`:
  - horizontal radius slider (`1..50 km`, default `10`)
  - above/below sliders using General Units altitude selection
  - commit-on-finish slider writes to reduce DataStore churn
- Coordinator wiring:
  - ownship altitude flow passed to ADS-B repository
  - display filters flow applied at runtime without UI-owned filtering
- Repository/store filtering:
  - horizontal filter by configured radius
  - vertical filter against ownship altitude with fail-open when altitude unknown
  - emergency risk disabled when ownship position is unavailable
  - display cap prioritization: emergency -> distance -> age
- Map styling:
  - proximity color expression from distance property
  - emergency color precedence over proximity gradient
  - neutral color fallback when ownship reference is unavailable
- Runtime observability:
  - snapshot/debug counters include vertical and capped filtering counts

## 5) Implemented File Touch List

- Settings/preferences:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbFilterSettings.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt`
- ADS-B runtime filtering:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelStateBuilders.kt`
- Map rendering:
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbGeoJsonMapper.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanels.kt`

## 6) Defaults and Limits (Implemented)

- Horizontal radius:
  - default: `10 km`
  - min: `1 km`
  - max: `50 km`
- Vertical filters:
  - above default: `3000 ft` equivalent
  - below default: `2000 ft` equivalent
  - slider max: `10000 ft` equivalent (`3048 m`)
  - step:
    - feet mode: `100 ft`
    - meters mode: `50 m`

## 7) Test Coverage and Verification (Implemented)

- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepositoryTest.kt`
  - default/clamp/persistence for radius + above + below
- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTest.kt`
  - vertical above/below filtering with/without ownship altitude
- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
  - dynamic radius application (10 default, 50 max)
  - snapshot radius consistency
  - immediate reselection on settings updates
- `feature/map/src/test/java/com/example/xcpro/map/AdsbGeoJsonMapperTest.kt`
  - distance properties required for proximity-color style
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
  - wiring of settings flow and ownship altitude path

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Verification status:
- Passed in implementation run on 2026-02-17:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 8) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| 50 km radius increases displayed traffic and API pressure | Medium | keep default 10 km, retain adaptive polling/credit guardrails | XCPro Team |
| Vertical filters hide expected traffic if unit conversion is wrong | High | SI-only storage, conversion tests, UI label tests | XCPro Team |
| Proximity color conflicts with emergency styling | Medium | emergency override precedence fixed and tested | XCPro Team |

## 9) Acceptance Gates

- Architecture layering remains compliant.
- No business filtering logic in UI.
- New settings persist and restore correctly.
- Radius and vertical filtering are deterministic.
- Proximity color policy matches distance thresholds.
- Existing emergency-collision visual behavior still works.

## 10) Deep-Pass Audit Log

### Pass 1 (2026-02-17)

Newly identified missed details:

1. Ownship altitude propagation gap:
   - Current ADS-B center/origin updates carry only latitude/longitude.
   - Vertical filtering cannot be robust until ownship altitude is propagated through coordinator/use-case/repository.
2. Display cap interaction:
   - ADS-B display is capped at 30 targets.
   - Increasing radius to 50 km does not imply all in-range aircraft are shown.
3. Proximity-color rendering constraint:
   - `icon-color` works only with SDF icons.
   - Current ADS-B icon registration path uses non-SDF image adds.
4. Dateline/anti-meridian edge case:
   - `computeBbox` falls back to `[-180, 180]` longitude when crossing anti-meridian.
   - At larger radii this can force near-global requests and harm quota/perf.
5. Selection UX side effect:
   - Selected target is auto-cleared when not present in `rawAdsbTargets`.
   - Vertical filtering may dismiss details sheet even when target still exists in cache but is filtered out.
6. Polling/credit side effect:
   - `withinRadiusCount > 0` keeps hot cadence.
   - Larger radius can bias runtime toward faster polling and higher API pressure.
7. Snapshot observability gap:
   - Current snapshot provides fetched/within/displayed counts only.
   - No explicit vertical-filtered count fields for runtime debugging.
8. Un-gated center churn:
   - `mapLocation` center/origin pushes are not gated by ADS-B overlay enabled/streaming state.
   - Creates unnecessary selection/snapshot churn while overlay is off.

Contract updates from pass 1:

- Vertical filters require explicit ownship altitude path design before implementation.
- Proximity-color implementation must choose one:
  - SDF image pipeline + style `icon-color` expression, or
  - pre-tinted icon buckets (no SDF dependency).

### Pass 2 (2026-02-17)

Newly identified missed details:

1. Ownship-unknown proximity semantics are not yet defined:
   - Current runtime falls back to ADS-B center when ownship reference is missing.
   - This can color/filter by camera/query center instead of pilot position, violating intent.
2. No anti-flicker policy for threshold boundaries:
   - Planned color boundaries (`2 km`, `5 km`) and vertical limits can oscillate due GPS/altitude noise.
   - Without hysteresis/dwell, icons will rapidly switch color and in/out visibility.
3. Immediate removal behavior amplifies filter flicker:
   - Display smoother removes entries instantly when not present in incoming target set.
   - Vertical threshold crossings will appear as abrupt disappear/reappear events.
4. Unit-switch behavior is unspecified for vertical slider state:
   - Need explicit rule: persist physical value in meters and remap UI when unit preference changes.
   - Avoid semantic drift (for example 3000 ft turning into 3000 m on unit switch).
5. Emergency-priority style interaction needs exact precedence rule:
   - Emergency visuals currently rely on emergency icon IDs.
   - Proximity coloring must not mask or weaken emergency indication.

Contract updates from pass 2:

- Add hysteresis/dwell requirements for:
  - vertical filter boundaries
  - proximity color boundary transitions
- Define fail-open/fail-neutral behavior explicitly:
  - no ownship position -> no proximity urgency coloring
  - no ownship altitude -> do not vertically filter targets

### Pass 3 (2026-02-17)

Newly identified missed details:

1. Altitude-path integration blast radius:
   - Extending `MapLocationUiModel` with altitude would touch many map/UI call sites.
   - Lower-risk design is a dedicated ownship-altitude flow from flight-data SSOT into ADS-B coordinator/use-case.
2. Poll-cadence coupling ambiguity with new filters:
   - Current adaptive polling uses `withinRadiusCount`.
   - Once vertical filters exist, cadence semantics must be explicit:
     - cadence from horizontal-nearby traffic, or
     - cadence from post-filter displayed traffic.
   - Using post-filter count alone can under-poll when aircraft are just outside current vertical limits.
3. Settings application while map runtime is paused:
   - ADS-B settings are edited in a separate screen/viewmodel.
   - Runtime apply path must ensure changed radius/vertical filters are pushed immediately on map return without requiring manual toggle.
4. Vertical default conversion policy for metric users:
   - `3000 ft`/`2000 ft` defaults convert to non-round metric values.
   - Need explicit normalization policy (preserve exact physical value vs round-to-step display value) to avoid surprising UI jumps.
5. Documentation consistency debt (historical, resolved):
   - `ADSB.md` previously documented runtime as 20 km while planned defaults were 10/50.
   - Keep "implemented" and "planned" sections explicitly separated until code lands, then collapse into one authoritative runtime envelope.

Resolution note (2026-02-17):
- `ADSB.md` was rewritten to an implementation-state contract with configurable radius and vertical/proximity behavior.

Contract updates from pass 3:

- Prefer dedicated ownship-altitude flow over broad UI model mutation.
- Define polling metric ownership before implementation (`horizontal_count` vs `displayed_count`).
- Add explicit re-apply contract on map resume/startup for new ADS-B settings.

### Pass 4 (2026-02-17)

Newly identified missed details:

1. Settings write-churn risk from slider interaction:
   - Current ADS-B slider pattern writes preferences on every `onValueChange` tick.
   - With 3 new ADS-B sliders, this would amplify DataStore writes and runtime churn while dragging.
2. Immediate-apply contract for radius/vertical changes is not explicit:
   - `reconnectNow()` exists and is currently used for credential changes only.
   - Without an explicit fast-apply path, users can change filters and still wait for the next poll window.
3. Display-cap prioritization bias:
   - Current selection applies `.sortedBy(age).thenBy(distance).take(maxDisplayed)`.
   - Under larger radius, this can retain fresher distant traffic and evict older but nearer traffic.

Contract updates from pass 4:

- Define settings commit semantics:
  - UI drags update local transient state.
  - Repository writes occur on commit (`onValueChangeFinished`) or debounced updates.
- Define immediate settings apply path:
  - instant reselect from cache
  - optional immediate fetch/reconnect when radius materially changes
- Define cap prioritization order before implementation:
  - emergency risk first, then proximity, then freshness.

### Pass 5 (2026-02-17)

Newly identified missed details:

1. Vertical-separation altitude datum ambiguity:
   - ADS-B target altitude currently resolves `geoAltitudeM ?: baroAltitudeM`.
   - Ownship altitude source for new vertical filtering is not yet fixed by contract.
2. Emergency-risk fallback semantics are not explicit:
   - Current reference fallback (`ownshipOrigin ?: center`) feeds emergency collision-risk heading checks.
   - If ownship is unknown, emergency classification can be computed against map/query center instead of pilot position.
3. Replay/live origin ownership is still ambiguous:
   - ADS-B center/origin updates are driven by `mapLocation` stream events.
   - During replay sessions, this can shift ADS-B reference ownership unless explicitly gated.

Contract updates from pass 5:

- Define altitude reference contract for vertical filtering (including fallback order and tolerance policy).
- Require fail-safe emergency behavior when ownship reference is unavailable (do not compute emergency risk from center fallback).
- Define replay policy for ADS-B ownship-origin inputs (allowed vs gated).

### Pass 6 (2026-02-17)

Newly identified missed details:

1. Proximity-color data path is incomplete in current map properties:
   - GeoJSON mapper does not currently emit a distance property for style expressions.
   - Overlay style uses icon image + opacity only; proximity-color contract needs explicit property plumbing.
2. Smooth color transitions need smoothed distance ownership:
   - Motion smoother already interpolates `distanceMeters` per aircraft frame.
   - If proximity color is derived only at provider update boundaries, color will still jump at poll cadence.
3. Filter observability needs per-reason counters:
   - Current snapshot/debug fields do not explain why a target is excluded (horizontal radius, vertical band, stale, display cap).
   - This blocks operator-level troubleshooting once multiple filters are active.

Contract updates from pass 6:

- Add explicit `distanceMeters` map property contract for ADS-B features (finite-guarded).
- Tie proximity-color computation to frame-level smoothed values (or equivalent deterministic bucket path) to avoid step jumps.
- Extend ADS-B snapshot/debug contract with filter-reason counters and user-facing count-label semantics.
