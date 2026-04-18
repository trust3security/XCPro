# OGN.md - XCPro OGN Runtime Behavior (Current)

Purpose:
Describe what XCPro currently does for OGN traffic in production code.

Authoritative references:
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/OGN/OGN_PROTOCOL_NOTES.md`

## Scope

Implemented:
- Live OGN downlink from `aprs.glidernet.org:14580`.
- Map overlay toggle, icon-size settings, configurable receive-radius settings, and display-update mode settings.
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
   - Scia bottom-sheet toggle: `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt`
   - Preferences: `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
   - Trail selection preferences: `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt`
2. ViewModel/use-case orchestration:
   - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt` (`OgnTrafficUseCase`)
   - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
3. Repository and parsing:
   - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnAprsLineParser.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnDdbRepository.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailRepository.kt`
4. Map runtime rendering:
   - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/map/OgnThermalOverlay.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/map/OgnGliderTrailOverlay.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
5. OGN details UI:
   - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnMarkerDetailsSheet.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalDetailsSheet.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`

## Runtime Semantics

Streaming gate:
- OGN streaming is enabled only when:
  `allowSensorStart && mapVisible && ognOverlayEnabled`
- Thermal detection consumes the same OGN stream and does not run when OGN streaming is off.
- `mapVisible` is treated as lifecycle `STARTED` visibility; transient `ON_PAUSE` does not force
  OGN disconnect, which reduces reconnect churn when returning from options/modals.

Center source:
- Query center is updated from ownship GPS (`mapLocation`).
- OGN does not use camera center fallback.

Radius contract:
- User-configurable receive radius around ownship: `20..300 km` (`150 km` default).
- Implemented on both:
  - APRS login filter: `r/<lat>/<lon>/<radiusKm>`
  - Client-side haversine check: `<= radiusKm` around latest requested GPS center.
    - If requested center is temporarily unavailable, fallback uses active subscription center.
- Advanced auto-radius mode (optional):
  - Uses flight context first (`isFlying`, ownship speed), then zoom as a secondary adjustment.
  - Effective radius buckets: `40 / 80 / 150 / 220 km`.
  - Bucket change policy: candidate must remain stable for `30s` and changes are applied no faster than every `60s`.
  - Manual slider value is preserved as fallback when auto mode is turned off.

Login identity:
- APRS login uses a persisted per-install client callsign (`^[A-Z][A-Z0-9]{2,8}$`).
- Passcode is derived from the active client callsign at connect time.
- This avoids cross-device disconnects from shared login identity collisions.

Reconnect behavior:
- Waits for a valid center before opening stream.
- Reconnects when center moves >= 20 km from the active subscription center.
- Reconnects when effective receive radius changes so the APRS login filter uses the new radius.
- Policy reconnects for center/radius changes are immediate (no backoff delay).
- Connection state stays `CONNECTING` until server `logresp ... verified` or first valid traffic frame.
- Exponential backoff: `1s -> 2s -> ... -> 60s`.
- Keepalive interval: `60s`.
- Stall timeout: `120s`.
- Stall/liveness authority is inbound-only:
  - any inbound line (traffic or server comment) updates stream activity
  - outbound keepalive writes do not reset stall timer

Target lifecycle:
- Stale visual threshold: `60s` (lower alpha).
- Target eviction threshold: `120s`.
- Source-time ordering behavior:
  - Parser extracts APRS source-time candidates for `/hhmmssh` and `@ddhhmmz` forms.
  - Repository applies anti-rewind guard against older source-time frames.
  - Repository keeps per-target latest accepted timed-source timestamp authority,
    so delayed older timed frames are rejected even after an untimed fallback commit.
  - Repository enforces timed-source lock for untimed frames:
    - if a target has recent timed history, untimed frames are non-authoritative
    - untimed fallback is allowed only after timed-source silence window (`30s`)
  - Frames that imply implausible motion are dropped before position commit.
    - motion plausibility uses source-time deltas when available, with monotonic
      fallback deltas when source time is missing.
  - Snapshot diagnostics expose dropped-frame counters for both policies.
    - debug panel shows both counters as `Drops (order/motion)`.

Display update mode (UI-only):
- `Real-time`: no OGN map redraw throttling.
- `Balanced`: OGN map redraw cadence capped to about `1s`.
- `Battery`: OGN map redraw cadence capped to about `3s`.
- Applies to OGN traffic, thermal, and glider-trail overlays only.
- Does not change APRS ingest, parser, or repository update cadence.

Thermal hotspot lifecycle:
- Thermal entry threshold: `verticalSpeed >= 0.3 m/s`.
- Thermal continuation threshold: `verticalSpeed >= 0.15 m/s`.
- Confirmed thermal minimums:
  - duration `>= 25s`
  - sample count `>= 4`
  - peak climb `>= 0.5 m/s`
  - altitude gain `>= 35m` when altitude samples are available
  - cumulative turn `>730 deg` (fake climb suppression)
- Thermal continuity grace: `20s` without strong-climb sample before finalization.
- Missing target timeout: `45s` since last fresh OGN sample before finalization.
- Fresh-sample de-dup cache keeps present targets protected from stale re-entry and prunes absent target IDs after the missing-timeout window.
- Hotspot retention is user-configurable from Hotspots settings:
  - `1..23h`: rolling window by hotspot wall-clock age
  - `All day`: keep until local midnight (`12:00 AM`)
- Hotspot display share is user-configurable from Hotspots settings:
  - `5..100%`: keeps only the strongest top share of retained hotspots
  - `5%` keeps only top 5% strongest climbs; `100%` keeps all retained hotspots
- Area dedupe publishes one best hotspot per local area radius (highest climb winner).
- Thermal timeout/finalization housekeeping runs from repository-managed timers, not only from upstream target emissions.

OGN glider trail lifecycle:
- Trail history retention window: `20 minutes`.
- Trails are derived only from fresh OGN samples (`lastSeenMillis` strictly increasing per target).
- Segment creation requires:
  - valid start/end coordinates
  - vertical speed uses finite sample when available; otherwise neutral fallback (`0.0 m/s`)
  - minimum movement distance (`>= 15 m`)
  - anti-jump guard (`<= 25 km` between consecutive samples)
- Color mapping uses snail trail 19-step vario ramp (deep navy sink -> yellow zero -> dark purple climb).
- Width mapping is asymmetric:
  - stronger sink -> thinner (`2.0 px` down to `0.8 px`)
  - stronger climb -> thicker (`2.0 px` up to `7.5 px`)
- Segment cap: `24,000` total in-memory segments.
- Map runtime render cap: newest `12,000` segments (safety cap to bound map-side allocation).
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
  - DDB `aircraftTypeCode == 2` (tow/tug aircraft): `MRP -> R.drawable.ic_ogn_yellowtug`, `FOO -> R.drawable.ic_ogn_whitetug`, else `R.drawable.ic_ogn_redtug`
  - DDB `aircraftTypeCode == 3` (helicopter): `R.drawable.ic_adsb_helicopter`
  - DDB `aircraftTypeCode == 4` (paraglider): `R.drawable.ic_ogn_hangglider`
  - DDB `aircraftTypeCode == 5` (hang glider): `R.drawable.ic_ogn_hangglider`
  - DDB `aircraftTypeCode == 6` (balloon): `R.drawable.ic_adsb_balloon`
  - DDB `aircraftTypeCode == 7` (UAV): `R.drawable.ic_adsb_drone`
  - DDB `aircraftTypeCode == 8` (static object): `R.drawable.ic_ogn_static`
  - Unknown/unsupported type: `R.drawable.ic_ogn_ufo`
- Satellite readability mode (runtime rendering only):
  - Condition: SkySight satellite overlay is enabled and any satellite layer
    (`imagery` or `radar` or `lightning`) is active.
  - Effect: glider icon mapping uses white contrast style image id `ogn_icon_glider_satellite`.
  - Non-glider icon mappings remain unchanged.
  - Update semantics are immediate for OGN traffic targets: contrast toggle forces
    an immediate OGN traffic overlay rerender.
  - On disable, glider icon mapping returns to default through the same immediate
    forced rerender path.
- Close-proximity red override (runtime rendering only):
  - Applies to glider icons only.
  - Condition: target distance `<= 1 km` and relative altitude within `+-300 ft` (inclusive).
  - Effect: glider icon uses ADS-B red `#FF1744`.
  - Precedence: overrides the normal glider altitude-band green/blue/black icon variants.
  - Labels remain unchanged.
  - Targets without a valid relative-altitude delta do not enter the red override.
- OGN trails:
  - Source/layer IDs:
    - `ogn-glider-trail-source`
    - `ogn-glider-trail-line-layer`
  - Rendered below OGN thermal circles/icons for readability.
- Layers:
  - `ogn-traffic-icon-layer`
  - `ogn-traffic-label-top-layer`
  - `ogn-traffic-label-bottom-layer`
- Overlay initialization lifecycle:
  - OGN overlay initialization is manager-owned (`MapOverlayManager`) and occurs
    on overlay creation/style lifecycle, not on every render call.
  - Pending throttled OGN render jobs are canceled on map detach to avoid
    stale post-detach renders.
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
  - Trails render only when both OGN overlay and `Show Scia` are enabled.
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

Close-zoom marker label semantics:
- OGN markers remain two-line labels when labels are visible.
- One line remains identifier + distance (`competition number/registration tail + distance`).
- The relative-height line now appends ground speed as `height | speed` when target speed is valid.
- Relative-height line placement keeps the existing altitude-band behavior:
  - above/near ownship: relative-height line renders above the identifier/distance line
  - below ownship: relative-height line renders below the identifier/distance line
- Speed formatting uses the general units SSOT (`UnitsPreferences.speed` via `UnitsFormatter.speed`),
  so OGN labels follow `km/h`, `kt`, `mph`, or `m/s` automatically.

## Reliability Hardening Status

- Connectivity reliability hardening (ordering + liveness + DDB cadence) implemented
  per:
  - `docs/OGN/CHANGE_PLAN_OGN_CONNECTIVITY_RELIABILITY_2026-03-01.md`

## Identity and Privacy Behavior

- DDB refresh and cache are handled by `OgnDdbRepository`.
- DDB refresh due-check runs during active sessions (not reconnect-only), with
  repository-side cadence check.
- DDB refresh outcome semantics:
  - successful refresh keeps normal cadence checks
  - `NotDue` responses advance the repository-side cadence anchor to avoid
    repeated minute-level relaunch attempts while DDB is already fresh
  - failed refresh (HTTP/transport/parser/empty-payload failure) retries on a
    bounded short window (`2..5 minutes`) instead of waiting for hourly cadence
- If DDB marks target as `tracked == false`, target is removed from displayed list.
- DDB lookup is type-aware (`device_type + device_id`) when type is known, with unknown-safe fallback.
- If DDB identity is missing or not identified, labels fall back to non-identifying id/callsign fields.
- If DDB `aircraft_type` is missing, icon type falls back to APRS typed `idXXYYYYYY` decode when present.

## Tests

Parser and policy:
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnAprsLineParserTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnSubscriptionPolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrackStabilizerTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnDdbJsonParserTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnGliderTrailRepositoryTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt`

Map runtime lifecycle:
- `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerOgnLifecycleTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/OgnGliderTrailOverlayRenderPolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/ui/MapRuntimeControllerWeatherStyleTest.kt`

Preferences and VM wiring:
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrailSelectionPreferencesRepositoryTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`
