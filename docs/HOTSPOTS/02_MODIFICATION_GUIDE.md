# HOTSPOTS Modification Guide

Use this guide when changing thermal hotspot behavior.

## 1) File map

- Runtime policy:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt`
- Hotspot model:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalModels.kt`
- Retention constants/helpers:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRetention.kt`
- Display share constants/helpers:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnHotspotsDisplayPercent.kt`
- Settings persistence:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
- Hotspots settings UI + VM:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/HotspotsSettingsScreen.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/HotspotsSettingsViewModel.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/HotspotsSettingsUseCase.kt`

## 2) Safe change recipes

## 2.1 Change turn threshold

If product wants a different anti-fake turn gate:

1. Update threshold constant in thermal repository.
2. Update tests for below/above threshold.
3. Update user-facing docs in this folder.

Do not hardcode threshold in UI layer.

## 2.2 Change area dedupe granularity

If dedupe feels too aggressive or too sparse:

1. Tune `AREA_DEDUP_RADIUS_METERS`.
2. Re-run crowding test fixtures.
3. Validate map readability and area winner behavior at typical zoom levels.

Do not dedupe in overlay rendering code; keep dedupe in repository output policy.
Keep winner ordering deterministic: active/recent first, then strength tie-breaks.

## 2.2A Keep hotspot anchors stable

Hotspot map position is a stable best-climb anchor, not a moving centroid.

1. Keep the rendered hotspot `latitude` / `longitude` tied to the strongest climb sample seen for that hotspot.
2. Do not re-center the hotspot on every later circling sample just because the pilot drifted while thermalling.
3. Only move the hotspot anchor when a later sample has a strictly stronger climb than the current anchor.

Why this exists:

- SCIA trail already shows the real flown circle/drift path.
- A moving hotspot dot suggests the thermal core itself moved, which is misleading.
- Stable anchors better match tactical pilot expectations when revisiting lift.

## 2.2B Selected thermal context overlay

Selected thermal context is a read-only projection over hotspot SSOT + raw SCIA trail segments.

1. Keep segment association in use-case/domain code, not in Composables or map overlay classes.
2. Match SCIA segments by hotspot `sourceTargetId` and hotspot monotonic time window.
3. Keep the selected overlay render-only:
   highlighted loop, occupancy hull, start/latest markers, and drift line.
4. Clear the selected overlay when selection disappears, the hotspot disappears, or thermals are hidden.
5. Keep the base hotspot overlay and base SCIA overlay unchanged; selection visuals are additive only.

Current owners:

- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnSelectedThermalContextProjector.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnSelectedThermalOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnThermalDetailsSheet.kt`

Defaults:

- Occupancy area = convex hull of valid loop points.
- Drift = observed `start -> latest`.
- Overlay scope = selected hotspot only.

## 2.2C Selected thermal details metrics

If product wants more or fewer selected-thermal metrics:

1. Derive the metric in the selected thermal projector or adjacent domain mapper.
2. Keep formatting and units adaptation in the details sheet only.
3. Use monotonic time for duration and wall time for age/freshness.
4. Do not recompute drift, age, duration, or altitude gain ad hoc in the Composable.

Current detail rows sourced from selected context:

- Age
- Drift
- Duration
- Altitude Gain

## 2.3 Change retention options

If slider range changes:

1. Update constants in `OgnThermalRetention.kt`.
2. Update DataStore clamping and UI label text.
3. Add test coverage for new lower/upper bounds.

Keep all-day semantics explicit (local midnight) unless product changes contract.

## 2.4 Change display percentage options

If hotspot density filtering changes:

1. Update `OgnHotspotsDisplayPercent.kt` bounds/default.
2. Keep filtering in `OgnThermalRepository.publishHotspots(...)` strength-order path.
3. Preserve filtering order: area winner ordering -> area dedupe -> top `N%` keep.
4. Update repository and preferences tests for clamping + strongest-first selection.

Do not apply percentage filtering in map overlay rendering code.

Why this exists:

- Keep map readability in dense traffic.
- Surface strongest climbs first for tactical relevance.
- Avoid rendering large clusters of weaker/duplicate hotspots.

## 2.5 Change modal behavior

If Hotspots settings entry behavior changes:

1. Keep one source of truth for settings state in ViewModel/repository.
2. Preserve top icon and color language consistency with existing modal/settings surfaces.
3. Validate back navigation and map return behavior.

## 2.6 Keep hotspots visible in map z-order

If hotspots seem to disappear when forecast/weather/satellite layers are enabled:

1. Verify thermal layer IDs are included in forecast/weather/satellite anchor lists.
2. Keep thermal circles/labels above heavy raster/vector weather layers.
3. Re-run map QA with forecast, weather rain, and satellite overlays enabled simultaneously.

Current thermal layer IDs:

- `ogn-thermal-circle-layer`
- `ogn-thermal-label-layer`

Related anchor lists:

- `feature/map/src/main/java/com/trust3/xcpro/map/ForecastRasterOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/SkySightSatelliteOverlay.kt`

## 2.7 Keep hotspot toggle semantics coherent

If users can enable Hotspots while OGN overlay is off, they may see no hotspots.

Required behavior:

1. Turning Hotspots on must also enable OGN overlay (same pattern as SCIA toggle path).
2. Keep this contract in coordinator/use-case paths, not in ad-hoc Composable state logic.

Current implementation uses option 1.

## 2.8 Preserve crash-hardening guards

Hotspot runtime must fail-soft, not fail-fast, in production rendering paths.

1. Keep confirmed-tracker hotspot IDs recoverable in repository logic (do not reintroduce hard invariant crashes for missing IDs).
2. Keep invalid coordinates filtered before map feature construction.
3. Keep thermal overlay render calls wrapped in error guards at runtime manager level.

Do not replace these guards with `error(...)`/`require(...)` paths in live runtime flows.

## 2.9 Change thermal hotspot color mapping range

If climb-color spread needs retuning:

1. Update `MAX_ABS_VARIO_KTS` in `OgnThermalColorScale.kt`.
2. Keep UI-facing range intent in knot units; convert once to m/s for internal mapping.
3. Update/add unit tests for clamp boundaries and center behavior.

Current hotspot color clamp:

- `+/-30 kt` mapped across a 19-step snail palette.

## 3) Common mistakes to avoid

- Computing hotspot retention in Composable code.
- Mixing monotonic and wall time in the same comparison.
- Filtering by turn using raw non-normalized heading differences.
- Selecting area winners in overlay code instead of repository.
- Applying display-percent filtering before area dedupe (changes intended output share).
- Allowing hotspot toggle-on while OGN overlay is off without explicit UX contract.
- Omitting thermal layer IDs from cross-overlay anchor lists.
- Reintroducing fatal hotspot-ID invariants in repository paths.
- Assuming all thermal coordinates are valid without render-time guarding.
- Rebuilding selected-thermal geometry inside overlay classes instead of a domain/use-case projector.
- Treating selected thermal drift as inferred wind instead of the observed start-to-latest presentation vector.
- Rendering occupancy hulls for every hotspot instead of only the selected hotspot.
- Updating docs only partially after behavior changes.

## 4) Performance notes

- Thermal policy runs on frequent target updates; keep O(n) per update where possible.
- Area dedupe should avoid expensive distance matrix calculations.
- Prefer grid bucketing over pairwise clustering for runtime cost control.

## 5) Replay/determinism note

- Time-dependent policies must remain deterministic for deterministic clock/test inputs.
- Tests should use fake clocks and explicit timestamp progression.

## 6) Re-pass operations note

- If targeted Gradle test runs fail with transient file-lock/delete errors under `transformDebugClassesWithAsm`, rerun the same command after daemon stop/retry.
- Record transient infra/build artifacts separately from product defects; do not mix them into hotspot policy findings.
