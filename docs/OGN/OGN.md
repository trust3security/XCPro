# OGN.md - XCPro OGN Runtime Behavior (Current)

Purpose:
Describe what XCPro currently does for OGN traffic in production code.

Authoritative references:
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/OGN/OGN_PROTOCOL_NOTES.md`

## Scope

Implemented:
- Live OGN downlink from `aprs.glidernet.org:14580`.
- Map overlay toggle and icon-size settings.
- OGN ownship settings for `Own FLARM ID` and `Own ICAO24` (6-hex each).
- Dedicated `Show Thermals` toggle and persisted preference.
- Per-aircraft trail visibility selection in the bottom-sheet OGN tab (persisted).
- On-map glider icons + labels, track rotation, stale fade, and stale eviction.
- On-map thermal hotspot overlay derived from OGN glider climb data.
- On-map per-glider OGN trail segments with sink/climb color + width encoding.
- OGN marker tap details sheet (ADS-B-style bottom sheet).
- OGN thermal hotspot tap details sheet (partial-sheet capable).
- DDB enrichment for labels and privacy flags.
- Typed ownship suppression in repository SSOT (ownship OGN target is filtered before UI/trail/thermal consumers).
- Connection/reconnect snapshot shown in debug panel (debug builds only).

Not implemented:
- OGN uplink (phone position transmit).
- OGN data use in navigation, task logic, scoring, or collision avoidance.

## End-to-End Path

1. UI toggle and settings:
   - Scia bottom-sheet toggle: `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
   - Preferences: `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
   - Trail selection preferences: `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt`
2. ViewModel/use-case orchestration:
   - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` (`OgnTrafficUseCase`)
   - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
3. Repository and parsing:
   - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
   - `feature/map/src/main/java/com/example/xcpro/ogn/OgnAprsLineParser.kt`
   - `feature/map/src/main/java/com/example/xcpro/ogn/OgnDdbRepository.kt`
   - `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalRepository.kt`
   - `feature/map/src/main/java/com/example/xcpro/ogn/OgnGliderTrailRepository.kt`
4. Map runtime rendering:
   - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/OgnThermalOverlay.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/OgnGliderTrailOverlay.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
5. OGN details UI:
   - `feature/map/src/main/java/com/example/xcpro/ogn/OgnMarkerDetailsSheet.kt`
   - `feature/map/src/main/java/com/example/xcpro/ogn/OgnThermalDetailsSheet.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`

## Runtime Semantics

Streaming gate:
- OGN streaming is enabled only when:
  `allowSensorStart && mapVisible && ognOverlayEnabled`
- Thermal detection consumes the same OGN stream and does not run when OGN streaming is off.

Center source:
- Query center is updated from ownship GPS (`mapLocation`).
- OGN does not use camera center fallback.

Radius contract:
- 300 km diameter around ownship.
- Implemented as 150 km radius filter on both:
  - APRS login filter: `r/<lat>/<lon>/150`
  - Client-side haversine check: `<= 150 km` around latest requested GPS center.
    - If requested center is temporarily unavailable, fallback uses active subscription center.

Reconnect behavior:
- Waits for a valid center before opening stream.
- Reconnects when center moves >= 20 km from the active subscription center.
- Connection state stays `CONNECTING` until server `logresp ... verified` or first valid traffic frame.
- Exponential backoff: `1s -> 2s -> ... -> 60s`.
- Keepalive interval: `60s`.
- Stall timeout: `120s`.

Target lifecycle:
- Stale visual threshold: `60s` (lower alpha).
- Target eviction threshold: `120s`.

Thermal hotspot lifecycle:
- Thermal entry threshold: `verticalSpeed >= 0.3 m/s`.
- Thermal continuation threshold: `verticalSpeed >= 0.15 m/s`.
- Confirmed thermal minimums:
  - duration `>= 25s`
  - sample count `>= 4`
  - peak climb `>= 0.5 m/s`
  - altitude gain `>= 35m` when altitude samples are available
- Thermal continuity grace: `20s` without strong-climb sample before finalization.
- Missing target timeout: `45s` since last fresh OGN sample before finalization.
- Fresh-sample de-dup cache keeps present targets protected from stale re-entry and prunes absent target IDs after the missing-timeout window.
- Hotspots persist in-memory for app session lifetime and clear on app restart.
- Thermal timeout/finalization housekeeping runs from repository-managed timers, not only from upstream target emissions.

OGN glider trail lifecycle:
- Trail history retention window: `20 minutes`.
- Trails are derived only from fresh OGN samples (`lastSeenMillis` strictly increasing per target).
- Segment creation requires:
  - valid start/end coordinates
  - finite vertical speed
  - minimum movement distance (`>= 15 m`)
  - anti-jump guard (`<= 25 km` between consecutive samples)
- Color mapping uses snail trail 19-step vario ramp (deep navy sink -> yellow zero -> dark purple climb).
- Width mapping is asymmetric:
  - stronger sink -> thinner (`2.0 px` down to `0.8 px`)
  - stronger climb -> thicker (`2.0 px` up to `7.5 px`)
- Segment cap: `24,000` total in-memory segments.
- Trails persist in-memory for app session lifetime and clear on app restart.

Ownship self-filter lifecycle:
- Match policy is typed and exact:
  - FLARM targets match only configured own FLARM hex.
  - ICAO targets match only configured own ICAO hex.
  - UNKNOWN type never auto-matches (false-negative preferred over false-positive suppression).
- Suppression happens in `OgnTrafficRepository` before `targets` publish.
- Suppressed canonical keys are exposed in snapshot diagnostics (`suppressedTargetIds`).
- Thermal/trail repositories consume suppression state and purge existing ownship-derived artifacts in-session.

Course parsing:
- `000` is treated as unknown track.
- `001..360` are accepted.
- `>360` is rejected as invalid.

## On-Map Rendering Behavior

- Icon:
  - DDB `aircraftTypeCode == 1` (glider/sailplane): `R.drawable.ic_adsb_glider`
  - DDB `aircraftTypeCode == 2` (tow/tug aircraft): `R.drawable.ic_ogn_tug`
  - DDB `aircraftTypeCode == 3` (helicopter): `R.drawable.ic_adsb_helicopter`
  - DDB `aircraftTypeCode == 4` (paraglider): `R.drawable.ic_ogn_hangglider`
  - DDB `aircraftTypeCode == 5` (hang glider): `R.drawable.ic_ogn_hangglider`
  - DDB `aircraftTypeCode == 6` (balloon): `R.drawable.ic_adsb_balloon`
  - DDB `aircraftTypeCode == 7` (UAV): `R.drawable.ic_adsb_drone`
  - DDB `aircraftTypeCode == 8` (static object): `R.drawable.ic_ogn_static`
  - Unknown/unsupported type: `R.drawable.ic_ogn_ufo`
- OGN trails:
  - Source/layer IDs:
    - `ogn-glider-trail-source`
    - `ogn-glider-trail-line-layer`
  - Rendered below OGN thermal circles/icons for readability.
- Layers:
  - `ogn-traffic-icon-layer`
  - `ogn-traffic-label-layer`
- Viewport culling: only targets inside visible map bounds render.
- Render cap: 500 targets.
- Label resolution priority:
  - DDB competition number
  - DDB registration
  - device hex
  - callsign fallback
- Overlay disabled state:
  - UI passes `emptyList()` to runtime overlay renderer.
- OGN trail overlay visibility gate:
  - Trails render when OGN overlay is enabled.
  - Per-aircraft filtering is applied from selected aircraft keys (bottom-sheet OGN tab).

Marker tap behavior:
- OGN markers support hit testing on icon and label layers.
- OGN marker tap opens OGN details bottom sheet.
- OGN thermal hotspot tap opens thermal details bottom sheet.
- OGN and ADS-B details sheets are mutually exclusive.
- OGN thermal details are mutually exclusive with OGN marker and ADS-B details.
- Selection clears when the target disappears or OGN overlay is disabled.
- Thermal selection clears when hotspot disappears or `Show Thermals` is disabled.
- Marker selection uses canonical typed target keys internally (`FLARM:HEX` / `ICAO:HEX` / fallback),
  with legacy key compatibility during matching.

## Identity and Privacy Behavior

- DDB refresh and cache are handled by `OgnDdbRepository`.
- If DDB marks target as `tracked == false`, target is removed from displayed list.
- DDB lookup is type-aware (`device_type + device_id`) when type is known, with unknown-safe fallback.
- If DDB identity is missing or not identified, labels fall back to non-identifying id/callsign fields.
- If DDB `aircraft_type` is missing, icon type falls back to APRS typed `idXXYYYYYY` decode when present.

## Tests

Parser and policy:
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnAprsLineParserTest.kt`
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnSubscriptionPolicyTest.kt`
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrackStabilizerTest.kt`
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnDdbJsonParserTest.kt`
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnGliderTrailRepositoryTest.kt`
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnThermalRepositoryTest.kt`

Preferences and VM wiring:
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrailSelectionPreferencesRepositoryTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
