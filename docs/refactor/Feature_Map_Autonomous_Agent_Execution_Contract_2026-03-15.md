# Feature:Map Autonomous Agent Execution Contract

## Purpose

Run the remaining `feature:map` right-sizing program autonomously, one phase at
a time, with mandatory build gates after every phase before continuing.

This contract is execution-only. The source of truth for phase content remains:

- `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
- `docs/refactor/Feature_Map_Settings_Lane_Release_Grade_Phased_IP_2026-03-15.md`

## Read First

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
10. `docs/refactor/Feature_Map_Settings_Lane_Release_Grade_Phased_IP_2026-03-15.md`

## Execution Rules

- Follow phases in order. Do not skip ahead because a later slice looks easier.
- Implement one phase at a time.
- Run the full AGENTS verification gate after every completed phase.
- Continue automatically to the next unblocked phase only after the gate passes.
- After every landed phase, default to a focused seam/code pass before
  considering the automation loop complete for that slice.
- After a phase lands and passes the build gate, the agent may run a focused
  seam/code pass for the next phase or to investigate issues revealed by the
  landed phase.
- If that seam/code pass finds a correctable issue and the fix stays inside the
  active next phase, the agent may implement the fix and continue the contract
  without waiting for another user prompt.
- If the seam/code pass finds a correctable issue inside the just-landed phase,
  the agent may fix it, rerun that phase gate, update the plan/docs, and keep
  moving without waiting for another user prompt.
- A seam/code pass that reveals an in-scope fix is not a stop condition; the
  agent should fix it and keep moving.
- If the seam/code pass shows the plan is wrong, the agent must update the
  active plan docs before continuing.
- If a phase needs a new seam pass, ADR, or compatibility note, do that inside
  the phase before changing production ownership.
- Do not revert unrelated user changes in a dirty worktree.
- Do not collapse multiple ownership moves into one build cycle.
- If a phase reveals the parent plan is wrong, update the plan docs before
  starting the next phase.

## Mandatory Phase Loop

For each phase:

1. Confirm the current unfinished phase from the active plan docs.
2. Run a focused seam/code pass for that phase if the plan requires one.
3. State the file list and ownership before editing.
4. Implement the phase only.
5. Update the active plan progress note and any affected ADR/doc contracts.
6. Run:
   - `.\gradlew.bat enforceRules`
   - `.\gradlew.bat testDebugUnitTest`
   - `.\gradlew.bat assembleDebug`
7. If all pass:
   - mark the phase landed in the plan docs
   - rescore the slice if `docs/ARCHITECTURE/AGENT.md` requires it
   - run a focused seam/code pass for the next phase unless the active plan
     already proves no seam pass is needed
   - continue to the next unblocked phase if the seam/code pass does not reveal
     a blocker, or if any revealed issue is fixed inside the just-landed phase
     or the next phase without widening scope
8. If any gate fails:
   - fix the phase and rerun the gate
   - if blocked, record the blocker in the active plan and stop

## Stop Conditions

Stop and update the plan instead of guessing when:

- the next phase requires a new durable module boundary or ADR
- the next phase would create a second SSOT or runtime owner
- the next phase cannot pass the build gate without widening scope beyond the
  planned seam
- the next phase would require `feature:flight-runtime` to depend directly on
  the UI-heavy `feature:profile` or `feature:variometer` modules
- the next phase would drag replay shell controllers into the runtime module
- the next phase conflicts with unrelated unowned worktree changes

## Current Ordered Backlog

As of 2026-03-16, the remaining execution order is:

1. Parent Phase 4:
   closeout only. Parent Phase 4 deleted the dead
   `DistanceCirclesOverlay` path, cleaned its shell/bootstrap residue, hardened
   `MapInitializerDataLoader`, switched `MapGestureSetup`,
   `MapRuntimeController`, and `MapScreenSections` to `AppLogger`, and added
   regression guards. The closeout seam confirms `MapInitializer`,
   `MapOverlayStack`, and `MapScreenContentRuntime` are steady-state shell
   owners, so no further extraction phase is permitted under this contract.
   Remaining automated work is limited to closeout scoring, final guard
   confirmation, and documenting the honest deferment of the older numeric
   target.
2. Any new follow-on work after this closeout must start from
   `docs/refactor/Feature_Map_Shell_Ergonomics_Release_Grade_Phased_IP_2026-03-16.md`
   instead of reopening this contract.

## Acceptance

This contract is satisfied only when:

- every remaining phase is marked landed or explicitly deferred in the active
  plans
- each landed phase has a passing build gate recorded at the time it landed
- `feature:map` reaches the shell-only target described by the parent plan
