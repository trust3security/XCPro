# ADR_LEVO_AUDIO_CANONICAL_THRESHOLD_CONTRACT_2026-03-27

## Metadata

- Title: Levo Audio Canonical Threshold Contract
- Date: 2026-03-27
- Status: Accepted
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: LEVO-20260327-AUDIO-THRESHOLD-UX
- Related change plan: `docs/LEVO/CHANGE_PLAN_LEVO_AUDIO_THRESHOLD_UX_SIMPLIFICATION_PHASED_IP_2026-03-27.md`
- Supersedes:
- Superseded by:

## Context

- Problem:
  - The shared Levo audio model, DataStore keys, and profile snapshot schema
    previously carried four raw threshold fields even though runtime playback
    only used one effective positive boundary and one effective negative
    boundary.
- Why now:
  - The UI has already been simplified to one `Lift Start` threshold and one
    `Sink Start` threshold.
  - Keeping the old four-field shared contract would preserve a misleading
    mental model and keep compatibility-only fields alive across modules.
- Constraints:
  - `LevoVarioPreferencesRepository` remains the SSOT.
  - Replay/live behavior must remain deterministic.
  - Existing installs and exported profiles must still import with preserved
    effective behavior.
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/LEVO/levo.md`

## Decision

Levo audio thresholds now use a canonical two-threshold contract end to end:

- `feature:flight-runtime` `VarioAudioSettings` exposes only
  `liftStartThreshold` and `sinkStartThreshold`.
- `feature:variometer` consumes those canonical thresholds directly in
  `VarioFrequencyMapper`.
- `feature:profile` persists canonical DataStore keys and removes the legacy
  raw keys on write.
- Profile snapshot capture writes canonical threshold fields only.
- Legacy DataStore values and legacy profile payloads are treated as
  compatibility inputs and converted to canonical thresholds on read/import.

Impact:

- Ownership/boundary choice:
  - `LevoVarioPreferencesRepository` remains the authoritative persistence
    owner.
  - `VarioAudioThresholdSemantics` owns only the compatibility conversion
    policy for legacy inputs.
- Dependency direction impact:
  - unchanged; `UI -> domain/use-case -> data`.
- API/module surface impact:
  - cross-module shared audio settings contract changes from four threshold
    fields to two canonical threshold fields.
- Time-base/determinism impact:
  - none; thresholds remain static values and replay/live semantics stay
    deterministic.
- Concurrency/buffering/cadence impact:
  - none; no new loops, buffering, or threading behavior.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep the four raw fields forever and rely on helper normalization | Lowest schema churn | Preserves a misleading shared contract and forces every owner to carry compatibility-only fields |
| Remove raw fields with no import fallback | Simplest implementation | Would break old installs/profile imports and violate compatibility expectations |

## Consequences

### Benefits
- One user-facing model now matches one shared runtime/persistence model.
- Repository and profile contributors no longer persist compatibility-only raw
  fields.
- Future callers cannot accidentally reinterpret the old redundant threshold
  pairs as first-class state.

### Costs
- Compatibility helper code remains for legacy DataStore/profile import paths.
- Tests and docs must explicitly cover canonical storage plus legacy fallback.

### Risks
- Legacy import could regress if fallback parsing drifts from the previous
  effective-threshold behavior.

## Validation

- Tests/evidence required:
  - shared semantics tests
  - mapper regression tests
  - repository legacy-key read/remove tests
  - profile capture/apply and restore tests, including legacy payload import
- SLO or latency impact:
  - none
- Rollout/monitoring notes:
  - legacy keys are removed lazily on the next settings write; read fallback
    remains in place for existing installs and old profile files.

## Documentation Updates Required

- `ARCHITECTURE.md`: none
- `CODING_RULES.md`: none
- `PIPELINE.md`: update Levo settings owner/storage description
- `CONTRIBUTING.md`: none
- `KNOWN_DEVIATIONS.md`: none

## Rollback / Exit Strategy

- What can be reverted independently:
  - canonical profile field names and DataStore keys can be reverted while
    preserving compatibility helpers if needed
- What would trigger rollback:
  - verified legacy import or settings-migration regression that changes live or
    replay audio start behavior
- How this ADR is superseded or retired:
  - superseded only if Levo audio threshold configuration is redesigned again
    at the shared model boundary
