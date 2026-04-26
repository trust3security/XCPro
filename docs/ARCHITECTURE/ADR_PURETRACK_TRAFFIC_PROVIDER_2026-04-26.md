## Purpose

Record the Phase 0 architecture and security boundary for adding PureTrack as
an XCPro traffic provider without starting production implementation.

## Metadata

- Title: PureTrack traffic provider lane and Phase 1/1.5 boundary
- Date: 2026-04-26
- Status: Accepted
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR:
- Related change plan: `docs/PURETRACK/PURETRACK_XCPRO_ADR.md`
- Supersedes:
- Superseded by:

## Context

- Problem:
  - XCPro does not currently ingest PureTrack traffic, so PureTrack-only
    aircraft or tracker markers are not visible through XCPro traffic paths.
  - PureTrack is an aggregator of multiple source families and is not equivalent
    to XCPro's existing ADS-B or OGN providers.
- Why now:
  - PureTrack API/auth/parser work needs an ownership and security boundary
    before production code is added.
- Constraints:
  - Preserve MVVM, UDF, SSOT, dependency direction, and replay determinism.
  - Keep business/runtime/provider logic out of map/UI code.
  - Treat the PureTrack application key as confidential because it is private
    to the paid user/account.
  - Do not feed PureTrack into proximity, collision, emergency audio, or other
    safety alert paths in Phase 0 or Phase 1.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/PURETRACK/PURETRACK_XCPRO_ADR.md`
  - `docs/PURETRACK/CODEX_PURETRACK_IMPLEMENTATION_HANDOFF_2026-04-26.md`
  - PureTrack Traffic API: <https://puretrack.io/help/api>
  - PureTrack object types: <https://puretrack.io/types.json>

## Decision

PureTrack will be treated as a separate provider-owned lane under
`feature:traffic`.

Locked decisions:
- PureTrack is not an OGN parser replacement.
- PureTrack is not an ADS-B source inside `AdsbTrafficRepository`.
- PureTrack is not a direct map/UI data source.
- Phase 1 is limited to PureTrack parser/client/session code and tests.
- Phase 1 may define an injected app-key provider seam and fake test provider
  only.
- Phase 1 must not add production PureTrack BuildConfig fields, app DI
  bindings, or other production key wiring.
- No production `BuildConfig.PURETRACK_APP_KEY` wiring is allowed under the
  current XCPro policy because the key is confidential and client-extractable.
- Phase 0 and Phase 1 must not change map runtime, OGN runtime, ADS-B emergency
  audio/collision logic, proximity alerts, or safety alert paths.
- PureTrack tokens must not be exported through profile backup/restore. Password
  storage is forbidden.

Phase 1.5 production defaults:
- App key handling: the PureTrack application key is confidential because it is
  private to the paid user/account. It must not be embedded in the Android APK,
  BuildConfig, resources, logs, profile backup, or any client-extractable
  storage.
- Production token persistence, when wired, must use Android Keystore-backed
  encrypted app-private storage, with no plaintext fallback, no email/password
  storage, and backup exclusion.
- The production Android token-store adapter may exist behind
  `PureTrackTokenStore` before runtime wiring, but DI/runtime binding remains
  blocked until the later PureTrack runtime phase.
- Provider mode: native XCPro traffic remains the default, PureTrack is
  user opt-in, and Hybrid remains experimental until a provider-neutral dedupe
  policy exists.
- Bounding boxes: production requests must fail closed above the documented
  `PureTrackBboxPolicy` threshold rather than widening or polling silently. The
  numeric threshold remains blocked until vendor/project confirmation.
- Polling: production polling remains blocked until a later runtime phase; when
  approved, it must be visible/foreground only, conservative, honor
  `Retry-After`/`429`, use exponential backoff, and never run in background.
- Profile backup: only non-secret PureTrack display/filter settings may be
  exported. Token, password, email, app key, watched keys, selected target,
  cached rows, raw API data, names, phone, and identity-bearing traffic data
  must never be exported.
- Logout is local-only token clearing unless an official PureTrack logout
  endpoint is verified from official docs.

Phase 2 XCPro-approved policy/defaults:
- App key: confidentiality is resolved by XCPro/user decision. No production
  `BuildConfig.PURETRACK_APP_KEY` wiring is allowed under the current XCPro
  policy because the key is confidential and client-extractable. Android
  runtime wiring remains blocked until a secure backend-mediated or equivalent
  non-client-secret strategy exists. Fake/test app-key providers remain allowed
  for unit tests.
- Token storage: production token persistence must use Android Keystore-backed
  encrypted app-private storage, with no plaintext fallback, backup exclusion,
  and no email/password storage.
- Provider mode: native XCPro traffic remains the default, PureTrack is
  user opt-in, and Hybrid remains experimental only.
- Bounding boxes: until PureTrack confirms vendor numeric limits, XCPro uses a
  provisional fail-closed threshold of width `<= 200 km`, height `<= 200 km`,
  and diagonal `<= 300 km`.
- Polling: until PureTrack confirms vendor rate limits, XCPro uses provisional
  polling of `30s` visible/flying, `60s` idle, never faster than `15s`, backoff
  `30/60/120/300`, and honors `Retry-After`.
- Profile backup: only non-secret PureTrack display/filter preferences may be
  exported.

Dependency direction impact:
- Phase 1 stays inside `feature:traffic` provider code and tests.
- Later map integration, if approved, must consume PureTrack through
  traffic-owned facades rather than calling PureTrack API/auth/parser code
  directly.

API/module surface impact:
- Phase 1 may introduce narrow PureTrack provider/session/parser types inside
  `feature:traffic`.
- No public map-facing API, app BuildConfig field, app DI binding, profile
  section, runtime repository, or provider-mode setting is approved by this ADR.

Time-base/determinism impact:
- Parser and mapper code must be pure and deterministic.
- Session/client time-dependent behavior, if any, must use injected time seams.
- PureTrack is live-only in Phase 1; replay must not poll PureTrack or depend on
  network/device state.

Concurrency/buffering/cadence impact:
- No production polling loop or runtime cadence is approved by Phase 1.
- Production polling cadence is unresolved and belongs to a later runtime ADR or
  change plan.
- Session/token mutation must be serialized in Phase 1.5 so login, logout, and
  invalid-token handling are last-auth-intent-wins. The session mutex must not
  be held across PureTrack network login calls.

## Unresolved Decisions

| Decision | XCPro-approved policy/default | Vendor fact still unresolved | Code blocked until resolved |
|---|---|---|---|
| Vendor bbox numeric limits | Use provisional fail-closed threshold: width `<= 200 km`, height `<= 200 km`, diagonal `<= 300 km`. | PureTrack warns that too-large bboxes can return whole-planet data but does not publish max width, height, area, or diagonal. | Widening above the provisional threshold; claiming the limit is vendor-confirmed. |
| Vendor polling cadence and rate limits | Use provisional polling: `30s` visible/flying, `60s` idle, never faster than `15s`, backoff `30/60/120/300`, honor `Retry-After`. | PureTrack docs do not publish rate limits, burst behavior, or recommended cadence. | Faster polling, burst behavior, or treating provisional cadence as vendor-approved. |
| Profile export/import implementation | Export non-secret display/filter preferences only. | No vendor fact required; exact XCPro settings allowlist remains product-owned. | Profile contributor/restore implementation until the non-secret settings allowlist is encoded and tested. |

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Fold PureTrack into OGN | PureTrack can include FLARM/OGN-derived targets | PureTrack has separate auth, row format, source taxonomy, and non-OGN sources. |
| Fold PureTrack into ADS-B | PureTrack can include ADS-B-derived targets | PureTrack is not ADS-B-only and would pollute ADS-B safety/emergency assumptions. |
| Start with map integration | Validates the visible helicopter use case sooner | Would bypass provider/session/runtime ownership and risk direct map data-source coupling. |
| Embed a PureTrack app key immediately | Mirrors current app-owned OpenSky BuildConfig pattern | XCPro has resolved the key as confidential; Android APK values are extractable. |

## Consequences

### Benefits
- PureTrack ownership is separated from ADS-B and OGN before implementation.
- App-key secrecy is resolved as confidential by XCPro/user decision.
- Phase 1 can proceed only as a narrow API/parser/session foundation once the
  key decision is resolved or fake-only tests are used.
- Safety and map runtime paths cannot accidentally consume PureTrack in the
  initial slice.

### Costs
- Real production auth wiring waits on a secure backend-mediated or equivalent
  non-client-secret strategy.
- Runtime polling, provider mode, map display, and safety/dedupe are deferred.
- Production token-store runtime wiring remains deferred even if the
  Android Keystore-backed, backup-excluded adapter exists behind
  `PureTrackTokenStore`.

### Risks
- Future code may try to treat recommended defaults as accepted decisions.
- Provider-mode and dedupe decisions can grow if deferred too long.

## Validation

- Tests/evidence required for Phase 0:
  - `./gradlew enforceRules`
- Tests/evidence required before Phase 1 is complete:
  - `./gradlew :feature:traffic:testDebugUnitTest`
  - `./gradlew enforceRules`
- SLO or latency impact:
  - none in Phase 0/1 because map runtime and overlays are out of scope
- Rollout/monitoring notes:
  - none until runtime/map phases

## Documentation Updates Required

- `ARCHITECTURE.md`: no change
- `CODING_RULES.md`: no change
- `PIPELINE.md`: no change until runtime wiring is added
- `CONTRIBUTING.md`: no change
- `KNOWN_DEVIATIONS.md`: no change

## Rollback / Exit Strategy

- What can be reverted independently:
  - this ADR and PureTrack planning-doc cross-links
- What would trigger rollback:
  - PureTrack confirms an integration model incompatible with a client-side
    traffic provider lane
  - XCPro owner rejects PureTrack traffic ingestion
- How this ADR is superseded or retired:
  - supersede with a later accepted ADR that resolves secure app-key delivery,
    provider mode, production bbox policy, polling cadence, and runtime/map
    ownership.
