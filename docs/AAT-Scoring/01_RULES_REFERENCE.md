# 01 — Rules Reference for Real-Time AAT in XCPro

## Why this file exists

This is the rules baseline Codex should use before touching implementation details.

It intentionally separates:

- **official FAI behavior**
- **existing XCPro/internal assumptions**
- **V1 product decisions**

## Source baseline

Use the current official FAI documents page as the source-of-truth index and treat the current Annex A / Alternative Scoring documents listed there as authoritative for competition behavior.

Use current Annex A rules for:

- AAT task definition
- assigned area geometry
- credited fix behavior
- marking distance / marking time / marking speed
- handicapped distance and handicapped speed
- start / finish closure handling
- scoring system selection
- scoring software version / hash auditability
- competitor communication limits
- flight recorder requirements

Use WeGlide live scoring only as a feasibility reference that real-time AAT scoring is practical in production.

## Official AAT facts Codex should implement around

### 1) Task definition

An Assigned Area Task is a speed task through:

- a Start
- two or more Assigned Areas in order
- a Finish
- a Minimum Task Time

Implication:

- AAT is not just “turnpoints with large radii”
- the minimum task time is a first-class rule input
- it must be stored in config, validated, and used in scoring

### 2) Allowed assigned area geometry under the default FAI profile

Under current Annex A, an Assigned Area is:

- a circle centered on a turn point, or
- a geometric figure bounded by specified bearings and max distance, optionally min distance

Implication:

- circles and sector-like geometric figures fit the default FAI profile
- “keyhole” should **not** be treated as default FAI AAT unless explicitly supported as a custom/local rule profile

### 3) Consecutive area separation

Consecutive Assigned Areas must be separated by at least 1 km.

Implication:

- the setup page and validator must enforce or clearly warn on this in FAI profile mode

### 4) Area achievement

A competitor is credited with achieving an Assigned Area if:

- a valid fix is in the observation zone, or
- the straight line between two consecutive valid fixes intersects the observation zone

Implication:

- live area detection must support **segment intersection**, not just “point in polygon / point in circle”

### 5) Credited fix behavior

For each Assigned Area, the scorer determines a single credited fix, and the set of credited fixes must maximize total credited distance.

Implication:

- a single “current target point” is not enough for official-style scoring
- XCPro needs a **credited-fix optimizer**
- provisional credited fixes may change as later legs develop

### 6) Marking distance for AAT

Codex should implement the official AAT logic for:

- completed task
- outlanded on last leg
- outlanded on an earlier leg

Implication:

- outlanded pilots still need correct marked distance
- live scoring must not collapse non-finishers into zero-distance placeholders

### 7) Marking time for AAT

For finishers:

- Marking Time = greater of:
  - elapsed time from valid start to finish
  - minimum task time

For non-finishers:

- marking time is undefined

Implication:

- only finishers get speed
- non-finishers get distance-based treatment only in classic scoring

### 8) Marking speed and handicaps

For finishers:

- `V = D / T`
- `Dh = D / H`
- `Vh = V / H`

Implication:

- the uploaded internal formula is only the **marking speed core**, not the full daily score
- if handicaps are enabled, both distance and speed must use handicap-adjusted values

### 9) Classic scoring vs Alternative scoring

Current Annex A requires organizers to state in Local Procedures which scoring system is used:

- Classic
- Alternative

Implication for V1:

- implement **Classic** first
- if Alternative is not already present in the repo, do **not** fake it
- expose the scoring-system field in setup, but gate Alternative with a warning or feature flag

### 10) Current classic daily score logic

In Classic scoring, for both RT and AAT:

- finishers get distance points plus speed points
- non-finishers get distance points only
- daily score depends on day parameters such as `Do`, `Vo`, `To`, `n1`, `n2`, `N`, `Pm`, `Pdm`, `Pvm`, `F`, `FCR`

Implication:

- a competition leaderboard needs a **day-parameter engine**, not just per-pilot math
- “who is winning right now?” is a competition-level calculation

### 11) Preliminary results before finish closure

Annex A includes a scorer note that before finish closure, competitors not fully accounted for are presumed in a way that keeps preliminary results representative, but they **shall not appear in the ranking**.

Implementation choice for XCPro V1:

- model these pilots as `pending_accounting`
- exclude them from visible ranking
- optionally include them in hidden preliminary parameter estimation

### 12) Start and finish geometry

Under current Annex A:

- Start types are line and cylinder
- cylinder start is explicitly noted as not to be used without a specific waiver
- finish geometry is ring or line
- finish ring is the preferred finish procedure

Implication:

- in default FAI profile mode:
  - allow line start
  - allow cylinder start only when a waiver flag/reference is stored
  - do not present “start sector” as default FAI behavior
  - default finish should be ring, with line allowed

### 13) Finish closure

After finish closure, pilots still on task are considered outlanded at the last valid GNSS fix immediately preceding closure time.

Implication:

- finish closure time must exist in config
- live leaderboard must transform airborne pilots to outlanded-at-closure when applicable

### 14) Scoring software version and hash

Annex A requires organizers to state:

- scoring software name
- version number
- checksum / hash of the scoring algorithm used

Implication:

- the AAT setup page should store algorithm version + hash
- published or exported results should include these values

### 15) Communication / visibility constraints

Competition data communication rules require care. Pilot-received data generally must come from a publicly available source, and other data communication between competitors and the ground is restricted unless allowed by organizers for safety.

Implication:

- do not default the live AAT leaderboard to pilot-visible in-flight mode
- default visibility should be `admin_only` or `public_spectator`
- pilot-visible mode must be an explicit organizer choice

### 16) Flight recorder requirement

For official scoring, FR recording interval is set to 1 second and final results depend on accepted flight logs.

Implication:

- all live results must be tagged as **provisional** until official logs are accepted
- final officialization must recalculate from FR logs

## What XCPro should do with non-FAI features

If the repo already supports or wants to support things like:

- keyhole AAT areas
- start sectors
- custom finish shapes
- alternative penalty models
- event-specific geometry variants

then Codex should not delete them blindly.

Instead:

- create a `RulesProfile`
- default to `FAI_ANNEX_A_CURRENT`
- place non-standard options under `CUSTOM_LOCAL_RULES`
- show a compliance badge and warnings in setup

## V1 product decisions

These decisions are intentionally conservative:

1. Default rules profile = `FAI_ANNEX_A_CURRENT`
2. Default scoring system = `CLASSIC`
3. Default ranking metric = projected classic day score when possible
4. Fallback ranking metric = projected handicapped speed
5. Default visibility = `ADMIN_ONLY`
6. Final official results always require FR-log reconciliation

## Existing internal-doc gaps Codex should fix

The uploaded internal docs are helpful for module shape, but Codex should not inherit these assumptions without correction:

- “official AAT speed formula implementation” is incomplete as a championship scoring description
- custom geometry support should not be silently labeled FAI-compliant
- existing real-time recommendation logic is not the same thing as a multi-pilot live leaderboard
- inconsistent historical package paths in docs should not lead to duplicate implementation trees

## Practical implementation conclusion

The correct XCPro design is:

- **single-pilot geometry + scoring primitives**
- plus **multi-pilot live aggregation**
- plus **rules/config management**
- plus **reconciliation from live tracking to accepted official logs**
- plus **clear status labels: official / provisional / projected**


## Reference documents to verify against

Codex should re-check the current official documents at implementation time, but this package was written against these references:

- FAI Documents page for Gliding (`Current Sporting Code for Gliding`, including current Annex A and Alternative Scoring listing)
- FAI Annex A PDF used for detailed rule reading (`sc3a_2025.pdf`)
- WeGlide live tracking docs noting that live scoring works for Racing and AAT
- internal uploaded docs:
  - `docs/02_Tasks/AAT_Tasks.md`
  - `docs/02_Tasks/archive/2026-03-task-doc-cleanup/AAT_IMPLEMENTATION_SUMMARY.md` (historical implementation snapshot)

The archived AAT implementation summary is retained as historical context only.

If the official FAI docs have changed by the time Codex runs, prefer the newer official text and keep the structure of this package.
