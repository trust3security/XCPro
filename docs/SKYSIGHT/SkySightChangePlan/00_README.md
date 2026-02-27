# SkySight integration plan bundle

This folder is a "hand-off bundle" for an autonomous coding agent (Codex) to implement SkySight-related overlays inside XC Pro.

## How to use
1. Start with `SkySightChangePlan.md` (master plan).
2. Follow `07_CODEX_TASKS_PHASED.md` in order. It matches the repository's required phased model (Phase 0..4).
3. Treat `03_API_AND_AUTH.md` as a contract stub. The exact response shapes / endpoints must be confirmed against the real API.
4. For SkySight satellite overlay behavior and settings, use `18_SATELLITE_OVERLAY_IMPLEMENTATION_PLAN.md`.

## Status update (2026-02-24)
- Forecast overlays are implemented and running in production wiring.
- SkySight satellite runtime overlays are implemented with:
  - satellite imagery tiles,
  - radar tiles,
  - lightning tiles,
  - animation and bounded history frames.

## Non-negotiables (repo rules)
- MVVM + UDF + SSOT. No bypassing the use-case layer.
- UI must not import data.
- ViewModels must not use Android UI types and must not do file/network work.
- Map runtime (MapLibre types) must stay inside UI/runtime controllers, not in ViewModels.
- Vendor neutrality rule: do NOT put vendor names in production strings or public APIs.

## Reality check
SkySight's public website terms include restrictions around mirroring/redistribution and reverse engineering. Before implementing aggressive caching, proxying, or scraping, ensure you have written permission for the intended usage.
