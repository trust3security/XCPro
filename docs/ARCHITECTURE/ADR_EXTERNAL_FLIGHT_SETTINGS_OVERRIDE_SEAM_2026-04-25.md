# ADR_EXTERNAL_FLIGHT_SETTINGS_OVERRIDE_SEAM_2026-04-25

## Metadata

- Title: Separate live external-flight settings overrides from external flight telemetry
- Date: 2026-04-25
- Status: Accepted
- Owner: XCPro
- Reviewers:
- Related issue/PR:
- Related change plan: `docs/BLUETOOTH/01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`
- Supersedes:
- Superseded by:

## Context

- Problem:
  LXNAV S100 broader sentence support (`LXWP2`, `LXWP3`, `PLXVS`) adds live
  `MacCready`, bugs, ballast overload factor, QNH-derived offset, OAT, and
  device/configuration status that do not fit the existing
  `ExternalInstrumentReadPort` telemetry seam.
- Why now:
  XCPro now consumes broader S100 data for cards, the ballast widget,
  Bluetooth settings, and runtime calculations. Without a separate seam, the
  implementation would either widen the telemetry port into a catch-all device
  bus or write live device state back into persisted repositories.
- Constraints:
  Preserve MVVM/UDF/SSOT layering, keep replay deterministic, keep Condor live
  selection isolated, and avoid hidden persistence side effects from live
  Bluetooth data.
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`

## Decision

Introduce `ExternalFlightSettingsReadPort` as a separate cross-feature seam for
session-scoped live overrides and display-only environment/status values from
external device settings/status sentences.

Required boundary choices:

- `ExternalInstrumentReadPort` remains narrow and telemetry-only:
  pressure altitude, confirmed TE vario, and generic external vario.
- `ExternalFlightSettingsReadPort` owns only session-scoped runtime overrides
  and display values such as:
  - `macCreadyMps`
  - `bugsPercent`
  - `ballastOverloadFactor`
  - `qnhHpa`
  - `outsideAirTemperatureC`
- Bluetooth runtime repositories may publish these values, but fused flight
  widgets/cards must read them through their existing runtime owners rather than
  directly from Bluetooth control/runtime state.
- Live external overrides must not persist into:
  - `QnhPreferencesRepository`
  - glider configuration storage
  - Levo/profile preferences
- Effective runtime ownership stays with the existing owners:
  - QNH runtime applies external QNH live and restores the base stored state
    when the override clears.
  - MacCready runtime prefers external MC while fresh without mutating stored
    Levo settings.
  - Bugs runtime prefers external bugs while fresh without mutating stored
    glider configuration.
  - Ballast overload factor is display-only in this slice; it may drive the
    read-only external ballast widget but does not become a glide/STF write
    path.

Dependency direction impact:

- `feature:variometer` publishes into the new read seam.
- `feature:flight-runtime`, `feature:map`, and `feature:profile` consume the
  read seam.
- Persistence-facing repositories remain below their owning features and are not
  written by the external-settings seam.

API/module surface impact:

- New cross-feature interface:
  `feature:flight-runtime/.../external/ExternalFlightSettingsReadPort.kt`
- New live-source resolver binding for phone vs Condor selection.
- Condor provides a no-op implementation so simulator mode keeps the seam
  explicit without inventing simulator-owned fake S100 settings.

Time-base/determinism impact:

- These values are session-scoped live overrides, not replay-authored flight
  telemetry.
- Replay determinism is unchanged because replay does not consume the live S100
  settings seam.

Concurrency/buffering/cadence impact:

- The seam is a simple `StateFlow` snapshot owner.
- Values are sticky while connected and clear on disconnect/session end.
- No new polling loop or buffering policy is introduced.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Widen `ExternalInstrumentReadPort` to carry all S100 fields | Reuses an existing external-device seam | Collapses telemetry and settings into one bus, breaks the narrow-seam rule, and encourages direct UI reads from device state |
| Write live S100 values directly into persisted QNH/glider/Levo repositories | Reuses existing owners without a new seam | Creates hidden persistence side effects, makes disconnect restoration harder, and risks incorrect cross-profile state |
| Expose raw LX runtime/configuration state directly to cards/widgets | Fastest UI path | Violates SSOT/fused-runtime ownership and creates a parallel Bluetooth UI/runtime path |

## Consequences

### Benefits
- Keeps telemetry, runtime overrides, persistence, and diagnostics on explicit
  owners.
- Lets S100 MC, bugs, QNH, OAT, and ballast-factor surfaces reuse the existing
  card/widget/layout system without creating LX-specific parallel runtime paths.
- Makes disconnect restoration explicit and testable.

### Costs
- Adds one more resolver-selected live seam and associated DI wiring.
- Requires each consuming owner to merge live external overrides with its base
  stored/runtime state.

### Risks
- If future work starts pushing unrelated device/configuration state through the
  seam, it can grow into a second catch-all bus.
- `PLXVS`, `LXWP2`, and `LXWP3` field semantics still require ongoing
  real-device validation to protect against protocol misunderstandings.

## Validation

- Tests/evidence required:
  - parser/runtime tests for `LXWP2`, `LXWP3`, `PLXVS`
  - QNH override tests proving live override without persistence mutation
  - MC/bugs/ballast/OAT card/widget tests
  - Bluetooth settings mapping tests for the new detail sections
- SLO or latency impact:
  - negligible; one additional `StateFlow` combine path in live runtime
- Rollout/monitoring notes:
  - validate with sanitized real S100 capture before treating all broader
    fields as hardware-signed-off

## Documentation Updates Required

- `ARCHITECTURE.md`: no change
- `CODING_RULES.md`: no change
- `PIPELINE.md`: add explicit external-flight-settings override rule
- `CONTRIBUTING.md`: no change
- `KNOWN_DEVIATIONS.md`: no change

## Rollback / Exit Strategy

- What can be reverted independently:
  - broader `LXWP2` / `LXWP3` / `PLXVS` consumers can be removed while keeping
    the seam if later device integrations still need it
  - specific consumers such as QNH or ballast widget external mode can be
    reverted independently
- What would trigger rollback:
  - real-device validation showing the assumed field semantics are materially
    wrong
  - evidence that the seam is causing hidden persistence or replay behavior
    regressions
- How this ADR is superseded or retired:
  - by a later ADR that either generalizes live external settings across more
    device families or replaces this seam with a stricter owner split
