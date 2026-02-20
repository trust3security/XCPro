# RainViewer Integration - Index

This folder is the canonical execution entrypoint for RainViewer radar in XCPro.

Current status (2026-02-20):
- RainViewer overlay is implemented in code.
- Deep pass was rerun and converted into a hardening plan for industry-grade quality.
- Legacy/duplicate plan file removed to keep one active execution source.
- Core resilience tranche is implemented (single-flight refresh, adaptive backoff, stale/live metadata semantics).
- Playback-quality tranche is implemented (30m boundary-frame filter + window-aware transition tuning).
- Metadata transport hardening is implemented (weather-specific HTTP client, conditional requests, 304 path, version-warning compatibility, monotonic dedupe, locale-safe host normalization).
- Settings-surface alignment is implemented in Weather settings (frame mode, manual frame selection, smooth/snow toggles).
- Phase 2C control-visibility polish is implemented (manual-frame controls now show only when actionable).
- Phase 2D in-map confidence signaling is implemented (map chip + shared status mapping + compose/policy tests).
- Phase 4B re-pass found remaining compliance tasks: explicit policy decision for provider literals, auditable attribution-visibility evidence, and attribution regression guard.
- Phase 4B second re-pass found two extra deltas: attribution payload should be explicit link-form, and attribution visibility currently relies on implicit defaults.
- Phase 4B third re-pass found additional compliance deltas: attribution link clickability may be blocked by custom gesture overlay, and no in-app fallback clickable attribution surface is documented.
- Phase 4B implementation pass is applied (bounded deviation, link-form attribution payload, explicit attribution enable, gesture pass-through guard, fallback settings link, and attribution tests/docs).
- Phase 4B evidence artifact is documented in `docs/RAINVIEWER/evidence/RAINVIEWER_PROVIDER_PERMISSION_ARTIFACT.md`.
- Expanded Phase 5B map-runtime weather regression tranche is implemented (manager dedupe/reapply tests, runtime style callback tests, lifecycle cleanup tests, renderer policy tests).
- Remaining work is Phase 5C test hardening and full green verification.

Canonical read order:
1. `docs/RAINVIEWER/01_RAINVIEWER_INDUSTRY_HARDENING_PLAN_2026-02-20.md`
2. `docs/RAINVIEWER/evidence/RAINVIEWER_PERMISSION_NOTE.md`
3. `docs/RAINVIEWER/evidence/RAINVIEWER_PROVIDER_PERMISSION_ARTIFACT.md`
4. `docs/RAINVIEWER/maplibre_native_android_rainviewer_radar.md` (reference only)
5. `docs/ARCHITECTURE/ARCHITECTURE.md`
6. `docs/ARCHITECTURE/CODING_RULES.md`
7. `docs/ARCHITECTURE/PIPELINE.md`
8. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`

Removed as obsolete:
- `docs/RAINVIEWER/01_RAINVIEWER_RAIN_OVERLAY_REPLACEMENT_PLAN.md`
- `docs/RAINVIEWER/02_RAINVIEWER_BLEND_FADE_TRANSITION_PLAN.md`

Active objective:
- Raise RainViewer integration from "working" to "industry-grade and compliance-safe"
  with explicit resilience, stale-data UX, and stronger test coverage.
