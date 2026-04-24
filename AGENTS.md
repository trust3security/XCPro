# AGENTS.md

## Purpose

This is the canonical instruction entrypoint for coding agents in this repo.
It defines what must be read first, what must never be violated, and what
checks are required before considering work complete.

Planning entrypoint for non-trivial work:

- `docs/ARCHITECTURE/PLAN_MODE_START_HERE.md`

Use that guide to decide whether a change needs planning before coding.
It does not replace the mandatory read order below.

## Mandatory Read Order

Read these files in order before making changes:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE_INDEX.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

If touching variometer/replay pipeline behavior, also read:

1. `docs/LEVO/levo.md`
2. `docs/LEVO/levo-replay.md`

## Non-Negotiables

- Preserve MVVM + UDF + SSOT layering.
- Respect dependency direction and module boundaries.
- Keep business/domain logic out of UI.
- Use injected clocks/time sources in domain/fusion paths.
- Do not introduce hidden global mutable state.
- Keep replay deterministic.
- If architecture rules are violated, fix the code or record a time-boxed
  exception in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue ID, owner, expiry).

## Feature Implementation Defaults

When implementing a feature, agents must keep ownership explicit and file
responsibilities narrow.

- Target `<= 450` lines per file when practical. The repo-wide enforced default
  remains `<= 500`; do not treat `450` as permission to ignore the hard cap.
- Split code by responsibility before a file becomes large. Prefer several
  focused files over one mixed-responsibility file.
- Reuse existing feature structure and naming before introducing new patterns.
- Identify the authoritative owner for each new piece of state before editing.

Ownership defaults:
- UI owns rendering, user input forwarding, and display-only logic.
- ViewModels own screen state, intent handling, and orchestration.
- Use cases/domain classes own business rules, calculations, and policy.
- Repositories own authoritative data coordination and persistence-facing state.
- Data sources/adapters own API, database, device, and file I/O.
- Mappers own model transformations between layers.

Implementation expectations:
- Do not put business logic in Composables, Activities, Fragments, adapters, or
  other UI classes.
- ViewModels may depend only on stable domain-facing seams: use cases or
  focused owner/port seams for authoritative reads, narrow flows, and simple
  authoritative commands. Naming alone is not policy.
- Do not inject low-level infra/data-source types, broad dependency bags, or
  thin rename-only forwarding wrappers into ViewModels.
- Do not bypass layers for convenience.
- Before touching runtime-heavy code, inspect existing use of `CoroutineScope(`,
  direct `Log.*`, `UUID.randomUUID()`, `TimeBridge.nowWallMs()`, `NoOp`, and
  compatibility shims so new code follows the established or corrected policy.
- When adding a new Kotlin `object`, singleton-like holder, or convenience
  constructor, state why it will not become a hidden state owner, service
  locator, or silent production fallback path.
- Before editing, state which files will be created or changed and what each
  file will own.
- After editing, summarize file ownership so review can confirm boundaries.

## Final Glide Guardrails

- `FlightDataRepository` is the fused-flight-data SSOT; keep
  `CompleteFlightData` flight-data only.
- `TaskManagerCoordinator` owns cross-feature task runtime state;
  `taskSnapshotFlow` is the authoritative cross-feature read seam.
- `TaskRepository` is UI projection only; do not use it as cross-feature
  runtime authority.
- Glide policy/math belongs in dedicated domain/use-case owners. UI adapters,
  cards, Composables, and formatting layers may render glide outputs but must
  not compute or own glide rules.
- Final glide consumers derive from fused runtime samples plus task/runtime
  seams; do not add task-route or glide-derived fields to `CompleteFlightData`.
- Canonical remaining task-route geometry belongs with task-runtime/boundary
  owners; do not approximate racing observation-zone routing by waypoint centers
  in `feature:map`, cards, or UI adapters when a boundary-aware touchpoint is
  available.
- Prefer additive glide/runtime migrations: add the new upstream owner, rewire
  consumers, then remove compatibility glue.
- Preserve replay determinism; use fused runtime samples and injected or replay
  time sources only.

## Task Execution Template (Per-Change)

For autonomous feature/refactor work, start from:

- `docs/ARCHITECTURE/PLAN_MODE_START_HERE.md`
- `docs/ARCHITECTURE/AGENT.md`

This ensures phased execution, acceptance criteria, required checks, and a mandatory quality rescore.

## Planning Discipline

- Never make assumptions. Before writing a non-trivial plan or implementing,
  verify discoverable facts from repo docs, code, configs, tests, and local
  system state first.
- If a fact, owner, behavior, product intent, file path, API contract, or
  default cannot be verified locally, get an explicit user decision or record it
  as an unresolved decision/blocker. Do not proceed on a guessed answer.
- Non-trivial plans must separate:
  - `Confirmed Boundaries / Verified Facts`
  - `Explicit Decisions / Defaults Chosen`
  - `Unresolved Decisions`
- `Explicit Decisions / Defaults Chosen` may contain only decisions backed by
  the user, repo docs, code contracts, or verified local evidence. Anything else
  belongs in `Unresolved Decisions` and must not drive implementation.
- For non-trivial refactors, runtime wiring changes, DI changes,
  ownership/boundary moves, and architecture-sensitive PRs, perform a
  second-pass architecture integrity review against the repo architecture docs,
  relevant ADR/change-plan material, actual diff, changed files, and touched
  tests before considering the work complete.

## Documentation Sync Rules

- If pipeline wiring changes, update `docs/ARCHITECTURE/PIPELINE.md`.
- If rules/policies change, update `docs/ARCHITECTURE/ARCHITECTURE.md` and/or
  `docs/ARCHITECTURE/CODING_RULES.md`.
- If ownership boundaries, module APIs, or long-lived architecture tradeoffs
  change, add or update an ADR using `docs/ARCHITECTURE/ADR_TEMPLATE.md`.
- For non-trivial feature/refactor work, start from
  `docs/ARCHITECTURE/PLAN_MODE_START_HERE.md`, then
  `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md` before implementation.
- Do not rely on assumptions; verify facts, get explicit decisions, or document
  unresolved blockers in-repo.

## Required Verification

Run these checks locally for non-trivial changes:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Staged verification policy for in-progress edits:
- Do not default to repo-wide heavy gates after every small edit.
- Prefer the smallest sufficient verification tier first:
  - local compile/debug loop: `dev-fast.bat` or `check-quick.bat`
  - architecture-sensitive or cross-layer/module edits: `./gradlew enforceRules` plus targeted module/class tests when useful
  - slice-complete / merge-ready local proof: `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`
- Escalate early when ownership, timebase, replay, DI wiring, or runtime/device behavior changes would make lighter checks misleading.
- Reserve connected tests for runtime/device/lifecycle behavior or explicit release/CI verification needs.
- Reserve `scripts/qa/*` evidence runs for map/overlay/replay/task gesture/runtime changes that require measured SLO proof.
- If local KSP/cache/lock state is the blocker, prefer `repair-build.bat` or targeted cleanup before repeating heavy gates.

Run when relevant (device/emulator available):

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Run for release/CI verification (full multi-module instrumentation):

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

Windows convenience:

```bat
preflight.bat
```

PR hygiene:
- Use `.github/pull_request_template.md` and complete the architecture drift checklist.

## Legacy Note

`docs/ARCHITECTURE/README_FOR_CODEX.md` is kept as a pointer for older flows.
This `AGENTS.md` file is the primary agent contract.
