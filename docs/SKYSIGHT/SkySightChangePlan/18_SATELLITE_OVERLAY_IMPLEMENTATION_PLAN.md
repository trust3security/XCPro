# SkySight Satellite Overlay Implementation Plan

Date: 2026-02-24
Owner: XCPro Team
Status: Active

## Purpose

Implement real SkySight satellite overlays (imagery, radar, lightning) in XCPro SkySight controls,
using the captured SkySight tile contract and existing overlay runtime architecture.

## Confirmed Tile Contract (Captured Evidence)

From captured SkySight bundle evidence (`tmp_sat_chunk.txt`):

1. Satellite imagery tiles
- `https://satellite.skysight.io/tiles/{z}/{x}/{y}@2x?date=YYYY/MM/DD/HHmm&mtg=true`

2. Radar tiles
- `https://satellite.skysight.io/radar/{z}/{x}/{y}@2x?date=YYYY/MM/DD/HHmm`

3. Lightning tiles
- `https://satellite.skysight.io/lightning/{z}/{x}/{y}@2x?date=YYYY/MM/DD/HHmm`
- source-layer: `lightning`

4. Time stepping
- 10-minute cadence.
- Runtime evidence shows a short history loop with up to 3 frames.

## Implemented User Options (SkySight Tab)

1. `Sat View` map style toggle (existing transient map-style behavior).
2. `Enable SkySight satellite overlays`.
3. Layer toggles:
- `Satellite imagery (clouds)`
- `Rain radar`
- `Lightning`
4. `Animate loop` toggle.
5. `History frames` slider (1-3 frames, 10-minute step).

## Architecture Contract

SSOT ownership:
- Satellite settings are persisted in `ForecastPreferencesRepository`.
- UI state is exposed through `ForecastOverlayRepository` -> `ForecastOverlayUiState`.
- Runtime render owner is `MapOverlayManager` -> `SkySightSatelliteOverlay`.

Dependency direction:
- UI -> `ForecastOverlayViewModel` -> use-cases -> preferences/repository SSOT.
- No MapLibre types in ViewModel or use-cases.

Runtime behavior:
- Overlay render uses selected SkySight time (`selectedTimeUtcMs`) with safety clamp to near-live.
- Supports simultaneous layer combinations:
  - imagery only
  - radar only
  - lightning only
  - any combination of the three.

## OGN Readability Coupling (Implemented 2026-02-24)

Goal:
- Improve OGN glider readability on dark/complex satellite imagery backgrounds.

Behavior:
- When SkySight satellite overlay is enabled and at least one satellite layer is active
  (imagery, radar, or lightning), OGN glider icon mapping switches to a white-contrast
  icon variant.
- Non-glider OGN icon mappings remain unchanged.
- On disable, icon mapping returns to normal glider icon mapping.

Runtime implementation:
- Runtime state owner: `MapOverlayManager` (`ognSatelliteContrastIconsEnabled`).
- OGN render owner: `OgnTrafficOverlay`.
- White glider style image id: `ogn_icon_glider_satellite`.
- Bitmap source: existing glider drawable tinted white at style-image registration time
  (no additional drawable asset required).

Update policy (intentional):
- Immediate one-shot refresh on mode transition.
- Existing markers are refreshed once when contrast mode changes, then normal OGN
  update cadence resumes.
- No continuous forced redraw loop is introduced.

## Verification

Required checks:

```bash
./gradlew --no-daemon --no-configuration-cache enforceRules testDebugUnitTest assembleDebug
```

Local implementation check:

```bash
./gradlew :feature:map:compileDebugKotlin
```

## Risks and Mitigations

1. Risk: tile host header/origin mismatch.
- Mitigation: include `satellite.skysight.io` in `SkySightMapLibreNetworkConfigurator` host allowlist.

2. Risk: animation jitter/flicker during style reload.
- Mitigation: `MapOverlayManager` re-creates and reapplies satellite overlay on style changes.

3. Risk: stale or unavailable frames when user selects future times.
- Mitigation: runtime clamp to near-live source time and 10-minute bucket alignment.

## Sources

1. Captured runtime evidence:
- `tmp_sat_chunk.txt`

2. Product behavior context:
- `https://kb.naviter.com/en/kb/weather-layers/`
