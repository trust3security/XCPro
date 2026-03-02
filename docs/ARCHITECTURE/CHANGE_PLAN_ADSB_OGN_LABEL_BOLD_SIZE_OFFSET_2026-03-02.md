# CHANGE_PLAN_ADSB_OGN_LABEL_BOLD_SIZE_OFFSET_2026-03-02.md

## 0) Metadata
- Title: ADS-B + OGN Marker Label Bold/Size/Offset Readability Pass
- Owner: Engineering
- Date: 2026-03-02
- Issue/PR: TBD
- Status: Draft

## 1) Scope
- Problem statement:
  - After removing halo and standardizing to black regular text, small-label readability is still weak in motion/clutter.
  - Top/bottom labels are close to icon silhouettes at some zoom/icon-size combinations.
- Why now:
  - This is a targeted UX readability hardening step on already-shipped marker semantics.
- In scope:
  - ADS-B marker label typography (weight/size) and vertical offset from icon.
  - OGN marker label typography (weight/size) and vertical offset from icon.
- Out of scope:
  - Label content semantics (height/distance and OGN identifier rules remain unchanged).
  - ADS-B/OGN repository/network/domain changes.
  - Marker details sheets.
- User-visible impact:
  - Marker labels appear bolder, slightly larger, and further away from icon center.

## 2) Architecture Contract

### 2.1 SSOT Ownership
| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Marker label content | Existing map label mappers | GeoJSON feature props | Recomputing in repository/domain |
| Typography constants | Overlay runtime classes | Symbol layer properties | Settings/data layer copies |

### 2.2 Dependency Direction
- Flow remains: `UI -> domain -> data`
- Modules/files touched:
  - `feature/map/.../AdsbTrafficOverlay.kt`
  - `feature/map/.../OgnTrafficOverlay.kt`
- Boundary risk:
  - None; styling-only runtime presentation change.

### 2.2A Boundary Moves (Mandatory)
| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| None | N/A | N/A | No ownership shift | Compile + tests |

### 2.2B Bypass Removal Plan (Mandatory)
| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| None | N/A | N/A | N/A |

### 2.3 Time Base
| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Marker style constants | N/A | Static constants only |

### 2.4 Threading and Cadence
- Dispatcher ownership: unchanged
- Primary cadence/gating sensor: unchanged
- Hot-path latency budget: unchanged

### 2.5 Replay Determinism
- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules: Unchanged (presentation constants only)

### 2.6 Enforcement Coverage
| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Accidental semantic drift in labels | Coding rules + prior marker contract | Unit test | `AdsbGeoJsonMapperTest`, OGN mapper tests |
| Overlay wiring regression | Architecture layering | Unit test | `MapOverlayManagerOgnLifecycleTest` |
| Build break from style constants | Contributing gates | Build | Gradle compile + full gates |

## 3) Data Flow (Before -> After)
No data-flow change.

```
Source -> Repository (SSOT) -> UseCase -> ViewModel -> Overlay Mapper -> Overlay SymbolLayer
```

Only SymbolLayer style constants (font/size/offset) change.

## 4) Implementation Phases

### Phase 0 - Lock visual contract (this doc)
- Goal:
  - Freeze target bold/size/offset contract before code edits.
- Exit criteria:
  - Contract values agreed.

### Phase 1 - ADS-B typography + spacing pass
- Goal:
  - Apply bold stack, +1 font size, and larger label offset.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- Proposed constants:
  - `LABEL_FONT_STACK`: `"Open Sans Bold", "Noto Sans Bold", "Arial Unicode MS Bold"`
  - `LABEL_TEXT_SIZE_SP`: `13f` (current `12f`)
  - `LABEL_TEXT_OFFSET_BASE_Y`: `1.35f` (current `1.1f`)
- Quick gate:
  - `./gradlew :feature:map:compileDebugKotlin`

### Phase 2 - OGN typography + spacing pass
- Goal:
  - Match ADS-B readability direction with bold + larger spacing.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
- Proposed constants:
  - `LABEL_FONT_STACK`: `"Open Sans Bold", "Noto Sans Bold", "Arial Unicode MS Bold"`
  - `LABEL_TEXT_SIZE_BASE_SP`: `13f` (current `12f`)
  - `MIN_LABEL_TEXT_SIZE_SP`: `12f` (current `11f`)
  - `MAX_LABEL_TEXT_SIZE_SP`: `17f` (current `16f`)
  - `LABEL_TEXT_OFFSET_BASE_Y`: `1.6f` (current `1.35f`)
- Quick gate:
  - `./gradlew :feature:map:compileDebugKotlin`

### Phase 3 - Tests update
- Goal:
  - Ensure no semantic label regressions.
- Files:
  - Existing ADS-B/OGN mapper tests as needed.
- Quick gate:
  - `./gradlew :feature:map:compileDebugKotlin`

### Phase 4 - Full verification
- Required gates:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 5) Test Plan
- Unit tests:
  - Keep label semantic tests unchanged/passing.
- Replay/regression:
  - Covered by existing tests and deterministic code path.
- UI/instrumentation:
  - Optional screenshot/visual pass if device available.

## 6) Risks and Mitigations
| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Bold text causes crowding in dense traffic | Medium | Increase Y-offset as specified; validate at default + max icon size | Engineering |
| Font fallback mismatch across devices | Low | Keep fallback stack and verify on MapLibre-supported fonts | Engineering |

## 7) Acceptance Gates
- Callsign suppression and existing label semantics remain unchanged.
- Both ADS-B and OGN labels are bold, +1 size, and offset further from icon center.
- All verification gates pass.

## 8) Rollback Plan
- Revert constants only in `AdsbTrafficOverlay.kt` and `OgnTrafficOverlay.kt`.
- No schema/data migration required.
