# SKYSIGHT_MVP_EXECUTION_ORDER.md

This is the practical order of operations so XCPro can ship an MVP quickly.

## Step 1 — Implement Stage A (no SkySight forecast endpoints needed)
Implement provider-neutral forecast overlays + FakeForecastProvider:
- Follow: `TRACK_A_FAKE_FORECAST_PROVIDER.md`

Deliverable: overlay renders as raster tiles, supports:
- parameter selection (4 fake layers)
- time slider (hourly 24h)
- opacity
- legend
- long-press value callout (disabled in task-edit mode)

## Step 2 — Capture SkySight API evidence pack
Do not guess API paths. Generate evidence files:
- Follow: `SKYSIGHT_EVIDENCE_CAPTURE.md` and `10_SKYSIGHT_API_CONTRACT_UNBLOCK_MVP.md`

Commit redacted evidence under:
- `docs/integrations/skysight/evidence/`

## Step 3 — Implement Stage B (SkySight adapter)
Only after evidence exists:
- Implement SkySight adapters for ports:
  - catalog (parameters + time semantics)
  - tileTemplate
  - legend
  - value
- Replace FakeForecastProvider binding with SkySight binding via DI
- Keep provider-neutral UI strings (vendor neutrality)

## Step 4 — Hardening
- Rate-limit backoff
- Debounce slider changes
- Tile auth strategy correctness
- Legend correctness (fixed vs dynamic scaling)
- End-to-end smoke checks

## Verification commands (minimum)
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
