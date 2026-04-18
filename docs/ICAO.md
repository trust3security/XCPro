# ICAO.md

## 0) Metadata

- Title: ADS-B Aircraft Type Identification and Engine-Aware Icon Plan
- Owner: XCPro Team
- Date: 2026-02-14
- Status: Draft for execution
- Scope: `feature/map` ADS-B identification, classification, and icon rendering

## 1) Objective and Quality Target

Move ADS-B aircraft identification from mixed UI heuristics to a deterministic, testable pipeline that can reliably show correct icon families, including two-engine and four-engine variants.

Target quality after completion:

- Architecture cleanliness: 5.0 / 5
- Maintainability/change safety: 5.0 / 5
- Test confidence on risky paths: 5.0 / 5
- Release readiness (ADS-B icon/type slice): 5.0 / 5

## 1A) 2026-02-14 Deep-Dive Findings (Current Runtime Truth)

What OpenSky provides for this feature:

- `/api/states/all?extended=1`:
  - `icao24` at state-vector index `0`
  - `callsign` at index `1`
  - `category` at index `17`
  - code: `feature/map/src/main/java/com/trust3/xcpro/adsb/OpenSkyStateVectorMapper.kt`
- ICAO metadata lookup:
  - endpoint: `https://opensky-network.org/api/metadata/aircraft/icao/{icao24}`
  - useful fields: `typecode`, `icaoAircraftType`, fallback `icaoAircraftClass`
  - code: `feature/map/src/main/java/com/trust3/xcpro/adsb/metadata/data/OpenSkyIcaoMetadataClient.kt`

Critical behavior detail:

- `category` can be `0`, `1`, or null for many aircraft.
- For those cases, icon quality depends on metadata enrichment keyed by ICAO24.
- If metadata is not available yet, icon must be deterministic `Unknown` (not a fake plane icon).

Fixes implemented in this pass:

- Unknown icon now maps to `ic_adsb_unknown` (no misleading plane fallback).
  - code: `feature/map/src/main/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIcon.kt`
- Metadata lookup throughput increased for missing ICAOs:
  - on-demand batch size raised from `3` to `8`
  - code: `feature/map/src/main/java/com/trust3/xcpro/adsb/metadata/data/AircraftMetadataRepositoryImpl.kt`
- Lookup order now prioritizes targets likely to render `Unknown`:
  - unknown/unsupported category and no metadata hints are looked up first
  - code: `feature/map/src/main/java/com/trust3/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`

## 1B) End-to-End Data and Icon Pipeline (How It Should Work)

1. Poll live states:
- `OpenSkyProviderClient.fetchStates(...)`
- `OpenSkyStateVectorMapper.parseResponse(...)`

2. Persist live traffic targets:
- `AdsbTrafficRepositoryImpl` publishes `rawAdsbTargets`

3. Enrich by ICAO24 metadata:
- `AdsbMetadataEnrichmentUseCase.targetsWithMetadata(...)`
- `AircraftMetadataRepositoryImpl.getMetadataFor(...)`
- fast path: Room DB hit
- slow path: async on-demand fetch via `OpenSkyIcaoMetadataClient`

4. Trigger immediate recompute when metadata arrives:
- repository emits `metadataRevision`
- enrichment use-case recomputes targets/details on revision updates

5. Choose icon deterministically:
- `iconForAircraft(...)` in `AdsbAircraftIconMapper.kt`
- precedence:
  1) heavy category hard rule (`category == 6`)
  2) metadata classification (`typecode`, then ICAO class decode)
  3) ICAO24 override list (non-heavy only)
  4) OpenSky category fallback
  5) `Unknown`

6. Render:
- `AdsbGeoJsonMapper.toFeature(...)`
- `AdsbTrafficOverlay` reads `icon_id` and displays style image

## 1C) Expected Runtime Outcomes

- If `category == 8` (Rotorcraft), helicopter icon should appear immediately.
- If `category` is unknown but ICAO metadata has `icaoAircraftType` like `H1P/H2T`, icon can switch from `Unknown` to helicopter after metadata lookup completes.
- If metadata is missing (404 or not yet synced), icon stays `Unknown` deterministically.
- Callsign label is independent from icon and comes from index `1`; if blank, fallback is ICAO24.

## 1D) Future AI Agent Bug-Hunt Playbook (ICAO/Icon Issues)

When icon is wrong or delayed:

1. Confirm raw inputs:
- check `category` and `callsign` parsing in `OpenSkyStateVectorMapper.kt`
- verify `extended=1` is still requested in provider client

2. Confirm enrichment triggers:
- ensure `metadataRevision` is observed in:
  - `targetsWithMetadata(...)`
  - `selectedTargetDetails(...)`
- ensure missing ICAOs are scheduled for on-demand lookup

3. Confirm mapping precedence:
- inspect `iconForAircraft(...)` and `iconForCategory(...)`
- verify no fallback path maps `Unknown` to a plane icon

4. Confirm render path:
- inspect `AdsbGeoJsonMapper.PROP_ICON_ID`
- inspect style image registration in `AdsbTrafficOverlay.ensureStyleImages(...)`

5. Run focused tests first:
- `AdsbAircraftIconMapperTest`
- `AdsbAircraftIconTest`
- `AdsbMetadataEnrichmentUseCaseTest`
- `AircraftMetadataRepositoryImplTest`

Helpful local scans:

```bash
rg -n "IDX_CALLSIGN|IDX_CATEGORY|extended=1" feature/map/src/main/java/com/trust3/xcpro/adsb
rg -n "metadataRevision|targetsWithMetadata|selectedTargetDetails" feature/map/src/main/java/com/trust3/xcpro/adsb
rg -n "iconForAircraft|iconForCategory|Unknown" feature/map/src/main/java/com/trust3/xcpro/adsb/ui
```

## 1E) External References and GitHub Search Pointers

Primary references:

- OpenSky REST docs: https://openskynetwork.github.io/opensky-api/rest.html
- OpenSky API docs index: https://openskynetwork.github.io/opensky-api/
- Official OpenSky API GitHub (docs source + clients): https://github.com/openskynetwork/opensky-api

Useful GitHub code searches:

- OpenSky state-vector docs source:
  - https://github.com/search?q=repo%3Aopenskynetwork%2Fopensky-api+state+vectors+category&type=code
- OpenSky docs for limits/category/callsign:
  - https://github.com/search?q=repo%3Aopenskynetwork%2Fopensky-api+callsign+category+extended&type=code

For XCPro repository searches on GitHub:

- Use `repo:<owner>/<repo>` once known, then query:
  - `iconForAircraft metadataRevision ICAO`
  - `OpenSkyStateVectorMapper IDX_CATEGORY`
  - `OpenSkyIcaoMetadataClient typecode icaoAircraftType`

## 2) Current Gaps (Why this is needed)

Observed in current code:

- Classification logic lives in UI mapper (`feature/map/src/main/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIconMapper.kt`).
- One function currently mixes source precedence, ICAO decode, category fallback, and UI icon selection.
- The `icaoAircraftType` middle digit is currently treated as size; it should represent engine count in ICAO aircraft class notation (example: `L2J` means landplane, 2 engines, jet).
- No explicit confidence/source model for "why this icon was chosen".
- Limited icon taxonomy for engine-aware display (single/twin/quad distinctions are not first-class).

## 3) Refactor Plan First (Mandatory before feature expansion)

### Phase R1 - Extract and isolate classification policy

Goal:

- Remove business classification logic from UI icon mapper.

Changes:

- Add domain models:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/domain/AircraftClassification.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/domain/AircraftKind.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/domain/ClassificationSource.kt`
- Add use-case contract:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/domain/ResolveAircraftClassificationUseCase.kt`

Rules:

- UI layer only maps `AircraftClassification` to `AdsbAircraftIcon`.
- No category/type decoding logic in composables or UI mapper.

Exit criteria:

- `AdsbAircraftIconMapper.kt` contains icon mapping only, not decode policy.

### Phase R2 - Introduce deterministic resolver with strict precedence

Goal:

- One deterministic resolver for all icon-driving classification decisions.

Resolution order:

1. Exact typecode lookup (authoritative reference table).
2. Decode `icaoAircraftType` class code.
3. OpenSky category fallback.
4. Unknown.

Conflict rule:

- Exact typecode wins.
- Keep category as secondary hint only (for details/debug, not icon override when authoritative match exists).

Files:

- Add resolver implementation:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/domain/DefaultResolveAircraftClassificationUseCase.kt`
- Refactor:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIconMapper.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`

Exit criteria:

- All icon assignments come from a `ClassificationResult` produced by resolver.

### Phase R3 - Move reference data and parsing to data layer

Goal:

- Keep reference lookup data-driven and updateable without editing UI logic.

Changes:

- Add reference table source:
  - `feature/map/src/main/assets/adsb/typecode_reference.csv`
- Add parser/repository:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/metadata/data/TypecodeReferenceParser.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/metadata/domain/TypecodeReferenceRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/metadata/data/TypecodeReferenceRepositoryImpl.kt`
- Add DI bindings in existing ADS-B module.

Minimum table columns:

- `typecode`
- `airframe_kind` (fixed_wing, rotary, glider, balloon, uav, etc.)
- `engine_count`
- `engine_type` (piston, turboprop, jet, electric, unknown)
- `wtc` (light, medium, heavy, super, unknown)
- `icon_family` (optional explicit override)

Exit criteria:

- New type additions require only data update plus tests, no mapper edits.

### Phase R4 - Add explanation and observability

Goal:

- Make classification auditable and debuggable.

Changes:

- Extend selected-target details model with non-UI-safe fields already in domain shape:
  - `classificationSource`
  - `engineCount`
  - `engineType`
  - `wtc`
- Show read-only details rows in ADS-B details sheet.

Files:

- `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbSelectedTargetDetails.kt`
- `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbMarkerDetailsSheet.kt`

Exit criteria:

- Pilot can see why the icon is chosen (source and decoded/lookup properties).

## 4) Feature Expansion Plan (after refactor baseline)

### Phase F1 - Engine-aware icon taxonomy

Add icon enum variants (keep current non-fixed-wing categories):

- `FixedWingSinglePiston`
- `FixedWingTwinPiston`
- `FixedWingSingleTurboprop`
- `FixedWingTwinTurboprop`
- `FixedWingTwinJet`
- `FixedWingTriQuadJet`
- `FixedWingHeavyGeneric`
- `HelicopterSingleTurbine`
- `HelicopterTwinTurbine`

Retain existing:

- `Glider`, `Balloon`, `Parachutist`, `Hangglider`, `Drone`, `Unknown`

Files:

- `feature/map/src/main/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIcon.kt`
- drawables in `feature/map/src/main/res/drawable/`
- `feature/map/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt` (image registration remains enum-driven)

### Phase F2 - Mapping policy from classification to icon

Rules:

- Engine-aware icons used when resolver confidence is `EXACT_TYPECODE` or valid ICAO class decode.
- If decode is partial (only kind known), use family generic icon.
- `Heavy`/`Super` WTC can elevate to heavy icon family only for fixed-wing.

Current explicit icon rule (implemented 2026-02-13):

- Twin-engine jet uses `ic_adsb_jet_twin.png` (`AdsbAircraftIcon.PlaneTwinJet`).
- Twin piston / twin turboprop uses `ic_adsb_twinprop.png` (`AdsbAircraftIcon.PlaneTwinProp`), driven by ICAO class (`L2P`/`L2T`) and turboprop typecode fallbacks (`AT7*`, `AT8*`, `DH8*`).
- Four-engine jet uses `ic_adsb_plane_heavy.png` (`AdsbAircraftIcon.PlaneHeavy`).
- OpenSky heavy category (`category == 6`) uses `ic_adsb_plane_heavy.png`.
- Current four-engine typecode heavy fallbacks include prefixes `A34`, `A38`, `B74` and exact `C17`.
- Metadata conflict precedence uses typecode first, then ICAO class decode.
- ICAO24 large-icon overrides apply only to non-heavy outcomes (they do not override heavy category/typecode).

### Phase F3 - Safety fallback behavior

Rules:

- Unknown or malformed input never crashes and always maps to deterministic `Unknown` or category fallback.
- No visual churn: same inputs must yield same icon every time.

## 5) SSOT and Dependency Contract

Authoritative ownership:

- Raw ADS-B live target: `AdsbTrafficRepository`
- Metadata enrichment: `AdsbMetadataEnrichmentUseCase`
- Classification decision: new `ResolveAircraftClassificationUseCase`
- UI icon resource mapping: `AdsbAircraftIconMapper` (pure mapper only)

Dependency direction remains:

- UI -> domain/use-case -> data

Forbidden:

- UI/composable decode logic for typecode/category/class code
- ViewModel exposing raw mutable classification internals

## 6) Test Plan (must pass before merge)

Unit tests (required):

- `typecode` exact lookup precedence over category.
- `icaoAircraftType` decode correctness (example set: `L1P`, `L2P`, `L1T`, `L2T`, `L2J`, `L4J`, `H1T`, `H2T`).
- Category fallback for unknown lookup and decode failures.
- Determinism test: same input stream twice -> identical icon outputs.
- Regression tests for known pitfalls (example: `C172` must stay light, not large).

Files:

- `feature/map/src/test/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIconMapperTest.kt` (refactor to mapper-only expectations)
- add:
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/domain/ResolveAircraftClassificationUseCaseTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/metadata/TypecodeReferenceRepositoryTest.kt`

Instrumentation (when relevant):

- Tap marker -> details sheet shows classification source and engine fields.

## 7) Delivery Sequence (strict order)

1. R1 policy extraction
2. R2 deterministic resolver
3. R3 data-driven typecode repository
4. R4 details/observability
5. F1 new icons
6. F2 final mapping policy
7. F3 hardening and regression pass
8. Docs sync and final quality rescore

## 8) Documentation Sync (required)

Update in same change set:

- `docs/ARCHITECTURE/PIPELINE.md` (ADS-B classification path)
- `docs/ADS-b/ADSB.md` (icon selection precedence and classification source semantics)
- `docs/ADS-b/ADSB_CategoryIconMapping.md` (category is fallback tier, not primary truth when richer data exists)

## 9) Verification Gates

Required:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 10) Risks and Mitigations

- Risk: incorrect ICAO class decode assumptions.
  - Mitigation: lock decode logic with reference tests and explicit parser contract.
- Risk: icon asset explosion.
  - Mitigation: allow icon-family + small engine badge strategy if asset count becomes costly.
- Risk: regressions in current category behavior.
  - Mitigation: keep all existing category tests and add parity assertions for old paths.

## 11) Completion Criteria for 5/5

All must be true:

- No UI/business logic leakage in icon classification path.
- Single resolver owns precedence and conflict rules.
- Data-driven typecode table in place with tests.
- Engine-aware icon mapping shipped and deterministic.
- Required gates green.
- Documentation updated and consistent with implementation.
