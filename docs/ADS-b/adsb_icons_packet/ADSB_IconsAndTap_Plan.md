# ADSB_IconsAndTap_Plan.md
Release-grade implementation plan for ADS-B icon classification, heading rotation, and tap-to-details in XCPro.

Status: ready for implementation.
Scope: incremental UI/map enhancement on top of existing ADS-B data pipeline.

## 0) Goal
Upgrade ADS-B map rendering from generic markers to per-aircraft icons with heading-aware rotation, while keeping architecture compliance and no-flicker updates.

## 1) Non-goals
1. No changes to provider, polling cadence, auth, retry, or ADS-B radius behavior.
2. No new business logic in repositories or domain for visual classification.
3. No collision-avoidance semantics; informational UI only.
4. No breaking changes to existing OGN overlay behavior.

## 2) Required Reading Order
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. Existing `feature/map` ADS-B and OGN overlay code
5. This plan

## 3) Architecture Compliance Contract
This packet must satisfy all of the following:

1. Layering and ownership:
   - UI -> domain -> data.
   - Classification for icon/type is UI mapping logic only, not repository/store logic.
2. SSOT:
   - ADS-B repository remains authoritative for raw traffic state.
   - ViewModel holds only UI selection state (`selectedAdsbId`) and derived view state.
3. ViewModel purity:
   - No Android UI types, no persistence, no direct repository calls outside use-cases.
4. Timebase:
   - No new wall-clock logic in domain/fusion paths.
5. UI rules:
   - Render state only; no business math in composables.
6. Vendor neutrality:
   - No provider brand in production UI strings.
7. Encoding:
   - Production Kotlin remains ASCII-only.

## 4) Baseline (Current System)
1. ADS-B data retrieval, filtering, and snapshot state are already implemented.
2. ADS-B markers currently render as circle + heading text arrow + label.
3. Tap-to-details exists, but selection should be ID-based for stronger state correctness.
4. No drawable resources currently exist under `feature/map/src/main/res/`; this packet creates them.

## 5) External Data Contract (OpenSky)
Use only fields already available in the existing model:
1. `trackDeg` (`true_track`) for icon rotation.
2. `category` for emitter class.
3. `speedMps` for small-jet heuristic when category alone is ambiguous.

Do not invent engine type from unavailable data.

## 6) UI Classification Specification
Define a pure UI helper:

```kotlin
enum class AdsbAircraftKind {
    SmallSingleEngine,
    SmallJet,
    LargeJet,
    Helicopter,
    Glider,
    Unknown
}
```

Inputs:
1. `category: Int?`
2. `speedMps: Double?`

Constant:
1. `SMALL_JET_SPEED_THRESHOLD_MPS = 120.0`

Rules (ordered):
1. `category == 8` -> `Helicopter`
2. `category == 9` -> `Glider`
3. `category in {4, 5, 6}` -> `LargeJet`
4. `category == 7` -> `SmallJet`
5. `category == 3` -> `SmallJet` if `speedMps >= threshold`, else `SmallSingleEngine`
6. `category == 2` -> `SmallSingleEngine`
7. otherwise `Unknown`

Important:
1. This mapping function must be side-effect free and unit-tested.
2. Keep raw category in UI model for debug/details display.

## 7) Map Icon Asset Contract
Copy provided assets into `feature/map/src/main/res/drawable/`:
1. `ic_adsb_small_single_engine.xml`
2. `ic_adsb_small_jet.xml`
3. `ic_adsb_large_jet.xml`
4. `ic_adsb_helicopter.xml`
5. `ic_adsb_glider.xml`
6. `ic_adsb_unknown.xml`

Asset requirements:
1. Icon points north/up at rest.
2. Solid, high-contrast shape.
3. Consistent viewport and visual size.

## 8) MapLibre Rendering Contract
Refactor ADS-B overlay to per-feature icon rendering.

1. Register style images once per style lifecycle in overlay initialization.
2. Add GeoJSON properties per aircraft feature:
   - `icao24` (stable ID string)
   - `icon_id` (style image key)
   - `track_deg` (Double, default 0.0 when null)
   - existing detail properties (`callsign`, `alt_m`, `speed_mps`, `vr_mps`, `age_s`, etc.)
3. Use `SymbolLayer` icon rendering:
   - `iconImage(Expression.get("icon_id"))`
   - `iconRotate(Expression.get("track_deg"))`
   - `iconRotationAlignment("map")`
4. Keep callsign label in separate layer if needed.
5. No flicker rule:
   - never recreate source/layers during normal updates.
   - only call `GeoJsonSource.setGeoJson(...)` on data refresh.

## 9) Tap and Selection State Contract
Move to ID-based selection flow.

1. Overlay tap lookup returns ADS-B ID, not full model object.
2. ViewModel stores:
   - `selectedAdsbId: StateFlow<Icao24?>`
   - `selectedAdsbTarget: StateFlow<AdsbTrafficUiModel?>` derived from current target list + selected ID
3. On tap:
   - set selected ID.
4. On dismiss or overlay disable:
   - clear selected ID.
5. If selected ID disappears from current target list:
   - derived target becomes null and sheet closes.

This avoids duplicated stale object state and aligns with SSOT/UDF.

## 10) Details Sheet Contract
Details sheet must display:
1. Callsign
2. ICAO24
3. Aircraft type label (derived from UI classification helper)
4. Altitude
5. Speed
6. Track
7. Vertical rate
8. Age seconds
9. Distance from user (use existing units formatter)
10. Safety disclaimer text

Formatting:
1. Reuse existing `UnitsFormatter` and units preferences.
2. Keep strings provider-neutral.

## 11) File-Level Implementation Plan
Primary edits:

1. Add UI helpers:
   - `feature/map/src/main/java/com/example/xcpro/map/adsb/AdsbAircraftKind.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/adsb/AdsbAircraftKindMapper.kt`
   - optional: `feature/map/src/main/java/com/example/xcpro/map/adsb/AdsbIconIds.kt`
2. Modify overlay:
   - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
3. Modify tap bridge:
   - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
4. Modify ViewModel selection:
   - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
5. Modify details UI:
   - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbMarkerDetailsSheet.kt`
6. Add resources:
   - `feature/map/src/main/res/drawable/ic_adsb_*.xml`

Do not move classification into:
1. `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
2. `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`

## 12) Phased Execution Plan
Phase 1: Pure helpers and tests
1. Add aircraft kind enum + mapper + tests.
2. Add icon-id mapping helper + tests.

Phase 2: Overlay rendering migration
1. Register style images.
2. Switch circle/arrow rendering to icon symbol rendering.
3. Preserve stable source/layer IDs and no-flicker updates.

Phase 3: Tap/selection hardening
1. Return tapped ICAO24 ID from overlay lookup.
2. ViewModel stores selected ID and derives selected target.

Phase 4: Details sheet and UX polish
1. Show aircraft kind label and distance.
2. Keep existing safety disclaimer.

Phase 5: Verification and release checks
1. Run tests and static checks.
2. Manual validation on map interactions.

## 13) Test Plan (Release Gates)
Unit tests (required):
1. Aircraft kind mapping for all relevant categories.
2. Boundary tests around `SMALL_JET_SPEED_THRESHOLD_MPS` (below/equal/above).
3. Icon-id mapping per aircraft kind.
4. Feature property mapping includes `icao24`, `icon_id`, `track_deg`.
5. Track null handling defaults to `0.0`.
6. ViewModel selection-by-ID behavior:
   - tap sets ID
   - details resolves from live list
   - details clears when item disappears

Verification commands:
1. `./gradlew :feature:map:testDebugUnitTest --console=plain`
2. `./gradlew :feature:map:compileDebugKotlin --console=plain`
3. `./gradlew enforceRules --console=plain`

## 14) Manual Acceptance Checklist
1. Helicopter category renders helicopter icon.
2. Glider category renders glider icon.
3. Large/heavy categories render large-jet icon.
4. Small category uses speed heuristic for small-jet vs small-single-engine.
5. Icons rotate correctly with heading and stay north-up when track missing.
6. Tapping icon opens correct details.
7. Panning/zooming/style changes keep ADS-B overlay stable (no flicker/rebuild artifacts).
8. Existing ADS-B toggle behavior remains correct.

## 15) Risks and Mitigations
1. Risk: style reload loses registered images.
   - Mitigation: register images inside overlay initialize path for every style load.
2. Risk: tap mismatches when objects refresh.
   - Mitigation: ID-based selection derivation in ViewModel.
3. Risk: hidden architecture drift.
   - Mitigation: keep classification in UI helper only; no repository/store changes for visual type.
4. Risk: icon orientation errors.
   - Mitigation: keep north-oriented assets and track rotation tests.

## 16) Definition of Done
Complete when all are true:
1. Behavior matches acceptance checklist.
2. Unit tests and compile checks pass.
3. `enforceRules` passes.
4. No new entries in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.
