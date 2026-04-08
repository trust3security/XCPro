## Purpose

Record the Phase 4 build-logic decision for shared secret-property loading and
singular OpenSky BuildConfig ownership.

## Metadata

- Title: Centralize secret-property loading in build logic and keep OpenSky BuildConfig app-owned
- Date: 2026-04-06
- Status: Accepted
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: Phase 4 of `XCPro Architecture Hardening IP`
- Related change plan: `docs/refactor/XCPro_Architecture_Hardening_Release_Grade_Phased_IP_2026-04-06.md`
- Supersedes:
- Superseded by:

## Context

- Problem:
  - `app`, `feature:map`, `feature:forecast`, `feature:livefollow`, and
    `feature:weglide` duplicated `local.properties` and `readSecretProperty`
    helpers in their Gradle scripts.
  - `feature:map` also generated OpenSky BuildConfig fields even though current
    production code only reads them from `app`.
- Why now:
  - The seam hardening pass should finish with one build-config owner per
    secret and without repeated script-local property loading logic.
- Constraints:
  - Keep build behavior unchanged apart from removing duplicate ownership.
  - Avoid `buildSrc`; prefer explicit included build logic.
  - Do not widen which modules receive secret BuildConfig fields.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`

## Decision

- Add an included `build-logic` build that owns shared secret-property loading
  through the `xcpro.secret-properties` plugin.
- `app`, `feature:forecast`, `feature:livefollow`, and `feature:weglide`
  consume that plugin rather than re-declaring local property-loading helpers.
- `app` is the only owner of OpenSky BuildConfig fields.
- `feature:map` keeps its own `BuildConfig` for module-local flags such as
  `DEBUG`, but no longer generates OpenSky secrets.

Dependency direction impact:
- none for production code; build logic only

API/module surface impact:
- build scripts get a shared `SecretPropertiesExtension`
- secret BuildConfig fields are narrower because duplicate map-module ownership
  is removed

Time-base/determinism impact:
- none

Concurrency/buffering/cadence impact:
- none

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Leave duplicated helpers in place | no build-script churn | keeps duplicate ownership and drift risk |
| Use `buildSrc` for the helper | simple familiar option | less explicit than an included `build-logic` build |
| Keep OpenSky BuildConfig fields in both `app` and `feature:map` | avoids touching map gradle script | preserves duplicate secret ownership with no active consumer |

## Consequences

### Benefits
- Secret-property loading logic lives in one maintained owner.
- OpenSky BuildConfig ownership is singular and reviewable.
- `feature:map` no longer receives secrets it does not use.

### Costs
- Adds one included build to maintain.
- Build-script consumers now depend on a small custom plugin.

### Risks
- Build-logic changes can break Gradle configuration if the plugin contract drifts.
- Future modules may bypass the shared plugin unless review keeps the rule strict.

## Validation

- Tests/evidence required:
  - touched-module Gradle compile proof
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- SLO or latency impact:
  - none expected
- Rollout/monitoring notes:
  - watch configuration-time failures first

## Documentation Updates Required

- `ARCHITECTURE.md`: no change
- `CODING_RULES.md`: no change
- `PIPELINE.md`: no change
- `CONTRIBUTING.md`: no change
- `KNOWN_DEVIATIONS.md`: no change

## Rollback / Exit Strategy

- What can be reverted independently:
  - the `build-logic` included build
  - per-module Gradle rewires
  - `feature:map` OpenSky BuildConfig removal
- What would trigger rollback:
  - unstable Gradle configuration or plugin resolution failures
- How this ADR is superseded or retired:
  - supersede only if a later build-system ADR introduces a different approved
    owner for secret/config loading
