# SkySightChangePlan (master)

Goal: integrate SkySight forecast overlays into XC Pro's MapLibre map, using the existing overlay architecture and repo rules.

Note:
- This file is the original forecast-track master plan.
- Satellite overlay integration is tracked in `18_SATELLITE_OVERLAY_IMPLEMENTATION_PLAN.md`.
- As of 2026-02-24, SkySight satellite runtime controls are implemented in code.
- As of 2026-02-25, a SkySight tab regression is confirmed: non-wind parameter chip
  selection currently forces secondary primary overlay state off, which blocks
  intended two-parameter combinations such as Convergence + Rain.

This plan is split into focused documents:

- `01_SCOPE_AND_MILESTONES.md` - what we will build (and what we will not)
- `02_SYSTEM_DESIGN.md` - XC Pro architecture-compliant design
- `03_API_AND_AUTH.md` - auth + API contract stub (fill in after confirming actual responses)
- `04_DATA_MODEL_AND_STORAGE.md` - SSOT ownership, models, caching, preferences
- `05_UI_AND_MAP_INTEGRATION.md` - MapLibre raster overlay + UI controls + gestures
- `06_TEST_PLAN.md` - unit/integration/UI testing strategy
- `07_CODEX_TASKS_PHASED.md` - executable work breakdown in required Phase 0..4 format
- `08_RISKS_LEGAL_AND_COMPLIANCE.md` - ToS/privacy/security risks and mitigations
- `18_SATELLITE_OVERLAY_IMPLEMENTATION_PLAN.md` - SkySight satellite imagery/radar/lightning implementation plan and runtime contract

Current overlay contract in XCPro (2026-02-25):
- Forecast overlays support up to three concurrent branches:
  - one selected primary non-wind overlay,
  - one optional secondary non-wind overlay,
  - one optional wind overlay.
- SkySight satellite overlays are independent from forecast branches and support
  any combination of:
  - satellite imagery (clouds),
  - radar,
  - lightning.

Known issues and corrective actions (2026-02-25):
- SkySight tab parameter selection wiring bypasses multi-overlay toggle use-cases and
  force-disables secondary primary selection.
  - Corrective action: route SkySight tab parameter toggles through the toggle
    use-case path and preserve secondary primary state transitions.
- SkySight tab currently does not expose explicit secondary-primary controls even though
  runtime and repository layers support them.
  - Corrective action: add clear secondary-primary controls in SkySight tab UI or
    formally remove secondary-primary feature support if parity-only mode is chosen.
- Time slot generation is currently static (06:00-20:00 local region day) and may produce
  no-content states near day boundaries.
  - Corrective action: add provider-backed slot availability contract or fallback behavior
    for out-of-coverage windows.

Definition of done:
- A user can enable forecast overlays on the map, pick parameter/time, and adjust global visual settings.
- The overlay survives style reloads and app restarts (preferences restored).
- A user can select and keep intended multi-overlay forecast combinations supported by the
  XCPro contract (including Convergence + Rain and optional wind overlay).
- A user can enable SkySight satellite overlays and independently toggle:
  - satellite imagery (clouds),
  - radar,
  - lightning,
  - animation,
  - history frames (1-3).
- When satellite overlays are active, OGN glider icons switch to white-contrast mode
  and refresh immediately on mode transition to improve readability.
- Auth is handled securely (no secrets in logs, no plaintext storage of tokens).
- All required repo gates pass: `enforceRules`, unit tests, debug assemble.
- No architecture rule violations (SSOT ownership explicit, no ViewModel I/O, no MapLibre in ViewModels).
