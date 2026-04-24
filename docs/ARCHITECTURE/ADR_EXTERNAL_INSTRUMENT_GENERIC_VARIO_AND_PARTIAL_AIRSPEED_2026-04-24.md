# ADR_EXTERNAL_INSTRUMENT_GENERIC_VARIO_AND_PARTIAL_AIRSPEED_2026-04-24

## Metadata

- Title: Keep external instrument seam narrow while adding generic external vario and partial external airspeed support
- Date: 2026-04-24
- Status: Accepted
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: n/a
- Related change plan: LX S100 narrow-seam phased implementation approved on 2026-04-24
- Supersedes: n/a
- Superseded by: n/a

## Context

- Problem:
  LXNAV S100 support required `PLXVF` integration so PLXVF-only streams could
  drive the existing widget, cards, and audio. The repo already had two
  separate live seams:
  - `ExternalInstrumentReadPort` for pressure altitude and confirmed TE vario
  - `ExternalAirspeedWritePort` for the canonical external airspeed owner
  The change needed broader capability without turning either seam into a
  catch-all device bus.
- Why now:
  The production LX slice needed to use both `LXWP0` and `PLXVF` so pilots see
  the best available S100 data in the existing XCPro runtime.
- Constraints:
  - keep SSOT ownership explicit
  - preserve replay determinism and existing time-base rules
  - avoid direct Bluetooth/UI coupling
  - avoid parallel LX-only widget/audio paths
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/LEVO/levo.md`

## Decision

Describe the decision in direct terms.

Required:
- ownership/boundary choice:
  - `ExternalInstrumentReadPort` gains exactly one new field:
    `externalVarioMps`.
  - `ExternalAirspeedWritePort` remains the only live external airspeed ingress.
  - LX Bluetooth runtime owns sentence parsing and sentence-local state only.
  - `feature:flight-runtime` owns fusion precedence, TAS derivation from
    partial airspeed samples, netto gating, and audio authority.
- dependency direction impact:
  - Bluetooth adapters publish into existing domain-facing seams.
  - `feature:map` and `dfcards-library` continue to consume fused outputs only.
  - No UI or map code depends on Bluetooth sentence/runtime classes.
- API/module surface impact:
  - `ExternalInstrumentFlightSnapshot` now carries `externalVarioMps`.
  - `AirspeedSample` now supports partial external samples (`IAS-only`,
    `TAS-only`, or paired values).
  - LX `PLXVF` support is additive to the existing `LXWP0`/`LXWP1` runtime.
- time-base/determinism impact:
  - external instrument freshness still uses existing monotonic
    `receivedMonoMs` timestamps.
  - replay behavior is unchanged; replay ignores live external instrument input.
- concurrency/buffering/cadence impact:
  - no new long-lived owner or buffering layer was added
  - existing Bluetooth runtime publication cadence and flight-runtime freshness
    windows remain authoritative

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Widen `ExternalInstrumentReadPort` into a general LX data bus | Simple place to park all new S100 fields | Breaks ownership, duplicates existing airspeed/wind/settings owners, and would encourage UI reach-through |
| Add LX-specific widget/audio paths | Fastest way to show S100-specific behavior | Creates parallel truths outside fused runtime and violates SSOT/pipeline rules |
| Derive TAS in the LX repository before publishing airspeed | Keeps flight-runtime unchanged | Pushes atmosphere/QNH policy into a transport adapter and duplicates central airspeed policy |

## Consequences

### Benefits
- S100 `PLXVF`-only streams can drive the existing widget and audio without a
  new UI path.
- Airspeed ownership stays singular and reusable across live, replay, and
  simulator paths.
- Fusion, cards, widget, and audio continue to share one runtime truth.

### Costs
- `feature:flight-runtime` now handles more partial-sample policy.
- Tests need explicit coverage for `IAS-only` external airspeed and
  `externalVarioMps` precedence.

### Risks
- `PLXVF.Vario` is still provisional as a main raw vario/audio source until
  sanitized hardware validation confirms the field semantics.
- Operators do not get an LX-specific badge in the widget; source consistency
  comes from fused behavior, not a dedicated visual label.

## Validation

- Tests/evidence required:
  - LX parser/runtime tests for `PLXVF`
  - flight-runtime precedence and IAS-derivation tests
  - widget/card tests for S100-mode subtitle and `NETTO` no-data gating
  - audio authority tests for external TE and external raw vario
- SLO or latency impact:
  - no intended cadence or latency increase; audio remains on the existing
    fused runtime path
- Rollout/monitoring notes:
  - keep `PLXVF` hardware validation as an acceptance gate for future protocol
    tightening

## Documentation Updates Required

- `ARCHITECTURE.md`: no change
- `CODING_RULES.md`: no change
- `PIPELINE.md`: updated
- `CONTRIBUTING.md`: no change
- `KNOWN_DEVIATIONS.md`: no change

## Rollback / Exit Strategy

- What can be reverted independently:
  - `PLXVF` publishing can be removed without changing the rest of the live
    pipeline ownership
  - IAS-only support on `AirspeedSample` can be rolled back independently of
    the generic external-vario seam if needed
- What would trigger rollback:
  - verified hardware evidence that `PLXVF.Vario` is not safe to use as a
    provisional main vario/audio field
  - regressions that show the new seam is mixing sources or bypassing fused
    owners
- How this ADR is superseded or retired:
  - supersede it with a later ADR if broader external instrument support adds a
    new stable owner for wind, MC, ballast, QNH, or device status
