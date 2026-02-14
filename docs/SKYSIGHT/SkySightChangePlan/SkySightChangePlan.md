# SkySightChangePlan (master)

Goal: integrate SkySight forecast overlays into XC Pro's MapLibre map, using the existing overlay architecture and repo rules.

This plan is split into focused documents:

- `01_SCOPE_AND_MILESTONES.md` - what we will build (and what we will not)
- `02_SYSTEM_DESIGN.md` - XC Pro architecture-compliant design
- `03_API_AND_AUTH.md` - auth + API contract stub (fill in after confirming actual responses)
- `04_DATA_MODEL_AND_STORAGE.md` - SSOT ownership, models, caching, preferences
- `05_UI_AND_MAP_INTEGRATION.md` - MapLibre raster overlay + UI controls + gestures
- `06_TEST_PLAN.md` - unit/integration/UI testing strategy
- `07_CODEX_TASKS_PHASED.md` - executable work breakdown in required Phase 0..4 format
- `08_RISKS_LEGAL_AND_COMPLIANCE.md` - ToS/privacy/security risks and mitigations

Definition of done:
- A user can enable a forecast overlay on the map, pick a parameter, pick a forecast time, and adjust opacity.
- The overlay survives style reloads and app restarts (preferences restored).
- Auth is handled securely (no secrets in logs, no plaintext storage of tokens).
- All required repo gates pass: `enforceRules`, unit tests, debug assemble.
- No architecture rule violations (SSOT ownership explicit, no ViewModel I/O, no MapLibre in ViewModels).

