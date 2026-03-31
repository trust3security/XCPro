> Archived on 2026-03-31.
> This was the one-off Codex execution prompt for the first selected-group
> fan-out pass.
> Current active direction remains
> [xcpro-close-proximity-declutter-brief.md](../../xcpro-close-proximity-declutter-brief.md),
> [xcpro-phase-2a-close-proximity-declutter-plan.md](../../xcpro-phase-2a-close-proximity-declutter-plan.md),
> and [README.md](../../README.md).
> Treat this file as historical implementation context only.

# XCPro Phase 2B implementation brief for Codex

Implement **Phase 2B only**.

## Goal

Add **selected-group, display-only fan-out** for tightly packed traffic targets so close aircraft become distinguishable and tappable without mutating true aircraft coordinates.

This sits **on top of Phase 2A**. Phase 2A label admission stays in place.

## Source of truth

Use the active Phase 2A state and docs already on the branch. Do not revive any archived declutter behavior.

## Contract to implement

When a **selected aircraft** belongs to a **packed screen-space group** at close zoom:

- the **selected aircraft stays primary**
- the **selected aircraft stays at its true coordinate**
- the **selected aircraft keeps the full Phase 2A label behavior**
- the **other aircraft in that packed group fan out around it**
- the fanned aircraft use **display-only displaced icon positions**
- each displaced aircraft gets a **leader line** back to its true coordinate
- **taps must work on the displaced icons**
- when selection clears, the selected target changes, the group is no longer packed, or zoom leaves the fan-out range, the fan-out collapses back to true positions

If there is **no selected aircraft**, do **not** auto-fan groups in this phase.

That is the key scope guard.

## Important scope boundaries

Do **not** do any of the following in this change:

- no clustering
- no spiderfy redesign beyond the selected-group fan-out defined here
- no regroup/count pill work
- no compact short-label system for secondary aircraft
- no ranking changes
- no `MAX_TARGETS` changes
- no ADS-B / OGN shared architecture rewrite
- no repository/domain-coordinate mutation
- no projection invalidation or coordinate pinning behavior
- no reconnect-policy work
- no unrelated cleanup/refactors

## Global behavior rules

1. **Fan-out is selection-driven in Phase 2B**
   - only the packed group containing the selected aircraft may fan out
   - all other groups remain collapsed and continue to use Phase 2A label suppression only

2. **True coordinates remain source of truth**
   - aircraft positions in repository/domain/runtime state remain true positions
   - fan-out is a display-layer concern only

3. **Primary stays anchored**
   - selected aircraft remains at true coordinate
   - non-primary members displace around the primary in screen space

4. **Deterministic layout**
   - the same group should lay out the same way across rerenders unless membership changes
   - avoid jitter and slot reshuffling from frame to frame

5. **Tap behavior must follow displayed icons**
   - when a secondary aircraft is displaced, tapping the displaced icon must select that aircraft
   - do not require tapping the hidden true coordinate

## Activation rules

Implement fan-out only when all of the following are true:

- viewport is in **close zoom**
- a **selected target id** exists
- that selected target is present in the overlay data
- the selected target belongs to a packed group using screen-space proximity
- the packed group contains at least 2 aircraft

Suggested close-zoom threshold:
- reuse the existing close zoom band used for full-size traffic behavior, unless the code shape requires a narrowly different constant

Suggested packed-group detection:
- reuse or extend the same screen-space grouping basis already introduced in Phase 2A so both phases agree on group membership
- do **not** create a second incompatible grouping system

## Layout rules

Use a deterministic fan-out layout around the selected/primary target.

Recommended starting behavior:

- primary stays at center / true coordinate
- non-primary members are assigned clockwise slots around the primary
- first ring for small groups
- second ring only when needed for larger groups

Suggested starting values:
- packed-group detection radius: `40-48 px`
- first-ring fan-out radius: `32-40 px`
- second-ring radius: `52-64 px`
- slot ordering: stable sort by existing stable target identity key used for selection/tap mapping

Do not over-engineer the layout in this phase.
A clean deterministic ring layout is enough.

## Label behavior during fan-out

Keep Phase 2A behavior intact:

- primary/selected target keeps the normal allowed full label
- non-primary targets in the fanned group do **not** regain full labels
- do not introduce secondary compact labels in this phase unless the existing code already requires a minimal retained label for correctness

## Leader lines

Add leader lines only for displaced non-primary aircraft.

Rules:
- line starts at the aircraft's true coordinate
- line ends at the displaced display position
- no leader line for the primary target
- keep styling visually light and subordinate to icons
- leader lines must rebuild correctly after style recreation

## Runtime and data-shape rules

Acknowledge this explicitly in the implementation:

- Phase 2A kept zoom-band behavior restyle-only
- Phase 2B is different because displaced icons and leader lines require display geometry
- it is acceptable to rebuild **display features** when selected-group fan-out state changes
- it is **not** acceptable to mutate or pin the underlying true traffic coordinates
- do not reintroduce the old OGN projection invalidation / traffic-source rewrite behavior that was removed in hardening

## Overlay scope

Implement this for the traffic overlays already carrying Phase 2A packed-group label behavior.

Use the narrowest code shape that keeps OGN and ADS-B behavior consistent, but do not force a broad shared refactor.

If one overlay needs a small adapter seam, keep it local and minimal.

## Style recreation

Make sure fan-out state survives style reload correctly:

- if selection still exists and the group is still packed, recreate displaced icons and leader lines after style recreation
- if not, fall back cleanly to true-coordinate rendering
- no crash if selection or overlay data arrives before style is ready

## Tap / hit behavior

This is mandatory:

- tapping a displaced icon must select the correct aircraft id
- selection must then re-anchor the group around the newly selected aircraft on the next layout pass
- direct taps on non-fanned targets must continue to work
- do not regress close-zoom target selection

If the existing hit-test path assumes true coordinates only, adapt it narrowly for displayed icon geometry while fan-out is active.

## Files / code shape

Use current repo structure and adapt names only if needed.

Expected areas to touch:

- packed-group runtime logic in `feature/traffic/.../map/`
- OGN traffic overlay display feature generation
- ADS-B traffic overlay / mapper display feature generation
- runtime manager/delegate seam for selected target id forwarding if needed
- overlay hit-test path so displayed icons are tappable
- style/layer support for leader lines

If you must add a new helper, keep it small and purpose-specific.
A reasonable name would be something like:
- `TrafficPackedGroupFanoutLayout.kt`

## Acceptance criteria

The change is correct only if all of these are true:

1. Selecting an aircraft inside a packed group causes only that selected group to fan out.
2. The selected aircraft remains at its true coordinate.
3. Non-primary aircraft in the selected packed group render at displaced display positions.
4. Leader lines connect displaced aircraft back to true coordinates.
5. Tapping a displaced icon selects the correct aircraft.
6. Changing selection re-centers the fan-out around the newly selected aircraft.
7. Clearing selection collapses the group back to true positions.
8. If the selected aircraft is no longer in a packed group, fan-out collapses automatically.
9. True coordinates remain the source of truth; no repository/runtime coordinate mutation is introduced.
10. Phase 2A label suppression remains intact for non-primary targets.
11. No regression in non-fanned target tap behavior.
12. No crash on style recreation or overlay recreation.

## Tests to add/update

Add focused tests for:

- deterministic layout slotting for the same group membership
- selected target remains primary and undisplaced
- non-primary targets receive displaced display positions
- leader lines map true -> display positions correctly
- tap selection works on displaced icons
- selection change re-anchors the group correctly
- clearing selection collapses fan-out
- style recreation restores or collapses correctly depending on current state
- no-fan-out behavior when no selection exists
- no-fan-out behavior outside close zoom

Prefer narrow unit tests around the layout helper and overlay mapping behavior first, then the minimum runtime/delegate coverage.

## Manual QA checklist

Report each item as `DONE` or `NOT RUN` unless you actually executed it.

- select one aircraft inside a close packed group and confirm the group fans out
- confirm selected aircraft stays on its true position
- tap a displaced secondary aircraft and confirm selection moves correctly
- clear selection and confirm the group collapses
- zoom out below the fan-out threshold and confirm collapse
- zoom back in and reselect to confirm fan-out returns
- verify OGN close packed group behavior
- verify ADS-B close packed group behavior
- verify no regression in ordinary non-packed taps
- verify style recreation while a selected packed group is active

## Output required

Return:

- concise summary of what changed
- exact files changed
- tests run and pass/fail
- manual QA list with `DONE` or `NOT RUN`
- remaining risks
- any deviations from this contract

Do not broaden the scope beyond Phase 2B.
