# HOTSPOTS Active/Recent Winner Summaries

Date: 2026-02-27

## Product Summary

- Goal: when multiple hotspots exist within the same local area (~700 m),
show the one that is most useful now (active/recent), not only the historical
strongest climb.
- User impact: fewer misleading stale markers in crowded thermalling zones.
- No new settings required in first rollout.

## Engineering Summary

- Keep SSOT in `OgnThermalRepository`; do not move winner policy to UI.
- Replace strength-first-only winner ordering with explicit priority tiers:
active/recent first, then strength tie-break, then deterministic recency/ID.
- Thermal hotspot hue mapping uses a 19-step snail palette keyed by `maxClimbRateMps`
  and clamped to `+/-30 kt` (UI units, internally converted to m/s).
- Preserve existing contracts: retention, top-share filtering, `>730` turn gate,
and fail-soft crash guards.
- Add deterministic fake-clock tests in `OgnThermalRepositoryTest`.

## QA Summary

- Validate that, inside ~700 m, a newer active hotspot can replace an older
finalized stronger hotspot when policy rank says it should.
- Reconfirm old contracts remain true:
  - 1h/all-day retention
  - 5..100% strongest-share filter
  - fake-climb turn gate
  - no crash on missing hotspot ID recovery path
- Run required checks:
  - `./gradlew.bat enforceRules`
  - `./gradlew.bat :feature:map:testDebugUnitTest`
  - `./gradlew.bat assembleDebug`

## Rollout Summary

- Phase 0: lock baseline with failing tests.
- Phase 1: add explicit winner ranking policy helpers.
- Phase 2: wire policy into repository dedupe publish path.
- Phase 3: regression/hardening tests.
- Phase 4: docs sync and final verification.
