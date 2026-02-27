# SkySight Multi Overlay Implementation Notes

Date: 2026-02-17
Owner: XCPro Team
Status: Research complete, implementation guidance ready

## Purpose

Document what SkySight currently supports for concurrent overlays, and define
the safest XCPro implementation shape for parity.

## Summary

SkySight supports:

- One primary weather layer (selected forecast product).
- Optional wind overlay on top of that primary layer.

Confirmed examples:

- Rain + wind
- Thermal + wind
- Thermal height + wind

Not confirmed as user-selectable behavior:

- Multiple independent primary weather layers at the same time
  (example: rain + thermal + cloud as three primary layers).

Important distinction for XCPro:
- SkySight parity is one primary + optional wind.
- XCPro currently contains a secondary non-wind overlay architecture path
  (primary + secondary + optional wind), with unit tests that assert
  pair behavior such as Convergence + Rain.

## Evidence Snapshot

Research source bundles (public web app assets):

- `https://skysight.io/secure/assets/main-BpvAoCUP.js`
- `https://skysight.io/secure/assets/index-H1TC4YZc.js`
- `https://skysight.io/secure/assets/glidelayer-CW0wpgll.js`

Observed behavior in the app code:

- Global wind mode store with values:
  - `off`
  - `barbs` or `arrows` (default symbol mode)
  - `particles` (streams)
- Per-product renderers add wind when wind mode is enabled.
- Wind source path uses `/wind/{z}/{x}/{y}/{param}`.
- Symbol wind uses barbs/arrows; streams use a dedicated particle layer.

Convergence-specific verification is documented here:

- `docs/SKYSIGHT/SkySightChangePlan/16_CONVERGENCE_AVAILABILITY_AND_IMPLEMENTATION_NOTES.md`
  - Confirms convergence product id `wblmaxmin` in authenticated SkySight
    artifacts and confirms route-level support.
  - Notes remaining point-value field mapping caveat for convergence.

## Product Options Observed

Routes/products currently exposed include:

- `thermal-strength`
- `thermal-height`
- `thermal-depth`
- `star-rating`
- `significant-weather`
- `glide-range`
- `cu-depth`
- `cu-cloudbase`
- `overdevelopment`
- `cloud-cover`
- `cape`
- `rain`
- `surface-temp`
- `surface-dewpoint`
- `lowest-cloud-layer`
- `cloud-tops`
- `smoke`
- `bom-rain`
- `wind-altitude`
- `surface-wind`
- `boundary-layer-wind`
- `bl-top-wind`
- `convergence`
- `ridge-lift`
- `wind-shear`
- `msl-pressure`
- `vertical-velocity-1km` to `vertical-velocity-7km`
- `flight-rules`
- `low-level-cloud-cover`
- `mid-level-cloud-cover`
- `high-level-cloud-cover`
- `thermal-hotspots`
- `turbulence-sfc-fl100`
- `turbulence-fl100-fl250`
- `turbulence-fl250-fl400`
- `visibility`
- `freezing-level`
- `density-altitude-surface`
- `relief`
- `oktas-cu`
- `cu-cloud-frac`
- `icing-base`
- `icing-top`

## What This Means For XCPro

For strict SkySight parity, XCPro should implement:

- Exactly one selected primary forecast parameter.
- A separate optional wind overlay layer that can be shown simultaneously.
- Wind render mode selection:
  - Arrow
  - Barb
  - Streams (optional follow-up if not in MVP)

This keeps behavior aligned with SkySight without introducing unsupported
multi-primary overlay complexity.

For XCPro extended mode (current code-path capability), allow:
- One selected primary non-wind overlay.
- One optional secondary non-wind overlay.
- One optional wind overlay.
- Maximum concurrent forecast branches: 3.

This extended mode is valid only if UI and use-case wiring preserve secondary
selection behavior.

## Confirmed XCPro regression (2026-02-25)

Root cause:
- SkySight tab chip actions currently route through a single-select ViewModel
  method that always disables secondary primary state.

Impact:
- Users cannot keep intended non-wind pair combinations from SkySight tab
  (example: Convergence + Rain), even though repository/runtime paths support it.

Required correction:
- Route SkySight tab non-wind chip actions through toggle selection use-cases.
- Expose explicit secondary non-wind controls in SkySight tab UI.
- Keep map runtime branch composition unchanged (primary + secondary + wind).

## Recommended XCPro Architecture Shape

State model:

- `selectedPrimaryParameterId` (single value)
- `windOverlayEnabled` (bool)
- `windDisplayMode` (ARROW | BARB | STREAMS)
- `selectedTimeUtcMs`
- `selectedRegion`
- `opacityPrimary`
- `opacityWind` (optional, if we want independent tuning)

Render model:

- Primary layer branch:
  - indexed fill or product-specific visual branch
- Wind layer branch:
  - symbol arrows/barbs from wind vector tiles, or particle stream path
- Deterministic z-order:
  - base map
  - primary forecast layer
  - wind overlay
  - operational overlays (airspace/task/traffic/user icon)

Data fetch model:

- Resolve primary tile spec from primary parameter + time + region.
- Resolve wind tile spec from selected wind parameter/time + region.
- Keep request cadence slot-based, not frame-based.

## MVP Scope Recommendation

Include now:

- Rain + wind symbols
- Thermal + wind symbols
- Thermal height + wind symbols
- Shared time slider for both primary and wind layers

Defer:

- Multi-primary stacking (example: rain + thermal together)
- Particle streams if performance or complexity risk is high
- Independent time offsets per overlay

## UX Guidance

In forecast UI:

- Keep a Primary Parameter selector (single select).
- Add a Wind overlay section:
  - Enabled toggle
  - Mode chips (Arrow, Barb, Streams if supported)
  - Optional wind parameter selector (surface wind, BL top wind, etc.)
- Keep clear status text when primary/wind data is unavailable.

## Validation Checklist

- Primary only renders correctly.
- Primary + wind renders together.
- Wind mode switch updates symbols correctly.
- Track Up and North Up keep primary and wind stable and readable.
- Time change updates both layers for the same slot.
- Missing wind data does not clear valid primary layer.
- Style reload re-applies both primary and wind layers.

## Open Questions

- Should XCPro expose Streams in MVP or ship Arrow/Barb first?
- Should wind use same parameter as primary wind products or a dedicated
  secondary wind parameter setting?
- Should primary and wind have separate opacity controls?
