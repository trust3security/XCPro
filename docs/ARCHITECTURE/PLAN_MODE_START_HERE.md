# PLAN_MODE_START_HERE.md

## Purpose

This is the beginner entrypoint for planning non-trivial work in XCPro.

Use it to decide whether a change needs planning before coding and to produce
the minimum safe plan.

This guide does not replace `../../AGENTS.md`, `ARCHITECTURE.md`, or
`CODING_RULES.md`. Those files remain authoritative.

---

## When To Use Plan Mode

Start in plan mode, or write a short change plan first, when any are true:

- the change touches more than one layer (`UI`, `ViewModel`, `use case`,
  `repository`, `data source`)
- the change adds or changes authoritative state and you need to ask
  "who owns this?"
- the change affects pipeline wiring, replay behavior, time handling, or
  determinism
- the change needs new DI wiring, a new module boundary, or a new public or
  cross-module API
- the change removes a bypass or moves responsibility from one class/module to
  another
- the change touches map runtime, task runtime, sensors, IGC/replay, glide, or
  other architecture-sensitive paths
- the change will likely require `PIPELINE.md`, a change plan, or an ADR to be
  updated

Simple rule of thumb:

- `1 file, no state/API/ownership change` -> probably trivial
- `2+ files` or any ownership/runtime change -> treat it as non-trivial

If you are unsure, use plan mode.

---

## You Can Usually Skip Plan Mode For

These are usually trivial:

- typo, copy, comment, or markdown-only wording fix
- small layout, spacing, or color tweak in one UI file
- one-file bug fix that does not change ownership, state contracts, APIs, or
  runtime behavior
- test-only additions that do not require production design changes

---

## Minimum Read Path Before Planning

Read these first:

1. `../../AGENTS.md`
2. `ARCHITECTURE.md`
3. `CODING_RULES.md`
4. `PIPELINE_INDEX.md`
5. relevant sections in `PIPELINE.md`
6. `CODEBASE_CONTEXT_AND_INTENT.md`
7. `KNOWN_DEVIATIONS.md`

Also read when relevant:

- `../LEVO/levo.md`
- `../LEVO/levo-replay.md`

Use `PIPELINE_INDEX.md` to find the right section in `PIPELINE.md` quickly.

---

## Minimum Plan Output

Before coding a non-trivial change, write down:

- requested outcome
- in scope
- out of scope
- authoritative owner for each changed state item
- likely files to create or modify and what each file owns
- time base and replay/determinism expectations
- tests and verification commands
- docs to update
- blockers or missing decisions

If you cannot name the authoritative owner for changed state, do not start
coding yet.

---

## Smallest Safe Workflow

1. Decide whether the change is trivial or non-trivial.
2. If it is non-trivial, start from `CHANGE_PLAN_TEMPLATE.md`.
3. Cut scope until the change is the smallest useful slice.
4. Reuse one or two similar existing files or feature paths.
5. Do not start implementation until ownership, boundaries, time base, and
   tests are clear.

For autonomous feature/refactor execution after planning, use `AGENT.md`.

---

## Prompt Starter

Use this as a starting point with an agent:

```text
Use plan mode if available.
I want to add/fix <feature>.
Do not code yet.
Give me the smallest safe plan with:
- scope
- SSOT owner
- likely files and file ownership
- timebase/replay risks
- tests
- docs to update
- blockers
```

---

## Ready To Code Checklist

You are ready to implement when:

- each changed state item has one authoritative owner
- dependency direction still fits `UI -> domain -> data`
- time base and replay rules are explicit
- required verification commands are named
- required doc sync is named
- any ADR or deviation need is identified before coding starts
