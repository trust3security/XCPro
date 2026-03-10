# AGENT_AUTOMATION_CONTRACT_ADSB_POSITION_FRESHNESS_PHASED_EXECUTION_2026-03-10.md

## Purpose

Define the autonomous execution contract for the ADS-B position-freshness and
rewind-fix workstream.

Primary plan anchor:

- `docs/ADS-b/CHANGE_PLAN_ADSB_POSITION_FRESHNESS_REWIND_FIX_2026-03-10.md`

Companion references:

- `docs/ADS-b/README.md`
- `docs/ADS-b/ADSB.md`
- `docs/PROXIMITY/README.md`

This contract is subordinate to:

- `AGENTS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`

## 0) Automation Scope

- Coverage:
  - autonomous execution of Phase 0 -> Phase 5 from the ADS-B freshness plan
  - code, docs, baseline tests, and evidence updates required by the plan
- Explicitly in scope:
  - provider timing authority (`response.timeSec`, `timePositionSec`, `lastContactSec`)
  - ADS-B store overwrite, expiry, ordering, and freshness policy
  - proximity/emergency timing consumers
  - map smoother, GeoJSON, stale/emergency visual semantics
  - execution log and ADS-B doc-index synchronization
- Explicitly out of scope:
  - ADS-B transport/auth retry redesign
  - OGN runtime behavior
  - unrelated IGC, benchmark, or map-runtime work already in progress elsewhere
- Mode:
  - autonomous execution without per-phase approval unless blocked

## 0.1) Activation and Finish Authority

User directive for this run:

- create an automated agent contract
- initiate it immediately
- use only basic build checks

Hard rules:

- contract creation alone does not count as initiation
- after the contract and execution log are created, the runner must move
  directly into Phase 0 code-bearing work
- reduced verification mode is allowed for this run, but no full release-readiness
  claim may be made without the broader repo-default checks being restored later

## 1) Required Read Order Before Execution

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/ADS-b/README.md`
10. `docs/ADS-b/CHANGE_PLAN_ADSB_POSITION_FRESHNESS_REWIND_FIX_2026-03-10.md`

## 2) Workspace Preconditions

Before execution starts:

1. Run `git status --short`.
2. Record the allowed pre-existing dirty worktree set in the execution log.
3. Do not touch unrelated dirty files.
4. Stop if new unexpected unrelated changes appear after Phase 0 begins.

Recommended execution log target:

- `docs/ADS-b/EXECUTION_LOG_ADSB_POSITION_FRESHNESS_PHASED_EXECUTION_2026-03-10.md`

## 3) Non-Negotiable Rules

- Preserve `UI -> domain/use-case facade -> repository/data`.
- Keep ADS-B timing authority in the repository/store SSOT, not in UI or map code.
- Keep visual smoothing non-authoritative.
- Do not reintroduce device-wall dependence for geometry freshness.
- Keep replay/shared runtime behavior deterministic for identical ordered inputs.
- Do not revert unrelated user changes.

## 4) Mandatory Per-Phase Automation Loop

For every phase:

1. Run `git status --short`.
2. Re-read the active phase scope and exit criteria from the plan.
3. Record in the execution log:
   - SSOT ownership changes
   - dependency-direction impact
   - time-base declarations
   - files touched
4. Implement only phase-scoped changes.
5. Run the mandatory basic build gate.
6. Update the execution log with results, residual risks, and score.
7. Advance only when the basic gate is green or a blocker is recorded.

## 5) Basic Build Gate Policy

Mandatory gate for this run after each code-bearing phase:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew assembleDebug
```

Allowed fallback when Gradle cache/tooling is unstable:

```bash
./gradlew enforceRules --no-configuration-cache
./gradlew assembleDebug --no-configuration-cache
```

Explicitly not run by default in this contract:

- `./gradlew testDebugUnitTest`
- instrumentation suites

Reason:

- user requested basic build checks only for this execution run

## 6) Phase Contract

### Phase 0

- lock current rewind/freshness failure modes with baseline tests/evidence
- create execution log and scope lock
- run basic build gate

### Phase 1

- introduce authoritative timing fields and fallback classification
- keep compatibility aliases explicit
- run basic build gate

### Phase 2

- migrate store/order/expiry/emergency semantics to geometry freshness
- run basic build gate

### Phase 3

- harden track/trend timing against stale-coordinate reuse
- run basic build gate

### Phase 4

- harden details/GeoJSON/overlay/smoother visual behavior
- run basic build gate

### Phase 5

- sync docs, evidence, and final phase scoring
- run basic build gate

## 7) Minimum Reporting After Each Phase

1. What changed
2. Files touched
3. Commands run and pass/fail
4. Residual risks
5. Updated phase score `/100` using:
   - architecture compliance: 30
   - correctness/risk closure: 25
   - map/UX outcome: 20
   - test design/coverage progress: 15
   - build discipline and rollback clarity: 10

## 8) Stop Conditions

1. Unexpected unrelated dirty-file changes appear after execution begins.
2. Architecture violation cannot be fixed in-phase.
3. Basic build gate stays red after 3 corrective attempts.
4. Scope drift leaves the ADS-B freshness/rewind workstream.

