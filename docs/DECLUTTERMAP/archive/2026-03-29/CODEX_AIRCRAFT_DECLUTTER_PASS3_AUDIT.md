# Aircraft declutter Pass 3 — implementation audit and hardening

You are working in the current repo. The Path B screen-space declutter patch has already been implemented. Your job is to audit it against the intended product behavior and acceptance criteria, then make only narrowly-scoped fixes if they are obvious and low-risk.

## Goal

Decide whether the current Path B implementation is actually sound in practice, not just architecturally neat.

Focus on these questions:

1. Are nearby aircraft now separated in screen space at low zoom?
2. Do offsets reduce or disappear as zoom increases?
3. Does tap-selection still hit the right aircraft on displaced icons?
4. Does selected OGN traffic still align with the ring/line/badge overlays?
5. Does ADS-B motion smoothing still behave sanely with declutter enabled?
6. Can declutter become stale during pan/rotate due to throttling or missing invalidation?
7. Are labels still coherently attached to the displaced icon when shown?

## Inputs to read first

- `docs/aircraft-declutter-implementation.md`
- the shared declutter engine and runtime support files
- the OGN and ADS-B overlay integration files
- the updated runtime camera invalidation code
- the focused tests added in Pass 2

## Scope rules

1. Do **not** broaden scope into clustering, leader lines, or a major overlay rewrite.
2. Do **not** move declutter logic out of runtime overlay code.
3. Do **not** replace Path B with Path A in this pass.
4. Keep any code fix small and reviewable.
5. If a concern requires larger architectural work, report it clearly instead of half-fixing it.

## Audit tasks

### 1) Trace the real data flow

Write down the exact current flow for both OGN and ADS-B:

- true coordinates in
- projection step
- declutter step
- unprojection step
- GeoJSON emission
- hit-testing path

Be explicit about which file owns each step.

### 2) Check acceptance criteria against code

For each item below, mark it as one of:

- `PASS`
- `CONCERN`
- `FAIL`

Criteria:

- nearby aircraft stop fully piling on top of each other
- aircraft remain individually selectable
- true domain coordinates remain untouched outside runtime display path
- map-screen state ownership remains unchanged
- selected OGN target still lines up with ring/line/badge overlays
- ADS-B smoothing still works without a second fighting animation path
- layout is deterministic and not obviously jittery
- offsets reduce or disappear as zoom increases or collisions resolve
- patch does not silently change filtering, clustering, or z-order policy

### 3) Look for the hard bugs

Inspect specifically for these failure modes:

- offset computed from stale camera state
- pan/rotate not invalidating when needed
- labels attached to true point while icon uses display point
- hit-testing reading a different layer/source geometry than the displaced geometry
- selected OGN target accidentally offset in some paths but not others
- ADS-B smoother and declutter both keeping independent prior-state caches that can create wobble
- deterministic ordering depending on iteration order instead of stable keys
- viewport throttling causing visible lag or missed final refreshes
- tests only proving engine math but not runtime integration seams

### 4) Make only tiny fixes when justified

Apply code changes only if all of these are true:

- the fix is clearly correct from local code inspection,
- the fix is narrowly scoped,
- the fix stays within the existing design,
- the fix does not exceed roughly 5 files of meaningful edits.

Otherwise, do not patch it. Report it.

### 5) Produce a report

Write `docs/aircraft-declutter-pass3-audit.md` with:

- summary verdict
- chosen findings table (`PASS` / `CONCERN` / `FAIL`)
- exact files inspected
- any small fixes applied
- remaining risks for manual QA
- recommended next pass, if any

## Manual QA checklist to include in the report

Include these explicit manual checks:

1. Two aircraft nearly coincident at low zoom.
2. Three to five aircraft in one tight group.
3. Same group while zooming in and out repeatedly.
4. Heading-up / rotation changes without target updates.
5. Tap each displaced icon in a crowded group.
6. Selected OGN target in a crowded group.
7. ADS-B emergency or priority target in a crowded group.
8. OGN and ADS-B targets near each other, since cross-overlay collision is still out of scope.

## Deliverables

1. `docs/aircraft-declutter-pass3-audit.md`
2. A concise chat summary containing:
   - verdict,
   - any code fixes actually made,
   - top 3 remaining risks,
   - whether the patch is ready for manual device QA.
